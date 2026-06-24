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

// ============================================================================
// sdk-engine-mediapipe — MediaPipe LLM Inference implementation of LlmEngine
// ============================================================================
// Provides a production implementation of [ai.velat.core.api.LlmEngine] backed
// by Google's MediaPipe LLM Inference API (com.google.mediapipe:tasks-genai).
//
// Why an Android library (not pure Kotlin):
//   - MediaPipe LLM Inference requires android.content.Context to load models
//     and access the platform's native delegate selection (CPU / GPU / NPU).
//   - The Maven artifact ships per-ABI native .so files; only an Android
//     project can dexify those into an APK.
//
// Consumers depend on this module when they want MediaPipe as their inference
// runtime. Alternative backends (llama.cpp, ExecuTorch) would live in sibling
// modules and satisfy the same LlmEngine contract.
// ============================================================================

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "ai.velat.engine.mediapipe"
    compileSdk =
        libs.versions.android.compile.sdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.android.min.sdk
                .get()
                .toInt()
        // Consumer ProGuard rules merged into any app that depends on this lib.
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        // Java 11 bytecode — matches the rest of the project. Android desugaring
        // handles APIs newer than minSdk (26 / Android 8.0).
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        // Compile Kotlin to JVM 11 bytecode. JvmTarget value comes from the
        // version catalog so all modules stay aligned.
        jvmToolchain(
            libs.versions.jvm.target
                .get()
                .toInt(),
        )
    }
}

dependencies {
    // sdk-core exposes the LlmEngine interface we implement. Use `api` (not
    // `implementation`) because the consumer of sdk-engine-mediapipe needs to
    // see LlmEngine, Token, GenerationOptions, VelatError on its classpath
    // to call our public methods. With `implementation` these types would be
    // hidden from consumers.
    api(project(":sdk-core"))

    // MediaPipe LLM Inference — provides LlmInference and LlmInferenceSession.
    // Ships native .so files per ABI (arm64-v8a, armeabi-v7a, x86_64) plus
    // the model loading and chat templating machinery.
    api(libs.mediapipe.tasks.genai)

    // Coroutines — we map MediaPipe's callback-based API onto a Flow<Token>
    // using callbackFlow, which lives in coroutines-core.
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Test dependencies — unit tests run on JVM with mocked MediaPipe.
    testImplementation(libs.bundles.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
}

// Android library modules don't ship with a default `test` task wired to
// JUnit Platform; we opt in explicitly so JUnit 5 (Jupiter) tests are found.
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
