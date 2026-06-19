# Contributing to Velat

Thanks for considering a contribution. This document covers the conventions
used in this codebase.

## Development setup

### Prerequisites

- JDK 11 or higher
- Git
- An IDE that supports Kotlin and Gradle (IntelliJ IDEA or Android Studio
  recommended)

### Initial setup

```bash
git clone https://github.com/getvelat/velat-android.git
cd velat-android
./gradlew assemble
```

The Gradle wrapper (`./gradlew`) downloads the configured Gradle version
(currently 8.11.1) automatically on first use; no manual install required.

### IDE setup

Open the project root in IntelliJ IDEA or Android Studio. The IDE should
detect the Gradle project and import it. Wait for indexing to complete
before navigating code.

Recommended IDE plugins:
- Kotlin (bundled)
- Gradle (bundled)
- EditorConfig (bundled — reads our `.editorconfig`)

## Commit conventions

This project uses [Conventional Commits](https://www.conventionalcommits.org/).
Commit messages must follow this format:

```
<type>(<optional-scope>): <short description>

<optional longer body>

<optional footer>
```

### Types

| Type        | Use for                                                |
|-------------|--------------------------------------------------------|
| `feat`      | A new feature                                          |
| `fix`       | A bug fix                                              |
| `docs`      | Documentation changes only                             |
| `style`     | Formatting, missing semicolons (no code change)        |
| `refactor`  | Code change that neither fixes a bug nor adds a feature|
| `perf`      | Performance improvement                                |
| `test`      | Adding or correcting tests                             |
| `build`     | Build system, dependencies, or tooling changes         |
| `ci`        | CI configuration changes                               |
| `chore`     | Other changes that don't modify source or test files   |

### Scopes (optional)

Common scopes for this project:

- `core` — changes in `sdk-core`
- `engine` — changes in `sdk-engine-mediapipe`
- `storage` — changes in `sdk-storage-sqlite`
- `rag` — changes in `sdk-rag`
- `pdf` — changes in `sdk-pdf`
- `compose` — changes in `sdk-compose`
- `samples` — changes in any sample app
- `deps` — dependency version bumps

### Examples

```
feat(core): add VelatStore interface

Defines the persistence contract for embedding storage. Implementations
will plug in through Velat.create()'s graph composition.
```

```
fix(rag): correct RRF score normalization for empty result sets

The previous implementation divided by zero when one of the two ranking
sources returned no results. Now we treat an empty list as not
contributing to the fused score.
```

```
docs: add Day 1 scaffolding overview
```

```
build(deps): bump kotlin from 2.1.0 to 2.1.10
```

### Breaking changes

A breaking change is any change to public API that requires a consumer to
modify their code. Mark these with `BREAKING CHANGE:` in the commit body
or with a `!` after the type:

```
feat(core)!: rename Velat.create() to Velat.initialize()

BREAKING CHANGE: Velat.create() is renamed to Velat.initialize() for
consistency with Android's SDK conventions. Migration: replace all
Velat.create() calls with Velat.initialize().
```

Breaking changes during the 0.x phase trigger a minor version bump
(0.1 → 0.2). Once we hit 1.0, breaking changes require a major version
bump (1.x → 2.0).

## Code style

- **Indentation**: 4 spaces for Kotlin (per [Kotlin style guide](https://kotlinlang.org/docs/coding-conventions.html))
- **Line length**: 120 characters max
- **Encoding**: UTF-8
- **Line endings**: LF

These are enforced by `.editorconfig`; your IDE should pick them up
automatically. Code that doesn't conform is rejected by CI.

## Testing

- Every public API gets at least one unit test in the same package under
  `src/test/kotlin/`.
- Tests use JUnit 5 (Jupiter), MockK, Turbine, and Truth.
- Run tests: `./gradlew test`

## Pull requests

- Branch from `main`. Branch names should describe the work, e.g.
  `feat/sqlite-vec-spike` or `fix/chunker-overlap-bug`.
- Keep PRs focused. One feature or fix per PR.
- Reference issues in the PR description: `Closes #42`.
- CI must pass before review.

## License

By contributing, you agree that your contributions will be licensed under
the Apache License 2.0. See [LICENSE](LICENSE).
