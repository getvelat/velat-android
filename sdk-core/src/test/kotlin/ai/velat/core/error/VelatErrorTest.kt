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
package ai.velat.core.error

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class VelatErrorTest {

    @Test
    fun `each subclass preserves its message`() {
        assertThat(VelatError.ModelLoad("a").message).isEqualTo("a")
        assertThat(VelatError.ModelDownload("b").message).isEqualTo("b")
        assertThat(VelatError.InsufficientMemory("c").message).isEqualTo("c")
        assertThat(VelatError.StorageFailure("d").message).isEqualTo("d")
        assertThat(VelatError.GenerationTimeout("e").message).isEqualTo("e")
        assertThat(VelatError.UnsupportedDevice("f").message).isEqualTo("f")
        assertThat(VelatError.DocumentParse("g").message).isEqualTo("g")
        assertThat(VelatError.Cancelled("h").message).isEqualTo("h")
    }

    @Test
    fun `cause is preserved for chaining`() {
        val original = IllegalStateException("root cause")
        val wrapped = VelatError.ModelLoad("wrapped", cause = original)

        assertThat(wrapped.cause).isSameInstanceAs(original)
    }

    /**
     * Compile-time guarantee: removing or adding a subclass without
     * updating this when expression is a compile error because the sealed
     * class has all its subclasses known.
     *
     * If you add a new VelatError subclass, this test fails to compile —
     * which forces you to extend the match here, which forces consumers
     * (via the same exhaustive pattern in their own code) to handle the
     * new case too.
     */
    @Test
    fun `when over sealed hierarchy is exhaustive`() {
        val errors: List<VelatError> = listOf(
            VelatError.ModelLoad("a"),
            VelatError.ModelDownload("b"),
            VelatError.InsufficientMemory("c"),
            VelatError.StorageFailure("d"),
            VelatError.GenerationTimeout("e"),
            VelatError.UnsupportedDevice("f"),
            VelatError.DocumentParse("g"),
            VelatError.Cancelled("h"),
        )

        val names = errors.map { e ->
            when (e) {
                is VelatError.ModelLoad -> "ModelLoad"
                is VelatError.ModelDownload -> "ModelDownload"
                is VelatError.InsufficientMemory -> "InsufficientMemory"
                is VelatError.StorageFailure -> "StorageFailure"
                is VelatError.GenerationTimeout -> "GenerationTimeout"
                is VelatError.UnsupportedDevice -> "UnsupportedDevice"
                is VelatError.DocumentParse -> "DocumentParse"
                is VelatError.Cancelled -> "Cancelled"
            }
        }

        assertThat(names).containsExactly(
            "ModelLoad",
            "ModelDownload",
            "InsufficientMemory",
            "StorageFailure",
            "GenerationTimeout",
            "UnsupportedDevice",
            "DocumentParse",
            "Cancelled",
        ).inOrder()
    }
}
