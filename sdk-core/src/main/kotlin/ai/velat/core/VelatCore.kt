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
package ai.velat.core

/**
 * Core constants and metadata for the Velat SDK.
 *
 * This is a placeholder for Day 1; the real public API (Velat entry point,
 * domain entities, interfaces) lands in Day 3.
 *
 * @see ai.velat.core for the package's full structure
 */
public object VelatCore {

    /**
     * The semantic version of this Velat SDK release.
     *
     * Useful for diagnostics, telemetry, and bug reports. Follows
     * [Semantic Versioning 2.0.0](https://semver.org/).
     *
     * Example:
     * ```
     * Log.d("MyApp", "Using Velat ${VelatCore.VERSION}")
     * ```
     */
    public const val VERSION: String = "0.1.0-SNAPSHOT"
}
