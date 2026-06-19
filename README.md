# Velat

> Production-grade on-device LLM and RAG SDK for Android.
>
> Run LLMs locally. Feed them your documents. Nothing leaves the device.

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)

**Status:** Pre-alpha. Day 1 scaffolding. No public release yet.

---

## What is Velat

Velat is an Android SDK that lets you add on-device AI features — chat over a
small language model and retrieval-augmented question answering over the user's
documents — in approximately ten lines of Kotlin. It runs entirely on the
device. Nothing the user types and nothing the user uploads ever leaves the
phone.

It's built on:

- [MediaPipe LLM Inference](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference)
  for on-device language model execution.
- [sqlite-vec](https://github.com/asg017/sqlite-vec) for persistent vector
  storage.
- SQLite FTS5 for keyword (BM25) search.
- Reciprocal Rank Fusion (RRF) for hybrid retrieval.

It is **not** a wrapper around Google's deprecated MediaPipe RAG SDK; the
RAG layer is original work, distributed under Apache 2.0, with no dependency
on deprecated code.

## What Velat is not

- Not an inference engine (we wrap MediaPipe LLM Inference)
- Not a multi-platform library yet (Android-only for v0.1; iOS port planned
  for v0.5+)
- Not a hosted service (everything runs on-device)

## Roadmap

- **v0.1 (planned: Q3 2026)** — MediaPipe LLM Inference wrapper, sqlite-vec
  vector store, hybrid retrieval, PDF/text/markdown ingestion, citations,
  Compose helpers.
- **v0.2** — Cross-encoder reranking, OCR, auto-model-selection.
- **v0.3** — Tool calling, multi-query retrieval, conversation persistence.
- **v0.5** — iOS port via Kotlin Multiplatform.

## Project Structure

```
velat-android/
├── sdk-core/                # Pure Kotlin foundation (KMP-ready)
├── (more modules added incrementally)
└── docs/adr/                # Architecture Decision Records
```

Full architecture details: [docs/adr/](docs/adr/).

## Building

```bash
git clone https://github.com/getvelat/velat-android.git
cd velat-android
./gradlew assemble
```

Requires JDK 11+. Gradle is provided via the wrapper (`./gradlew`); no local
install needed.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Apache License 2.0. See [LICENSE](LICENSE).

The core SDK is open source and free for any use, commercial or otherwise.
Future Pro tier modules will be distributed under a separate commercial
license. The structure of the project (interfaces, module boundaries) is
designed to support both without compromising the OSS core.
