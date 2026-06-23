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
 * Contract for a text-embedding model.
 *
 * Embedders convert text into fixed-length floating-point vectors whose
 * geometric proximity reflects semantic similarity: texts about the same
 * topic produce vectors that are close (small cosine distance), unrelated
 * texts produce vectors that are far apart.
 *
 * Velat uses embeddings during both indexing (each chunk is embedded once
 * and stored) and querying (each query is embedded once per request, then
 * compared against stored chunk embeddings for retrieval).
 *
 * Lifecycle: like [LlmEngine], implementations hold native resources and
 * MUST be closed. Use Kotlin's `use {}` block for scoped lifetimes.
 *
 * Implementations are expected to L2-normalize their output so that
 * dot-product equals cosine similarity — this simplifies downstream
 * storage and search code.
 *
 * Example (conceptual):
 * ```
 * MediaPipeEmbedder.fromAssets(context, "multilingual-e5-small.tflite").use { embedder ->
 *     val embedding = embedder.encode("Hello, world").getOrThrow()
 *     println("dim = ${embedding.size}")  // 384 for multilingual-e5-small
 * }
 * ```
 */
public interface Embedder : AutoCloseable {
    /**
     * The dimensionality of the embedding vectors this implementation
     * produces. Constant for the lifetime of the instance.
     */
    public val dimensions: Int

    /**
     * Encode a single text string into an embedding vector of length
     * [dimensions]. Result is L2-normalized.
     *
     * Typical latency on flagship Android in 2026: 10-50ms for short
     * input (one chunk).
     */
    public suspend fun encode(text: String): Result<FloatArray>

    /**
     * Encode multiple texts in one call. Implementations may batch the
     * computation internally for efficiency. The order of the output
     * matches the order of the input.
     *
     * Used during document indexing to amortize per-call overhead across
     * many chunks.
     */
    public suspend fun encodeBatch(texts: List<String>): Result<List<FloatArray>>
}
