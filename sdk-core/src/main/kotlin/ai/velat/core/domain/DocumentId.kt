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

/**
 * Stable identifier for a document indexed in Velat.
 *
 * Wraps a [Long] in a type-safe inline class so the compiler can distinguish
 * a [DocumentId] from a raw `Long` or from a [ChunkId]. At runtime, the JVM
 * sees only the underlying `Long` — there is no allocation overhead.
 *
 * Document IDs are assigned by the storage layer when a document is first
 * indexed and remain stable across application restarts.
 *
 * Example:
 * ```
 * val id = DocumentId(42L)
 * println(id.value)  // 42
 * ```
 *
 * @property value The underlying long identifier.
 */
@JvmInline
public value class DocumentId(public val value: Long)
