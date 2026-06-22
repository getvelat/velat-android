# ADR 0001: Architecture Overview

**Status:** Accepted
**Date:** 2026-06-19 (Day 1)
**Deciders:** Rafal Niski

## Context

Velat is a production-grade on-device LLM and RAG SDK for Android, distributed
via Maven Central as `ai.velat:sdk:<version>`. It must:

- Ship a usable v0.1 in 8 weeks of solo work.
- Support potential commercial Pro tier modules without restructuring the OSS core.
- Be portable to iOS later (planned v0.5+) without rewriting domain logic.
- Maintain stable public API for B2B customers who integrate it into their apps.
- Be auditable by compliance-conscious customers (legal, medical, regulated industries).

This document captures the architectural decisions made before any production
code was written, so future contributors and future-me can understand the
reasoning.

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
- **Future Pro modules**: Commercial modules like `sdk-storage-encrypted` and
  `sdk-rag-advanced` can be developed and published separately under their own
  commercial license without affecting the OSS core.
- **Architectural enforcement**: `sdk-core` being a `kotlin.jvm` module
  (not Android library) means the compiler refuses Android-specific imports.

### Trade-off

- More Gradle configuration overhead. Mitigated by version catalog
  (`gradle/libs.versions.toml`) centralizing dependencies.

## Decision 2: License — Apache 2.0 for OSS core

Considered: MIT, Apache 2.0, AGPL, BSL (Business Source License), proprietary.

**Chosen:** Apache 2.0.

### Why

- **Patent grant**: Apache 2.0 includes an explicit grant of patents related
  to the contribution, with automatic termination if a licensee sues for patent
  infringement on the same code. For an SDK implementing algorithms (RRF fusion,
  retrieval pipelines), this protection matters. MIT has no such clause.
- **B2B-friendly**: Compliance-conscious customers' legal teams audit Apache 2.0
  quickly because it's familiar and explicit. AGPL would frighten enterprise
  buyers (forces them to open-source their own code). BSL would slow adoption.
- **OSI-approved**: Recognized as an open-source license by the Open Source
  Initiative. Required for Maven Central publishing of "free" artifacts.
- **Permissive**: Allows commercial use, modification, redistribution without
  open-sourcing derivatives. Maximizes adoption.

### Trade-off

- Anyone can fork Velat and create a competing product. Mitigation: brand,
  community, integration support, and faster iteration speed are the moat —
  not the source code itself.

## Decision 3: Monetization — Open Core

The SDK foundation stays Apache 2.0 forever. Future Pro features (encrypted
storage, advanced reranking, OCR integration, compliance documentation pack,
priority support) ship under separate commercial license as additional modules.

### Why

- **No SaaS backend**: Velat is entirely on-device. We can't monetize hosted
  services (the typical OSS-to-paid path). We must monetize the code itself.
- **Open Core is industry-standard**: GitLab, Elastic (pre-2021), MongoDB
  (pre-SSPL) all used this model successfully.
- **OSS adoption is the moat**: Free SDK builds developer trust and ecosystem
  momentum. Paid tier captures enterprise budget for premium features.

### Architectural implications

To make Pro modules feasible without re-architecting in v0.2:

- All cross-module dependencies go through interfaces defined in `sdk-core`.
- The graph composition in `Velat.create()` is the only place that knows about
  concrete implementations.
- Pro implementations plug in by providing concrete classes for the same
  interfaces; the OSS core doesn't need to change.

### Trade-off

- Discipline cost: every new feature must consider "does this belong in OSS
  or Pro?" Wrong choice locks us into the wrong tier.
- License validation infrastructure (LicenseValidator interface, JWT signing,
  Cloudflare Worker) doesn't ship in v0.1 — deferred to v0.2 when the first
  Pro module lands.

## Decision 4: KMP-ready Android-first

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

## Decision 5: No DI framework

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

## Decision 6: No Room (direct SQLite)

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

## Decision 7: Conventional Commits

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

## Decision 8: Code quality enforced from day 1

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

- **License key validation infrastructure**: v0.2 milestone, when first Pro
  module ships.
- **Pricing tiers**: After MVP launch and customer discovery confirms
  willingness-to-pay tier boundaries.
- **iOS port specifics**: v0.5 milestone.
- **Logo and brand kit**: Before public launch, not before v0.1 development.
