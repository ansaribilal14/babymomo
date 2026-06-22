# Architecture Decisions

This document summarizes the research that drove BABYMOMO's architecture. Two deep research reports were produced during v0.1 development (one on the OpenDroid reference repo, one on mobile memory-graph architectures). The full reports are not in this repo, but the conclusions are below.

---

## 1. LLM integration — adopted from OpenDroid (Apache-2.0)

**Source:** Studied https://github.com/yashab-cyber/opendroid (Apache-2.0).

### What we adopted (with attribution, not code)

1. **`LlmProvider` interface shape** — `suspend complete(messages, config): Result<LlmResponse>` + `Flow<String> streamComplete(messages, config)` + `suspend isAvailable()` + `suspend status()`. Single interface for all LLM access (local, LAN, remote). Maps perfectly onto BABYMOMO's on-device-first → LAN → cloud strategy.

2. **Provider fallback chain** — `LlmProviderChain` tries providers in priority order until one succeeds. Default order: Local (wrapped) → Remote if configured (wrapped) → Mock (always available).

3. **`WrappedLLMProvider` decorator pattern** — enriches the system prompt at the edge with recalled memories + graph facts + active project context. Uses `dagger.Lazy<T>` to break the Hilt cycle (LLM provider needs memory; memory needs LLM for extraction).

4. **Poisoned-memory cleanup** — defensive deletion of LLM-persisted "facts" containing known garbage phrases ("As an AI...", "I don't know", "[ERROR]", etc.). Moved from `Application.onCreate` (which blocks startup) to a periodic WorkManager job.

### What we did NOT adopt (and why)

- **Fake streaming** — OpenDroid's `streamComplete()` runs `complete()` to finish, then re-emits words with a 50ms delay. Not real streaming. BABYMOMO's `RemoteLlmProvider` does REAL Server-Sent Events streaming via OkHttp.

- **Substring memory recall** — OpenDroid loads all memories into memory and does naive substring filtering. No embeddings, no vectors. BABYMOMO uses real embeddings + flat-cosine vector search + graph expansion + 4-signal reranking.

- **Always-on foreground service** — OpenDroid uses a foreground service as its only background mechanism. BABYMOMO uses `WorkManager` for periodic maintenance (the correct Android pattern).

- **Hardcoded "Gemini Nano on-device" mock** — OpenDroid claims on-device inference but their GeminiNanoProvider returns canned JSON. BABYMOMO's `LocalLlmProvider` is honestly stubbed for v0.2 and the chain falls through to `MockLlmProvider` transparently.

---

## 2. Memory graph — Option E (Room + flat cosine + bi-temporal graph)

**Source:** Deep research with critic on mobile vector+graph hybrid memory architectures (24 web searches, 6 options evaluated A–F, top 2 red-teamed).

### Architectural options evaluated

| Option | Description | Score | Verdict |
|---|---|---|---|
| A | Room graph + sqlite-vss vectors in same DB | 4/10 | Fights Room (Room doesn't support loading SQLite extensions). |
| B | Room graph + hnswlib-java (in-memory HNSW) | 6/10 | Real ANN at scale, but RAM cost is the killer (100k × 384d ≈ 150 MB resident). |
| C | ObjectBox for both (graph + vectors) | 7/10 | First mobile vector DB, but it's an object DB, not a graph DB — no traversal query language. Vendor lock-in. |
| D | Apache Jena TDB (RDF triples) + custom vector layer | 5/10 | Heavy, JVM-oriented, not Android-native. |
| **E** | **Room graph + flat brute-force cosine (in pure Kotlin)** | **8/10** | **Selected.** Zero native deps, zero patent risk, fully Room-native, fast enough at personal scale (10–50k memories, ~10ms). |
| F | LanceDB mobile | 6/10 | Columnar + vector native, but Kotlin SDK immature. |

### Why Option E won (executive summary)

- **Zero native dependencies** — no JNI, no NDK build, no patent exposure.
- **Room-native** — same database file, same transaction log, same Flow/Hilt/migration story as the rest of the app.
- **Fast enough at personal scale** — 50k memories = ~10ms query, ~76 MB resident. The realistic ceiling for a single user's lifetime.
- **Upgrade path behind an interface** — `VectorIndex` is an interface; `FlatVectorIndex` is the impl. When memory count crosses ~50k or p95 latency exceeds 200ms, drop in `HnswVectorIndex` (one class change). Graph schema and retrieval pipeline stay untouched.

### Embedding model

- **Default:** `BAAI/bge-small-en-v1.5` — 384-dim, 33.4M params, ~33 MB int8 ONNX, MTEB 62.28, Apache 2.0.
- **Runtime:** ONNX Runtime Mobile with NNAPI delegate (GPU/NPU acceleration on supported devices).
- **Multilingual upgrade path:** `EmbeddingGemma` (256-d Matryoshka, multilingual) — drop-in replacement behind `Embedder` interface; re-embed all memories on switch.
- **v0.1 status:** `OnnxEmbedder` is stubbed. Uses `MockEmbedder` (deterministic hash-based, 384-dim) for tests + first launch.

### Bi-temporal model (adapted from Zep / Graphiti)

Every memory and relation tracks:
- `createdAt` — ingestion time (when we wrote it)
- `validFrom` — event time (when the fact became true in the world)
- `validUntil` — invalidation time (null = currently true; non-null = superseded)
- `supersededBy` — FK to the memory that replaced this one

Facts are NEVER deleted — they are invalidated. This enables time-travel queries ("what did I know about John's job last month?") and provenance audits.

### Retrieval pipeline (4-signal reranker)

```
query → embed (BGE-small, ~20ms)
      → vector recall (top 30, flat cosine)
      → entity match (query tokens vs entities.canonicalName + aliases)
      → graph expansion (2-hop neighbors of matched entities)
      → rerank: 0.5·cos_sim + 0.2·graph_proximity + 0.2·confidence + 0.1·recency_decay
      → top 8 memories + top 20 graph facts
      → assemble into <memories> + <graph> blocks in system prompt
```

### Critic's key insight

Empirical evidence from prior art:
- **Letta's filesystem baseline** scored 74% on the LoCoMo long-conversation benchmark, beating several specialized memory systems. Implication: retrieval quality and reranking matter more than fancy tiering.
- **Mem0's dual-store drift bug** (vector store and graph store get out of sync) — avoided by using a single Room database for both.
- **HybridRAG's win** was about *combining* vector+graph, not the index algorithm.

Conclusion: the graph schema, bi-temporal model, reranker, and extraction quality are what make memory strong. The vector index is a commodity. Option E invests engineering effort where it matters.

### Risks surfaced by the critic

1. **Extraction LLM weakness (HIGH)** — the real bottleneck isn't the DB, it's whether Gemma 2B Q4 can cleanly extract typed entities/relations from free text. *Mitigation:* allow user to upgrade to Phi-3/Qwen 7B for extraction; fall back to regex/NER; expose an editable entities UI.

2. **Scale wall at 100k+ memories (MEDIUM)** — flat cosine over 100k × 384d ≈ 150 MB RAM and 10–30 ms latency — borderline on 4 GB phones with the LLM also resident. *Mitigation:* load only active (`validUntil IS NULL`) embeddings; the `VectorIndex` interface makes HNSW migration a one-class swap. 1M memories is 5–10 years out for a single user.

---

## 3. Other key decisions

### Hilt + Room + Compose (no exceptions)
Google-blessed stack. Maximum compatibility, minimum surprise.

### Jetpack Compose (not XML views)
Single-activity Compose host. Warm companion palette (cream/amber, serif headings, generous rounding). Drawer + bottom-bar navigation across 7 screens.

### WorkManager (not Service)
Periodic memory maintenance runs every 24h via WorkManager. Not a foreground service — those are for user-visible ongoing tasks (music playback, navigation), not background housekeeping.

### `dagger.Lazy<T>` for cycle-breaking
`WrappedLlmProvider` needs `MemoryRecaller` to inject memories into the system prompt. `MemoryExtractor` needs `LlmProvider` to call the extraction LLM. Direct injection creates a Hilt cycle. `dagger.Lazy<T>` breaks it: the dependency is resolved on first use, not at construction.

### User-downloadable models (not bundled)
APK stays small (~85 MB). Users pick a model that fits their device's RAM from a curated catalog (Gemma 2B / Phi-3 mini / Qwen 2.5 1.5B / Llama 3.2 3B / SmolLM2 1.7B). All GGUF format — runtime is swappable (llama.cpp / MediaPipe / MLC) without changing the catalog.

### MediaPipe GenAI as the first on-device LLM runtime (v0.2)

**Decision:** For v0.2 we wire `LocalLlmProvider` to [MediaPipe GenAI](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android) (`com.google.mediapipe:genai-text-llm-inference-android:0.10.14`), with two Gemma models added to the catalog in `.task` format (the MediaPipe-specific model container — NOT GGUF). The existing GGUF/`LLAMA_CPP` catalog entries remain untouched; they fall through the `LocalLlmProvider` dispatch table to Remote / Mock until their runtime is wired in a later PR.

**Why MediaPipe GenAI won the "first runtime" slot:**

1. **Google-blessed, pre-built `.so` per ABI.** MediaPipe's AAR ships arm64-v8a, armeabi-v7a, and x86_64 native libs in-tree. No NDK build, no CMakeLists, no toolchain pain on our side — drop the dependency in and Gradle does the rest. Compare to llama.cpp (we'd have to build + ship our own JNI wrapper) or MLC LLM (TVM compile step per model).
2. **Works with Gemma out of the box.** Gemma 2B Instruct is the canonical MediaPipe LLM Inference sample model; `.task` files for int8 and int4 quantizations are Google-hosted. Gemma is also our default extraction model for the memory graph (see §2), so we get one consistent model family across reasoning + extraction.
3. **Fastest path to real on-device inference.** Wrapping the `LlmInference` + `LlmInferenceSession` Java API in a thin Kotlin engine (`MediapipeLlmEngine`, ~150 lines) was a single-PR job. Native llama.cpp would have been a multi-week JNI/NDK project.
4. **Streaming IS supported.** MediaPipe's `LlmInferenceSession.generateResponseAsync(partialResultListener)` delivers partial results via callback. One caveat: the callback receives the **cumulative** text (not deltas) — `MediapipeLlmEngine` converts cumulative → delta before emitting through its `Flow<String>`. This matches the rest of our `LlmProvider` ecosystem which all emit deltas.
5. **Same dependency shape as our embedding stack.** MediaPipe is already a Google product we trust (we use ONNX Runtime for embeddings today, but MediaPipe Tasks is the parallel Google-blessed path); adopting it for LLM keeps the "official Google runtime" surface area coherent.

**Limitations we accept with MediaPipe GenAI (and how we mitigate):**

- **Only Gemma + a handful of other models in `.task` format.** MediaPipe GenAI does NOT consume arbitrary GGUF files — you must convert models to MediaPipe's `.task` format (a tflite-flatbuffer container with tokenizer + weights). In practice the supported set is Gemma (1.1, 2), Falcon, Phi-2, and StableLM. This is why our catalog's Phi-3 / Qwen / Llama / SmolLM2 entries stay on `LLAMA_CPP` — we can't run them via MediaPipe today. *Mitigation:* when llama.cpp JNI is wired in a later PR, those entries light up unchanged. The `ModelRuntime` enum on `ModelEntity` was designed for exactly this multi-runtime world.
- **No `topP` / `stopSequences` / per-request `maxTokens` in session options (v0.10.14).** MediaPipe derives `topP` from `topK` internally; stop sequences are unsupported; `maxTokens` is set engine-wide (we cap at 8192 = Gemma's context length). *Mitigation:* document the gap in `MediapipeLlmEngine` kdoc; the `LlmGenerationConfig` fields are silently ignored for MediaPipe models. Not a blocker for v0.2 (none of our agents rely on stop sequences today).
- **One session per engine at a time.** MediaPipe's `LlmInferenceSession` is not safe for concurrent use on a single `LlmInference` instance. *Mitigation:* `MediapipeLlmEngine` serializes all inference through a `Mutex`. For v0.2 this is fine (the kernel is single-user, single-conversation); if we ever pipeline agents in parallel we'll need to either pool engines or accept the serialization.
- **`.task` URLs are Google-hosted and versioned.** Unlike HuggingFace GGUF URLs (stable per-commit), Google's `storage.googleapis.com/mediapipe-models/...` paths can change between MediaPipe releases. The catalog entries carry a `// TODO: verify URL` comment; `ModelDownloadWorker` (planned for v0.2) should validate the URL returns 200 before showing a Download button.

**Where this leaves us:** with v0.2, a user who downloads `gemma-2b-it-mediapipe` or `gemma-1b-it-mediapipe` from the Models tab gets REAL on-device Gemma inference through `LocalLlmProvider` → `MediapipeLlmEngine` → MediaPipe native libs. The provider chain falls through to Remote / Mock for any other model runtime, exactly as in v0.1.
