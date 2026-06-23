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
package ai.velat.core.error

/**
 * Sealed hierarchy of errors that Velat operations can produce.
 *
 * Velat operations return `Result<T>` rather than throwing for expected
 * failure modes (model not loaded, storage full, generation timed out).
 * The `Result` failure path carries an instance of [VelatError] so callers
 * can match on its sealed subtypes exhaustively in `when`:
 *
 * ```
 * velat.addDocument(file).onFailure { cause ->
 *     when (cause) {
 *         is VelatError.ModelLoad -> showModelDownloadDialog()
 *         is VelatError.InsufficientMemory -> showLowMemoryWarning()
 *         is VelatError.StorageFailure -> showStorageErrorWithRetry()
 *         is VelatError.DocumentParse -> showUnsupportedFormatMessage()
 *         is VelatError.Cancelled -> { /* user cancelled, no UI needed */ }
 *         is VelatError.ModelDownload,
 *         is VelatError.GenerationTimeout,
 *         is VelatError.UnsupportedDevice -> showGenericErrorWithDetails(cause)
 *     }
 * }
 * ```
 *
 * Velat does NOT throw [VelatError] from public APIs; it returns it
 * through `Result` so the compiler enforces handling. The fact that
 * [VelatError] extends [RuntimeException] is an implementation detail that
 * lets us include the failure chain (cause) and stack trace without
 * defining our own infrastructure.
 *
 * @param message Human-readable description of the failure (English).
 * @param cause Underlying cause; preserved for diagnostics and re-throwing
 *   contexts (e.g., wrapping a Coroutine cancellation).
 */
public sealed class VelatError(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {

    /**
     * Failed to load the requested LLM or embedding model file from disk
     * or memory. Possible causes: file missing, file corrupted, format
     * incompatible with the configured inference engine, insufficient
     * memory to load the model.
     */
    public class ModelLoad(message: String, cause: Throwable? = null) : VelatError(message, cause)

    /**
     * Failed to download a required model from the configured CDN.
     * Possible causes: no network connectivity, CDN unavailable, signature
     * verification failed (downloaded file does not match expected hash),
     * insufficient disk space.
     */
    public class ModelDownload(message: String, cause: Throwable? = null) : VelatError(message, cause)

    /**
     * The operation requires more memory than is currently available on
     * the device. Typical cause is loading a model whose RAM footprint
     * (weights + KV cache) exceeds the app's available heap.
     *
     * Common remediation: choose a smaller quantization (e.g., INT4
     * instead of INT8) or a smaller model variant, or reduce the
     * configured context size.
     */
    public class InsufficientMemory(message: String, cause: Throwable? = null) : VelatError(message, cause)

    /**
     * Persistent storage operation failed. Possible causes: database file
     * locked by another process, disk full, file system permissions,
     * corruption of the underlying SQLite database.
     */
    public class StorageFailure(message: String, cause: Throwable? = null) : VelatError(message, cause)

    /**
     * Generation did not complete within the configured timeout. The
     * inference engine may have stalled (e.g., model produced a sequence
     * without a stop token) or the device may be thermally throttled.
     *
     * Output produced before the timeout has already been emitted; this
     * error only signals that no further output will arrive.
     */
    public class GenerationTimeout(message: String, cause: Throwable? = null) : VelatError(message, cause)

    /**
     * The current device does not meet Velat's hardware or OS requirements.
     * Returned from `Velat.checkSupport()` and as a wrapped failure when
     * `Velat.create()` is called on an unsupported device.
     */
    public class UnsupportedDevice(message: String, cause: Throwable? = null) : VelatError(message, cause)

    /**
     * Failed to parse or extract text from a document. Possible causes:
     * unsupported format, image-only PDF with no embedded text and OCR
     * disabled, corrupt file, encrypted PDF without a password.
     */
    public class DocumentParse(message: String, cause: Throwable? = null) : VelatError(message, cause)

    /**
     * The operation was cancelled, either explicitly by the caller (via
     * coroutine cancellation) or implicitly (e.g., the host scope was
     * destroyed). Distinct from other failures because no recovery action
     * is appropriate — the caller asked for it to stop.
     */
    public class Cancelled(message: String) : VelatError(message)
}
