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

import ai.velat.core.api.Chunker
import ai.velat.core.domain.Chunk

/**
 * Default [Chunker] implementation: groups sentences into chunks up to a
 * target token budget with configurable overlap between consecutive chunks.
 *
 * Algorithm:
 *
 * 1. Split [text] into sentences on `.`, `!`, `?`, or double newline.
 * 2. Greedily pack sentences into a buffer until adding the next sentence
 *    would exceed [targetTokens].
 * 3. Emit the buffer as a [Chunk]; carry forward roughly [overlapTokens]
 *    worth of content from the end of the emitted chunk into the next.
 * 4. Repeat until all sentences are consumed.
 *
 * Why sentence boundaries: chunks that respect sentence boundaries make
 * coherent retrieval results. Splitting mid-sentence destroys meaning and
 * confuses both embedding models and the downstream LLM.
 *
 * Why overlap: a sentence near the end of one chunk might be the natural
 * answer for a query, but without overlap the surrounding sentences (in
 * the next chunk) are lost. Overlap ensures any 1–2 sentence pattern is
 * fully contained in at least one chunk.
 *
 * Limitations of this v0.1 implementation:
 *
 * - Sentence splitting is naive (regex-based, English/Latin-script biased).
 *   Better implementations are deferred to v0.2+.
 * - Token counts come from [SimpleTokenizer] — an approximation, not the
 *   model's real tokenizer.
 * - No structure preservation (headings, lists, code blocks). v0.2 may
 *   add a `StructureAwareChunker`.
 *
 * Pure Kotlin: no platform dependencies. Tested on the JVM.
 *
 * @property targetTokens Target token count per chunk.
 * @property overlapTokens Approximate token count carried over from one
 *   chunk into the next.
 * @property tokenizer Tokenizer used to estimate token counts.
 */
public class SentenceChunker(
    public val targetTokens: Int = DEFAULT_TARGET_TOKENS,
    public val overlapTokens: Int = DEFAULT_OVERLAP_TOKENS,
    public val tokenizer: SimpleTokenizer = SimpleTokenizer(),
) : Chunker {

    init {
        require(targetTokens > 0) { "targetTokens must be > 0, was $targetTokens" }
        require(overlapTokens in 0 until targetTokens) {
            "overlapTokens must be in [0, $targetTokens), was $overlapTokens"
        }
    }

    override fun chunk(text: String, pageNumber: Int?, documentOffset: Int): List<Chunk> {
        if (text.isBlank()) return emptyList()

        val sentences = splitIntoSentences(text)
        if (sentences.isEmpty()) return emptyList()

        val chunks = mutableListOf<Chunk>()
        val buffer = StringBuilder()
        var bufferStart = 0
        var bufferTokens = 0

        for (sentence in sentences) {
            val sentenceTokens = tokenizer.count(sentence.text)
            val wouldExceed = bufferTokens + sentenceTokens > targetTokens && buffer.isNotEmpty()

            if (wouldExceed) {
                chunks += emitChunk(
                    text = buffer.toString(),
                    bufferStart = bufferStart,
                    documentOffset = documentOffset,
                    pageNumber = pageNumber,
                    tokenCount = bufferTokens,
                )
                val carryoverStart = computeOverlapStart(buffer.toString(), overlapTokens)
                val carryover = buffer.substring(carryoverStart)
                bufferStart += carryoverStart
                buffer.clear()
                buffer.append(carryover)
                bufferTokens = tokenizer.count(carryover)
            }

            if (buffer.isEmpty()) {
                bufferStart = sentence.start
            }
            buffer.append(sentence.text)
            bufferTokens += sentenceTokens
        }

        if (buffer.isNotEmpty()) {
            chunks += emitChunk(
                text = buffer.toString(),
                bufferStart = bufferStart,
                documentOffset = documentOffset,
                pageNumber = pageNumber,
                tokenCount = bufferTokens,
            )
        }

        return chunks
    }

    private fun emitChunk(
        text: String,
        bufferStart: Int,
        documentOffset: Int,
        pageNumber: Int?,
        tokenCount: Int,
    ): Chunk {
        val trimmed = text.trim()
        val leading = text.indexOf(trimmed)
        val absoluteStart = documentOffset + bufferStart + leading.coerceAtLeast(0)
        return Chunk(
            text = trimmed,
            charStart = absoluteStart,
            charEnd = absoluteStart + trimmed.length,
            pageNumber = pageNumber,
            tokenCount = tokenCount,
        )
    }

    private fun computeOverlapStart(buffer: String, tokens: Int): Int {
        if (tokens <= 0) return buffer.length
        val approxChars = (tokens * tokenizer.charsPerToken).toInt().coerceAtMost(buffer.length)
        val tail = buffer.length - approxChars
        // Snap to the nearest preceding whitespace so we don't break a word.
        val snapped = buffer.lastIndexOf(' ', startIndex = tail).let { if (it < 0) 0 else it + 1 }
        return snapped.coerceIn(0, buffer.length)
    }

    private data class Sentence(val text: String, val start: Int)

    /**
     * Split text into sentence-like units while preserving character offsets.
     *
     * Boundary detection: terminal punctuation followed by whitespace, OR
     * double newline (paragraph break). Each returned sentence includes
     * its trailing whitespace so concatenation reconstructs the input.
     */
    private fun splitIntoSentences(text: String): List<Sentence> {
        val sentences = mutableListOf<Sentence>()
        var sentenceStart = 0
        var i = 0
        while (i < text.length) {
            val boundary = findSentenceBoundary(text, i)
            if (boundary != null) {
                val whitespaceEnd = skipWhitespace(text, boundary)
                addSentenceIfNotBlank(text, sentenceStart, whitespaceEnd, sentences)
                sentenceStart = whitespaceEnd
                i = whitespaceEnd
            } else {
                i++
            }
        }
        addSentenceIfNotBlank(text, sentenceStart, text.length, sentences)
        return sentences
    }

    /**
     * Returns the index just past a sentence boundary starting at [from],
     * or null if no boundary occurs there.
     */
    private fun findSentenceBoundary(text: String, from: Int): Int? {
        val c = text[from]
        if (isTerminalPunctuation(c) && isAtSentenceEnd(text, from)) {
            return from + 1
        }
        if (isDoubleNewlineStart(text, from)) {
            return from + 2
        }
        return null
    }

    private fun isTerminalPunctuation(c: Char): Boolean = c == '.' || c == '!' || c == '?'

    private fun isAtSentenceEnd(text: String, index: Int): Boolean =
        index + 1 >= text.length || text[index + 1].isWhitespace()

    private fun isDoubleNewlineStart(text: String, index: Int): Boolean =
        text[index] == '\n' && index + 1 < text.length && text[index + 1] == '\n'

    private fun addSentenceIfNotBlank(text: String, start: Int, end: Int, sink: MutableList<Sentence>) {
        if (start >= end) return
        val slice = text.substring(start, end)
        if (slice.isNotBlank()) {
            sink += Sentence(text = slice, start = start)
        }
    }

    private fun skipWhitespace(text: String, from: Int): Int {
        var j = from
        while (j < text.length && text[j].isWhitespace()) j++
        return j
    }

    /** Defaults for [SentenceChunker] constructor parameters. */
    public companion object {
        /** Default value for [targetTokens]. */
        public const val DEFAULT_TARGET_TOKENS: Int = 250

        /** Default value for [overlapTokens]. */
        public const val DEFAULT_OVERLAP_TOKENS: Int = 50
    }
}
