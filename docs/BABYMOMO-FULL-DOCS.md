# BABYMOMO — The Definitive Technical Document

**A private AI companion that grows into your personal operating system.**
v0.3.0 · Kotlin 1.9.22 · Jetpack Compose · MediaPipe GenAI 0.10.35 · ONNX Runtime 1.17 · Room 2.6.1 · MIT License

> BABYMOMO is an Android app that ships one AI, one memory, and one brain, forever — running entirely on the user's device. v0.3.0 is the first release where the three pillars are simultaneously real: a real Gemma LLM streams token-by-token through MediaPipe GenAI's session API; a real BGE-small-en-v1.5 int8 ONNX model produces 384-dim semantic embeddings through a real BERT WordPiece tokenizer; and a real bi-temporal knowledge graph persists entities, relations, and atomic facts with full provenance. This document explains what the app does, how it compares to the landscape, how it is engineered, and how a single user message flows through the entire system from keystroke to memory card.

---

## Table of Contents

1. What our app does
2. Standpoint vs competitors
3. How the framework is set up
4. Normal workflow when a user asks a question
5. How the brain system works
6. How the LLM is integrated
7. How the memory web system is set up and ensures perfection
8. Appendix A — Glossary
9. Appendix B — References
10. Appendix C — Version history
11. Appendix D — Roadmap

---

## 1. What our app does

BABYMOMO is, in plain English, an AI that lives on your phone, talks to you, and remembers everything important about your life — projects, goals, people, preferences, past conversations — so the longer you use it, the more useful it becomes. It is not a chatbot in the ChatGPT sense. A chatbot starts every conversation with amnesia; BABYMOMO starts every conversation by silently searching a personal knowledge graph it has been building since your first message, then hands that context to the language model so the answer is grounded in what it actually knows about you.

The target user is someone whose life has enough moving parts that they would benefit from a second brain: a founder juggling multiple projects, a graduate student with research threads and deadlines, a consultant with many clients, a parent coordinating a household, or anyone who has ever re-explained the same context to a fresh ChatGPT window for the fifth time this week. The problem BABYMOMO solves is forgetting — not the user's forgetting, but the assistant's. Every existing AI assistant forgets. BABYMOMO does not.

The end-to-end experience is deliberately warm. You install the APK (sideloaded from GitHub Releases or self-built; no Play Store needed). On first launch you see a cream-and-amber Compose UI with a single chat screen. Out of the box — before any model is downloaded — the app runs on a `MockLlmProvider` that responds with friendly, deterministic canned text so the UI is fully explorable. The first real action is opening the **Models** tab and downloading one of seven catalog models. For v0.3 the two that actually run on-device are the MediaPipe `.task` Gemma builds (`gemma-2b-it-mediapipe`, int8, ~1.7 GB; `gemma-1b-it-mediapipe`, int4, ~1.4 GB for low-RAM phones). Once a Gemma model is activated, every subsequent message is answered by real Gemma running on your phone's CPU/GPU/NPU through MediaPipe GenAI 0.10.35, with tokens streaming to the UI as they are generated.

After the first real conversation, you start seeing memory growth. Every turn — both your message and MOMO's response — is auto-persisted as an episodic memory. In the background, a `MemoryExtractor` calls the LLM with a structured extraction prompt, parses JSON, resolves entities against the knowledge graph, asserts bi-temporal relations, and writes atomic facts as semantic memories. Open the **Memory** tab and you can browse memories by type (Working / Episodic / Semantic / Procedural), filter by source (`USER_STATED` vs `LLM_INFERRED`), search by content, and watch the counts climb: active memories, total memories, entities, relations. Open the **Projects** tab and create a living project that auto-creates a matching node in the knowledge graph so the LLM can recall project context during chat. Open the **Skills** tab to run one of five built-in mini-workflows (Write Article, Summarize, Study Assistant, Plan Project, Analyze PDF). Open the **Agents** tab to see the five specialist agents (Planner, Researcher, Memory, Critic, Executor) and the routing pipeline that decides when each fires.

The key user-facing differentiators from ChatGPT, Claude, Gemini, and Copilot are: (1) your data never leaves your device — no cloud round-trip, no telemetry, no training on your prompts; (2) the memory is real and inspectable — you can read every memory, see its confidence and provenance, and watch the graph grow; (3) the memory is bi-temporal — facts are invalidated, not deleted, so you can ask "what did MOMO know about John's job last month?"; (4) the assistant works offline once a model is downloaded; (5) the entire stack — LLM, embeddings, tokenizer, vector index, graph, reranker — is on-device and open-source.

Concrete things a user can do today in v0.3: chat with real Gemma 2B Instruct and watch tokens stream; tell MOMO "remember that my sister Aisha works at Northwind as a data engineer" and watch a `PERSON` entity for Aisha, a `PLACE` entity for Northwind, a `WORKS_AT` relation between them, and a semantic fact appear in the Memory tab; ask "what do you know about my sister?" two days later and have the recaller pull those entities via 2-hop graph expansion and inject them into the system prompt so MOMO answers correctly; create a project "Q4 launch plan", add tasks, set it as the active project, and have MOMO automatically use that project as primary context in subsequent chats; invoke the Plan Project skill by typing "create a project for learning Rust" and watch MOMO parse its own structured output and persist a real project entity with five seeded tasks.

---

## 2. Standpoint vs competitors

BABYMOMO sits at an unusual intersection: on-device LLM chat, persistent memory graph, personal knowledge tool, and agent orchestrator. No single competitor covers all four axes. The honest framing is that we outperform everyone on the intersection while losing to specialists on each individual axis.

### 2.1 Cloud AI assistants (ChatGPT, Claude, Gemini, Copilot)

| Capability | Cloud assistants | BABYMOMO v0.3 |
|---|---|---|
| On-device, offline inference | ❌ | ✅ (MediaPipe Gemma) |
| Persistent personal memory graph | ❌ (ChatGPT Memory is a flat text blob) | ✅ (bi-temporal entities + relations) |
| Privacy (no cloud round-trip) | ❌ | ✅ |
| Frontier model quality | ✅ (GPT-4o, Claude 3.5, Gemini 1.5) | ⚠️ (Gemma 2B — much weaker) |
| Tool use / function calling at scale | ✅ | ⚠️ (5 skills, no function-call protocol) |
| Multimodal (vision, voice) | ✅ | ❌ |
| Cost to operate | $ per token | $0 (all on-device) |

They have a frontier model (GPT-4o is roughly 10× Gemma 2B's reasoning quality), mature tool use, multimodal input, and a polished ecosystem. We have a real knowledge graph that survives across sessions, bi-temporal provenance, on-device privacy, and zero recurring cost.

### 2.2 Local AI chat apps (LM Studio, MLC Chat, PocketPal, Layla, Faraday)

| Feature | LM Studio / Faraday | MLC Chat / PocketPal | Layla | BABYMOMO v0.3 |
|---|---|---|---|---|
| Local LLM runtime | llama.cpp | MLC LLM (TVM) | llama.cpp | MediaPipe GenAI + (v0.4) llama.cpp |
| Real streaming | ✅ | ✅ | ✅ | ✅ (MediaPipe ProgressListener → Flow) |
| Persistent memory graph | ❌ | ❌ | ⚠️ (flat notes) | ✅ (bi-temporal) |
| Vector recall + rerank | ❌ | ❌ | ❌ | ✅ (BGE-small + 4-signal rerank) |
| Agent orchestration | ❌ | ❌ | ❌ | ✅ (5 agents, 4-stage pipeline) |
| Skills / project system | ❌ | ❌ | ❌ | ✅ |
| Model catalog + downloader | ✅ | ✅ | ✅ | ✅ (WorkManager + MD5) |
| Android-native UI | ❌ (desktop) | ✅ | ✅ | ✅ (Compose) |

Our advantage over this entire category is the memory graph: every peer stops at "load a model, chat, lose history." We are the only local-chat app with a real knowledge graph, real embeddings, and real reranking. Our disadvantage is runtime breadth — today only MediaPipe Gemma actually runs on-device; arbitrary GGUF (Phi-3, Qwen, Llama, SmolLM2) is wired into the catalog but the llama.cpp JNI bridge is pending v0.4, so those entries fall through to Remote/Mock.

### 2.3 Memory-augmented AI (Mem0, Letta/MemGPT, Zep, Kin, Apple Intelligence)

| System | Memory model | Bi-temporal | On-device | Vector + graph hybrid |
|---|---|---|---|---|
| Mem0 | Vector + graph (dual-store) | ❌ (drift bug known) | ❌ | ✅ |
| Letta / MemGPT | Tiered (working / archival) | ❌ | ❌ | ⚠️ (vector only) |
| Zep / Graphiti | Graph + temporal | ✅ | ❌ | ✅ |
| Kin | Personal journal + recall | ❌ | ✅ | ❌ |
| Apple Intelligence | System-level recall | ❌ | ✅ | ❌ (closed) |
| **BABYMOMO** | **Room + graph + flat vector** | **✅** | **✅** | **✅** |

Our memory architecture most closely resembles Zep/Graphiti's bi-temporal model — adapted to Room and pure-Kotlin cosine instead of Neo4j + a separate vector store. The single-store design (graph + vectors in one Room file) is the explicit fix for Mem0's documented drift bug where the vector store and graph store get out of sync. Letta's filesystem baseline scoring 74% on LoCoMo (beating several specialized memory systems) drove our decision to invest in reranking quality, not index sophistication.

### 2.4 Personal knowledge tools (Notion AI, Obsidian + plugins, Logseq, Tana)

These are document-first: you write notes, optionally query them with AI. BABYMOMO is conversation-first: you chat, the AI extracts structure automatically. Overlap exists in the project entity and the graph-browsable memory, but the philosophy is inverted — Notion AI expects you to author the structure, BABYMOMO extracts it for you. Our weakness: no rich document editor, no collaborative multi-user, no web clipper. Our strength: zero manual authoring burden — the graph grows as a side effect of conversation.

### 2.5 On-device OS-style AI (Apple Intelligence, Google Pixel AI, Samsung Galaxy AI)

These are deep-OS integrations: system-wide writing tools, notification summaries, image cleanup, Siri/Pixel assistant upgrades. BABYMOMO is a single app — it cannot rewrite text in any other app's text field, cannot see your notifications, cannot integrate with the OS assistant. What we have that they don't: a fully open, inspectable, bi-temporal memory graph; the ability to swap models; cross-manufacturer portability (any Android 8+ device, not just Pixel or Galaxy); and a transparent on-device reasoning pipeline you can read end-to-end in Kotlin.

### Three strongest advantages

1. **The memory graph is the IP.** Bi-temporal entities + relations + 4-signal rerank + invalidation is a deeper memory model than any local-chat competitor and matches the best research (Zep/Graphiti) while running on-device.
2. **The architecture is honestly layered.** `LlmProvider` → `LlmProviderChain` → `WrappedLlmProvider` → `LocalLlmProvider` → `MediapipeLlmEngine` is a clean, swappable stack. Same for `Embedder` → `EmbedderProvider` → `OnnxEmbedder` and `VectorIndex` → `FlatVectorIndex`. Every layer has a documented upgrade path.
3. **Everything is on-device and open-source.** Real Gemma, real BGE ONNX, real WordPiece tokenizer, real flat-cosine vector index — no cloud, no telemetry, MIT-licensed.

### Three biggest weaknesses

1. **Only Gemma works for on-device LLM today.** The five GGUF catalog entries (Phi-3, Qwen, Llama, SmolLM2, Gemma GGUF) fall through to Remote/Mock pending the v0.4 llama.cpp JNI bridge.
2. **MediaPipe `.task` download URLs are unverified.** Google's `storage.googleapis.com/mediapipe-models/...` paths can change between releases; catalog entries carry a `// TODO: verify URL` comment.
3. **No first-launch onboarding, no graph visualization, no sync.** A new user lands on an empty chat with no model and has to discover the Models tab themselves. The graph is queryable but not visually browsable. Memory lives on one device — lose the phone, lose the mind.

---

## 3. How the framework is set up

BABYMOMO is a single-module Android app (`applicationId = com.babymomo`, `minSdk = 26`, `targetSdk = 34`, `compileSdk = 34`) built with Gradle 8.4 (AGP 8.2.2), Kotlin 1.9.22, and a single `libs.versions.toml` version catalog as source of truth. The build is KSP-driven (no kapt) and uses the Compose compiler plugin (`kotlinCompilerExtensionVersion = "1.5.9"`).

### Tech stack

| Library | Version | Role |
|---|---|---|
| Kotlin | 1.9.22 | Language; JVM target 17 |
| AGP | 8.2.2 | Android Gradle Plugin |
| KSP | 1.9.22-1.0.17 | Annotation processor (Room, Hilt) |
| Compose BOM | 2024.02.02 | UI (Material3, foundation, navigation) |
| Hilt | 2.50 | DI (with `hilt-work` 1.2.0 for `@HiltWorker`) |
| Room | 2.6.1 | Local SQLite ORM (10 entities, 9 DAOs, schema exported to `app/schemas/`) |
| ONNX Runtime | 1.17.0 | On-device BGE-small embedding inference with NNAPI delegate |
| MediaPipe GenAI | 0.10.35 | On-device Gemma LLM inference (session API, ProgressListener streaming) |
| OkHttp | 4.12.0 | HTTP (model downloads, remote LLM SSE streaming) |
| Moshi | 1.15.0 | JSON (LLM extraction, OpenAI-compatible chat completions) |
| WorkManager | 2.9.0 | Background jobs (periodic memory sweep, model downloads) |
| Coroutines | 1.7.3 | Async (incl. `kotlinx-coroutines-guava` for MediaPipe `ListenableFuture`) |
| Coil / DataStore / Accompanist | 2.5.0 / 1.0.0 / 0.34.0 | Images / prefs / permissions |

### Module structure (actual `app/src/main/java/com/babymomo/` tree)

```
com/babymomo/
├── BabymomoApp.kt                 # @HiltAndroidApp, WorkManager config, startup sweep
├── MainActivity.kt                # Single-activity Compose host
├── core/
│   ├── llm/
│   │   ├── LlmProvider.kt         # interface + LlmMessage / LlmGenerationConfig / LlmResponse
│   │   ├── LlmProviderChain.kt    # Local → Remote → Mock fallback
│   │   ├── WrappedLlmProvider.kt  # enriches system prompt with memories + graph + project
│   │   ├── LocalLlmProvider.kt    # dispatches by ModelRuntime
│   │   ├── RemoteLlmProvider.kt   # OpenAI-compatible, REAL SSE streaming
│   │   ├── MockLlmProvider.kt     # deterministic fallback
│   │   ├── MediapipeLlmEngine.kt  # MediaPipe LlmInference + session + streaming
│   │   └── di/LlmModule.kt        # @Provides OkHttpClient, Moshi, qualified providers
│   ├── memory/
│   │   ├── Embedder.kt            # interface
│   │   ├── OnnxEmbedder.kt        # BGE-small int8 ONNX + NNAPI
│   │   ├── BertTokenizer.kt       # real BERT WordPiece (30,522 vocab)
│   │   ├── MockEmbedder.kt        # hash-based fallback
│   │   ├── EmbedderProvider.kt    # routes to Onnx or Mock
│   │   ├── VectorIndex.kt         # interface + FlatVectorIndex (brute cosine)
│   │   ├── MemoryGraph.kt         # entities, relations, 2-hop expansion
│   │   ├── MemoryService.kt       # addEpisodic/Semantic/Procedural, invalidate
│   │   ├── MemoryRecaller.kt     # vector recall → entity match → graph → 4-signal rerank
│   │   ├── MemoryExtractor.kt     # LLM extraction prompt → JSON → resolve → assert → link
│   │   ├── MemoryMaintenance.kt   # poisoned cleanup + bi-temporal GC
│   │   └── di/MemoryModule.kt     # @Binds VectorIndex → FlatVectorIndex
│   ├── kernel/
│   │   ├── MomoKernel.kt          # brain stem: process() + streamProcess()
│   │   ├── RequestClassifier.kt   # rule-based routing → RoutingDecision
│   │   └── di/KernelModule.kt
│   ├── agents/
│   │   ├── Agent.kt               # interface + AgentTask / AgentResult
│   │   ├── AgentOrchestrator.kt   # Research → Plan → Critic → Execute
│   │   ├── PlannerAgent.kt        # breaks down complex goals
│   │   ├── ResearchAgent.kt       # recalls memories, summarizes
│   │   ├── MemoryAgent.kt         # explicit remember/recall
│   │   ├── CriticAgent.kt         # PASS/REVISE/REJECT verdict
│   │   └── ExecutorAgent.kt       # runs matching Skill
│   ├── skills/
│   │   ├── Skill.kt               # interface + SkillResult
│   │   ├── Skills.kt              # 5 built-in skills
│   │   ├── SkillRegistry.kt       # finds skill by trigger keywords
│   │   └── di/SkillsModule.kt     # @ElementsIntoSet multi-binding
│   └── projects/
│       ├── ProjectService.kt      # createProject + graph node auto-creation
│       └── ProjectContextProvider.kt  # active-project StateFlow → injected into system prompt
├── data/db/
│   ├── BabymomoDatabase.kt        # @Database(entities=[10], version=1, exportSchema=true)
│   ├── entity/                    # MemoryEntity, EntityEntity, RelationEntity, ...
│   └── dao/                       # 9 DAOs
├── work/
│   ├── MemoryMaintenanceWorker.kt # 24h periodic
│   └── ModelDownloadWorker.kt     # one-shot, OkHttp streaming + MD5 + foreground notification
├── model/
│   └── ModelManager.kt            # active model flow + 7-entry catalog
└── ui/                            # Compose: theme, nav, 7 screens (chat, memory, projects, ...)
```

### DI architecture

Hilt with `@InstallIn(SingletonComponent::class)` everywhere; every long-lived object is `@Singleton`. **`LlmModule`** provides `OkHttpClient` (15s connect, 120s read/write, `HttpLoggingInterceptor` at BASIC), `Moshi`, and qualified `LlmProvider` bindings (`@DefaultLlm` = the chain, `@LocalLlm`, `@RemoteLlm`, `@MockLlm`); `LlmBindModule` `@Binds` the chain to `LlmProvider` so anything injecting `LlmProvider` gets the chain. `MediapipeLlmEngine` is provided via its `@Inject constructor` (no explicit `@Provides`). **`MemoryModule`** `@Binds` `FlatVectorIndex` to the `VectorIndex` interface. **`SkillsModule`** uses `@ElementsIntoSet` multi-binding so adding a sixth skill is one `@Provides` line. **Cycle-breaking**: `WrappedLlmProvider` needs `MemoryRecaller` (to inject memories into the system prompt) and `MemoryExtractor` needs `LlmProvider` (to call the extraction LLM) — direct injection creates a Hilt cycle. Both sides take `dagger.Lazy<T>` so the dependency resolves on first use, not at construction. This is the OpenDroid pattern, adopted with attribution.

### Build system

Root `build.gradle.kts` declares plugins (no versions — those live in `libs.versions.toml`). The `:app` module applies `android.application`, `kotlin.android`, `ksp`, `hilt`. KSP args: `room.schemaLocation = $projectDir/schemas` (Room exports the schema JSON for migration tracking) and `room.incremental = true`. Release enables `isMinifyEnabled` + `isShrinkResources` with `proguard-android-optimize.txt`. Packaging excludes duplicate META-INF entries (Netty, kotlin_module, AL2.0/LGPL2.1) and uses `jniLibs { useLegacyPackaging = false }` so MediaPipe and ONNX native libs stay uncompressed in the APK for direct mmap.

### CI/CD (`.github/workflows/android.yml`)

The `Build Android APK` workflow triggers on push to `main`/`dev`, tags matching `v*`, PRs to `main`, and `workflow_dispatch`. It runs on `ubuntu-latest` (30-min timeout): checkout → JDK 17 (Temurin, cached) → Android SDK (android-34, build-tools 34.0.0) → cache Gradle → `./gradlew assembleDebug --no-daemon --stacktrace` → upload `app-debug.apk` as a workflow artifact named `babymomo-debug-apk` (30-day retention). On failure it uploads `app/build/reports/` as `build-reports`. A second `release` job (needs `build`, only on `v*` tags) downloads the artifact, renames it `BABYMOMO-<tag>-debug.apk`, and creates a GitHub Release via `softprops/action-gh-release@v2` with auto-generated notes.

### The "live representation" workflow

The repo is the single source of truth: edit Kotlin → `git commit -m "feat: <what>"` → `git push` → CI builds the debug APK → APK is attached as a 30-day workflow artifact → on `git tag v0.x.0 && git push --tags` the APK is also attached to a GitHub Release. Anyone can clone, build, and reproduce the exact APK that's on GitHub Actions — no hidden build server, no proprietary CI config, no manual release step. The repo state always mirrors the latest build state.

---

## 4. Normal workflow when a user asks a question

This section traces a single user message — say, "what do you know about my sister's job?" — through the entire system from keystroke to the memory card that appears in the Memory tab.

### ASCII diagram

```
ChatScreen (Compose)
   │  user types "what do you know about my sister's job?"
   ▼
ChatViewModel.sendMessage(text)
   │  1. create MessageEntity(USER, text), conversationDao.upsertMessage, update UI state
   │  2. build history = last 10 messages → List<LlmMessage>
   ▼
MomoKernel.streamProcess(userMessage, history)
   │  3. RequestClassifier.classify() → RoutingDecision(needMemory=true, complexity=SIMPLE)
   │     emit KernelStreamEvent.Routing(decision)
   │  4. (optional) if needPlanning || needResearch || needCritic:
   │        AgentOrchestrator.run() → Research → Plan → Critic → Execute
   │        prepend "Agent context:\n..." system message
   │  5. build final messages = [agentCtx?] + history + [user]
   ▼
LlmProviderChain.streamComplete(messages, config)        ← injected as @DefaultLlm LlmProvider
   │  6. activeChain() = [wrappedLocal?, wrappedRemote?, wrappedMock]
   │     try each in order; first one that emits wins
   ▼
WrappedLlmProvider.streamComplete(messages, config)
   │  7. enrichMessages(messages):
   │     a. MemoryRecaller.recall(userMsg.content, topK=8)
   │        i.   embed query (BGE-small ONNX, ~20ms)
   │        ii.  vectorIndex.search(qEmb, k=30) → top 30 candidates (flat cosine)
   │        iii. memoryGraph.searchEntities(query, 10) → matched entities
   │        iv.  memoryGraph.twoHopNeighbors(matchedEntityIds) → graph expansion
   │        v.   4-signal rerank: 0.5·cos + 0.2·graph_prox + 0.2·conf + 0.1·recency
   │        vi.  take top 8 memories + 20 graph facts
   │     b. projectContextProvider.currentContext() → active project block
   │     c. assemble: BASE_SYSTEM_PROMPT + <memories> + <graph> + <active_project>
   │     d. return [systemMsg] + messages.filter { it.role != SYSTEM }
   ▼
LocalLlmProvider.streamComplete(enriched, config)
   │  8. modelManager.activeModelFlow().first() → ModelEntity(MEDIAPIPE_GENAI, READY, path)
   │  9. mediapipe.configure(path)   (idempotent; no-op if already loaded)
   │ 10. formatGemmaPrompt(messages) → <start_of_turn>user\n{system}\n\n{user}<end_of_turn>\n<start_of_turn>model\n
   ▼
MediapipeLlmEngine.streamComplete(prompt, config)        ← Flow<String> of token deltas
   │ 11. inferenceMutex.withLock { ... }   (serialize all inference on this engine)
   │ 12. LlmInferenceSession.createFromOptions(engine, sessionOptions)  (topK, topP, temp, seed)
   │ 13. session.addQueryChunk(prompt)
   │ 14. ProgressListener<String> { partial, done -> delta = partial.substring(lastLen); channel.trySend(delta); if (done) channel.close() }
   │ 15. session.generateResponseAsync(listener) → ListenableFuture<String>
   │ 16. for (delta in channel) emit(delta)   ← real streaming to UI
   │ 17. future.await()   (surface any post-last-callback error)
   │ 18. finally: session.close()
   ▼
ChatViewModel collects KernelStreamEvent.Token → appends to streamingText → Compose recomposes
   │ 19. on Done: create MessageEntity(ASSISTANT, fullResponse), persist, update UI
   ▼
MomoKernel bgScope.launch { ... }   ← deferred, non-blocking
   │ 20. memoryService.addEpisodicMemory("User: ...", confidence=1.0, source=USER_STATED)
   │ 21. memoryService.addEpisodicMemory("MOMO: ...", confidence=0.9, source=LLM_INFERRED)
   │ 22. memoryExtractor.extract("User: ...\nMOMO: ...", confidenceThreshold=0.6):
   │       a. LLM complete(extraction prompt) → JSON {entities, relations, facts}
   │       b. for each entity: memoryGraph.resolveOrCreate(name, type, aliases, desc)
   │          (canonicalize → findByCanonicalName OR matchByAlias → merge aliases/desc → upsert)
   │       c. for each relation: memoryGraph.assertRelation(src, tgt, type, sourceMemoryId)
   │          (check outgoingCurrent → dedup → insert with validUntil=null)
   │       d. for each fact with conf >= 0.6: memoryService.addSemanticMemory(...)
   │       e. memoryGraph.linkMemoryToEntities(semanticMemId, [(entity, conf), ...])
   ▼
Memory tab (Compose) observes memoryDao Flow → new card appears with type, source, confidence
```

### Numbered narrative (the "why" the diagram doesn't show)

The diagram above shows the call graph mechanically; these notes explain the non-obvious decisions at each step.

**Steps 1–5 (UI → kernel → classifier).** `ChatViewModel.sendMessage()` creates a `MessageEntity(role=USER, status=COMPLETE)`, persists it, and appends it to the in-memory list before doing anything else — so the user sees their message immediately even if the LLM is slow. The conversation is created lazily on first send with the user's text (truncated to 40 chars) as the title. History is bounded to the last 10 messages to fit Gemma's 4096-token engine cap. `MomoKernel.streamProcess` emits `RoutingDecision` first so the UI can render a routing-reason chip; `RequestClassifier` is a pure keyword + length/word-count function (unit-tested, no DI) — for "what do you know about my sister's job?" it returns `SIMPLE` with `stepBudget = 1` and no agents.

**Step 6 (optional orchestrator).** `AgentOrchestrator.run()` fires only when `needPlanning || needResearch || needCritic`. For a complex query like "research the latest MLOps tools and plan how I'd adopt one", it runs Research (recalls 12 memories, LLM summarizes) → Plan (LLM decomposes into 3–7 subtasks) → Critic (PASS/REVISE/REJECT) → Execute (matching Skill), concatenating each stage's output into an "Agent context" system message prepended to the conversation.

**Steps 7–9 (chain + enrichment).** `LlmProviderChain.activeChain()` returns `[wrappedLocal?, wrappedRemote?, wrappedMock]` — Local is included iff the active model is READY + MEDIAPIPE_GENAI + engine loaded; Remote iff `apiKey` non-blank; Mock always. The first provider to emit a token wins, and once one emits, the chain does not fall back (so a mid-stream failure doesn't restart the response). `WrappedLlmProvider.enrichMessages()` is the critical seam: it runs `MemoryRecaller.recall()` on the last user message (skipping if `content.length < 4`), which performs the 6-stage retrieval (embed → vector recall top 30 → entity match → 2-hop expansion → 4-signal rerank → assemble top 8 + 20 facts). It then appends `projectContextProvider.currentContext()` as an `<active_project>` block. The enriched system prompt replaces the original system message; all user/assistant messages pass through unchanged.

**Steps 10–12 (MediaPipe streaming).** `LocalLlmProvider` calls `mediapipe.configure(path)` (idempotent — no-op if the same path is already loaded), `formatGemmaPrompt()` (wraps each turn in `<start_of_turn>user`/`<start_of_turn>model` markers; Gemma has no dedicated system role so system content is prepended to the immediately-following user turn), then `mediapipe.streamComplete()`. The engine acquires `inferenceMutex`, creates a one-shot `LlmInferenceSession`, `addQueryChunk`s the prompt, and calls `generateResponseAsync(ProgressListener)`. The listener fires on the MediaPipe inference thread with **cumulative** text; the engine computes the delta suffix (`partial.substring(lastEmittedLen)`) and pushes it through an unbounded `Channel<String>`. The flow drains the channel with `for (delta in channel) emit(delta)` — **real streaming**, not fake (compare OpenDroid, which runs `complete()` to finish then re-emits words with a 50ms delay). `ChatViewModel` collects `KernelStreamEvent.Token` and appends to `streamingText`; Compose recomposes per token.

**Steps 13–14 (background memory write + extraction).** On `KernelStreamEvent.Done`, the kernel launches a coroutine on `bgScope` (`SupervisorJob` + `Dispatchers.Default`, surviving the streaming flow's completion) so memory work happens in parallel with the user reading the answer. It writes two episodic memories (`User:` at confidence 1.0 / `USER_STATED`, `MOMO:` at 0.9 / `LLM_INFERRED`), both with real BGE embeddings upserted to Room + FlatVectorIndex. Then `MemoryExtractor.extract()` calls the LLM at `temperature = 0.1` with a structured extraction prompt, parses JSON defensively (strips code fences, finds outermost `{ ... }`, returns null on any failure so a bad extraction never crashes the app), and for each entity calls `memoryGraph.resolveOrCreate()` (canonicalize → find by canonical name or alias → merge or insert — this is the dedup step where "Aisha", "aisha", "Aisha " collapse to one entity), for each relation calls `memoryGraph.assertRelation()` (dedup against `outgoingCurrent`), for each fact above `confidenceThreshold = 0.6` calls `memoryService.addSemanticMemory()`, and finally `memoryGraph.linkMemoryToEntities()` to wire the new semantic memory back to its entities. The Memory tab's Compose observes `memoryDao` Flows; new cards appear within a second of the user's message completing.

---

## 5. How the brain system works

BABYMOMO's brain is built on a deliberate human-brain metaphor that maps cleanly onto the code. The "MOMO Mind" metaphor: the app is a digital mind that, like a human mind, has a brain stem (the kernel that classifies and routes every input), a cortex (the LLM that does the heavy reasoning), a hippocampus (the memory system that writes and recalls), and a prefrontal cortex (the agent orchestrator that plans, critiques, and executes). The metaphor is not just marketing — it drives the module boundaries.

### The 5-agent system

Five specialist agents implement the `Agent` interface (`id`, `displayName`, `description`, `isAvailable()`, `run(task)`). Each is a `@Singleton` injected by Hilt.

- **PlannerAgent** (`id = "planner"`): breaks a high-level goal into 3–7 subtasks with effort estimates and dependencies. System prompt asks for `GOAL / PLAN / RISKS / NEXT_ACTION`. Fires when `RequestClassifier` sets `needPlanning = true` (triggered by "plan", "roadmap", "strategy", "steps to", "break down" keywords, or `complexity = COMPLEX`). Produces a `plan` artifact. Runs at `temperature = 0.3`.
- **ResearchAgent** (`id = "researcher"`): collects and summarizes. Before calling the LLM it runs `memoryRecaller.recall(task.input, topK = 12)` and injects the recalled memories into its system prompt so the LLM grounds its analysis in what the user actually knows. Output: `FINDINGS / ANALYSIS / GAPS / RECOMMENDATION`. Fires when `needResearch = true`. Runs at `temperature = 0.2`.
- **MemoryAgent** (`id = "memory"`): handles explicit memory writes ("remember …", "note: …", "don't forget …") and explicit recall searches. Writes go through `memoryService.addSemanticMemory(content, confidence = 1.0, source = USER_STATED)`; reads through `memoryService.searchContent(input, limit = 10)`. This is the user-facing memory affordance — the auto-extraction in `MomoKernel` is separate and always-on.
- **CriticAgent** (`id = "critic"`): verifies plans and answers. System prompt asks for `VERDICT: PASS | REVISE | REJECT` with issues and a suggested fix. Output is parsed for the verdict string and mapped to `AgentStatus.SUCCESS` / `PARTIAL`. Fires when `needCritic = true` (triggered by "verify", "check if", "is it true", "validate", "double-check") **and** only after the Planner has succeeded. Runs at `temperature = 0.1` — we want the critic deterministic.
- **ExecutorAgent** (`id = "executor"`): runs matching skills. Calls `skillRegistry.findSkillForInput(input)` (iterates skills, checks `triggerKeywords`); on match, calls `skill.execute(input)`. On no match, returns `SKIPPED` (not `FAILED`) so the orchestrator doesn't treat it as an error. Fires when `needTools = true`.

### AgentOrchestrator pipeline

`AgentOrchestrator.run(userMessage, routing)` executes a fixed pipeline in a deliberate order: **Research → Plan → Critic → Execute**. Why this order: (1) **Research first** because the Planner needs context — if the Planner ran first it would plan against zero recalled memories. (2) **Plan second** because the Critic needs something to critique — its `task.input` is literally `"Plan to verify:\n${planResult.output}"`, so it can only run if `planResult.status == SUCCESS`. (3) **Critic third**, conditionally on `routing.needCritic` and a successful Plan; its verdict is appended as a `[Critic]` block. (4) **Execute last** because skill execution is the side-effecting step (creating projects, writing articles, generating study guides); running it last lets it incorporate Plan and Critic outputs later. Each stage's output is appended to a `StringBuilder` as `[Research] …`, `[Plan] …`, `[Critic] …`, `[Action] …`. The whole string becomes a system message (`"Agent context:\n$orchestratorContext"`) prepended to the conversation history before the LLM chain is invoked. So the final LLM call sees: agent context (if any), last 10 conversation turns, current user message.

### Routing decisions

`RequestClassifier.classify(userMessage)` is a pure function — no DI, no I/O, exhaustively unit-tested. It returns `RoutingDecision(needMemory, needPlanning, needResearch, needCritic, needTools, needInternet, complexity, stepBudget, reason)`. Complexity escalation: `SIMPLE` (default, `stepBudget = 1`); `MODERATE` (any keyword hit, or length > 200 chars with multiple sentences and > 30 words, `stepBudget = 3`); `COMPLEX` (length > 500 chars or multi-sentence with > 80 words, `stepBudget = 6`, forces `needPlanning = true`). The `stepBudget` is informational today — not yet enforced as a hard cap on agent iterations — but it's recorded in `RoutingDecision` so the orchestrator can respect it once agents become iterative.

### The reasoning protocol embedded in the system prompt

`WrappedLlmProvider.BASE_SYSTEM_PROMPT` instructs the LLM to follow a 5-step protocol on every turn:

```
1. THINK   — break down what they want and why
2. CONNECT — search the provided <memories> and <graph> for relevant context
3. ANSWER  — give a direct, useful response grounded in what you know
4. LEARN   — note what new facts you should extract and store as memories
5. ACT     — if a project, goal, or task is mentioned, suggest creating/updating one
```

The prompt also instructs the LLM to cite memory IDs in square brackets (`[m_abc123]`), to treat `<memories>` and `<graph>` as authoritative over general knowledge for personal facts, and to use `<active_project>` as primary context if present. Tone: "warm, direct, never sycophantic. The user is the operator; you are the OS."

### Background vs foreground work

The split is deliberate: the user sees the routing chip, streaming tokens, and the final response in **foreground** (synchronous to the flow). Everything that writes memory — the two episodic writes (`User:` and `MOMO:`), the `MemoryExtractor.extract()` call with its LLM round-trip, entity resolution, relation assertion, semantic memory persistence, entity linking — runs in **background** on `MomoKernel.bgScope`, a `CoroutineScope(SupervisorJob() + Dispatchers.Default)` that outlives the streaming flow. The user gets their answer fast, and memory work happens in parallel; if extraction fails (LLM unavailable, JSON unparseable), the user's answer is unaffected. The cost is that the Memory tab updates a second or two after the chat response completes — an acceptable trade-off, and one that signals "MOMO is learning" rather than appearing instant.

---

## 6. How the LLM is integrated

The LLM integration is the most layered part of the codebase: one interface → three implementations → one chain → one decorator → one MediaPipe engine.

### The `LlmProvider` interface

```kotlin
interface LlmProvider {
    val name: String
    suspend fun isAvailable(): Boolean
    suspend fun status(): String
    suspend fun complete(messages: List<LlmMessage>, config: LlmGenerationConfig = LlmGenerationConfig()): Result<LlmResponse>
    fun streamComplete(messages: List<LlmMessage>, config: LlmGenerationConfig = LlmGenerationConfig()): Flow<String>
}
```

Supporting types: `LlmMessage(role, content)` with `LlmRole { SYSTEM, USER, ASSISTANT, TOOL }`; `LlmGenerationConfig(temperature, topP, topK, maxTokens, stopSequences, seed)`; `LlmResponse(content, tokensIn, tokensOut, latencyMs, finishReason, providerName, modelName)`. This interface shape was adopted (with attribution, not code) from OpenDroid (Apache-2.0). It maps cleanly onto OpenAI-compatible HTTP APIs and onto MediaPipe's session API.

### The three implementations

- **`MockLlmProvider`** — deterministic, always-available fallback. `isAvailable() = true` always. `complete()` returns a canned reply based on simple keyword matching (greetings, "remember", "project", "what do you know about me", questions, default). `streamComplete()` splits the reply on whitespace/punctuation and emits tokens with a small delay. This is what runs before any model is downloaded, so the UI is fully explorable out of the box.
- **`RemoteLlmProvider`** — OpenAI-compatible HTTP client. `configure(baseUrl, apiKey, modelName)` sets the endpoint (works with OpenAI, Groq, OpenRouter, Ollama-on-LAN — anything that speaks `/v1/chat/completions`). `complete()` does a non-streaming POST with Moshi-serialized `ChatCompletionRequest`, parses `ChatCompletionResponse`, returns `LlmResponse`. `streamComplete()` does **REAL Server-Sent Events streaming** — sets `Accept: text/event-stream`, reads the body line-by-line, parses each `data: ` line as JSON, extracts the delta content, and `emit()`s it; on `data: [DONE]` the flow completes. This is not fake streaming (which would run `complete()` then re-emit words with a delay) — tokens arrive as the remote model generates them.
- **`LocalLlmProvider`** — dispatches by `ModelRuntime`. `isAvailable()` checks `modelManager.activeModelFlow()`, confirms `status == READY` and `localPath` non-blank, then: `MEDIAPIPE_GENAI` → `mediapipe.configure(path)` (idempotent) → returns `mediapipe.isLoaded() && mediapipe.loadedPath() == path`; `LLAMA_CPP` / `MLC_LLM` / `ONNX_RUNTIME` → returns `false` (pending v0.4). `complete()` and `streamComplete()` similarly dispatch — MediaPipe path runs real inference; other runtimes emit a clear diagnostic and fall through to Remote/Mock via the chain.

### `LlmProviderChain`

The chain is itself a `LlmProvider` (injectable anywhere `LlmProvider` is expected). It holds `localProvider`, `remoteProvider`, `mockProvider` plus `dagger.Lazy<MemoryRecaller>` and `dagger.Lazy<ProjectContextProvider>` (lazy to break the Hilt cycle). It wraps each provider in a `WrappedLlmProvider` lazily. `activeChain()` returns currently-available wrapped providers in priority order: Local → Remote → Mock. `complete()` iterates, returns the first success, records the last error if all fail. `streamComplete()` collects from each in order; the first that emits a token wins, and once a provider has emitted, subsequent providers are not tried (so a mid-stream failure doesn't restart the response from a fallback). If everything fails, it emits `[All LLM providers failed]`.

### `WrappedLlmProvider` decorator

`WrappedLlmProvider(delegate, memoryRecaller, projectContextProvider)` is the seam where memory meets LLM. Its `enrichMessages(messages)` method is the single most important function in the app: it takes the raw message list, runs `MemoryRecaller.recall()` on the last user message, builds the enriched system prompt with `<memories>`, `<graph>`, and `<active_project>` blocks, and returns the new message list. `complete()` and `streamComplete()` both call `enrichMessages()` first, then delegate. The decorator takes `dagger.Lazy` for both memory and project context because `MemoryExtractor` (in the memory package) needs `LlmProvider` — direct injection would create a Hilt cycle. This is the OpenDroid pattern, documented in `docs/architecture-decisions.md` §1.

### `MediapipeLlmEngine` internals

The v0.3 crown jewel. `@Singleton`, holds `@Volatile var llmInference: LlmInference?` and `loadedPath: String?`.

- **Engine lifecycle**: `configure(modelPath)` is idempotent — if `llmInference != null && loadedPath == modelPath`, returns immediately; if the path differs, the previous engine is `close()`d first. `LlmInference.createFromOptions(context, LlmInferenceOptions.builder().setModelPath(modelPath).setMaxTokens(4096).setMaxTopK(40).build())` creates the engine. On failure, `llmInference` is reset to `null` and the exception is re-thrown wrapped in `IllegalStateException`. `release()` closes the engine and forgets the path; safe to call multiple times.
- **Per-request session**: `LlmInferenceSession.createFromOptions(engine, sessionOptionsFromConfig(config))` with `topK` (clamped to engine's `maxTopK = 40`), `topP`, `temperature`, `randomSeed`. `session.addQueryChunk(prompt)` adds the formatted Gemma prompt. `session.generateResponse()` (synchronous) or `session.generateResponseAsync(listener)` (streaming) produces the response. `session.close()` is always called in `finally`.
- **Mutex serialization**: `inferenceMutex: Mutex` serializes all inference (streaming + non-streaming). MediaPipe's `LlmInferenceSession` is NOT safe for concurrent use on a single `LlmInference` engine. For v0.3 this is fine — the kernel is single-user, single-conversation.
- **Streaming via `ProgressListener`**: `ProgressListener<String> { partial, done -> ... }` is a Java SAM interface. MediaPipe invokes it on the inference thread with **cumulative** text (the full text so far), not deltas. The engine tracks `lastEmittedLen` (single-writer, no synchronization needed) and emits only the suffix `partial.substring(lastEmittedLen)`. Deltas are pushed through an unbounded `Channel<String>`; the flow drains it with `for (delta in channel) emit(delta)`. When `done == true`, the channel closes and the for-loop exits. The `ListenableFuture<String>` from `generateResponseAsync` is `await()`ed at the end (via `kotlinx-coroutines-guava`) to surface any error that occurred after the last progress callback. On `CancellationException` (user cancelled mid-stream), the engine calls `session.cancelGenerateResponseAsync()` and rethrows.
- **Config mapping**: `LlmGenerationConfig.topK` is clamped to `[1, 40]`; `temperature`, `topP`, `seed` passed through. `maxTokens` is engine-wide (4096), not per-request — `config.maxTokens` is silently ignored. `stopSequences` are not supported by MediaPipe — silently ignored. Both gaps are documented in the engine's KDoc.

The crucial point: this is **real streaming**. The UI sees tokens as MediaPipe produces them, not after the full response completes. Compare to OpenDroid, whose `streamComplete()` runs `complete()` to finish, then re-emits words with a 50ms delay — fake streaming that we explicitly did not adopt.

### The model catalog

`ModelManager.DEFAULT_CATALOG` ships 7 entries:

| ID | Runtime | Size | Quant | Min RAM |
|---|---|---|---|---|
| gemma-2b-it-q4 | LLAMA_CPP | 1.8 GB | Q4_K_M | 6 GB |
| phi-3-mini-4k-q4 | LLAMA_CPP | 2.2 GB | Q4_K_M | 6 GB |
| qwen2.5-1.5b-q4 | LLAMA_CPP | 1.0 GB | Q4_K_M | 4 GB |
| llama-3.2-3b-q4 | LLAMA_CPP | 2.5 GB | Q4_K_M | 8 GB |
| smollm2-1.7b-q4 | LLAMA_CPP | 1.0 GB | Q4_K_M | 4 GB |
| gemma-2b-it-mediapipe | MEDIAPIPE_GENAI | 1.7 GB | int8 | 6 GB |
| gemma-1b-it-mediapipe | MEDIAPIPE_GENAI | 1.4 GB | int4 | 4 GB |

The 5 GGUF entries are pending the v0.4 llama.cpp JNI bridge — they download fine (file lands in internal storage) but `LocalLlmProvider.isAvailable()` returns `false` for `LLAMA_CPP`, so the chain falls through to Remote/Mock. The 2 MediaPipe `.task` entries are fully live in v0.3.

### `ModelDownloadWorker`

A `@HiltWorker CoroutineWorker` enqueued via `WorkManager.enqueueUniqueWork("babymomo.model.download.<modelId>", ExistingWorkPolicy.REPLACE, …)`. It marks the model `DOWNLOADING`, calls `setForeground` immediately (Android 14+ 5-second window), streams the OkHttp response body to `filesDir/models/<modelId>.tmp` in 64 KB chunks, throttling `setProgress` to every 512 KB (avoids DB churn) and updating the foreground notification percent. On completion: optional MD5 verification (`MessageDigest("MD5")` over the temp file, compared case-insensitively to `ModelEntity.md5`), atomic `renameTo` to the final filename (fallback `copyTo` + delete if rename fails across volumes), `modelManager.markDownloaded(modelId, path)`. On `CancellationException`: delete temp file, reset status to `NOT_DOWNLOADED`. On `IOException`: if retry budget (`MAX_RUN_ATTEMPTS = 5`) exhausted, mark `ERROR`; else `Result.retry()` (status stays `DOWNLOADING` so the UI doesn't ping-pong). The UI observes `getWorkInfosForUniqueWorkFlow(...)` mapped to a `Flow<DownloadState>` (Idle / Downloading / Verifying / Complete / Failed) and renders a `LinearProgressIndicator` + Cancel during download, Retry on failure.

---

## 7. How the memory web system is set up and ensures perfection

The memory graph is BABYMOMO's core IP. It is the thing that distinguishes us from every local-chat competitor and from every cloud assistant's flat-memory blob.

### The bi-temporal model (adapted from Zep / Graphiti)

Every memory and every relation carries four timestamps: `createdAt` = ingestion time (when BABYMOMO wrote the row); `validFrom` = event time (when the fact became true in the world — defaults to `now` for episodic/semantic, `now` for relations asserted by `MemoryExtractor`; parsing temporal reference from text is v0.5+ work); `validUntil` = invalidation time (`null` = currently true; non-null = superseded); `supersededBy` = FK to the memory/relation that replaced this one (set when `MemoryService.invalidate(memoryId, byId)` is called). **Facts are NEVER deleted — they are invalidated.** This enables time-travel queries: "what did MOMO know about John's job last month?" is `SELECT * FROM memories WHERE content LIKE '%John%' AND validFrom <= :lastMonth AND (validUntil IS NULL OR validUntil > :lastMonth)`. The schema is exported to `app/schemas/com.babymomo.data.db.BabymomoDatabase/1.json` for migration tracking.

### The 4-tier memory taxonomy

`MemoryType { WORKING, EPISODIC, SEMANTIC, PROCEDURAL }`:

- **WORKING** — the current turn's context. Not yet heavily used in v0.3 (the kernel uses in-memory `conversationHistory` instead); reserved for future scratchpad-style memory.
- **EPISODIC** — "this happened" memories. Both `User: ...` and `MOMO: ...` turns are stored as episodic via `addEpisodicMemory`. Source is `USER_STATED` for the user's message, `LLM_INFERRED` for MOMO's response. Confidence 1.0 / 0.9.
- **SEMANTIC** — "this is true" facts extracted by `MemoryExtractor`. "Aisha WORKS_AT Northwind as a data engineer" becomes a semantic memory. Source is `LLM_INFERRED`, confidence from the extraction LLM's JSON.
- **PROCEDURAL** — "how to do X" skills and recipes. `WriteArticleSkill` stores its output as procedural with `tags = ["article", "draft"]`. Source `USER_STATED`, confidence 0.9.

### The knowledge graph schema

Three tables. **`entities`** (`EntityEntity`): `EntityType { PERSON, PROJECT, GOAL, SKILL, PLACE, EVENT, IDEA, FILE, NOTE }` (9 types). Columns: `id` (PK, `ent_<uuid16>`), `type`, `name` (original), `canonicalName` (unique index — lowercase, stripped of non-alphanumerics, whitespace-collapsed, max 128 chars), `aliasesCsv` (comma-joined, merged on re-resolve), `description`, `createdAt`, `updatedAt`. **`relations`** (`RelationEntity`): `RelationType { WORKS_AT, OWNS, INTERESTED_IN, MEMBER_OF, DEPENDS_ON, MENTIONS, DERIVED_FROM, FRIEND_OF, FAMILY_OF, LEADS, PARTICIPATES_IN, LOCATED_IN, HAPPENED_ON, PARENT_OF, CHILD_OF, RELATED_TO }` (16 types). Columns: `id` (PK, `rel_<uuid16>`), `sourceEntityId`, `targetEntityId`, `type`, `confidence`, `validFrom`, `validUntil` (bi-temporal), `sourceMemoryId` (FK to the memory that produced this relation — provenance), `createdAt`; indices on source, target, type, validUntil, validFrom. **`memory_entity_links`** (`MemoryEntityLink` — many-to-many): composite PK `(memoryId, entityId)`; columns `memoryId`, `entityId`, `confidence`, `extractedAt`; indices on `entityId`, `memoryId`, `confidence`. This join table lets `MemoryRecaller` find memories linked to entities mentioned in the query (the `graphProximity` rerank signal).

### The vector index

`VectorIndex` interface with one implementation today:

```kotlin
interface VectorIndex {
    suspend fun rebuild()
    suspend fun upsert(record: VectorRecord)
    suspend fun remove(id: String)
    suspend fun search(query: Embedding, k: Int = 30, namespace: String = "default"): List<SearchHit>
    fun size(): Int
}
```

`FlatVectorIndex` is brute-force cosine in pure Kotlin. It holds a `mutableListOf<VectorRecord>` guarded by a `Mutex`, exposes `sizeFlow: StateFlow<Int>` for UI, and rebuilds from `memoryDao.activeEmbeddings("default")` (only `validUntil IS NULL` rows). Cosine is `dot / (|a| · |b|)` over the 384-dim float arrays. Embeddings are stored inline in the `memories` table as a `BLOB` (`embedding: ByteArray`, 384 × 4 = 1536 bytes/row) and decoded back via `Float.fromBits` on the little-endian int. Performance: 50k memories ≈ 10ms query on a modern phone, ~76 MB resident — comfortable for personal scale. The `VectorIndex` interface is the explicit upgrade path: when memory count crosses ~50k or p95 latency exceeds 200ms, drop in `HnswVectorIndex` (one class change); graph schema and retrieval pipeline stay untouched. This is the Option E decision from `docs/architecture-decisions.md` §2 — selected after evaluating 6 options (sqlite-vss, hnswlib-java, ObjectBox, Apache Jena, flat cosine, LanceDB) and red-teaming the top 2.

The embedding model behind it: **BGE-small-en-v1.5**, 384-dim, 33.4M params, ~33 MB int8 ONNX, MTEB 62.28, Apache 2.0. Bundled as an app asset at `app/src/main/assets/models/bge-small-en-v1.5-int8.onnx`. Loaded by `OnnxEmbedder` via `OrtSession` with `SessionOptions.addNnapi()` (GPU/NPU acceleration on supported devices, transparent CPU fallback). Tokenization by `BertTokenizer` — real BERT WordPiece with the canonical `bert-base-uncased` 30,522-token `vocab.txt` bundled at `app/src/main/assets/models/bert-base-uncased-vocab.txt` (~232 KB). The tokenizer does the full standard pipeline: clean text → lowercase + NFD accent strip → whitespace split → per-character punctuation split → greedy longest-match WordPiece with `##` continuation pieces → truncate to 510 → add `[CLS]` + `[SEP]` → pad to 512 → attention mask → all-zero token type IDs. The model sees the exact token-id distribution it was trained on — semantic quality matches the BGE-small reference numbers. v0.2's hash-based stand-in tokenizer is gone.

### The retrieval pipeline

`MemoryRecaller.recall(query, topK = 8)` is the read side. Steps with each signal's role:

1. **Embed query** → 384-dim BGE vector (~20ms).
2. **Vector recall** → `vectorIndex.search(qEmb, k = 30)` returns top 30 candidates by cosine. This is the broad net — semantic neighbors.
3. **Entity match** → `memoryGraph.searchEntities(query.take(80), 10)` matches query tokens against `entities.canonicalName` and aliases via SQL LIKE. This catches exact-name hits that cosine might miss (e.g., "Aisha" matches the `PERSON` entity even if the query's embedding isn't close to any memory about her).
4. **2-hop graph expansion** → `memoryGraph.twoHopNeighbors(matchedEntityIds)`. One-hop: `relationDao.neighborsCurrent(entityIds)` returns all current relations touching those entities. Then 1-hop again from the new neighbor entities. Dedup relations + entities. This pulls in the graph neighborhood — "Aisha WORKS_AT Northwind" + "Northwind LOCATED_IN Seattle" + "Aisha FRIEND_OF Sam".
5. **4-signal rerank** → for each of the 30 vector hits, compute:
   - `0.5 · cosineSimilarity` — topical relevance. Dominates because it's the strongest signal of "is this memory about the same thing as the query?"
   - `0.2 · graphProximity` — 1.0 if the memory is linked (via `memory_entity_links`) to a matched entity, else 0.0. Rewards memories tied to the entities the query mentioned.
   - `0.2 · confidence` — the extraction LLM's confidence (0.0–1.0). Rewards high-quality extractions over LLM guesses.
   - `0.1 · recencyDecay` — `exp(-age / 30 days)`. A memory 30 days old gets ~37% of the recency weight, 60 days ~13%, 90 days ~5%. Gentle tie-breaker that prefers fresher facts.

   Sort descending, take top 8.
6. **Assemble** → top 8 memories become `<memories>` block with `id`, `confidence`, `source` attributes; top 20 graph facts become `<graph>` block as `"$src ${relType} $tgt (current|ended YYYY-MM, conf=X)"`.

This beats pure vector RAG because the graph-proximity and confidence signals add structure that cosine alone can't see. The HybridRAG paper's empirical finding — vector + graph beats either alone — is the citation. Letta's filesystem baseline scoring 74% on LoCoMo (beating several specialized memory systems) drove the related decision to invest in reranking quality, not index fanciness: the flat-cosine index is a commodity, the 4-signal reranker is where the engineering effort pays off.

### The write pipeline (`MemoryExtractor`)

`MemoryExtractor.extract(text, sourceMemoryId, confidenceThreshold = 0.6)` is the write side: (1) **build extraction prompt** — structured prompt asking for JSON with `entities` (name, type, aliases, description), `relations` (source, type, target), and `facts` (text, confidence); rules: only explicitly stated facts, one fact per item, entity names exactly as they appear. (2) **LLM call** — `llm.complete([system(EXTRACTION_SYSTEM_PROMPT), user(prompt)], LlmGenerationConfig(temperature = 0.1, maxTokens = 800))` — low temperature for determinism. (3) **Parse JSON** — strip code fences, find outermost `{ ... }`, Moshi-parse into `ExtractionJson`; returns `null` on any failure so a bad extraction never crashes the app. (4) **Entity resolution** — for each `EntityJson`, `memoryGraph.resolveOrCreate(name, type, description, aliases)` canonicalizes the name, checks `entityDao.findByCanonicalName` and `entityDao.matchByAlias`, and either returns the existing entity (merging aliases + description if new info is provided) or inserts a new one. This is the dedup step — "Aisha", "aisha", "Aisha " all resolve to one entity. (5) **Relation assertion** — for each `RelationJson`, `memoryGraph.assertRelation(src, tgt, type, sourceMemoryId)` checks `relationDao.outgoingCurrent(source.id)` for an existing same-type relation to the same target; if found, returns it (dedup); else inserts a new `RelationEntity` with `validUntil = null`. (6) **Semantic memory persistence** — for each `FactJson` with `confidence >= 0.6`, `memoryService.addSemanticMemory(fact.text, fact.confidence, sourceMemoryId, linkedEntities)` computes a real BGE embedding, inserts a `MemoryEntity` with `type = SEMANTIC`, upserts the vector index. (7) **Entity linking** — `memoryGraph.linkMemoryToEntities(semanticMemId, [(entity, conf), ...])` writes `MemoryEntityLink` rows so future recalls can find this memory via the entities it mentions.

### Poisoned-memory cleanup

The extraction LLM occasionally produces garbage — "As an AI...", "I don't know", "[ERROR]", empty strings, prompt echoes. `MemoryDao.deletePoisonedFromSource(MemorySource.LLM_INFERRED)` runs a SQL DELETE against known garbage patterns. Invoked from `MemoryMaintenance.runStartupSweep()` (on app launch via `BabymomoApp.onCreate`) and `MemoryMaintenance.runPeriodicSweep()` (every 24h via `MemoryMaintenanceWorker`). If any rows are deleted, `memoryService.rebuildIndex()` refreshes the in-memory vector index. This pattern is adopted from OpenDroid (Apache-2.0), but moved from `Application.onCreate` (which blocks startup) to a periodic WorkManager job — the correct Android pattern.

### `MemoryMaintenanceWorker`

A `@HiltWorker` periodic `CoroutineWorker` enqueued with `PeriodicWorkRequestBuilder<MemoryMaintenanceWorker>(24, TimeUnit.HOURS).setInitialDelay(15, TimeUnit.MINUTES)`. `ExistingPeriodicWorkPolicy.KEEP` means re-enqueueing on every app launch is a no-op if a schedule already exists. `doWork()` calls `maintenance.runPeriodicSweep()` which: (a) ensures meta keys (`schema_version`, `embedding_model`, `embedding_dims`, `extraction_model`, `created_at`) are set; (b) deletes poisoned LLM_INFERRED memories; (c) hard-deletes memories invalidated more than 90 days ago via `memoryDao.purgeInvalidatedBefore(cutoff)` — the bi-temporal GC, keeping the DB from growing unbounded while preserving recent history for time-travel queries; (d) rebuilds the vector index if anything changed; (e) updates `last_maintenance` meta key.

### How "perfection" is ensured

Six mechanisms, in order of importance: (1) **Provenance** — every memory and relation tracks its `source` (`USER_STATED`, `LLM_INFERRED`, `SENSOR`, `IMPORTED`, `DERIVED`) and, for relations, the `sourceMemoryId` FK to the exact memory that produced it; you can always answer "where did MOMO learn this?". (2) **Confidence scores** — every memory carries `confidence: Float` (0.0–1.0) from the extraction LLM; the reranker weights it at 0.2, `MemoryExtractor.confidenceThreshold = 0.6` filters low-confidence facts at write time, and the UI surfaces confidence on every memory card. (3) **Bi-temporal auditability** — `createdAt` + `validFrom` + `validUntil` + `supersededBy` on every memory and relation; time-travel queries are possible; facts are never deleted, only invalidated; the 90-day GC is the only deletion, and only for already-invalidated rows. (4) **Graceful degradation** — `EmbedderProvider.current()` returns `OnnxEmbedder` if loadable, else `MockEmbedder` transparently; `LlmProviderChain` falls through Local → Remote → Mock; the app never crashes on a missing model. (5) **Single source of truth** — one Room DB file (`babymomo_memory.db`), one transaction log, one set of Flows; graph and vectors live in the same database — no dual-store sync issues (the explicit fix for Mem0's documented drift bug). (6) **Schema versioning** — `@Database(version = 1, exportSchema = true)` with `room.schemaLocation`; the exported `1.json` is checked into `app/schemas/com.babymomo.data.db.BabymomoDatabase/1.json`; future migrations will use proper `Migration` objects (v0.3 still uses `fallbackToDestructiveMigration` — a known gap, tracked for v0.5).

---

## Appendix A — Glossary

- **Bi-temporal model** — A data model where every fact tracks both when it was recorded (`createdAt`) and when it was true in the world (`validFrom` / `validUntil`), plus a pointer to the fact that superseded it (`supersededBy`). Facts are never deleted — they are invalidated. Enables time-travel queries. Adapted from Zep / Graphiti.
- **WordPiece** — A subword tokenization algorithm used by BERT and BGE. Greedy longest-match against a fixed vocabulary; continuation pieces are prefixed with `##`. A word that can't be fully decomposed maps to `[UNK]`. The canonical `bert-base-uncased` vocab has 30,522 tokens.
- **ONNX Runtime** — An open-source cross-platform ML inference engine. `onnxruntime-android` ships per-ABI native libs; `SessionOptions.addNnapi()` routes supported ops to Android's Neural Networks API (GPU/NPU).
- **MediaPipe GenAI** — Google's on-device generative-AI runtime. `com.google.mediapipe:tasks-genai:0.10.35` ships the `LlmInference` engine + `LlmInferenceSession` API used by `MediapipeLlmEngine`. v0.3 upgraded from 0.10.14 (which lacked the session API) to 0.10.35 (which ships it).
- **Hilt** — Google's recommended DI framework for Android, built on Dagger. `@HiltAndroidApp`, `@Singleton`, `@InstallIn(SingletonComponent::class)`, `@HiltWorker`, `@HiltViewModel`. `dagger.Lazy<T>` breaks cycles by resolving on first use.
- **Room** — Google's SQLite ORM. `@Database`, `@Entity`, `@Dao`, `@TypeConverter`. KSP-driven (no kapt). Schema exported to JSON for migration tracking.
- **WorkManager** — Android's recommended API for deferrable background work. `CoroutineWorker`, `PeriodicWorkRequestBuilder`, `OneTimeWorkRequestBuilder`, `enqueueUniqueWork`, `setForeground` (Android 14+ foreground service type requirement).
- **Flow** — Kotlin's cold async stream. `Flow<String>` of token deltas is the streaming contract across all `LlmProvider` implementations. `StateFlow` for UI state. `collectAsStateWithLifecycle` in Compose.
- **Cosine similarity** — `dot(a, b) / (|a| · |b|)`. Measures the angle between two vectors, independent of magnitude. BGE outputs are L2-normalized so cosine reduces to dot product, but `FlatVectorIndex` computes the full form defensively.
- **Reranker** — A second-stage ranker that re-orders initial candidates using more signals than the first-stage retriever. BABYMOMO's reranker uses 4 signals: cosine similarity, graph proximity, confidence, recency decay.
- **Provenance** — The record of where a piece of knowledge came from. In BABYMOMO: `MemorySource` enum (`USER_STATED`, `LLM_INFERRED`, `SENSOR`, `IMPORTED`, `DERIVED`) on memories; `sourceMemoryId` FK on relations.
- **`dagger.Lazy<T>`** — A Dagger provider that resolves the dependency on first `.get()` call, not at construction time. Used to break the Hilt cycle between `WrappedLlmProvider` (needs memory) and `MemoryExtractor` (needs LLM).

---

## Appendix B — References

- **GitHub repo**: https://github.com/ansaribilal14/babymomo
- **Architecture decisions**: `docs/architecture-decisions.md` in the repo — 290 lines covering LLM integration (OpenDroid adoption), memory graph (Option E selection, 6 options evaluated), MediaPipe GenAI decision, model downloads, embeddings, testing strategy.
- **CHANGELOG**: `CHANGELOG.md` — full v0.1.0, v0.2.0, and unreleased (v0.3) history.
- **Prior art — memory graphs**:
  - **Zep / Graphiti** — bi-temporal graph memory for LLM apps. BABYMOMO's bi-temporal model (`createdAt` / `validFrom` / `validUntil` / `supersededBy`) is adapted from Graphiti's research.
  - **Mem0** — memory layer for LLMs. Documented dual-store drift bug (vector + graph get out of sync) — BABYMOMO avoids it by using a single Room DB for both.
  - **Letta / MemGPT** — tiered memory (working / archival). Their filesystem baseline scoring 74% on LoCoMo drove our investment in reranking over index sophistication.
  - **GraphRAG** (Microsoft) — knowledge-graph-augmented retrieval.
  - **HybridRAG** — empirical finding that vector + graph retrieval beats either alone. Direct citation for our 4-signal reranker.
- **Prior art — LLM integration**:
  - **OpenDroid** (Apache-2.0) — `LlmProvider` interface shape, `WrappedLLMProvider` decorator with `dagger.Lazy` cycle-break, poisoned-memory cleanup. Adopted with attribution, not code. We did NOT adopt their fake streaming, substring recall, or foreground service.
- **Embedding model**: `BAAI/bge-small-en-v1.5` — 384-dim, 33.4M params, ~33 MB int8 ONNX, MTEB 62.28, Apache 2.0. Sourced from `onnx-community/bge-small-en-v1.5-ONNX`, re-saved as a single all-embedded `.onnx`.
- **Tokenizer vocab**: `bert-base-uncased` 30,522-token `vocab.txt`.
- **LLM runtime**: MediaPipe GenAI `tasks-genai:0.10.35` — `LlmInference` + `LlmInferenceSession` + `ProgressListener` API.
- **Test files**: `MemoryGraphTest.kt`, `FlatVectorIndexCosineTest.kt`, `MemoryRecallerRerankTest.kt`, `RequestClassifierTest.kt` — hand-written in-memory DAOs, no MockK.

---

## Appendix C — Version history

- **v0.1.0** (2026-06-22) — Initial release. Full chat UI, bi-temporal memory graph, flat-cosine vector index, 4-signal reranker, 5 agents, 5 skills, project system, WorkManager maintenance, `MockLlmProvider` + `RemoteLlmProvider` (real SSE), `LocalLlmProvider` stub. CI builds APK on every push.
- **v0.2.0** (2026-06-22) — MediaPipe GenAI scaffold (engine stubbed against 0.10.14 AAR which lacked the session API). Two Gemma `.task` models added to catalog. Real model downloads via `ModelDownloadWorker` (OkHttp + MD5 + foreground notification). BGE-small ONNX wired (placeholder asset; falls back to `MockEmbedder`). Hash-based `BertTokenizer` stand-in. Unit tests for graph, vector, classifier, reranker.
- **v0.3.0** (current) — Real on-device Gemma LLM via MediaPipe GenAI 0.10.35 session API (`MediapipeLlmEngine` fully implemented with real `ProgressListener` streaming). Real BGE-small-en-v1.5 int8 ONNX binary bundled as app asset (~33 MB). Real BERT WordPiece tokenizer with canonical 30,522-token `vocab.txt`. `LocalLlmProvider.isAvailable()` returns `true` for `MEDIAPIPE_GENAI` models — the chain picks Local up for the first time. GGUF/`LLAMA_CPP` catalog entries still fall through pending v0.4.

---

## Appendix D — Roadmap

- **v0.4 — llama.cpp JNI bridge.** Wire `LocalLlmProvider` to a llama.cpp JNI wrapper so the 5 GGUF catalog entries (Gemma 2B GGUF, Phi-3 mini, Qwen 2.5 1.5B, Llama 3.2 3B, SmolLM2 1.7B) run on-device. Multi-week JNI/NDK project; the `ModelRuntime.LLAMA_CPP` enum slot is already designed for this.
- **v0.5 — First-launch onboarding + `abiFilters`.** A guided onboarding flow that prompts the user to pick + download a model on first launch. `abiFilters` to split the APK by ABI (arm64-v8a, armeabi-v7a, x86_64) so the MediaPipe + ONNX native libs don't bloat every install. Proper Room `Migration` objects replacing `fallbackToDestructiveMigration`. Integration tests with `Room.inMemoryDatabaseBuilder` + Compose UI tests.
- **v0.6 — Graph visualization.** A Compose Canvas screen that renders the knowledge graph visually — entities as nodes, relations as edges, color-coded by type, browsable by tap. Recursive CTE graph traversal in Room for multi-hop queries beyond 2 hops.
- **v0.7 — Sync.** End-to-end encrypted sync of the Room DB across devices (so losing your phone doesn't lose your mind). Likely a CRDT-based approach over a self-hostable backend; the bi-temporal model is already sync-friendly (last-writer-wins on `validUntil` is well-defined). Multilingual embedding upgrade to `EmbeddingGemma` (256-d Matryoshka) with a one-time re-embedding migration.
