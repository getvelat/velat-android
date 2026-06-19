// ============================================================================
// sdk-core — Pure Kotlin foundation module
// ============================================================================
// Contains:
//   - Public API interfaces (LlmEngine, Embedder, VelatStore, Retriever, ...)
//   - Domain entities (Chunk, ScoredChunk, Citation, DocumentId, ...)
//   - Pure algorithms (Chunker, RrfFuser)
//   - Sealed error hierarchy (VelatError)
//
// What this module IS:
//   - Pure Kotlin/JVM library
//   - Zero Android dependencies
//   - Zero MediaPipe / sqlite dependencies (only interfaces describing them)
//
// What this module is NOT:
//   - Not an Android library (does not depend on android.* or androidx.*)
//   - Not an implementation of inference, storage, or RAG
//
// Why this discipline matters:
//   1. Testability: unit tests run on plain JVM in milliseconds.
//   2. KMP-readiness: when we port to iOS, this module becomes the shared
//      `commonMain` source set with minimal changes.
//   3. Architectural clarity: the dependency rule "domain doesn't know about
//      platform" is enforced by the compiler — you can't accidentally import
//      Android types because they're not on the classpath.
// ============================================================================

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// JVM target — Java 11 bytecode. Matches the rest of the project.
// Compatible with all Android targets (Android compiles Kotlin to JVM bytecode
// then dexifies it; the source JVM target doesn't constrain Android compat).
kotlin {
    jvmToolchain(libs.versions.jvm.target.get().toInt())
}

dependencies {
    // Kotlin stdlib is automatic for kotlin-jvm plugin, but we list it
    // explicitly for clarity.
    implementation(libs.kotlin.stdlib)

    // Coroutines — async primitives used throughout Velat's public API.
    implementation(libs.coroutines.core)

    // Serialization — for Config persistence, metadata JSON.
    implementation(libs.serialization.json)

    // okio — multiplatform I/O. We use okio.Path / okio.FileSystem instead of
    // java.io.File so that domain code stays portable to non-JVM targets later.
    implementation(libs.okio)

    // Datetime — multiplatform date/time. Replaces java.time which is JVM-only
    // (well, JVM and Android, but not Kotlin/Native for iOS).
    implementation(libs.datetime)

    // Test-only dependencies.
    testImplementation(libs.bundles.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
}

// JUnit 5 (Jupiter) configuration. Gradle defaults to JUnit 4 platform; we
// need to explicitly opt into the Jupiter platform for @Test from
// org.junit.jupiter.api to be discovered.
tasks.test {
    useJUnitPlatform()
}
