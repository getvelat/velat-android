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
package ai.velat.core.rag

import ai.velat.core.domain.ChunkId
import ai.velat.core.domain.ScoredChunk

/**
 * Combines multiple ranked result lists into a single fused ranking using
 * Reciprocal Rank Fusion (RRF).
 *
 * RRF is a simple and robust fusion technique introduced by Cormack,
 * Clarke, and Buettcher (2009). Given several ranked lists of the same
 * documents, the RRF score for a document is the sum of `1 / (k + rank)`
 * across all lists that contain it, where `rank` is its 1-based position
 * in that list and `k` is a small smoothing constant.
 *
 * Two properties make RRF nice for hybrid retrieval:
 *
 * 1. **Score-scale invariant.** It uses ranks, not the underlying scores,
 *    so we can fuse cosine similarities with BM25 scores without any
 *    normalization step. The two scoring schemes don't have to be
 *    commensurable; only their rankings matter.
 * 2. **Robust to missing items.** A document appearing in only one of the
 *    input lists still gets a contribution; it just won't beat documents
 *    that appear in multiple lists.
 *
 * In Velat, the dense (cosine) and sparse (BM25) result lists from
 * [ai.velat.core.api.VelatStore] are fused via this class inside the
 * hybrid retriever in `sdk-rag`.
 *
 * @property k Smoothing constant. The original RRF paper recommends `k = 60`;
 *   Velat uses that as the default. Larger `k` weights all ranks more
 *   evenly; smaller `k` puts more emphasis on top results.
 */
public class RrfFuser(public val k: Int = DEFAULT_K) {

    init {
        require(k > 0) { "k must be > 0, was $k" }
    }

    /**
     * Fuse two ranked result lists (dense and sparse) into a single
     * ordering. Returns at most [topK] results.
     *
     * The returned list's [ScoredChunk.score] is the RRF score, not the
     * underlying retrieval score. Treat it as ordinal.
     *
     * @param dense The dense (vector similarity) ranking, most-relevant
     *   first.
     * @param sparse The sparse (keyword) ranking, most-relevant first.
     * @param topK Maximum number of results to return.
     */
    public fun fuse(dense: List<ScoredChunk>, sparse: List<ScoredChunk>, topK: Int): List<ScoredChunk> {
        require(topK > 0) { "topK must be > 0, was $topK" }
        if (dense.isEmpty() && sparse.isEmpty()) return emptyList()

        val scores = mutableMapOf<ChunkId, Float>()
        val chunkLookup = mutableMapOf<ChunkId, ScoredChunk>()

        dense.forEachIndexed { index, sc ->
            val contribution = 1f / (k + index + 1f)
            scores.merge(sc.chunkId, contribution) { a, b -> a + b }
            chunkLookup.putIfAbsent(sc.chunkId, sc)
        }
        sparse.forEachIndexed { index, sc ->
            val contribution = 1f / (k + index + 1f)
            scores.merge(sc.chunkId, contribution) { a, b -> a + b }
            chunkLookup.putIfAbsent(sc.chunkId, sc)
        }

        return scores.entries
            .sortedByDescending { it.value }
            .take(topK)
            .map { entry ->
                val original = chunkLookup.getValue(entry.key)
                original.copy(score = entry.value)
            }
    }

    /** Defaults for [RrfFuser]. */
    public companion object {
        /**
         * Default smoothing constant from the original RRF paper.
         */
        public const val DEFAULT_K: Int = 60
    }
}
