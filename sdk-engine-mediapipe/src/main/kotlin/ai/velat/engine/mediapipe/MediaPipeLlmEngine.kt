/*
 * Copyright 2026 Rafal Niski
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.velat.engine.mediapipe

import ai.velat.core.api.GenerationOptions
import ai.velat.core.api.LlmEngine
import ai.velat.core.api.LlmEngineInfo
import ai.velat.core.domain.Token
import ai.velat.core.error.VelatError
import android.content.Context
import com.google.mediapipe.tasks.core.OutputHandler.ProgressListener
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * Implementation of [LlmEngine] backed by Google's MediaPipe LLM Inference.
 *
 * One [MediaPipeLlmEngine] instance owns one loaded model. Loading happens
 * eagerly when the instance is constructed (via [fromFile]). The model file
 * must already exist on disk at the supplied path — [MediaPipeLlmEngine]
 * does not download.
 *
 * Architecture note (MediaPipe API quirk): in MediaPipe LLM Inference 0.10.x,
 * the streaming callback is registered ONCE on the [LlmInference] at
 * construction time, not per-session. There is no way to set a different
 * listener for a different generation. We work around this with a single
 * [AtomicReference] that holds the currently-active listener; [generate]
 * installs its own callback into the slot for the duration of the Flow, and
 * the MediaPipe-level listener delegates to whatever is in the slot.
 *
 * Sessions: every call to [generate] creates a fresh MediaPipe
 * [LlmInferenceSession] for one prompt's sampling configuration, then closes
 * it when generation finishes or the collector is cancelled. This means
 * [MediaPipeLlmEngine] is stateless across calls — multi-turn conversations
 * are the host application's responsibility.
 *
 * Concurrency: a single engine supports only ONE active [generate] flow at
 * a time. Concurrent [generate] calls from different coroutines fail fast
 * with [VelatError.GenerationTimeout]. Host applications should serialize
 * their own usage (e.g., via a `Mutex` in a higher-level `Velat` class).
 *
 * Example:
 * ```
 * val engine = MediaPipeLlmEngine.fromFile(
 *     context = applicationContext,
 *     modelFile = File(filesDir, "gemma-2-2b-it-int4.task"),
 * ).getOrThrow()
 *
 * engine.use {
 *     it.warmup()
 *     it.generate("What is the capital of France?").collect { token ->
 *         print(token.text)
 *     }
 * }
 * ```
 */
public class MediaPipeLlmEngine private constructor(
    private val llmInference: LlmInference,
    private val activeListener: AtomicReference<ResultListener?>,
    override val info: LlmEngineInfo,
) : LlmEngine {

    /** Set to `true` after [close] has been called. Idempotent close. */
    private val closed = AtomicBoolean(false)

    override suspend fun warmup(): Result<Unit> = runCatching {
        checkNotClosed()
        withContext(Dispatchers.IO) {
            runOneShotGeneration(
                prompt = WARMUP_PROMPT,
                options = GenerationOptions(
                    temperature = 0.0f,
                    topK = 1,
                    maxTokens = WARMUP_MAX_TOKENS,
                ),
                onToken = { /* discard warmup output */ },
            )
        }
    }.mapVelatError { cause ->
        VelatError.ModelLoad("Warmup failed: ${cause.message}", cause)
    }

    override fun generate(prompt: String, options: GenerationOptions): Flow<Token> = callbackFlow {
        val streamHandle = beginGenerate(prompt, options) { close(it) } ?: return@callbackFlow
        activeListener.set { partial, done ->
            if (!partial.isNullOrEmpty()) trySend(Token(partial))
            if (done) close()
        }
        kickOffGeneration(streamHandle) { close(it) }
        // When the Flow terminates for ANY reason (normal completion, error,
        // collector cancellation), release the listener slot and close the
        // session. Without this, native resources leak.
        awaitClose {
            releaseSlot(streamHandle)
        }
    }

    override suspend fun reset(): Result<Unit> = runCatching {
        checkNotClosed()
        // v0.1: stateless across generate() calls. No per-engine state to reset.
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            activeListener.set(null)
            runCatching { llmInference.close() }
        }
    }

    private fun checkNotClosed() {
        check(!closed.get()) { "MediaPipeLlmEngine is closed" }
    }

    private fun createSession(options: GenerationOptions): LlmInferenceSession {
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(options.topK.coerceAtLeast(1))
            .setTopP(options.topP)
            .setTemperature(options.temperature)
            .build()
        return LlmInferenceSession.createFromOptions(llmInference, sessionOptions)
    }

    /**
     * Begin a streaming generation: validate engine state, claim the listener
     * slot, create a session. Returns a handle on success, null on failure
     * (with [onFailure] invoked with the typed error).
     */
    private fun beginGenerate(
        prompt: String,
        options: GenerationOptions,
        onFailure: (VelatError) -> Unit,
    ): StreamHandle? {
        if (closed.get()) {
            onFailure(VelatError.ModelLoad("Engine is closed; cannot generate"))
            return null
        }
        if (!activeListener.compareAndSet(null, NOOP_LISTENER)) {
            onFailure(VelatError.GenerationTimeout("Another generation is already in progress"))
            return null
        }
        val session = openSession(options) { error ->
            activeListener.set(null)
            onFailure(error)
        } ?: return null
        return StreamHandle(session, prompt)
    }

    /**
     * Issue addQueryChunk + generateResponseAsync. On failure, releases
     * resources held by [handle] and forwards a typed error.
     */
    private fun kickOffGeneration(handle: StreamHandle, onFailure: (VelatError) -> Unit) {
        @Suppress("TooGenericExceptionCaught") // native bridge can throw anything
        try {
            handle.session.addQueryChunk(handle.prompt)
            handle.session.generateResponseAsync()
        } catch (t: Throwable) {
            releaseSlot(handle)
            onFailure(VelatError.GenerationTimeout("Generation failed: ${t.message}", t))
        }
    }

    /** Clear the listener slot and close the session held by [handle]. */
    private fun releaseSlot(handle: StreamHandle) {
        activeListener.set(null)
        runCatching { handle.session.close() }
    }

    /**
     * Create a [LlmInferenceSession], mapping OOM / generic native throws to
     * typed [VelatError]. Returns null on failure (with [onFailure] invoked).
     */
    @Suppress("TooGenericExceptionCaught") // catch-Throwable is intentional for native bridge
    private fun openSession(
        options: GenerationOptions,
        onFailure: (VelatError) -> Unit,
    ): LlmInferenceSession? = try {
        createSession(options)
    } catch (oom: OutOfMemoryError) {
        onFailure(VelatError.InsufficientMemory("Out of memory creating session", oom))
        null
    } catch (t: Throwable) {
        onFailure(VelatError.ModelLoad("Failed to create session: ${t.message}", t))
        null
    }

    /** Holds the per-generation resources we need to release together. */
    private data class StreamHandle(val session: LlmInferenceSession, val prompt: String)

    /**
     * Run a single prompt-to-completion generation using a fresh session.
     * Used by [warmup]. Suspends until MediaPipe signals `done` or the
     * coroutine is cancelled.
     */
    private suspend fun runOneShotGeneration(
        prompt: String,
        options: GenerationOptions,
        onToken: (Token) -> Unit,
    ): Unit = suspendCancellableCoroutine { continuation ->
        val listener = oneShotListener(continuation, onToken)
        if (!activeListener.compareAndSet(null, listener)) {
            continuation.resumeWith(
                Result.failure(IllegalStateException("Another generation is already in progress")),
            )
            return@suspendCancellableCoroutine
        }
        val session = openSession(options) { err ->
            activeListener.set(null)
            if (continuation.isActive) continuation.resumeWith(Result.failure(err))
        } ?: return@suspendCancellableCoroutine

        continuation.invokeOnCancellation {
            activeListener.set(null)
            runCatching { session.close() }
        }
        startOneShot(session, prompt, continuation)
    }

    private fun oneShotListener(
        continuation: kotlinx.coroutines.CancellableContinuation<Unit>,
        onToken: (Token) -> Unit,
    ): ResultListener = { partial, done ->
        if (!partial.isNullOrEmpty()) onToken(Token(partial))
        if (done && continuation.isActive) continuation.resume(Unit)
    }

    @Suppress("TooGenericExceptionCaught") // native bridge throws unchecked
    private fun startOneShot(
        session: LlmInferenceSession,
        prompt: String,
        continuation: kotlinx.coroutines.CancellableContinuation<Unit>,
    ) {
        try {
            session.addQueryChunk(prompt)
            session.generateResponseAsync()
        } catch (t: Throwable) {
            activeListener.set(null)
            runCatching { session.close() }
            if (continuation.isActive) continuation.resumeWith(Result.failure(t))
        }
    }

    /** Factory methods for [MediaPipeLlmEngine]. */
    public companion object {

        /**
         * Load the model at [modelFile] and return a ready-to-use engine.
         *
         * Loading is performed on [Dispatchers.IO] because MediaPipe's model
         * load reads the entire weight file (gigabytes) into RAM and is not
         * suitable for the main thread.
         */
        public suspend fun fromFile(
            context: Context,
            modelFile: File,
            contextSize: Int = DEFAULT_CONTEXT_SIZE,
            maxTopK: Int = DEFAULT_MAX_TOP_K,
        ): Result<MediaPipeLlmEngine> = withContext(Dispatchers.IO) {
            runCatching {
                requireModelFileExists(modelFile)
                buildEngine(context, modelFile, contextSize, maxTopK)
            }.mapVelatError { cause -> mapLoadError(cause, modelFile) }
        }

        private fun requireModelFileExists(modelFile: File) {
            require(modelFile.exists()) {
                "Model file does not exist: ${modelFile.absolutePath}"
            }
            require(modelFile.length() > 0L) {
                "Model file is empty: ${modelFile.absolutePath}"
            }
        }

        private fun buildEngine(
            context: Context,
            modelFile: File,
            contextSize: Int,
            maxTopK: Int,
        ): MediaPipeLlmEngine {
            // The result-listener slot is shared between the MediaPipe
            // callback and the engine instance. Created BEFORE the
            // LlmInference because the listener closes over it.
            val activeListener = AtomicReference<ResultListener?>(null)

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(contextSize)
                .setMaxTopK(maxTopK)
                .setResultListener(
                    ProgressListener<String> { result, done ->
                        activeListener.get()?.invoke(result, done)
                    },
                )
                .build()

            val inference = LlmInference.createFromOptions(
                context.applicationContext,
                options,
            )

            return MediaPipeLlmEngine(
                llmInference = inference,
                activeListener = activeListener,
                info = LlmEngineInfo(
                    modelName = modelFile.nameWithoutExtension,
                    contextSize = contextSize,
                    maxOutputTokens = contextSize,
                ),
            )
        }

        private fun mapLoadError(cause: Throwable, modelFile: File): VelatError = when (cause) {
            is OutOfMemoryError ->
                VelatError.InsufficientMemory(
                    "Out of memory loading model ${modelFile.name}",
                    cause,
                )

            is IllegalArgumentException ->
                VelatError.ModelLoad(
                    "Invalid model file or options: ${cause.message}",
                    cause,
                )

            else ->
                VelatError.ModelLoad(
                    "Failed to load model ${modelFile.name}: ${cause.message}",
                    cause,
                )
        }

        /** Default value for [fromFile]'s `contextSize`. */
        public const val DEFAULT_CONTEXT_SIZE: Int = 4096

        /** Default value for [fromFile]'s `maxTopK`. */
        public const val DEFAULT_MAX_TOP_K: Int = 40

        /** Prompt used by [warmup] to trigger eager model load + first inference. */
        private const val WARMUP_PROMPT: String = "Hi"

        /** Maximum tokens generated during [warmup]. Small to keep warmup fast. */
        private const val WARMUP_MAX_TOKENS: Int = 4

        /**
         * Placeholder listener used to claim the [activeListener] slot before
         * the real per-stream listener is installed. Compare-and-set with
         * `null` is the contended path; this constant lets us write the
         * intermediate state without per-call allocation.
         */
        private val NOOP_LISTENER: ResultListener = { _, _ -> }
    }
}

/**
 * Callback signature for MediaPipe streaming results. The `partial` parameter
 * may be null when the underlying native layer signals an error or empty
 * tick; callers must null-check before processing.
 */
private typealias ResultListener = (partial: String?, done: Boolean) -> Unit

/**
 * Replace any failure in this [Result] with a value produced by [transform].
 * Successes pass through unchanged.
 */
private inline fun <T> Result<T>.mapVelatError(transform: (Throwable) -> VelatError): Result<T> =
    if (isSuccess) this else Result.failure(transform(exceptionOrNull()!!))
