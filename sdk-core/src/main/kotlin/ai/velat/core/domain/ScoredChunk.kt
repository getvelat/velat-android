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
 * A [Chunk] paired with a relevance score from a retrieval operation.
 *
 * Higher [score] means more relevant to the query. The exact scale depends
 * on the retrieval strategy that produced the result:
 *
 * - **Dense retrieval (cosine similarity)**: typically in `[0.0, 1.0]`,
 *   where `1.0` means identical to the query embedding.
 * - **Sparse retrieval (BM25)**: unbounded positive values; magnitude is
 *   query- and corpus-dependent and is only meaningful for ordering.
 * - **Hybrid retrieval (RRF fusion)**: small positive values typically in
 *   `[0.0, ~0.1]`; only meaningful for ordering, not as absolute relevance.
 *
 * In general, treat scores as ordinal (compare within a single result list)
 * rather than as absolute relevance metrics.
 *
 * @property chunkId Identifier of the underlying chunk.
 * @property documentId Identifier of the document this chunk belongs to.
 * @property chunk The chunk's content and offsets.
 * @property score Relevance score; higher is more relevant.
 */
public data class ScoredChunk(
    public val chunkId: ChunkId,
    public val documentId: DocumentId,
    public val chunk: Chunk,
    public val score: Float,
)
