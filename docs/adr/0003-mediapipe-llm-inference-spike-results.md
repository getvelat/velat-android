# ADR 0003: MediaPipe LLM Inference — Spike Results (Day 4)

**Status:** Accepted
**Date:** 2026-06-24 (Day 4)
**Deciders:** Rafal Niski
**Related:** [ADR 0001 — Architecture Overview](0001-architecture.md), Decision 5 (Inference Engine)

## Context

Day 3 locked the `LlmEngine` interface in `sdk-core`. Day 4's job was to verify
that **Google AI Edge MediaPipe LLM Inference** — the engine choice from ADR 0001 —
can actually satisfy that contract on the target hardware (Pixel 8, Pixel 7a)
without breaking the streaming-Flow assumption baked into the interface.

If MediaPipe couldn't stream, couldn't be cancelled cleanly, or had hidden
threading constraints that fought the `Flow<Token>` shape, the contract would
need re-design before Day 5 implementations stacked on top of it. Better to
discover that now than after `sdk-storage-sqlite` and `sdk-rag` are wired up.

This ADR records:
1. The actual MediaPipe 0.10.21 API as observed (it differs from older docs).
2. The mapping from MediaPipe's threading model to our `Flow<Token>`.
3. Measured cold-load / TTFT / decode-rate numbers on real devices.
4. Constraints discovered that callers of `LlmEngine` need to know.

## What MediaPipe 0.10.21 Actually Looks Like

The MediaPipe LLM Inference Java API at version `com.google.mediapipe:tasks-genai:0.10.21`
is **not** what older Google samples and blog posts show. Verified directly via
`javap` against the published AAR. The shape:

```text
LlmInference                       // engine, owns the model + listener
├── createFromOptions(ctx, opts)
├── LlmInferenceOptions.Builder
│   ├── setModelPath(String)
│   ├── setMaxTokens(int)
│   ├── setMaxTopK(int)
│   ├── setResultListener(ProgressListener<String>)   ← listener lives HERE
│   └── setErrorListener(ErrorListener)
└── close()

LlmInferenceSession                // per-request session sharing engine state
├── createFromOptions(engine, sessionOpts)
├── LlmInferenceSessionOptions.Builder
│   ├── setTemperature(float)
│   ├── setTopK(int)
│   └── setTopP(float)
├── addQueryChunk(String)
├── generateResponseAsync()        ← NO listener parameter
└── close()
```

Two non-obvious consequences:

**(a) The result listener is per-engine, not per-call.** Older blog posts show
`generateResponseAsync(listener)` — that overload does not exist in 0.10.21.
You install the listener once on the `LlmInference` engine; every session's
streaming output flows through it. There is no way to attach a callback to a
specific generate() invocation.

**(b) Only one in-flight generate per engine is safe.** Because the listener is
single, two concurrent generate() calls would race on the same callback slot
and interleave tokens unpredictably. The API doesn't document this — the
constraint is structural.

## Mapping MediaPipe → `LlmEngine.generate(): Flow<Token>`

`sdk-core`'s contract is per-call streaming:

```kotlin
public fun generate(prompt: String, options: GenerationOptions): Flow<Token>
```

Each call must return its own stream and be independently cancellable via
coroutine cancellation. MediaPipe gives us a per-engine listener instead.
Bridge:

```kotlin
private val activeListener = AtomicReference<ResultListener?>(null)

// Installed once at engine creation, delegates to whatever is in the slot
private val rootListener = ProgressListener<String> { partial, done ->
    activeListener.get()?.invoke(partial, done)
}

override fun generate(prompt: String, options: GenerationOptions): Flow<Token> =
    callbackFlow {
        val streamHandle = beginGenerate(prompt, options) { close(it) }
            ?: return@callbackFlow
        activeListener.set { partial, done ->
            if (!partial.isNullOrEmpty()) trySend(Token(partial))
            if (done) close()
        }
        kickOffGeneration(streamHandle) { close(it) }
        awaitClose { releaseSlot(streamHandle) }
    }
```

`beginGenerate` uses `activeListener.compareAndSet(null, …)` to refuse a second
concurrent caller — they get `VelatError.GenerationTimeout` (will rename to
`Busy` once we add it). `releaseSlot` clears the listener slot and closes the
session.

This pattern keeps the per-call contract intact at the `sdk-core` boundary even
though the engine underneath is per-engine.

## Measured Performance (TODO — fill in after device runs)

Tested with **Gemma 2 2B IT INT4** (`Gemma2-2B-IT_multi-prefill-seq_q4_ekv4096.task`,
1.36 GB) via `samples/engine-spike`.

| Metric | Pixel 8 (Tensor G3) | Pixel 7a (Tensor G2) |
|---|---|---|
| Cold load (first `createFromOptions`) | TODO ms | TODO ms |
| Warm load (cached on disk) | TODO ms | TODO ms |
| TTFT — 5-token prompt | TODO ms | TODO ms |
| TTFT — 200-token prompt | TODO ms | TODO ms |
| Sustained decode rate | TODO tok/s | TODO tok/s |
| Peak RAM during decode | TODO MB | TODO MB |
| Context window used | 4096 tokens | 4096 tokens |
| Backend | CPU (XNNPACK) | CPU (XNNPACK) |

GPU backend (LiteRT GPU delegate) deferred to a follow-up ADR — MediaPipe's
GPU path for Gemma 2 has known correctness issues on Tensor SoCs as of the
0.10.21 release (see [issue ref pending verification]).

## Constraints Surfaced for Callers of `LlmEngine`

These need to flow into the SDK's `Velat.create()` composition root and
the `sdk` module's user-facing docs:

1. **Single generation at a time per engine.** Calling `generate()` while a
   previous Flow is still collecting throws. Apps that want concurrent chats
   need multiple `LlmEngine` instances (which means multiple loaded models —
   not free). v0.1 doesn't address this; we'll surface a `BusyError` and let
   apps serialize on their side.

2. **`close()` must wait for in-flight generation.** MediaPipe's
   `LlmInference.close()` will tear down the listener mid-stream and crash the
   native layer if a session is still active. `MediaPipeLlmEngine.close()`
   currently checks `activeListener.get() == null` and throws if not. UX
   responsibility: callers cancel scopes before closing.

3. **Model file must outlive the engine.** MediaPipe holds the file open via
   mmap. Deleting the `.task` file while the engine is loaded crashes the
   process. We require callers to manage model file lifecycle.

4. **Context size is fixed at engine creation.** Cannot change between
   sessions. `GenerationOptions.maxTokens` is bounded by `contextSize -
   promptTokens`. The engine doesn't enforce this — over-long prompts produce
   garbage output rather than a clean error. We will add length validation
   in `MediaPipeLlmEngine.generate()` once we have a real tokenizer (Day 5
   alongside `Embedder`).

5. **ABI is arm64-v8a only.** MediaPipe ships armeabi-v7a and x86_64 too, but
   we don't ship them — INT4 Gemma 2 doesn't run on 32-bit ARM in practice
   (out of address space) and we have no 64-bit x86 device target.

## Decision

Adopt `MediaPipeLlmEngine` as the v0.1 implementation of `LlmEngine`. Ship in
the `sdk-engine-mediapipe` module, depending on `tasks-genai:0.10.21`.

The `LlmEngine` interface in `sdk-core` does NOT change — the
`AtomicReference<ResultListener?>` bridge proves it doesn't need to.

The constraints above become **`MediaPipeLlmEngine` KDoc**, not `LlmEngine`
KDoc. They're properties of *this* implementation. A future
`LlamaCppLlmEngine` might not share them.

## What This Does Not Cover (and why)

- **Selection between INT4 / INT8 / FP16 Gemma variants.** Out of scope for
  Day 4. We picked INT4 because it's the only one that fits memory budget on
  Pixel 7a. Other quant levels are a `v0.2+` exploration if someone needs them.
- **Custom tokenizer for chunk-token-count enforcement.** Belongs with the
  `Embedder` work on Day 5; both wrap MediaPipe text components.
- **Model download / caching strategy.** Day 7's job — `ModelManager` interface
  is not on the v0.1 critical path.

## References

- MediaPipe LLM Inference Android Guide:
  https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android
- AAR inspected: `~/.gradle/caches/.../tasks-genai-0.10.21.aar`
- Spike code: [`sdk-engine-mediapipe/src/main/kotlin/ai/velat/engine/mediapipe/MediaPipeLlmEngine.kt`](../../sdk-engine-mediapipe/src/main/kotlin/ai/velat/engine/mediapipe/MediaPipeLlmEngine.kt)
- Sample app + measurement README: [`samples/engine-spike/README.md`](../../samples/engine-spike/README.md)
