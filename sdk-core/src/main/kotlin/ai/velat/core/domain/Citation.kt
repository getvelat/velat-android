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
package ai.velat.core.domain

/**
 * A reference to a specific passage that informed an answer.
 *
 * Citations are returned alongside each generated response so the host
 * application can render "where this came from" affordances — a list of
 * source documents under the answer, deep links into a PDF viewer at the
 * exact page, highlighted passages in the original text, etc.
 *
 * Carries enough metadata to be self-contained: title to display, page
 * number for PDFs, character offsets to highlight the exact passage. The
 * [chunkId] and [documentId] can be used to fetch fuller context from
 * Velat's storage layer if needed.
 *
 * @property documentId Stable identifier of the source document.
 * @property documentTitle Human-readable title supplied when the document was indexed.
 * @property chunkId Stable identifier of the specific chunk.
 * @property text The chunk's text, as included in the prompt.
 * @property pageNumber One-based page number for PDF sources; `null` for non-paginated documents.
 * @property charStart Inclusive character offset into the original document.
 * @property charEnd Exclusive character offset into the original document.
 */
public data class Citation(
    public val documentId: DocumentId,
    public val documentTitle: String,
    public val chunkId: ChunkId,
    public val text: String,
    public val pageNumber: Int?,
    public val charStart: Int,
    public val charEnd: Int,
)
