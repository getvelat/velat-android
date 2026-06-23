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

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class SentenceChunkerTest {

    @Test
    fun `blank input produces no chunks`() {
        val chunker = SentenceChunker()

        assertThat(chunker.chunk("")).isEmpty()
        assertThat(chunker.chunk("   \n\n   ")).isEmpty()
    }

    @Test
    fun `single short sentence becomes a single chunk`() {
        val chunker = SentenceChunker(targetTokens = 100, overlapTokens = 0)
        val text = "The quick brown fox jumps over the lazy dog."

        val chunks = chunker.chunk(text)

        assertThat(chunks).hasSize(1)
        assertThat(chunks[0].text).isEqualTo(text)
        assertThat(chunks[0].charStart).isEqualTo(0)
        assertThat(chunks[0].charEnd).isEqualTo(text.length)
    }

    @Test
    fun `chunks split when target token budget is exceeded`() {
        // Each sentence is ~5 chars ≈ 1-2 tokens. With targetTokens = 3 and
        // no overlap, two short sentences should fit per chunk.
        val chunker = SentenceChunker(targetTokens = 3, overlapTokens = 0)
        val text = "A1 b. A2 b. A3 b. A4 b."

        val chunks = chunker.chunk(text)

        assertThat(chunks.size).isAtLeast(2)
        // Reconstruct: trimmed chunks together should cover all non-whitespace input
        val recon = chunks.joinToString("") { it.text }.replace(" ", "")
        assertThat(recon).contains("A1")
        assertThat(recon).contains("A4")
    }

    @Test
    fun `page number is attached to every emitted chunk`() {
        val chunker = SentenceChunker(targetTokens = 2, overlapTokens = 0)
        val text = "One. Two. Three. Four. Five. Six."

        val chunks = chunker.chunk(text, pageNumber = 7)

        assertThat(chunks).isNotEmpty()
        chunks.forEach { assertThat(it.pageNumber).isEqualTo(7) }
    }

    @Test
    fun `document offset is applied to chunk start positions`() {
        val chunker = SentenceChunker(targetTokens = 100, overlapTokens = 0)
        val text = "Hello world."

        val chunks = chunker.chunk(text, documentOffset = 100)

        assertThat(chunks).hasSize(1)
        assertThat(chunks[0].charStart).isEqualTo(100)
        assertThat(chunks[0].charEnd).isEqualTo(100 + text.length)
    }

    @Test
    fun `double newline acts as sentence boundary`() {
        val chunker = SentenceChunker(targetTokens = 3, overlapTokens = 0)
        val text = "Paragraph one\n\nParagraph two"

        val chunks = chunker.chunk(text)

        // Should produce at least 2 chunks because the paragraphs are split.
        assertThat(chunks.size).isAtLeast(2)
    }

    @Test
    fun `invalid targetTokens throws at construction`() {
        assertThrows<IllegalArgumentException> {
            SentenceChunker(targetTokens = 0)
        }
    }

    @Test
    fun `overlap larger than target throws at construction`() {
        assertThrows<IllegalArgumentException> {
            SentenceChunker(targetTokens = 100, overlapTokens = 100)
        }
    }
}
