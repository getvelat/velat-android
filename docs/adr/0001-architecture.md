# ADR 0001: Architecture Overview

**Status:** Accepted
**Date:** 2026-06-19 (Day 1)
**Deciders:** Rafal Niski

## Context

Velat is an on-device LLM and RAG SDK for Android, distributed via Maven Central
as `ai.velat:sdk:<version>`. The architecture must:

- Be portable to iOS later (planned v0.5+) without rewriting domain logic.
- Maintain stable public API for downstream consumers who integrate it into their apps.
- Be auditable by compliance-conscious users (legal, medical, regulated industries).
- Allow swapping implementations of internal components (inference engine,
  vector store, document loaders) without rippling changes across modules.

This document captures the architectural decisions made before any production
code was written, so future contributors can understand the reasoning.

## Decision 1: Multi-module Gradle structure

Velat is split into focused modules rather than a monolithic library:

```
sdk-core             — Pure Kotlin foundation (KMP-ready)
sdk-engine-mediapipe — MediaPipe LLM Inference wrapper
sdk-storage-sqlite   — sqlite-vec + FTS5 persistence
sdk-pdf              — PDF parsing (PDFBox-Android)
sdk-rag              — Chunking, retrieval, RRF fusion pipeline
sdk                  — Public entry point; composes the modules above
sdk-compose          — Compose helpers (optional dependency for consumers)
samples/*            — Demo apps
```

Day 1 only creates `sdk-core`. Other modules are added incrementally as their
implementations land.

### Why multi-module

- **Modularity**: A consumer that doesn't use PDF can exclude `sdk-pdf` to
  reduce APK size.
- **Substitutability**: We can introduce `sdk-engine-llamacpp` later as an
  alternative inference backend without touching `sdk-rag` or `sdk-core`.
- **Build efficiency**: Incremental compilation per-module means changes in
  one module don't recompile the others.
- **Extension points**: Cross-module dependencies go through interfaces in
  `sdk-core`. Alternative implementations can plug in without modifying core.
- **Architectural enforcement**: `sdk-core` being a `kotlin.jvm` module
  (not Android library) means the compiler refuses Android-specific imports.

### Trade-off

- More Gradle configuration overhead. Mitigated by version catalog
  (`gradle/libs.versions.toml`) centralizing dependencies.

## Decision 2: License — Apache 2.0

Considered: MIT, Apache 2.0.

**Chosen:** Apache 2.0.

### Why

- **Patent grant**: Apache 2.0 includes an explicit grant of patents related
  to the contribution, with automatic termination if a licensee sues for patent
  infringement on the same code. For an SDK implementing algorithms (RRF fusion,
  retrieval pipelines), this protection matters. MIT has no such clause.
- **Integration-friendly**: Legal teams audit Apache 2.0 quickly because it's
  familiar and explicit.
- **OSI-approved**: Recognized as an open-source license by the Open Source
  Initiative. Required for Maven Central publishing.
- **Permissive**: Allows use, modification, and redistribution without
  open-sourcing derivative work.

## Decision 3: KMP-ready Android-first

`sdk-core` is a pure Kotlin/JVM library with zero Android dependencies. Other
modules (`sdk-engine-mediapipe`, `sdk-storage-sqlite`, etc.) are Android
libraries.

When iOS support is added (planned v0.5+), `sdk-core` becomes the `commonMain`
source set of a Kotlin Multiplatform library. Android-specific modules get
iOS siblings (e.g., `sdk-engine-coreml`).

### Why Android-first instead of KMP-from-day-1

- **Velocity**: KMP build configuration in 2026 is mature but still slower
  than pure Android development. The Android-only MVP ships ~30-40% faster.
- **iOS portability cost is paid by `sdk-core` rules, not by KMP setup**:
  if `sdk-core` is pure Kotlin/JVM and uses KMP-compatible libraries (okio,
  kotlinx.coroutines, kotlinx.datetime), the eventual iOS port becomes a
  refactor of platform-specific modules, not the domain layer.
- **iOS demand is hypothetical**: We have zero confirmed iOS customers. Building
  for hypothetical demand burns time.

See [ADR 0002](0002-kmp-readiness-rules.md) for the specific KMP-readiness
rules enforced in `sdk-core`.

### Trade-off

- Some discipline tax on `sdk-core` development (use okio instead of java.io.File,
  kotlinx.datetime instead of java.time, etc.).
- iOS port in v0.5 will still require ~4-6 weeks of work, not "press the
  KMP button."

## Decision 4: No DI framework

Velat does not use Hilt, Koin, Dagger, or any DI framework. Dependency injection
happens via constructor injection. `Velat.create()` composes the object graph.

### Why

- **Consumer footprint**: Hilt requires kapt in the consumer's app and adds
  to their build time. Inappropriate for a library.
- **Koin is runtime DI**: Adds startup cost and complicates SDK initialization.
- **Constructor injection is sufficient**: Velat has < 20 internal classes with
  clear graph composition. Manual wiring in `Velat.create()` is readable and
  debuggable.
- **Standard in Kotlin libraries**: OkHttp, Retrofit, Ktor — all use constructor
  injection without a DI framework.

### Trade-off

- `Velat.create()` becomes the central composition root. If it grows beyond
  ~100 lines we should refactor into a builder helper, not introduce DI.

## Decision 5: No Room (direct SQLite)

We use the Android SQLite framework directly (via `androidx.sqlite`), not Room.

### Why

- **Virtual tables**: sqlite-vec and FTS5 require `CREATE VIRTUAL TABLE`
  statements. Room doesn't support virtual tables natively.
- **Performance**: On hot retrieval paths (~30ms target), Room's annotation-
  processor-generated DAO methods add measurable overhead.
- **Build time**: Room's annotation processing (kapt) adds significant
  compile time. Direct SQLite has no annotation processing.
- **Storage layer abstraction**: We define `VelatStore` interface in
  `sdk-core`. Implementations are swappable (could even add a Room-backed
  implementation later as an alternative).

### Trade-off

- Manual SQL writing and result mapping. Mitigated by keeping the database
  layer thin and well-tested.

## Decision 6: Conventional Commits

All commit messages follow [Conventional Commits 1.0.0](https://www.conventionalcommits.org/).

### Why

- **Automated changelog**: Conventional-changelog tooling can generate
  release notes from commit history.
- **Semantic versioning hints**: `feat:` triggers minor version bump,
  `fix:` triggers patch, `BREAKING CHANGE:` triggers major. Automation possible.
- **Mental discipline**: Forces "what kind of change is this?" thinking
  before commit. Splits mixed commits into atomic changes.

### Trade-off

- One more rule to follow. Mitigated by CONTRIBUTING.md documenting the format.

## Decision 7: Code quality enforced from day 1

Spotless (formatting + license headers), Detekt (static analysis), and Binary
Compatibility Validator (API tracking) all run in CI on every PR. No "we'll
add tooling later."

### Why

- **Free signal**: Quality tools catch issues humans don't notice in review.
- **Style consistency**: A solo founder today may be a team of 5 in 18 months.
  Consistent style from day 1 means future contributors don't fight legacy.
- **API stability**: BCV catches accidental breaking changes before they ship.
  Critical for an SDK with version commitments.

### Trade-off

- Some upfront setup cost. Paid back within weeks of development.

## Future decisions deferred

These are explicitly NOT decided today, to be revisited at named milestones:

- **iOS port specifics**: v0.5 milestone (see [ADR 0002](0002-kmp-readiness-rules.md)
  for the discipline that keeps the path open).
- **Reranking architecture**: when cross-encoder support lands.
- **Telemetry policy**: if/when any opt-in usage metrics are added.
