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

/**
 * Character-count-based approximation of LLM tokenization.
 *
 * Used during chunking and context-budget planning where we need a rough
 * answer to "how many tokens will this text consume?" without paying the
 * cost of running the actual model tokenizer.
 *
 * The approximation: 1 token ≈ [charsPerToken] characters. The default of
 * `4.0` matches English text reasonably well for BPE/SentencePiece-style
 * tokenizers. Polish, German, and other morphologically richer languages
 * tokenize denser; pass a smaller [charsPerToken] (e.g., `3.0`) for those.
 *
 * For exact token counts at generation time, the implementation modules
 * (sdk-engine-mediapipe) use the model's real tokenizer. This class is
 * for planning, not precision.
 *
 * @property charsPerToken Average characters per token assumed by this
 *   tokenizer. Configurable for language tuning.
 */
public class SimpleTokenizer(public val charsPerToken: Double = DEFAULT_CHARS_PER_TOKEN) {

    /**
     * Estimate the number of tokens in [text]. Always returns at least 1
     * for non-empty input.
     */
    public fun count(text: String): Int {
        if (text.isEmpty()) return 0
        val raw = (text.length / charsPerToken).toInt()
        return if (raw < 1) 1 else raw
    }

    /** Defaults for [SimpleTokenizer]. */
    public companion object {
        /** Default chars-per-token assumption (English-tuned). */
        public const val DEFAULT_CHARS_PER_TOKEN: Double = 4.0
    }
}
