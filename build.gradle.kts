// ============================================================================
// Velat — Root build script
// ============================================================================
// This is the top-level build file. Two responsibilities:
//
//   1. Declare plugin versions (via `apply false`) so subprojects can use
//      them without re-declaring versions.
//   2. Apply project-wide tooling: Spotless (Day 2), Detekt (later).
//
// Reference: https://docs.gradle.org/current/userguide/plugins.html
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

    // Spotless — applied at ROOT level (not subprojects) for two reasons:
    //   1. Single configuration source of truth (DRY).
    //   2. Glob targets across all modules work from one place.
    //   3. Kotlin DSL type-safe accessor for `spotless { }` requires the
    //      plugin to be applied to THIS project; subprojects-block apply
    //      doesn't expose the extension at compile time.
    // Trade-off: no per-module `:sdk-core:spotlessCheck` task. We get one
    // global `./gradlew spotlessApply` / `spotlessCheck` instead. Fine at our scale.
    alias(libs.plugins.spotless)

    // Detekt — also applied at root for the same reason. Each subproject
    // contributes its sources via the root detekt task.
    alias(libs.plugins.detekt) apply false

    // Docs generation — Dokka generates HTML docs from KDoc comments.
    // Applied to root so we can aggregate across modules via `dokkaHtmlMultiModule`.
    alias(libs.plugins.dokka)

    // Publishing — applied to library modules later
    alias(libs.plugins.maven.publish) apply false

    // API compatibility validation — generates and validates *.api snapshot files.
    // Applied at root; configured below to track only library modules.
    alias(libs.plugins.binary.compatibility)
}

// ============================================================================
// Spotless — auto-formatting + license header injection
// ============================================================================
// Commands:
//   ./gradlew spotlessApply  → fix all formatting + headers (run locally before commit)
//   ./gradlew spotlessCheck  → verify, fail if issues (run in CI)
//
// License header template lives at config/spotless/license-header-kotlin.txt.
// ============================================================================
spotless {
    // Kotlin source files — every .kt file in any module's src/.
    kotlin {
        // Glob across all modules. Excludes build/ outputs implicitly because
        // Spotless excludes build directories by default.
        target("**/src/**/*.kt")
        targetExclude("**/build/**", "**/generated/**")

        // Run ktlint to enforce Kotlin style guide. Version inherited from
        // libs.versions.toml. .editorconfig provides the actual rules.
        ktlint(libs.versions.ktlint.get())
            .editorConfigOverride(
                mapOf(
                    "max_line_length" to "120",
                ),
            )

        // Auto-inject Apache 2.0 license header if missing.
        // $YEAR is replaced with the current year by Spotless.
        licenseHeaderFile(
            rootProject.file("config/spotless/license-header-kotlin.txt"),
        )

        // Universal text rules
        trimTrailingWhitespace()
        endWithNewline()
    }

    // Kotlin Gradle scripts — build.gradle.kts, settings.gradle.kts.
    // No license header (these are build config, not distributed source).
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")

        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }

    // Generic text rules for markdown, yaml, .editorconfig, .gitignore.
    format("misc") {
        target("**/*.md", "**/*.yml", "**/*.yaml", ".gitignore", ".editorconfig")
        targetExclude("**/build/**", "**/.gradle/**", "**/node_modules/**")

        trimTrailingWhitespace()
        endWithNewline()
    }
}

// ============================================================================
// Detekt — static analysis
// ============================================================================
// Applied to every subproject. Each subproject gets its own `detekt` task.
//
// Commands:
//   ./gradlew detekt           → analyze all modules
//   ./gradlew :sdk-core:detekt → analyze just sdk-core
//
// Config layering:
//   - Base rules: config/detekt/detekt.yml (applied to every module)
//   - sdk-core: + config/detekt/detekt-sdk-core.yml (KMP discipline:
//     bans android.*, androidx.*, java.io.File, etc.)
//   - Future modules: their own detekt-<module>.yml as needed
// ============================================================================
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jetbrains.dokka")

    // Add the detekt-formatting plugin so we get the additional formatting rules
    // (mirror of ktlint rules implemented as detekt rules — second-line defense).
    dependencies {
        add("detektPlugins", rootProject.libs.detekt.formatting)
    }

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        // Base config that applies to all modules.
        config.from(rootProject.files("config/detekt/detekt.yml"))

        // Per-module additional config. The KMP discipline rules for sdk-core
        // are layered on top of the base config.
        if (name == "sdk-core") {
            config.from(rootProject.files("config/detekt/detekt-sdk-core.yml"))
        }

        // We don't use a baseline file — every issue must be fixed or
        // explicitly suppressed with @Suppress. No accumulated tech debt.
        baseline = null

        // Fail the build on any issue (matches detekt.yml's maxIssues: 0).
        buildUponDefaultConfig = true
    }
}

// ============================================================================
// Dokka — KDoc → HTML documentation
// ============================================================================
// Commands:
//   ./gradlew dokkaHtmlMultiModule  → generate aggregated docs to build/dokka/htmlMultiModule
//   ./gradlew :sdk-core:dokkaHtml   → generate single-module docs to sdk-core/build/dokka/html
//
// We'll eventually publish the multi-module output to docs.velat.ai/api.
// For now, just verifying that doc generation works locally.
// ============================================================================
tasks.named<org.jetbrains.dokka.gradle.DokkaMultiModuleTask>("dokkaHtmlMultiModule") {
    outputDirectory.set(rootProject.layout.buildDirectory.dir("dokka/htmlMultiModule"))
    moduleName.set("Velat")
}

// ============================================================================
// Binary Compatibility Validator (BCV)
// ============================================================================
// Tracks the public API surface of every library module in *.api snapshot
// files committed to the repo. Fails the build if current API doesn't match.
//
// Commands:
//   ./gradlew apiCheck  → verify current API matches snapshot (CI gate)
//   ./gradlew apiDump   → regenerate snapshot (only run when API change is intentional)
//
// Workflow when changing public API:
//   1. Make the API change in code.
//   2. Run `./gradlew apiDump` locally.
//   3. Commit both the code change AND the updated *.api file.
//   4. CI's apiCheck will pass (snapshot matches new code).
//
// If you forgot to run apiDump → CI fails with a diff showing what changed.
// This is the SAFETY NET that catches accidental breaking changes.
// ============================================================================
apiValidation {
    // Modules to EXCLUDE from API tracking. Sample apps, test utilities, and
    // any module that isn't a published library go here. When we add samples/
    // and demo apps later, add them to this list.
    ignoredProjects.addAll(
        listOf(
            // No projects to ignore yet — sdk-core is our only library and
            // it should be tracked. Future: add "sample-chat", "velat-notes", etc.
        ),
    )

    // Classes/packages marked with these annotations are excluded from API
    // tracking. We use @InternalVelatApi (declared later in sdk-core) to mark
    // technically-public types that aren't part of the SDK contract.
    nonPublicMarkers.addAll(
        listOf(
            // Future: "ai.velat.core.InternalVelatApi"
        ),
    )
}

// Helper accessor for libs in the subprojects block above.
// Kotlin DSL doesn't expose `libs` inside `subprojects { dependencies { } }`
// without this trick. It works because `the<>()` is a Gradle DSL utility
// that finds the named extension on the project.
val Project.libs
    get() = the<org.gradle.accessors.dm.LibrariesForLibs>()

// `./gradlew clean` is provided automatically by the `base` plugin (applied
// implicitly by Spotless and by Kotlin/Android plugins on subprojects).
// It already deletes each project's build directory; no explicit task needed.
