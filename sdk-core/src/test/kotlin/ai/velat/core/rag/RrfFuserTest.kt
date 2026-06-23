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

import ai.velat.core.domain.Chunk
import ai.velat.core.domain.ChunkId
import ai.velat.core.domain.DocumentId
import ai.velat.core.domain.ScoredChunk
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class RrfFuserTest {

    private val docId = DocumentId(1L)

    @Test
    fun `default k is 60 from original RRF paper`() {
        assertThat(RrfFuser.DEFAULT_K).isEqualTo(60)
        assertThat(RrfFuser().k).isEqualTo(60)
    }

    @Test
    fun `empty inputs produce empty output`() {
        val fuser = RrfFuser()

        assertThat(fuser.fuse(emptyList(), emptyList(), topK = 10)).isEmpty()
    }

    @Test
    fun `only-dense input is returned ordered by RRF score`() {
        val fuser = RrfFuser()
        val a = scored(1L, score = 0.9f)
        val b = scored(2L, score = 0.5f)
        val c = scored(3L, score = 0.2f)

        val fused = fuser.fuse(dense = listOf(a, b, c), sparse = emptyList(), topK = 3)

        // Order should match input order (because dense was the only source).
        assertThat(fused.map { it.chunkId }).containsExactly(
            ChunkId(1L),
            ChunkId(2L),
            ChunkId(3L),
        ).inOrder()
    }

    @Test
    fun `chunks appearing in both lists score higher than singletons`() {
        val fuser = RrfFuser()
        // Chunk 1 appears in both lists at rank 1 → strongest contribution.
        // Chunk 2 only in dense at rank 2.
        // Chunk 3 only in sparse at rank 2.
        val c1 = scored(1L, score = 0.9f)
        val c2 = scored(2L, score = 0.5f)
        val c3 = scored(3L, score = 0.5f)

        val fused = fuser.fuse(dense = listOf(c1, c2), sparse = listOf(c1, c3), topK = 3)

        assertThat(fused).hasSize(3)
        assertThat(fused.first().chunkId).isEqualTo(ChunkId(1L))
        assertThat(fused.first().score).isGreaterThan(fused[1].score)
        assertThat(fused.first().score).isGreaterThan(fused[2].score)
    }

    @Test
    fun `topK caps the result size`() {
        val fuser = RrfFuser()
        val all = (1..10).map { scored(it.toLong(), score = 0f) }

        val fused = fuser.fuse(dense = all, sparse = emptyList(), topK = 3)

        assertThat(fused).hasSize(3)
    }

    @Test
    fun `topK larger than input returns all available items`() {
        val fuser = RrfFuser()
        val items = listOf(scored(1L, 0f), scored(2L, 0f))

        val fused = fuser.fuse(dense = items, sparse = emptyList(), topK = 99)

        assertThat(fused).hasSize(2)
    }

    @Test
    fun `score in output is the RRF score not the input score`() {
        val fuser = RrfFuser(k = 60)
        val a = scored(1L, score = 0.99f)

        val fused = fuser.fuse(dense = listOf(a), sparse = emptyList(), topK = 1)

        // At rank 1 with k = 60: contribution is 1 / (60 + 0 + 1) = 1/61 ≈ 0.0164
        assertThat(fused.first().score).isWithin(0.001f).of(1f / 61f)
        // Should NOT be the input score.
        assertThat(fused.first().score).isNotEqualTo(0.99f)
    }

    @Test
    fun `zero or negative k is rejected at construction`() {
        assertThrows<IllegalArgumentException> { RrfFuser(k = 0) }
        assertThrows<IllegalArgumentException> { RrfFuser(k = -1) }
    }

    @Test
    fun `zero or negative topK is rejected at call time`() {
        val fuser = RrfFuser()

        assertThrows<IllegalArgumentException> {
            fuser.fuse(emptyList(), emptyList(), topK = 0)
        }
    }

    private fun scored(id: Long, score: Float): ScoredChunk = ScoredChunk(
        chunkId = ChunkId(id),
        documentId = docId,
        chunk = Chunk(
            text = "chunk $id",
            charStart = 0,
            charEnd = 7,
            pageNumber = null,
            tokenCount = 2,
        ),
        score = score,
    )
}
