<div align="center">

# BABYMOMO

**A private AI companion that grows into your personal operating system.**

Not a chatbot. Not an assistant. A digital mind that lives on your device,
remembers your life, understands your projects, and helps execute them.

[![Build APK](https://github.com/ansaribilal14/babymomo/actions/workflows/android.yml/badge.svg)](https://github.com/ansaribilal14/babymomo/actions/workflows/android.yml)
[![Version](https://img.shields.io/badge/version-0.1.0--debug-D97F3F?style=flat&logo=semver)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-7F52FF?logo=kotlin&logoColor=white)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.02-4285F4?logo=jetpackcompose&logoColor=white)
[![Hilt](https://img.shields.io/badge/Hilt-2.50-2496ED?logo=dagger&logoColor=white)
[![Room](https://img.shields.io/badge/Room-2.6.1-6DB33F?logo=sqlite&logoColor=white)
[![minSdk](https://img.shields.io/badge/minSdk-26-3DDC84?logo=android&logoColor=white)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

---

## 📖 The Mission

> *"Build an AI that remembers everything important about me and becomes smarter the longer I use it."*

Every existing AI assistant forgets. Every new chat means re-explaining your projects, goals, business, and plans. BABYMOMO fixes this by being **one AI, one memory, one brain, forever** — running entirely on your device.

### The BABYMOMO loop

```
   ┌─────────────────────────────────────────────────────┐
   │  User input                                          │
   └────────────────────┬────────────────────────────────┘
                        ▼
                 1. THINK       (RequestClassifier routes the turn)
                        ▼
                 2. CONNECT     (recall memories + graph facts)
                        ▼
                 3. ANSWER      (LLM generates response, streaming)
                        ▼
                 4. LEARN       (extract entities, relations, facts)
                        ▼
                 5. STORE       (persist as bi-temporal memories)
                        ▼
   ┌─────────────────────────────────────────────────────┐
   │  Response (with cited memory references like [m_abc]) │
   └─────────────────────────────────────────────────────┘
```

Most AI does: **Question → Answer → Forget.** BABYMOMO does the full loop above.

---

## 🏗 Architecture

```
                    ┌──────────────────────────┐
                    │      UI (Compose)         │
                    │  Chat · Memory · Projects │
                    │  Skills · Agents · Models │
                    └────────────┬─────────────┘
                                 │
                    ┌────────────▼─────────────┐
                    │       MomoKernel          │  ◄── brain stem
                    │  (RequestClassifier +     │
                    │   streamProcess + extract)│
                    └────────────┬─────────────┘
                                 │
            ┌────────────────────┼────────────────────┐
            ▼                    ▼                    ▼
   ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
   │   Agent         │  │   LLM Chain     │  │   Memory        │
   │  Orchestrator   │  │ (local→remote→  │  │  Service +      │
   │                 │  │  mock, wrapped) │  │  Recaller +     │
   │ • Planner       │  │                 │  │  Extractor +    │
   │ • Researcher    │  │  WrappedLlm     │  │  Graph          │
   │ • Memory        │  │  Provider       │  │                 │
   │ • Critic        │  │ (enriches       │  │  Room DB:       │
   │ • Executor      │  │  system prompt  │  │  • memories     │
   │                 │  │  with memories  │  │  • entities     │
   │                 │  │  + graph)       │  │  • relations    │
   │                 │  │                 │  │  • vectors      │
   └─────────────────┘  └─────────────────┘  └─────────────────┘
                                                       │
                                                       ▼
                                            ┌──────────────────┐
                                            │  Skills (5)      │
                                            │  + Projects      │
                                            │  + WorkManager   │
                                            └──────────────────┘
```

### Core modules

| Module | Purpose | Status |
|---|---|---|
| **MOMO Mind** | `LlmProvider` interface + chain (local → remote → mock) | ✅ Interface + Mock + Remote (real SSE); ⏳ Local runtime wired in v0.2 |
| **MOMO Memory** | Bi-temporal memories + knowledge graph + vector index | ✅ Full retrieval pipeline; ⏳ ONNX embeddings in v0.2 |
| **MOMO Kernel** | Brain stem — classifies + routes + persists every turn | ✅ Complete |
| **MOMO Agents** | 5 specialists orchestrated for complex turns | ✅ Complete |
| **MOMO Skills** | Pluggable mini-workflows (Write Article, Summarize, etc.) | ✅ 5 built-in skills |
| **MOMO Project System** | Living project entities with auto graph nodes | ✅ Complete |
| **MOMO Runtime** | WorkManager periodic maintenance | ✅ Complete |

See [`docs/`](docs/) for the deep research reports that drove these decisions.

---

## 📥 Get the APK

**Two ways to get the latest APK — no Android Studio required:**

### Option 1 — Download from GitHub Actions (always latest)

1. Go to the [**Actions tab**](https://github.com/ansaribilal14/babymomo/actions/workflows/android.yml).
2. Click the most recent successful run.
3. Scroll to the **Artifacts** section at the bottom.
4. Download `babymomo-debug-apk` — a ZIP containing `app-debug.apk`.
5. Transfer to your Android phone (Android 8.0+ / API 26+).
6. In **Settings → Apps → Special access → Install unknown apps**, allow your file manager to install APKs.
7. Tap the APK in your file manager to install.

### Option 2 — Build it yourself

```bash
# Prerequisites: JDK 17 (Temurin) + Android SDK 34 + build-tools 34.0.0
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk

git clone https://github.com/ansaribilal14/babymomo.git
cd babymomo
./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in Android Studio and let Gradle sync.

---

## 🎯 What works in v0.1

- **Full chat experience** — streaming responses (real SSE when using remote, simulated word-by-word on mock), auto-persisted conversations, routing-reason chip showing which agents were engaged.
- **Memory graph** — every turn auto-extracts entities + relations + facts (when a real LLM is configured). Browse the Memory tab to see memories by type (Working / Episodic / Semantic / Procedural), filter, search, and view stats (active count, total, entities, relations).
- **Projects** — create living projects with description + initial tasks. Each project auto-creates a matching node in the knowledge graph so the LLM can recall project context during chat.
- **Skills** — 5 skills registered via Hilt multi-binding. The Executor agent finds the right skill from trigger keywords and runs it.
- **Models catalog** — 5 curated GGUF models (Gemma 2B / Phi-3 mini / Qwen 2.5 1.5B / Llama 3.2 3B / SmolLM2 1.7B) ready for v0.2 download + activation.
- **Settings** — privacy controls (internet off by default), optional remote LLM provider config (works with OpenAI / Groq / OpenRouter / Ollama on LAN).

## ⏳ What's stubbed for v0.2

- **On-device LLM inference** — `LocalLlmProvider` detects downloaded models but the actual MediaPipe / llama.cpp / MLC-LLM runtime bridge is pending. Until then, the app uses `MockLlmProvider` so the UI is fully functional.
- **On-device embeddings** — `OnnxEmbedder` (BGE-small-en-v1.5) is stubbed. Currently uses `MockEmbedder` (deterministic hash-based, 384-dim).
- **PDF analysis skill** — returns a status message.
- **Model download** — UI shows catalog but the download worker is pending.

---

## 🗂 Repository layout

```
babymomo/
├── app/
│   ├── src/main/
│   │   ├── java/com/babymomo/
│   │   │   ├── BabymomoApp.kt              # Application entry, WorkManager setup
│   │   │   ├── MainActivity.kt             # Single-activity Compose host
│   │   │   ├── core/
│   │   │   │   ├── llm/                     # LlmProvider + chain (local→remote→mock)
│   │   │   │   ├── memory/                  # Embedder, VectorIndex, Graph, Service,
│   │   │   │   │                            #   Recaller, Extractor, Maintenance
│   │   │   │   ├── kernel/                  # MomoKernel + RequestClassifier
│   │   │   │   ├── agents/                  # 5 specialists + orchestrator
│   │   │   │   ├── skills/                  # Skill interface + 5 built-in skills
│   │   │   │   └── projects/                # ProjectService + context provider
│   │   │   ├── data/
│   │   │   │   └── db/                      # 10 Room entities + 9 DAOs + database
│   │   │   ├── ui/                          # Compose theme + nav + 7 screens
│   │   │   ├── work/                        # MemoryMaintenanceWorker
│   │   │   └── model/                       # ModelManager + catalog
│   │   └── res/                             # Strings, colors, themes, icons, XML
│   ├── build.gradle.kts                     # Module build config
│   └── proguard-rules.pro
├── gradle/
│   ├── libs.versions.toml                   # Version catalog (single source of truth)
│   └── wrapper/                             # Gradle 8.4 wrapper
├── .github/workflows/
│   └── android.yml                          # CI: build APK on every push
├── docs/                                    # Research reports driving the architecture
├── build.gradle.kts                         # Root build config
├── settings.gradle.kts
├── gradle.properties
├── CHANGELOG.md
├── LICENSE                                  # MIT
└── README.md                                # You are here
```

---

## 🔄 Live representation workflow

This repository is the **single source of truth** for BABYMOMO. Every session of work follows this flow:

1. **Make changes** — edit Kotlin source, resources, or docs.
2. **Commit + push** — `git add . && git commit -m "feat: <what>" && git push`.
3. **CI builds** — GitHub Actions runs `./gradlew assembleDebug` on every push.
4. **APK is uploaded** as a workflow artifact — anyone can download it from the Actions tab.
5. **Tag releases** — `git tag v0.x.0 && git push --tags` triggers a release build with the APK attached to a GitHub Release.

This means the repo state always mirrors the latest build state. Anyone can clone, build, and reproduce the exact APK that's on GitHub Actions.

### Branch strategy

- **`main`** — always builds green. Stable releases tagged from here.
- **`dev`** (planned) — ongoing development; merged to main when stable.

### Commit message conventions

We follow [Conventional Commits](https://www.conventionalcommits.org/):

- `feat:` new feature
- `fix:` bug fix
- `docs:` documentation only
- `refactor:` code change that neither fixes a bug nor adds a feature
- `chore:` build / tooling / CI changes
- `test:` adding or correcting tests

---

## 📚 Research that drove this architecture

Two deep research reports were produced during development and are preserved in [`docs/`](docs/):

1. **OpenDroid architecture analysis** — studied [opendroid](https://github.com/yashab-cyber/opendroid) to decide which patterns to adopt (LLMProvider interface shape, WrappedLLMProvider decorator with `dagger.Lazy` cycle-break, poisoned-memory cleanup). What we did NOT adopt: their fake streaming (we use real SSE), their substring memory recall (we use real embeddings), their always-on foreground service (we use WorkManager).

2. **Memory graph deep research with critic** — 6,200-word report evaluating Mem0, Letta, Zep/Graphiti, GraphRAG, embedding models, vector DB options A–F. After red-teaming the top 2, we selected **Option E**: Room + flat cosine + bi-temporal graph + BGE-small embeddings. Critic's key insight: empirical evidence (Letta's filesystem baseline scoring 74% on LoCoMo, beating specialized memory systems) shows that *retrieval simplicity + reranking quality* beats index sophistication — so we invest in reranking, not index fanciness.

---

## 🤝 Acknowledgements

BABYMOMO is original work. The following architectural patterns were adopted (with attribution, not code) from OpenDroid (Apache-2.0):

- `LLMProvider` interface shape + provider fallback chain
- `WrappedLLMProvider` decorator pattern with `dagger.Lazy` cycle-break
- Poisoned-memory cleanup pattern (moved from `Application.onCreate` to `WorkManager`)

The bi-temporal memory model is adapted from Zep / Graphiti's research. The 4-signal reranker (cosine similarity + graph proximity + confidence + recency decay) is BABYMOMO's own design, informed by HybridRAG's empirical finding that vector + graph beats either alone.

## 📜 License

MIT © Bilal Ansari. See [LICENSE](LICENSE).
