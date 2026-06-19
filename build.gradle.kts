// ============================================================================
// Velat — Root build script
// ============================================================================
// This is the top-level build file. It declares plugin versions that all
// modules can use. Modules then opt into specific plugins via their own
// build.gradle.kts files.
//
// Strategy: `apply false` means "make this plugin available to subprojects
// but don't apply it here." This is the standard pattern for multi-module
// builds with a version catalog (declared in gradle/libs.versions.toml).
//
// Reference: https://docs.gradle.org/current/userguide/plugins.html#sec:subprojects_plugins_dsl
// ============================================================================

plugins {
    // Android Gradle Plugin variants — for sdk-engine-mediapipe, sdk-pdf, etc.
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false

    // Kotlin variants
    // - kotlin.android: for Android library modules
    // - kotlin.jvm: for pure-Kotlin modules (sdk-core)
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false

    // Compose compiler — separate plugin since Kotlin 2.0
    alias(libs.plugins.compose.compiler) apply false

    // Code quality (we configure these in Day 2)
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false

    // Docs generation
    alias(libs.plugins.dokka) apply false

    // Publishing — applied to library modules later
    alias(libs.plugins.maven.publish) apply false

    // API compatibility validation — ensures public API doesn't change
    // without intent. Configured project-wide in Day 2.
    alias(libs.plugins.binary.compatibility) apply false
}

// ----------------------------------------------------------------------------
// Convenience tasks
// ----------------------------------------------------------------------------
// `./gradlew clean` removes all build outputs from every module.
// This delegates to each module's own clean task.
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
