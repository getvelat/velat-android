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
 * Information about a document that has been indexed by Velat.
 *
 * Returned by listing operations and embedded in [Citation]s so the host
 * application can display the source of a generated answer. The fields
 * here intentionally do NOT include the document's chunks or its full text
 * — fetch those through the storage layer when needed.
 *
 * The host application supplies [title] when adding the document; Velat
 * preserves it but does not interpret it.
 *
 * @property id Stable identifier; never changes for the lifetime of the document.
 * @property title Human-readable title (file name, user-supplied label).
 * @property sourceType Format the document was loaded from.
 * @property chunkCount Number of chunks produced during indexing.
 * @property indexedAtEpochMs Unix epoch milliseconds at which the document was indexed.
 * @property sizeBytes Approximate on-disk size in bytes (sum of chunk + embedding storage).
 * @property metadata Host-supplied metadata attached to this document.
 */
public data class DocumentInfo(
    public val id: DocumentId,
    public val title: String,
    public val sourceType: SourceType,
    public val chunkCount: Int,
    public val indexedAtEpochMs: Long,
    public val sizeBytes: Long,
    public val metadata: Metadata = Metadata.empty(),
)
