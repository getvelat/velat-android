# ADR 0002: KMP-readiness Rules for sdk-core

**Status:** Accepted
**Date:** 2026-06-19 (Day 1)
**Deciders:** Rafal Niski
**Related:** [ADR 0001 — Architecture Overview](0001-architecture.md), Decision 4

## Context

Velat ships Android-first. iOS support is planned for v0.5+ (around 12-15 months
from now). We want the iOS port to be a refactor of platform-specific modules,
not a rewrite of domain logic. This means `sdk-core` must be portable to
Kotlin Multiplatform's `commonMain` source set with minimal changes.

This ADR captures the hard rules enforced in `sdk-core` to keep that path open.

## The Rules

### Rule 1: No Android imports

`android.*` and `androidx.*` packages are **forbidden** in `sdk-core`.

**Enforcement**:
- Compile-time: `sdk-core` is a `kotlin.jvm` module, not Android library.
  Android packages aren't on the classpath; accidental imports fail to compile.
- Static analysis: Detekt's `ForbiddenImport` rule (configured in
  `config/detekt/detekt-sdk-core.yml`) catches and reports these imports with
  a helpful error message pointing to this ADR.

**Where Android imports belong**: `sdk-engine-mediapipe`, `sdk-storage-sqlite`,
`sdk-pdf`, `sdk-compose`, `sdk`.

### Rule 2: No java.io for file I/O — use okio

`java.io.File`, `java.io.FileInputStream`, `java.io.FileOutputStream` are
**forbidden** in `sdk-core`.

**Replacement**: [okio](https://square.github.io/okio/) — multiplatform I/O
library by Square.

**Why okio matters**:
- `java.io.*` is JVM-only. Kotlin/Native (iOS target) has no `java.io.File`.
- okio's `Path` and `FileSystem` abstractions are KMP-compatible.
- okio also gives us better-designed APIs (efficient buffering, Source/Sink
  abstractions).

**Enforcement**: Detekt forbids these imports. When `sdk-core` needs to express
"a path to something on disk", it uses `okio.Path`. Platform-specific modules
translate to `java.io.File` if needed.

### Rule 3: No java.time — use kotlinx.datetime

`java.time.*` is **forbidden** in `sdk-core`.

**Replacement**: [`kotlinx.datetime`](https://github.com/Kotlin/kotlinx-datetime)
— multiplatform date/time library by JetBrains.

**Why**:
- `java.time` is JVM-only (Android since API 26 backports, but still
  JVM-only in concept).
- `kotlinx.datetime` works on Kotlin/Native.

**Enforcement**: Detekt forbids `java.time.**` imports in sdk-core.

### Rule 4: Prefer kotlinx libraries over Java standard library equivalents

When choosing between `kotlinx.serialization` and `java.beans.XMLEncoder`,
between `kotlinx.coroutines` and `java.util.concurrent.CompletableFuture`,
between `kotlinx.datetime` and `java.time` — always prefer the kotlinx variant.

**Why**: kotlinx libraries are explicitly multiplatform. JVM-equivalents work
on Android but not iOS.

**Already locked in (Day 1 dependencies)**:
- `kotlinx.coroutines-core` for async primitives
- `kotlinx.serialization-json` for serialization
- `kotlinx.datetime` for dates and times

### Rule 5: No reflection-heavy APIs that don't work on Native

Avoid:
- `kotlin.reflect.full.*` (KReflection beyond basic features)
- `java.lang.reflect.*`
- Java annotation processing with retention RUNTIME

**Why**: Kotlin/Native has limited reflection support. Code that relies heavily
on reflection won't port cleanly.

**Compromise**: We can use compile-time annotation processing (KSP) — this
generates code at build time and the generated code itself doesn't use
reflection at runtime.

**Enforcement**: Not strictly enforced by Detekt (no clean rule for it).
Code review responsibility.

### Rule 6: Logging via interface, not direct calls

`sdk-core` doesn't directly call any logger. Define a `VelatLogger` interface;
implementations live in platform modules.

**Why**:
- `android.util.Log` is Android-only.
- `java.util.logging.Logger` is JVM-only.
- `print()` / `println()` works everywhere but isn't a production logger.

**Decision (deferred to first need)**: We'll add a `VelatLogger` interface
when the first sdk-core code needs to log something. For now, no logging
in `sdk-core`.

## What's Allowed

To be explicit, these ARE allowed in `sdk-core`:
- Pure Kotlin standard library (`kotlin.*`, `kotlin.collections.*`, etc.)
- kotlinx libraries (`kotlinx.coroutines`, `kotlinx.serialization`,
  `kotlinx.datetime`)
- okio
- JVM-standard types that are KMP-compatible (`String`, `Int`, `List`, etc.)
- Annotations from any library if used only at declaration sites
  (no runtime reflection)
- JUnit 5, MockK, Turbine, Truth (test-only — these never ship in the
  artifact, so JVM-only deps are fine here)

## How the Rules are Enforced

1. **Compile-time**: `sdk-core` is `kotlin.jvm` plugin, no Android plugin.
   Android packages aren't on the classpath.
2. **Static analysis**: Detekt's `ForbiddenImport` rule in
   `config/detekt/detekt-sdk-core.yml` lists banned imports with
   error messages pointing to this ADR.
3. **CI gate**: `./gradlew detekt` runs on every PR. Any forbidden import
   fails the build.
4. **Code review**: Reviewers reject PRs that try to bypass the rules.

## What the Rules Do NOT Apply To

These rules apply ONLY to `sdk-core`. Other modules are free to:
- Use Android types (sdk-engine-mediapipe, sdk-storage-sqlite, sdk-pdf,
  sdk-compose, sdk).
- Use java.io, java.time, java.util.concurrent.
- Use Android Log, Android Keystore, anything platform-specific.

The rules exist to keep `sdk-core` portable. Platform modules ARE platform
code; they're allowed to use platform APIs.

## When to Break a Rule

Almost never. But possible exceptions:

- **A KMP-compatible alternative literally doesn't exist**: e.g., if we need
  some functionality that has no kotlinx or okio equivalent. In this case:
  document the exception in code with `// KMP-EXCEPTION:` comment + reason,
  add a `@Suppress("ForbiddenImport")` annotation, and create an issue to
  revisit when the ecosystem catches up.
- **Performance crisis**: if a kotlinx alternative is measurably too slow
  in a hot path. Document with benchmark numbers.

Breaking a rule without one of these reasons is a bug. Reject in review.

## Future Considerations

- When we actually start the iOS port (v0.5), we'll convert `sdk-core` to a
  Kotlin Multiplatform module with `commonMain` source set. All `sdk-core`
  code today should move there with zero changes if these rules were followed.
- Some Compose APIs may also become KMP-compatible (Compose Multiplatform).
  When that happens, `sdk-compose` may also become KMP. Out of scope for now.
