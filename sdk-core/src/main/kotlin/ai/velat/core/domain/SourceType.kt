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
 * The original format of an indexed document.
 *
 * Used for telemetry, UI hinting (e.g., showing a PDF icon for PDF sources),
 * and for routing during re-indexing — a [SourceType] paired with the
 * original file path is enough to rebuild the document's chunks.
 */
public enum class SourceType {
    /** Extracted via a PDF parser; preserves page numbers. */
    PDF,

    /** Plain text file. */
    TEXT,

    /** Markdown file; structure may be preserved in v0.2+. */
    MARKDOWN,

    /** Text extracted via on-device OCR (v0.2+). */
    IMAGE_OCR,

    /** Any other source, including text supplied directly by the host application. */
    OTHER,
}
