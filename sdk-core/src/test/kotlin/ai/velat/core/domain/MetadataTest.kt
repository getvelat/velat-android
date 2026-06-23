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

internal class MetadataTest {

    @Test
    fun `empty returns instance with no values`() {
        val metadata = Metadata.empty()

        assertThat(metadata.values).isEmpty()
        assertThat(metadata.size).isEqualTo(0)
        assertThat(metadata.isEmpty).isTrue()
    }

    @Test
    fun `empty returns shared instance`() {
        assertThat(Metadata.empty()).isSameInstanceAs(Metadata.empty())
    }

    @Test
    fun `of builds metadata from pairs`() {
        val metadata = Metadata.of("category" to "legal", "year" to "2026")

        assertThat(metadata.size).isEqualTo(2)
        assertThat(metadata["category"]).isEqualTo("legal")
        assertThat(metadata["year"]).isEqualTo("2026")
    }

    @Test
    fun `of with no pairs returns empty shared instance`() {
        val metadata = Metadata.of()

        assertThat(metadata).isSameInstanceAs(Metadata.empty())
    }

    @Test
    fun `get returns null for missing key`() {
        val metadata = Metadata.of("present" to "yes")

        assertThat(metadata["missing"]).isNull()
    }

    @Test
    fun `equality is value-based`() {
        val a = Metadata.of("k" to "v")
        val b = Metadata.of("k" to "v")

        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }

    @Test
    fun `different values produce different instances`() {
        val a = Metadata.of("k" to "v1")
        val b = Metadata.of("k" to "v2")

        assertThat(a).isNotEqualTo(b)
    }
}
