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
 * A single piece of text produced during document indexing.
 *
 * Documents are split into chunks before being embedded and stored. Each
 * chunk is the unit of retrieval — when Velat answers a query, it
 * retrieves the most relevant chunks and feeds them as context to the LLM.
 *
 * A chunk carries enough metadata to be cited precisely: the source
 * document is implied by the containing [DocumentInfo], and the offsets
 * here ([charStart], [charEnd]) point back to the original text so the
 * host application can highlight the exact passage.
 *
 * The [tokenCount] is an approximation suitable for context-budget
 * planning. It is computed at chunking time by the [ai.velat.core.api.Chunker]
 * implementation in use.
 *
 * @property text The chunk's textual content. Never empty.
 * @property charStart Inclusive character offset into the source document where this chunk begins.
 * @property charEnd Exclusive character offset into the source document where this chunk ends.
 * @property pageNumber One-based page number for PDF sources; `null` for documents without page structure.
 * @property tokenCount Approximate token count used for context-budget planning.
 */
public data class Chunk(
    public val text: String,
    public val charStart: Int,
    public val charEnd: Int,
    public val pageNumber: Int?,
    public val tokenCount: Int,
)
