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
// samples:engine-spike — Android app that exercises MediaPipeLlmEngine on
// real device hardware.
//
// Purpose: verify the inference layer works end-to-end on Pixel 8 / Pixel 7a,
// measure TTFT and tok/s, surface MediaPipe constraints we couldn't see from
// pure JVM tests. NOT a production sample app — that comes later.
// ============================================================================

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.velat.sample.enginespike"
    compileSdk =
        libs.versions.android.compile.sdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "com.velat.sample.enginespike"
        minSdk =
            libs.versions.android.min.sdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.target.sdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        jvmToolchain(
            libs.versions.jvm.target
                .get()
                .toInt(),
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false // spike app; not shipping to Play Store
        }
    }

    // Restrict ABIs to those Pixel 8 and Pixel 7a actually use (both arm64-v8a).
    // Reduces APK size on debug builds and avoids pulling all native libs.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }
}

dependencies {
    implementation(project(":sdk-core"))
    implementation(project(":sdk-engine-mediapipe"))

    // AndroidX core for Activity + Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)

    // Lifecycle helpers for Compose (collectAsStateWithLifecycle, viewModel()).
    // Pinned with the same lifecycle version we already use elsewhere.
    val lifecycleVersion =
        libs.versions.androidx.lifecycle
            .get()
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")

    // Compose UI
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose debug tooling — preview rendering, layout inspector.
    debugImplementation(libs.bundles.compose.debug)

    // Coroutines for ViewModel-side flows
    implementation(libs.coroutines.android)
}
