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

/**
 * Contract for a cross-encoder reranker.
 *
 * A cross-encoder takes a (query, candidate) pair and produces a more
 * accurate relevance score than embedding similarity, at the cost of
 * being substantially slower per pair (one forward pass per candidate
 * versus the dot product of two precomputed embeddings).
 *
 * Velat's retrieval pipeline can use a cross-encoder in a two-stage
 * setup: cheap initial retrieval returns ~20-50 candidates, the
 * cross-encoder rescores them, and the top 5 are passed to the LLM. This
 * boosts precision for accuracy-sensitive use cases (legal, medical).
 *
 * **Declared here for API stability; not used by v0.1.** v0.2 adds the
 * Pro implementation `sdk-rag-advanced` which provides a concrete
 * cross-encoder backed by `bge-reranker-v2-m3` and a wrapping retriever
 * that uses it.
 */
public interface CrossEncoder : AutoCloseable {

    /**
     * Score the relevance of each candidate to the query. The returned
     * list is parallel to [candidates] — `result[i]` is the score for
     * `candidates[i]` — not reordered.
     *
     * Higher scores indicate more relevant. The exact scale is
     * implementation-specific; treat as ordinal.
     */
    public suspend fun score(query: String, candidates: List<String>): Result<List<Float>>
}
