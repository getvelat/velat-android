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
package com.velat.sample.enginespike

import ai.velat.core.api.GenerationOptions
import ai.velat.core.api.LlmEngine
import ai.velat.engine.mediapipe.MediaPipeLlmEngine
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * UI-state holder for the engine-spike screen.
 *
 * Owns the [LlmEngine] instance for the activity's lifetime and exposes
 * three observable flows: model load status, current generation progress,
 * and the prompt text the user is editing.
 *
 * Lifecycle: the engine is closed in [onCleared] so native resources are
 * released when the user navigates away (or the activity is destroyed).
 *
 * This is a SPIKE app — not a production sample. Patterns here (e.g., owning
 * a 1.5 GB model in a single ViewModel) are deliberately simple for
 * verification. The production `sdk` module's `Velat` class will handle
 * lifecycle, scope, and resource management more carefully.
 */
public class EngineSpikeViewModel : ViewModel() {

    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotLoaded(path = ""))
    public val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    private val _generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)
    public val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    private val _promptInput = MutableStateFlow("What is the capital of France?")
    public val promptInput: StateFlow<String> = _promptInput.asStateFlow()

    private var engine: LlmEngine? = null

    init {
        // Re-evaluate model presence on construction so the UI shows the
        // correct initial state.
        // We can't access context here; first refresh happens in loadModel().
    }

    /**
     * Locate the model file in the app's external files dir and (if found)
     * trigger an engine load. Called from the UI's "Load model" button.
     */
    public fun loadModel(context: Context) {
        viewModelScope.launch {
            val modelFile = resolveModelFile(context)
            if (!modelFile.exists()) {
                _modelState.value = ModelState.Missing(expectedPath = modelFile.absolutePath)
                return@launch
            }

            val startMs = System.currentTimeMillis()
            _modelState.value = ModelState.Loading(elapsedMs = 0L)

            // We can't easily report progress from inside MediaPipe's load, so
            // we just show "Loading…" until the suspend function returns.
            val result = MediaPipeLlmEngine.fromFile(
                context = context.applicationContext,
                modelFile = modelFile,
            )

            val coldLoadMs = System.currentTimeMillis() - startMs

            result.fold(
                onSuccess = { loaded ->
                    engine = loaded
                    _modelState.value = ModelState.Ready(
                        modelName = loaded.info.modelName,
                        coldLoadMs = coldLoadMs,
                    )
                },
                onFailure = { cause ->
                    _modelState.value = ModelState.Error(
                        message = cause.message ?: cause::class.simpleName.orEmpty(),
                    )
                },
            )
        }
    }

    public fun onPromptChange(newValue: String) {
        _promptInput.value = newValue
    }

    public fun generate() {
        val activeEngine = engine ?: return
        val prompt = _promptInput.value.takeIf { it.isNotBlank() } ?: return

        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            var firstTokenAt: Long? = null
            var tokens = 0
            val builder = StringBuilder()

            _generationState.value = GenerationState.Streaming(
                text = "",
                startedAtMs = startedAt,
                ttftMs = null,
                tokenCount = 0,
            )

            runCatching {
                activeEngine
                    .generate(prompt, GenerationOptions())
                    .collect { token ->
                        if (firstTokenAt == null) {
                            firstTokenAt = System.currentTimeMillis()
                        }
                        tokens++
                        builder.append(token.text)

                        _generationState.update {
                            GenerationState.Streaming(
                                text = builder.toString(),
                                startedAtMs = startedAt,
                                ttftMs = firstTokenAt?.let { it - startedAt },
                                tokenCount = tokens,
                            )
                        }
                    }
            }.fold(
                onSuccess = {
                    val totalMs = System.currentTimeMillis() - startedAt
                    val ttft = firstTokenAt?.let { it - startedAt } ?: totalMs
                    val decodeSec = (totalMs - ttft).coerceAtLeast(1) / 1000.0
                    val decodeRate = tokens / decodeSec.coerceAtLeast(0.001)
                    _generationState.value = GenerationState.Done(
                        text = builder.toString(),
                        ttftMs = ttft,
                        totalMs = totalMs,
                        tokenCount = tokens,
                        decodeRate = decodeRate,
                    )
                },
                onFailure = { cause ->
                    _generationState.value = GenerationState.Error(
                        message = cause.message ?: cause::class.simpleName.orEmpty(),
                    )
                },
            )
        }
    }

    override fun onCleared() {
        engine?.close()
        engine = null
        super.onCleared()
    }

    private fun resolveModelFile(context: Context): File {
        val baseDir = context.getExternalFilesDir(null)
            ?: context.filesDir
        val modelsDir = File(baseDir, "models").apply { mkdirs() }
        return File(modelsDir, MODEL_FILENAME)
    }

    public companion object {
        /**
         * Filename the spike app expects under
         * `context.getExternalFilesDir(null)/models/`. User pushes the
         * Google AI Edge Gemma 2 2B INT4 task bundle here via:
         *
         *   adb push gemma-2-2b-it-int4.task \
         *     /sdcard/Android/data/com.velat.sample.enginespike/files/models/
         */
        public const val MODEL_FILENAME: String = "gemma-2-2b-it-int4.task"
    }
}

/** UI-side model load state. */
public sealed interface ModelState {
    /** Model file does not exist at the expected path. */
    public data class Missing(val expectedPath: String) : ModelState

    /** Model file exists on disk but engine has not been initialized yet. */
    public data class NotLoaded(val path: String) : ModelState

    /** Engine load is in progress. */
    public data class Loading(val elapsedMs: Long) : ModelState

    /** Engine is ready to serve generations. */
    public data class Ready(val modelName: String, val coldLoadMs: Long) : ModelState

    /** Engine load failed; [message] is human-readable. */
    public data class Error(val message: String) : ModelState
}

/** UI-side generation state. */
public sealed interface GenerationState {
    /** No generation has run yet (or it has been reset). */
    public data object Idle : GenerationState

    /** Generation is in progress. Updated on every token. */
    public data class Streaming(val text: String, val startedAtMs: Long, val ttftMs: Long?, val tokenCount: Int) :
        GenerationState

    /** Generation finished successfully. */
    public data class Done(
        val text: String,
        val ttftMs: Long,
        val totalMs: Long,
        val tokenCount: Int,
        val decodeRate: Double,
    ) : GenerationState

    /** Generation failed before completion. */
    public data class Error(val message: String) : GenerationState
}
