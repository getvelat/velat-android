// ============================================================================
// Velat — top-level Gradle settings
// ============================================================================
// This file is evaluated BEFORE all build.gradle.kts files in the project.
// It defines:
//   1. Where Gradle finds plugins and dependencies (repositories)
//   2. Which modules are part of this build (include statements)
//   3. Gradle features enabled (Gradle 8+ requires explicit toolchain etc.)
//
// Reference: https://docs.gradle.org/current/userguide/build_lifecycle.html
// ============================================================================

// ----------------------------------------------------------------------------
// Plugin Management
// ----------------------------------------------------------------------------
// Defines where Gradle searches for plugins referenced via `plugins { ... }`
// blocks in build.gradle.kts files. Without this, those plugins fail to resolve.
pluginManagement {
    repositories {
        // gradlePluginPortal: standard repository for community Gradle plugins
        // (Detekt, Spotless, Dokka, etc.)
        gradlePluginPortal()
        // google: Google's Maven repository for Android Gradle Plugin (AGP),
        // AndroidX, and Google-published libs
        google()
        // mavenCentral: industry standard for Java/Kotlin libraries — Kotlin
        // plugins, kotlinx.*, JetBrains tooling
        mavenCentral()
    }
}

// ----------------------------------------------------------------------------
// Dependency Resolution Management
// ----------------------------------------------------------------------------
// Defines where Gradle searches for runtime/compile dependencies.
// We use FAIL_ON_PROJECT_REPOS mode to prevent any module from declaring
// its own repositories — all dependency sources must be declared here.
// This avoids the "module accidentally pulled from typosquat repo" risk.
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// ----------------------------------------------------------------------------
// Project name
// ----------------------------------------------------------------------------
// Shows in build output, IDE project tree. Should match the repo name.
rootProject.name = "velat-android"

// ----------------------------------------------------------------------------
// Modules
// ----------------------------------------------------------------------------
// Each `include(":name")` declares a Gradle module. The module must have a
// corresponding directory with a build.gradle.kts file.
//
// We include modules incrementally as we build them. The full planned module
// structure is documented in docs/adr/0001-architecture.md.
//
// Modules are added incrementally as their first code lands.
//
// Day 1: sdk-core (pure Kotlin foundation)
// Day 4: sdk-engine-mediapipe (Android library, MediaPipe LlmEngine impl)
//        samples:engine-spike (Android app for device-level verification)
// Day 5+: sdk-storage-sqlite, sdk-pdf, sdk-rag, sdk, sdk-compose

include(":sdk-core")
include(":sdk-engine-mediapipe")
include(":samples:engine-spike")
