# Changelog

All notable changes to BABYMOMO will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Unit tests for `MemoryGraph` (entity resolution, bi-temporal relations, graph expansion), `RequestClassifier` (routing heuristics), `FlatVectorIndex` (cosine similarity math), `MemoryRecaller` (4-signal rerank scoring).
- `CONTRIBUTING.md` with setup, branch strategy, commit conventions, PR process, and extension points (Skills, Agents, LLM providers).

### Planned for v0.2
- Wire `MediaPipe GenAI` or `llama.cpp` JNI bridge into `LocalLlmProvider` so downloaded models actually run on-device.
- Bundle `bge-small-en-v1.5` int8 ONNX (~33 MB) as an asset and wire `OnnxEmbedder`.
- Implement `ModelDownloadWorker` to fetch GGUF files from HuggingFace.
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
