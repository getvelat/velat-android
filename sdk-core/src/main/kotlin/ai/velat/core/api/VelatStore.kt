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

import ai.velat.core.domain.Chunk
import ai.velat.core.domain.ChunkId
import ai.velat.core.domain.DocumentId
import ai.velat.core.domain.DocumentInfo
import ai.velat.core.domain.ScoredChunk

/**
 * Persistent storage layer for documents, chunks, and their embeddings.
 *
 * Implementations back Velat's indexed corpus with concrete storage:
 *
 * - `sdk-storage-sqlite`: production implementation using SQLite with
 *   `sqlite-vec` for vector search and FTS5 for keyword search.
 * - Future Pro implementations may add encryption (SQLCipher), HNSW
 *   indexing, or different backends entirely.
 *
 * The contract intentionally exposes both dense (vector similarity) and
 * sparse (keyword) search as separate operations so retrievers can fuse
 * them — see `HybridRetriever` in `sdk-rag` and `RrfFuser` in
 * [ai.velat.core.rag].
 *
 * Lifecycle: implementations open database connections and may hold file
 * handles. [close] releases them; callers SHOULD use `use {}` or another
 * scoped pattern.
 *
 * Thread-safety: implementations are expected to be safe for concurrent
 * reads. Writes may serialize internally.
 */
public interface VelatStore : AutoCloseable {

    /**
     * Persist metadata for a new document and return its assigned
     * [DocumentId]. The document has no chunks until [insertChunks] is
     * called.
     *
     * The [DocumentInfo.id] field on the input is IGNORED; the
     * implementation assigns the id. The returned id should be used for
     * subsequent operations.
     */
    public suspend fun insertDocument(info: DocumentInfo): Result<DocumentId>

    /**
     * Persist chunks (with their precomputed embeddings) for a document
     * previously created via [insertDocument]. Returns the assigned
     * [ChunkId]s in input order.
     *
     * The implementation is responsible for atomically writing both the
     * dense (vector) and sparse (keyword) indexes so that subsequent
     * searches see a consistent state.
     */
    public suspend fun insertChunks(documentId: DocumentId, chunks: List<ChunkWithEmbedding>): Result<List<ChunkId>>

    /**
     * Find the [topK] chunks whose embeddings are most similar to
     * [queryEmbedding] under cosine distance.
     *
     * If [documentFilter] is non-null, restrict the search to chunks
     * belonging to those documents.
     */
    public suspend fun searchDense(
        queryEmbedding: FloatArray,
        topK: Int,
        documentFilter: List<DocumentId>? = null,
    ): Result<List<ScoredChunk>>

    /**
     * Find the [topK] chunks that best match [query] under a keyword
     * scoring function (typically BM25). Used by hybrid retrievers
     * alongside [searchDense].
     *
     * If [documentFilter] is non-null, restrict the search to chunks
     * belonging to those documents.
     */
    public suspend fun searchSparse(
        query: String,
        topK: Int,
        documentFilter: List<DocumentId>? = null,
    ): Result<List<ScoredChunk>>

    /**
     * Return metadata for every indexed document, ordered by indexed-at
     * time descending (most recent first).
     */
    public suspend fun listDocuments(): Result<List<DocumentInfo>>

    /**
     * Remove a document and all of its chunks (and their embeddings) in
     * an atomic operation. Returns success even if the document does not
     * exist.
     */
    public suspend fun deleteDocument(id: DocumentId): Result<Unit>

    /**
     * Remove all documents, chunks, and embeddings. The store remains
     * usable after this call; it is empty, not closed.
     */
    public suspend fun clear(): Result<Unit>
}

/**
 * A chunk paired with its precomputed embedding, used when persisting
 * chunks via [VelatStore.insertChunks].
 *
 * The store does not compute embeddings itself; callers (typically the
 * indexing pipeline in `sdk-rag`) precompute via an [Embedder] and pass
 * the result.
 *
 * @property chunk The text and offset metadata of the chunk.
 * @property embedding The dense vector representation; length must equal
 *   the [Embedder.dimensions] used to compute it.
 */
public data class ChunkWithEmbedding(public val chunk: Chunk, public val embedding: FloatArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChunkWithEmbedding) return false
        if (chunk != other.chunk) return false
        return embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int = 31 * chunk.hashCode() + embedding.contentHashCode()
}
