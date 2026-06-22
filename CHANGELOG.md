# Changelog

All notable changes to BABYMOMO will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Real BGE-small-en-v1.5 ONNX model bundled as an app asset (~33 MB).** `OnnxEmbedder` now loads the actual int8-quantized BGE model at runtime, producing real 384-dim semantic embeddings. The placeholder file is replaced with the real binary (sourced from `onnx-community/bge-small-en-v1.5-ONNX`'s `onnx/model_quantized.onnx` + external `_data` weights, re-saved as a single all-embedded `.onnx` via Python `onnx` so ORT loads it from a single asset without external-data file juggling). Verified inputs (`input_ids` / `attention_mask` / `token_type_ids`, all `int64`) and outputs (`last_hidden_state` `[batch, seq, 384]` + `sentence_embedding` `[batch, 384]`) match the BERT input contract `OnnxEmbedder` was already written against. APK size grows by ~33 MB but no runtime download is needed. The graceful-degradation fallback to `MockEmbedder` remains wired for corrupt-asset / failed-extraction edge cases.
- **Real BERT WordPiece tokenizer** with the canonical `bert-base-uncased` 30,522-token `vocab.txt` (~232 KB) bundled as an app asset at `app/src/main/assets/models/bert-base-uncased-vocab.txt`. `BertTokenizer` now performs proper greedy-longest-match WordPiece tokenization (with `##` continuation pieces, `[UNK]` mapping, `[CLS]`/`[SEP]`/`[PAD]` special tokens, 512-token truncation+padding), plus the standard BERT basic-tokenizer pre-processing (control-char cleaning, whitespace normalization, lowercasing, NFD accent stripping, per-character punctuation splitting). The vocab is loaded lazily on first `tokenize()` call and cached for the process lifetime (`@Singleton`). Embeddings now match the model's expected input distribution — semantic quality is significantly improved vs. v0.2's hash-based tokenizer. `OnnxEmbedder` now injects `BertTokenizer` via Hilt (constructor param) instead of constructing it inline; its inference pipeline is unchanged.

## [0.2.0] — 2026-06-22

### Added
- **On-device LLM inference scaffold via MediaPipe GenAI** for Gemma models in `.task` format. `LocalLlmProvider` dispatches to a new `MediapipeLlmEngine` (wrapping `com.google.mediapipe:tasks-genai:0.10.14`) when the active model's runtime is `MEDIAPIPE_GENAI`. **Note: the engine is stubbed in v0.2 — see Known Issues below.** Non-MediaPipe runtimes still fall through to Remote / Mock.
- **Two Gemma models added to the catalog in MediaPipe `.task` format**: `gemma-2b-it-mediapipe` (int8, ~1.7 GB) and `gemma-1b-it-mediapipe` (int4, ~1.4 GB, fits low-RAM devices). Both are intended to run via MediaPipe GenAI — no GGUF / llama.cpp JNI required. (Downloads work; on-device inference is stubbed pending v0.3 — see Known Issues.)
- **Real model downloads via `ModelDownloadWorker`** — users can fetch GGUF and `.task` models with live progress, integrity verification (MD5), and cancellation support. Downloads run as `CoroutineWorker`s through WorkManager with per-model unique work names (preventing duplicate queues), a foreground notification on the dedicated "Model downloads" channel (`IMPORTANCE_LOW`), and atomic temp-file → final-file rename after MD5 verification. `ModelsScreen` now shows a live `LinearProgressIndicator` + Cancel button during downloads and surfaces MD5/HTTP errors with a Retry affordance.
- **Real semantic embeddings via BGE-small-en-v1.5 ONNX model** (when the model asset is present; falls back to `MockEmbedder` otherwise). Bundled as an app asset — no download required.
- Unit tests for `MemoryGraph` (entity resolution, bi-temporal relations, graph expansion), `RequestClassifier` (routing heuristics), `FlatVectorIndex` (cosine similarity math), `MemoryRecaller` (4-signal rerank scoring).
- `CONTRIBUTING.md` with setup, branch strategy, commit conventions, PR process, and extension points (Skills, Agents, LLM providers).

### Known Issues
- **MediaPipe GenAI on-device LLM inference is STUBBED in v0.2.** `MediapipeLlmEngine` was originally written against MediaPipe's session-based LLM API (`LlmInferenceSession`, `addQueryChunk`, per-session `generateResponse`, streaming partial-result callbacks returning a Guava `ListenableFuture`, per-session `Options`). Inspection of the actual `tasks-genai:0.10.14` AAR (via `javap` on `classes.jar`) shows that **none of that session API exists** in 0.10.14 — the artifact ships only `LlmInference` + its nested `LlmInference.LlmInferenceOptions` (engine-level options, single `generateResponse(String): String`, `generateResponseAsync(String): void` returning void). The session API landed in a later MediaPipe release. Rather than ship a half-rewired engine against the wrong surface, `MediapipeLlmEngine` is a compile-clean stub that throws `IllegalStateException("MediaPipe GenAI runtime API not yet finalized — see v0.3")` from `configure` / `complete` / `streamComplete`. `LocalLlmProvider.isAvailable()` returns `false` for all runtimes, so `LlmProviderChain` skips Local entirely and falls through to Remote → Mock. Downloads of the Gemma `.task` catalog entries still work end-to-end (the model file lands in internal storage); only on-device inference is deferred. v0.3 will either upgrade `mediapipeGenai` to a version that ships the session API and restore the original engine, or rewrite the engine against the actual 0.10.14 `LlmInference.generateResponse` + `setResultListener` surface.
- **BGE-small ONNX embeddings ship as a `.placeholder`** (~0 bytes) rather than the real ~33 MB int8 model binary. `OnnxEmbedder.ensureLoaded()` detects the missing asset and returns `false`; `EmbedderProvider` falls back to `MockEmbedder`. The ONNX Runtime API integration itself is real (NNAPI delegate wired, BERT input contract, mean-pool + L2-norm) — only the model binary is absent from the repo to keep it cloneable. Drop the real `.onnx` into `app/src/main/assets/models/bge-small-en-v1.5-int8.onnx` to enable real embeddings. Also, `BertTokenizer` is a hash-based stand-in, not real WordPiece (see v0.3 plan). **[Resolved in v0.3]** — the real ~33 MB int8 ONNX binary is now bundled as an app asset; see the `[Unreleased] → Added` entry above. (The `BertTokenizer` hash-vs-WordPiece caveat is also resolved in v0.3 — see below.)
- **`BertTokenizer` is hash-based, not real WordPiece.** Embeddings (when the model is present) are meaningful but lower-quality than a proper BGE WordPiece tokenizer. Tracked for v0.3. **[Resolved in v0.3]** — the real `bert-base-uncased` 30,522-token `vocab.txt` is now bundled as an app asset and `BertTokenizer` performs proper greedy-longest-match WordPiece tokenization; see `[Unreleased] → Added` above.

### Planned for v0.3
- Wire `llama.cpp` JNI bridge into `LocalLlmProvider` for the existing GGUF catalog entries (Phi-3 / Qwen / Llama / SmolLM2). MediaPipe GenAI was wired first as the fastest path to real on-device inference; llama.cpp is the follow-up for arbitrary-GGUF support.
- Bundle the real `bge-small-en-v1.5` int8 ONNX model binary (~33 MB) — currently a `.placeholder` file is shipped; users (or CI) must drop the real `.onnx` into `app/src/main/assets/models/` to enable real embeddings. **[Done in v0.3]** — see `[Unreleased] → Added`.
- Bundle the real BGE WordPiece `vocab.txt` and implement a proper tokenizer (current `BertTokenizer` uses FNV-1a hash mod 30k — functional but lower-quality than real WordPiece). **[Done in v0.3]** — see `[Unreleased] → Added`.
- First-launch onboarding flow that prompts the user to pick + download a model.
- Graph visualization screen (currently entities/relations are queryable but not visually browsable).
- Recursive CTE graph traversal in Room for multi-hop queries.
- Proper Room migrations (replacing `fallbackToDestructiveMigration`).
- Integration tests (Room with in-memory DB) + UI tests (Compose).

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
