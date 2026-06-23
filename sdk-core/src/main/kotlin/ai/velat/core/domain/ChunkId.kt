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
 * Stable identifier for a chunk within an indexed document.
 *
 * Each indexed document is split into chunks; each chunk gets its own
 * [ChunkId]. Like [DocumentId], this is a zero-overhead inline class
 * around [Long].
 *
 * Chunk IDs are assigned by the storage layer at index time and remain
 * stable across application restarts.
 *
 * @property value The underlying long identifier.
 */
@JvmInline
public value class ChunkId(public val value: Long)
