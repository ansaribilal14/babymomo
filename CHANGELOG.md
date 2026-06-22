# Changelog

All notable changes to BABYMOMO will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Real on-device LLM inference via MediaPipe GenAI** for Gemma models in `.task` format. `LocalLlmProvider` now dispatches to a new `MediapipeLlmEngine` (wrapping `com.google.mediapipe:genai-text-llm-inference-android:0.10.14`) when the active model's runtime is `MEDIAPIPE_GENAI`. Real streaming (partial-result callback → token deltas) is supported; non-MediaPipe runtimes still fall through to Remote / Mock.
- **Two Gemma models added to the catalog in MediaPipe `.task` format**: `gemma-2b-it-mediapipe` (int8, ~1.7 GB) and `gemma-1b-it-mediapipe` (int4, ~1.4 GB, fits low-RAM devices). Both run via MediaPipe GenAI — no GGUF / llama.cpp JNI required.
- **Real model downloads via `ModelDownloadWorker`** — users can fetch GGUF and `.task` models with live progress, integrity verification (MD5), and cancellation support. Downloads run as `CoroutineWorker`s through WorkManager with per-model unique work names (preventing duplicate queues), a foreground notification on the dedicated "Model downloads" channel (`IMPORTANCE_LOW`), and atomic temp-file → final-file rename after MD5 verification. `ModelsScreen` now shows a live `LinearProgressIndicator` + Cancel button during downloads and surfaces MD5/HTTP errors with a Retry affordance.

### Planned for v0.2
- Wire `llama.cpp` JNI bridge into `LocalLlmProvider` for the existing GGUF catalog entries (Phi-3 / Qwen / Llama / SmolLM2). MediaPipe GenAI was wired first as the fastest path to real on-device inference; llama.cpp is the follow-up for arbitrary-GGUF support.
- Bundle `bge-small-en-v1.5` int8 ONNX (~33 MB) as an asset and wire `OnnxEmbedder`.
- Implement `ModelDownloadWorker` to fetch `.task` / GGUF files from their respective hosts.
- First-launch onboarding flow that prompts the user to pick + download a model.
- Graph visualization screen (currently entities/relations are queryable but not visually browsable).
- Recursive CTE graph traversal in Room for multi-hop queries.
- Proper Room migrations (replacing `fallbackToDestructiveMigration`).

## [0.1.0] — 2026-06-22

### Added — Initial release
- **MOMO Memory** (core IP): Room database with bi-temporal memories (`validFrom`/`validUntil`/`supersededBy` adapted from Zep/Graphiti) + knowledge graph (entities, relations, memory-entity links) + flat-cosine vector index + full retrieval pipeline (vector recall → entity match → 2-hop graph expansion → 4-signal rerank: cosine similarity + graph proximity + confidence + recency decay).
- **MOMO Mind**: `LlmProvider` interface with three implementations — `MockLlmProvider` (deterministic, always-available), `RemoteLlmProvider` (OpenAI-compatible with REAL Server-Sent Events streaming), `LocalLlmProvider` (v0.2 stub). `WrappedLlmProvider` decorator enriches the system prompt with recalled memories + graph facts + active project context. `LlmProviderChain` tries local → remote → mock.
- **MOMO Kernel**: `RequestClassifier` (rule-based routing decisions) + `MomoKernel` (streaming + non-streaming `process` methods). Auto-persists every conversation turn as episodic memory and triggers `MemoryExtractor` in the background.
- **MOMO Agents**: 5 specialists (Planner, Researcher, Memory, Critic, Executor) + `AgentOrchestrator` running them in a fixed pipeline based on routing decisions.
- **MOMO Skills**: 5 built-in skills (Write Article, Summarize, Study Assistant, Plan Project, Analyze PDF) registered via Hilt multi-binding.
- **Project System**: living project entities that auto-create a matching node in the knowledge graph; tasks with priorities and statuses.
- **Warm companion UI**: full Jetpack Compose app — cream/amber palette, serif headings, drawer + bottom-bar navigation, 7 screens (Chat, Memory, Projects, Skills, Agents, Models, Settings).
- **Models tab**: curated catalog of 5 downloadable GGUF models (Gemma 2B, Phi-3 mini, Qwen 2.5 1.5B, Llama 3.2 3B, SmolLM2 1.7B).
- **Settings**: privacy controls (internet off by default), optional remote provider config (URL/key/model), memory-extraction toggle, critic toggle.
- **WorkManager**: periodic 24h `MemoryMaintenanceWorker` for poisoned-memory cleanup + bi-temporal GC.
- **CI**: GitHub Actions workflow that builds the debug APK on every push and uploads it as a workflow artifact.

### Architecture decisions
- **Memory graph**: Option E (Room + flat cosine + bi-temporal graph + BGE-small embeddings) selected after evaluating 6 options and red-teaming the top 2. See `docs/memory-graph-research.md`.
- **LLM integration**: `LlmProvider` interface shape adopted (with attribution, not code) from OpenDroid (Apache-2.0). Streaming is REAL (not fake like OpenDroid's implementation).
- **WrappedLLMProvider decorator pattern**: adopted from OpenDroid. Uses `dagger.Lazy<T>` to break the Hilt cycle (LLM provider needs memory; memory needs LLM for extraction).
- **Poisoned-memory cleanup pattern**: adopted from OpenDroid, but moved from `Application.onCreate` (which blocks startup) to a periodic WorkManager job.
