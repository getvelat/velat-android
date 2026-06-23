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

import ai.velat.core.domain.DocumentId
import ai.velat.core.domain.ScoredChunk

/**
 * Contract for the retrieval stage of a RAG pipeline.
 *
 * A [Retriever] takes a user query and returns the [topK] most relevant
 * chunks from the corpus. Implementations choose how to score and combine
 * dense and sparse signals:
 *
 * - **Dense-only** ([RetrievalMode.DENSE_ONLY]): embedding similarity only.
 *   Fast, simple, weak on keyword-specific queries.
 * - **Sparse-only** ([RetrievalMode.SPARSE_ONLY]): keyword match (BM25)
 *   only. Strong on rare-keyword queries, weak on paraphrased ones.
 * - **Hybrid** ([RetrievalMode.HYBRID]): combine both via Reciprocal Rank
 *   Fusion (RRF). Velat's recommended default; better than either alone
 *   for general workloads.
 *
 * Implementations live in `sdk-rag` because they coordinate an [Embedder]
 * and a [VelatStore]; this interface in `sdk-core` lets the rest of the
 * SDK depend on retrieval without knowing the implementation.
 */
public interface Retriever {

    /**
     * Retrieve the [topK] chunks most relevant to [query].
     *
     * If [documentFilter] is non-null, only chunks from those documents
     * are considered.
     *
     * The returned list is ordered by descending relevance (most relevant
     * first). Scores are implementation-specific; treat them as ordinal,
     * not as absolute relevance values.
     */
    public suspend fun retrieve(
        query: String,
        topK: Int,
        documentFilter: List<DocumentId>? = null,
    ): Result<List<ScoredChunk>>
}

/**
 * How a [Retriever] combines dense (vector) and sparse (keyword) signals.
 *
 * Pass via the host app's `Velat.create()` config to switch behavior at
 * runtime. Hybrid is the production default.
 */
public enum class RetrievalMode {
    /** Use only dense vector similarity. Fastest, weakest on keyword queries. */
    DENSE_ONLY,

    /** Use only BM25 keyword scoring. Weakest on paraphrased queries. */
    SPARSE_ONLY,

    /** Combine both via Reciprocal Rank Fusion. Recommended default. */
    HYBRID,
}
