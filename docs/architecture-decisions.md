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

