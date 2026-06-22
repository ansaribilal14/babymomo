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

### Model downloads

**Decision:** Model downloads run through `ModelDownloadWorker` — a `@HiltWorker` `CoroutineWorker` enqueued via `WorkManager.enqueueUniqueWork("babymomo.model.download.<modelId>", ExistingWorkPolicy.REPLACE, …)`. The worker streams the response body to `filesDir/models/<modelId>.tmp`, optionally MD5-verifies the temp file against `ModelEntity.md5`, then atomically renames it to `filesDir/models/<filename>` and calls `ModelManager.markDownloaded(modelId, path)`. Live progress is surfaced via `setProgress(workDataOf(KEY_BYTES_DOWNLOADED, KEY_TOTAL_BYTES, KEY_PHASE))` and observed on the UI side by mapping `WorkManager.getWorkInfosForUniqueWorkFlow(...)` to a `Flow<DownloadState>` in `ModelDownloadState`.

**Why WorkManager (not a raw foreground `Service`):**

1. **Lifecycle safety.** A user-initiated download can outlive the foreground Activity (user backgrounds the app, swipes between tabs, rotates the device). WorkManager keeps the worker alive across configuration changes and process death (it retries on the next boot if the process is killed mid-flight); a hand-rolled `Service` would need to re-implement all of that.
2. **Native cancellation + dedup.** `enqueueUniqueWork(name, REPLACE, …)` guarantees only one in-flight download per model id — re-tapping "Download" while a download is running replaces the existing work rather than spawning a duplicate. `cancelUniqueWork(name)` cleanly cancels the coroutine (which the worker handles via `CancellationException` cleanup).
3. **Backoff + retry built in.** Transient failures (HTTP 5xx, IOException) return `Result.retry()` and WorkManager re-attempts with exponential backoff. The model row stays in `DOWNLOADING` during the backoff window (we only flip it to `ERROR` once the retry budget is exhausted, so the UI doesn't ping-pong between Retry and the progress bar). Permanent failures (HTTP 4xx, MD5 mismatch, empty body) return `Result.failure(workDataOf(KEY_ERROR to msg))` and surface in the UI as `DownloadState.Failed`.
4. **Already the project's background-job pattern.** `MemoryMaintenanceWorker` (the periodic 24h memory sweep) established `@HiltWorker` + `CoroutineWorker` + Hilt DI as the project's pattern. Reusing it keeps the worker surface area uniform — same DI graph, same `HiltWorkerFactory` configuration on `BabymomoApp`.

**Why OkHttp (not `HttpURLConnection`):** OkHttp is already a project dependency (used by `RemoteLlmProvider` for SSE streaming). Reusing the shared `OkHttpClient` singleton (provided by `LlmModule.provideOkHttp`) gives us connection pooling, configurable timeouts, transparent gzip, and the existing `HttpLoggingInterceptor` for diagnostics — all for free.

**Why a per-model unique work name (`"babymomo.model.download.<modelId>"`):** the same model can never be queued twice. If a user double-taps Download, or re-enters the Models tab while a download is running, the existing work is replaced atomically. Different models can download in parallel (their unique names don't collide).

**Why MD5 verification:** catalog entries optionally carry an `md5` field (currently blank for all entries — future work). When non-blank, the worker streams the downloaded temp file through `MessageDigest("MD5")` and compares the hex digest against the expected value, failing the work permanently on mismatch. This protects against truncated transfers (TCP/OS-level corruption) and tampered mirrors; MD5 was chosen over SHA-256 because (a) it's already fast enough on multi-GB files, (b) the threat model is accidental corruption, not adversarial tampering — model provenance is anchored by the catalog URL.

**Why a foreground notification (Android 14+):** Android 14 (API 34) requires long-running background work to declare a foreground service type — `FOREGROUND_SERVICE_TYPE_DATA_SYNC` here, paired with the `FOREGROUND_SERVICE_DATA_SYNC` permission (already in the manifest from v0.1). The notification lives on a dedicated channel (`babymomo.model.downloads`, `IMPORTANCE_LOW`, no badge) so it doesn't chirp on every progress update. The worker calls `setForeground(ForegroundInfo(...))` immediately after marking the model `DOWNLOADING` to stay inside the 5-second promotion window. Notification progress (`setProgress(100, percent, ...)`) is throttled to the same cadence as `setProgress` (~ every 512 KB) to avoid notification-rate-limiting.

**Why stream-to-temp-file + atomic rename:** if the process is killed mid-download (OOM, user force-stop), the temp file is left behind and cleaned up on the next attempt. The final file only ever exists at its final name in a fully-downloaded + verified state — the loader (`LocalLlmProvider` / `MediapipeLlmEngine`) never sees a partial file. The rename is atomic on the same filesystem; if it fails (cross-FS edge case) we fall back to `copyTo(overwrite=true)` + delete.

**What the UI sees:** `ModelsViewModel.downloadStateFlow(modelId)` returns a cold `Flow<DownloadState>` (Idle / Downloading(bytes, total) / Verifying / Complete / Failed) backed by `getWorkInfosForUniqueWorkFlow`. `ModelsScreen.ModelCard` collects it with `collectAsStateWithLifecycle(initialValue = Idle)` and renders a `LinearProgressIndicator` + Cancel button during `DOWNLOADING`, a Retry button + error message during `ERROR`, and the existing Activate button once the model row's status flips to `READY` (driven by the Room `Flow`, not the WorkInfo).

### Embeddings

**Decision:** For v0.2 we wire `OnnxEmbedder` to run `BAAI/bge-small-en-v1.5` directly on-device via ONNX Runtime Mobile (`com.microsoft.onnxruntime:onnxruntime-android:1.17.0`). The int8-quantized ONNX model is bundled as an app asset (`app/src/main/assets/models/bge-small-en-v1.5-int8.onnx`) and `EmbedderProvider` routes to it on first use; `MockEmbedder` remains the transparent fallback when the asset is missing.

**Why BGE-small-en-v1.5 won the embedding slot:**

1. **Right-sized for personal memory.** 384-dim × 50k memories = ~76 MB resident in the flat-cosine index — fits comfortably on a 4 GB phone alongside the LLM. A 768-d model would double that for ~2 MTEB points we don't need at personal scale.
2. **Tiny binary footprint.** ~33 MB int8 ONNX — small enough to bundle in the APK rather than download. This keeps v0.2 simple (no separate embedding-model downloader; no "embedding model not yet downloaded" state to manage) and lets the model ship pre-warmed for first launch.
3. **Apache-2.0 license.** Permissive — no GPL/CC-BY-NC entanglement for a commercial Android app.
4. **MTEB 62.28.** Solidly above `all-MiniLM-L6-v2` (56.3) and competitive with 768-d `bge-base-en-v1.5` (63.6) at a quarter of the dims. Good enough for semantic recall against Gemma-generated extraction text.
5. **Mature ONNX story.** `onnx-community/bge-small-en-v1.5-ONNX` publishes ready-to-use int8/quantized variants — no PyTorch → ONNX export step on our side.

**Why ONNX Runtime Mobile (not MediaPipe / llama.cpp):**

- BGE is a pure transformer encoder — ONNX Runtime is the canonical runtime for that class of model on Android. MediaPipe's text tasks don't cover encoder-only embedding models today.
- The `onnxruntime-android` AAR ships per-ABI native libs in-tree (same shape as the MediaPipe LLM dependency we already accept) — no NDK build on our side.
- `SessionOptions.addNnapi()` routes supported ops to the Android NNAPI delegate, which transparently targets GPU/NPU on capable devices and falls back to the CPU provider elsewhere. We wrap the call in `runCatching` so devices/emulators without NNAPI features degrade to CPU without crashing.

**Why bundled-as-asset (not downloaded):**

- The LLM model is user-downloadable because it's 1–4 GB and users need to pick a size that fits their device. The embedding model is 33 MB — small enough to bundle, large enough that bundling matters for first-launch latency.
- Bundling eliminates an entire category of "model not yet present" UI state from the memory pipeline. `MemoryService.addEpisodicMemory` can call `embedderProvider.current().embed(...)` unconditionally on the very first launch and get a real embedding back.
- The trade-off is a ~33 MB larger APK. Acceptable for v0.2; revisit if Play Store size policy or app-growth priorities change.

**Graceful degradation when the asset is missing:**

Dev / CI builds ship only a `bge-small-en-v1.5-int8.onnx.placeholder` marker file (the ~33 MB real binary is intentionally omitted from the repo — see the placeholder file for the HF download URL). `OnnxEmbedder.ensureLoaded()` detects this via `AssetManager.open()` → `FileNotFoundException`, marks itself unavailable, and returns `false`. `EmbedderProvider.current()` then returns `MockEmbedder` transparently — the rest of the app never sees a missing-model exception. This is the same "always-available fallback" pattern the LLM provider chain uses.

**v0.2 limitation — minimal hash-based tokenizer (NOT real BGE WordPiece):**

The real BGE-small-en-v1.5 expects WordPiece token IDs from its 30,522-token `vocab.txt` (the `bert-base-uncased` vocab). For v0.2 we did NOT bundle that vocab — instead `BertTokenizer` lowercases, splits on `[^a-z0-9]+`, and hashes each token to a stable int in `[0, 30_000)` via FNV-1a. The input_ids land inside the model's embedding table (same range as the real vocab), so the model still runs end-to-end and produces contextual pooled vectors — but they are NOT the IDs the model was trained on, so semantic quality is degraded vs. a proper WordPiece tokenizer. This is enough for v0.2 development + integration testing of the retrieval pipeline (the reranker's graph + recency + confidence signals carry most of the load until embeddings improve). See `BertTokenizer.kt` KDoc for the full caveat.

**v0.3 plan:**

1. **Preferred:** bundle `bert-base-uncased`'s 860 KB `vocab.txt` and implement real greedy-longest-match WordPiece (with `##` continuation pieces, `[UNK]` mapping, and the `[CLS]`/`[SEP]`/`[PAD]` handling already in place). This is a tokenizer-only change — the model file, `OnnxEmbedder`, and `EmbedderProvider` stay untouched.
2. **Alternative:** swap the whole embedder to `EmbeddingGemma` (256-d Matryoshka, multilingual, with its own SentencePiece tokenizer). This is a drop-in behind the `Embedder` interface but requires re-embedding all stored memories (the dim change is breaking). Tracked under the v0.3 multilingual upgrade.
---

## 4. Testing strategy

**Decision:** BABYMOMO ships with a foundational layer of JUnit 4 unit tests for the
deterministic, framework-independent core (memory graph, vector index, request classifier,
rerank scoring). The full DI / Room / Compose stack is exercised by instrumented tests
planned for v0.3.

### Why JUnit 4 + hand-written in-memory DAOs (not MockK)

1. **Tests real behavior.** Room DAOs are interfaces with `@Query` annotations. Mocking
   them with MockK verifies "the mock was called with these args" — not that production
   code works against real SQL semantics. A hand-written in-memory DAO (a `mutableListOf`
   + a few filter predicates, ~30 lines) faithfully mirrors the SQL (LIKE substring
   matching, `validUntil IS NULL` filtering, etc.) and tests the *real* `MemoryGraph`
   / `MemoryService` logic.
2. **Zero new dependencies.** MockK adds ~1 MB of bytecode and a learning curve. For an
   app where the testing surface is mostly "does this weighted sum / SQL-like filter do
   the right thing?", hand-written stubs are smaller, faster, and clearer.
3. **Reads like a spec.** Tests with backtick-quoted names (`fun `resolveOrCreate deduplicates by canonical name`()`)
   and self-contained in-memory DAOs read as executable documentation. A new contributor
   can understand `MemoryGraph`'s contract by reading the test file top-to-bottom.

See [`MemoryGraphTest.kt`](../app/src/test/java/com/babymomo/core/memory/MemoryGraphTest.kt)
for the canonical example: `InMemoryEntityDao`, `InMemoryRelationDao`, `InMemoryLinkDao`
are hand-written, ~30 lines each, and exercise entity resolution, bi-temporal invalidation,
and 1-/2-hop expansion without any mocking framework.

### Test the math, not the framework

Pure formulas (cosine similarity, rerank scoring, complexity escalation) are extracted into
small `internal` functions and tested directly. Spinning up the full DI graph (embedder +
vector index + graph + memory service) just to verify a weighted sum is overkill.

- **`MemoryRecaller.computeRerankScore(...)`** — extracted as an `internal` companion
  function so the 4-signal formula (0.5·cos + 0.2·graph + 0.2·conf + 0.1·recency) can be
  verified in isolation. See
  [`MemoryRecallerRerankTest.kt`](../app/src/test/java/com/babymomo/core/memory/MemoryRecallerRerankTest.kt).
- **`FlatVectorIndex.cosineSimilarity` / `bytesToFloats`** — kept private; exercised via
  the public `search()` / `rebuild()` API with constructed embeddings. No public-API
  widening required. See
  [`FlatVectorIndexCosineTest.kt`](../app/src/test/java/com/babymomo/core/memory/FlatVectorIndexCosineTest.kt).
- **`RequestClassifier.classify`** — pure function, no DI, tested exhaustively in
  [`RequestClassifierTest.kt`](../app/src/test/java/com/babymomo/core/kernel/RequestClassifierTest.kt).

### What's tested today (v0.1.x)

| Component | What | How |
|---|---|---|
| `MemoryGraph` | Entity resolution (canonicalization, alias merge), relation dedup, bi-temporal invalidation, 1-/2-hop neighbor expansion | Hand-written in-memory DAOs + `runTest` |
| `FlatVectorIndex` | Cosine similarity (identical/orthogonal/opposite/empty/different-length), byte-to-float decoding round-trip (384-dim → 1536 bytes → 384-dim lossless), sorted-descending search, dedup-by-id, remove | Public `search()` / `rebuild()` API |
| `RequestClassifier` | Keyword-triggered routing (planning, research, critic, tools, internet), length-based complexity escalation, `needMemory` invariant | Pure function, no DI |
| `MemoryRecaller` reranker | 4-signal scoring formula, individual signal weight checks, recency-decay curve at 0/30/60 days | Extracted `internal` companion function |

### Planned for v0.3

- **Integration tests (Room with in-memory DB).** `androidx.room:room-testing` provides
  `Room.inMemoryDatabaseBuilder(...)` which runs the actual SQLite + Room stack against a
  throwaway DB. These will cover the real SQL semantics (FTS, LIKE, indices, migrations)
  that the hand-written in-memory DAOs approximate. They run on the JVM via Robolectric
  (no device required) or as instrumented tests.
- **Compose UI tests.** `createAndroidComposeRule<MainActivity>()` for the 7 main screens —
  verify navigation flows, memory list filtering, chat streaming UI states. These require
  an emulator / device.
- **CI runs `./gradlew test` on every push.** Currently CI only runs `./gradlew
  assembleDebug` (build green = ship). Adding the test step is a one-line workflow change
  once the test count justifies the CI minutes — v0.3 is the target.
- **Agent + Skill tests with a stubbed LLM.** `MockLlmProvider` returns deterministic
  responses, making it possible to unit-test `PlannerAgent.run()` / `WriteArticleSkill.execute()`
  end-to-end without a real LLM.

