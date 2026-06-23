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
package ai.velat.core.api

import ai.velat.core.domain.SourceType
import okio.Path

/**
 * Contract for converting a document source (file path, byte stream,
 * plain text) into extracted text suitable for chunking and embedding.
 *
 * One implementation per source format:
 *
 * - `TextLoader` (sdk-core, built-in): plain text and markdown.
 * - `PdfLoader` (sdk-pdf, Android-only): PDF via PDFBox-Android.
 * - Future: DOCX, image OCR via ML Kit, audio transcription via Whisper.
 *
 * Loaders preserve page structure when the source format has one (PDFs
 * yield one [LoadedPage] per page). Sources without page structure
 * produce a single [LoadedPage] with `pageNumber = null`.
 */
public interface DocumentLoader {

    /**
     * File extensions this loader supports, lower-case, without the leading
     * dot. The host application's dispatcher uses this to route a [Path]
     * to the right loader.
     *
     * Example: `setOf("pdf")`, `setOf("txt", "md")`.
     */
    public val supportedExtensions: Set<String>

    /**
     * Load [source] and return its extracted text grouped by page (or as
     * a single page for unstructured formats).
     */
    public suspend fun load(source: DocumentSource): Result<LoadedDocument>
}

/**
 * Where a loader gets its input from.
 *
 * Velat accepts three forms so callers can integrate naturally with their
 * platform: file paths (most common), in-memory byte arrays (for content
 * received over the network without writing to disk), and pre-extracted
 * text (for tests and host-app-side custom extraction).
 */
public sealed interface DocumentSource {

    /**
     * A file on the device's filesystem, addressed by [okio.Path].
     *
     * @property path Location of the file to load.
     */
    public data class FromPath(public val path: Path) : DocumentSource

    /**
     * Raw bytes in memory, optionally with the original filename for type hints.
     *
     * @property bytes The document content as a byte array.
     * @property filename Original filename if known, used for extension-based
     *   loader routing.
     */
    public class FromBytes(public val bytes: ByteArray, public val filename: String?) : DocumentSource {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FromBytes) return false
            if (filename != other.filename) return false
            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = 31 * (filename?.hashCode() ?: 0) + bytes.contentHashCode()
    }

    /**
     * Pre-extracted text supplied directly, optionally with an identifier.
     *
     * @property content The extracted text content.
     * @property identifier Optional identifier (e.g., URL, conversation ID)
     *   to use as the title of the indexed document.
     */
    public data class FromText(public val content: String, public val identifier: String? = null) : DocumentSource
}

/**
 * The extracted contents of a single document.
 *
 * @property pages One or more pages of extracted text. Always non-empty.
 * @property sourceType The format of the original source.
 */
public data class LoadedDocument(public val pages: List<LoadedPage>, public val sourceType: SourceType)

/**
 * Extracted text from one logical page of a document.
 *
 * @property text The page's extracted text. May be empty (e.g., a blank
 *   PDF page); callers should filter empty pages before chunking.
 * @property pageNumber One-based page number for paginated sources (PDF),
 *   `null` for unstructured sources.
 */
public data class LoadedPage(public val text: String, public val pageNumber: Int?)
