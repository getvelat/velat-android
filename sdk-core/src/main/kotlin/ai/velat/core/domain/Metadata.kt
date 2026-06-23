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
 * Free-form key/value annotations attached to indexed documents.
 *
 * Metadata is opaque to Velat — values are stored alongside the document
 * and can be used for filtering ("retrieve only from documents tagged
 * `category=legal`"), but Velat itself does not interpret the values.
 *
 * Keys and values must be strings. Encode richer types (numbers, dates,
 * booleans) as strings on the host application's side.
 *
 * The class is immutable and value-equality based. Reuse a single empty
 * instance via [empty] when no metadata is needed.
 *
 * Example:
 * ```
 * val tags = Metadata.of(
 *     "category" to "legal",
 *     "uploaded_by" to "alice@company.com",
 *     "year" to "2026",
 * )
 * velat.addDocument(file, tags)
 * ```
 *
 * @property values The underlying read-only key/value map.
 */
@Suppress("DataClassContainsFunctions") // operator get and size/isEmpty accessors are first-class API
public data class Metadata(public val values: Map<String, String> = emptyMap()) {

    /** Returns the value for [key], or `null` if not present. */
    public operator fun get(key: String): String? = values[key]

    /** Returns the number of entries. */
    public val size: Int get() = values.size

    /** Returns `true` when no entries are present. */
    public val isEmpty: Boolean get() = values.isEmpty()

    /** Factories for [Metadata]. */
    public companion object {
        /** A shared empty instance — use when no metadata is required. */
        public fun empty(): Metadata = EMPTY

        /**
         * Convenience factory for a small set of pairs.
         *
         * Example:
         * ```
         * Metadata.of("category" to "legal", "year" to "2026")
         * ```
         */
        public fun of(vararg pairs: Pair<String, String>): Metadata =
            if (pairs.isEmpty()) EMPTY else Metadata(pairs.toMap())

        private val EMPTY = Metadata(emptyMap())
    }
}
