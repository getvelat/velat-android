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
package ai.velat.core.domain

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Smoke tests for the value classes used throughout sdk-core. These are
 * `@JvmInline value class` wrappers around primitives, so the main
 * properties we verify are: wrapping/unwrapping, value-equality, and
 * distinct types (compile-time guarantee, validated indirectly by usage).
 */
internal class ValueClassesTest {

    @Test
    fun `DocumentId wraps and exposes its value`() {
        val id = DocumentId(42L)

        assertThat(id.value).isEqualTo(42L)
    }

    @Test
    fun `DocumentIds with same value are equal`() {
        assertThat(DocumentId(7L)).isEqualTo(DocumentId(7L))
    }

    @Test
    fun `DocumentIds with different values are not equal`() {
        assertThat(DocumentId(1L)).isNotEqualTo(DocumentId(2L))
    }

    @Test
    fun `ChunkId wraps and exposes its value`() {
        val id = ChunkId(99L)

        assertThat(id.value).isEqualTo(99L)
    }

    @Test
    fun `ChunkIds with same value are equal`() {
        assertThat(ChunkId(3L)).isEqualTo(ChunkId(3L))
    }

    @Test
    fun `Token wraps and exposes its text`() {
        val token = Token("hello")

        assertThat(token.text).isEqualTo("hello")
    }

    @Test
    fun `Tokens with same text are equal`() {
        assertThat(Token("a")).isEqualTo(Token("a"))
    }
}
