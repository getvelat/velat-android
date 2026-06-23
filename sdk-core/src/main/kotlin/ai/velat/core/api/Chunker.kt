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

/**
 * Contract for splitting extracted document text into retrievable units.
 *
 * Chunking is the unglamorous half of retrieval quality. Bad chunks make
 * retrieval impossible regardless of embedding quality: too-large chunks
 * dilute relevance signals; too-small chunks lose context; chunks split
 * mid-sentence destroy meaning.
 *
 * Velat's default implementation is [ai.velat.core.rag.SentenceChunker],
 * which respects sentence boundaries and groups sentences up to a target
 * token budget with configurable overlap between consecutive chunks.
 *
 * Custom implementations are welcome — pass yours into `Velat.create()`
 * if your domain has structure the default doesn't preserve (e.g.,
 * markdown sections, source-code blocks).
 *
 * Chunkers are pure functions: given the same input, they produce the
 * same output. They live in `sdk-core` because they have no platform
 * dependencies.
 */
public interface Chunker {

    /**
     * Split [text] into chunks.
     *
     * @param text The plain text to chunk.
     * @param pageNumber Optional one-based page number to attach to each
     *   produced [Chunk]. Pass for PDF-sourced text; null for text without
     *   page structure.
     * @param documentOffset Character offset into the larger document that
     *   [text] starts at. The produced chunks' `charStart` / `charEnd`
     *   are relative to the document, not to this [text] slice.
     */
    public fun chunk(text: String, pageNumber: Int? = null, documentOffset: Int = 0): List<Chunk>
}
