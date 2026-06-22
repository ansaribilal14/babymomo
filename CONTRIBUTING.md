# Contributing to BABYMOMO

Thanks for considering a contribution! BABYMOMO is a private AI companion built around
on-device memory — every change should keep the experience private, warm, and never forgetful.
This document explains how to set up a dev environment, how to extend the major subsystems
(Memory, Mind, Kernel, Agents, Skills), and how to get your change merged.

> **TL;DR:** fork → branch off `dev` → write code + tests → `./gradlew assembleDebug` →
> `./gradlew test` → update `CHANGELOG.md` → open a PR using the template.

---

## 1. Dev environment

| Tool | Version | Notes |
|---|---|---|
| **JDK** | 17 (Temurin recommended) | Required for AGP 8.2 + Kotlin 1.9.22. JDK 21 also works. |
| **Android SDK** | Platform 34 (`platforms;android-34`) | `compileSdk = 34`, `targetSdk = 34`. |
| **Build-tools** | 34.0.0 (`build-tools;34.0.0`) | Pinned by the CI workflow. |
| **Android Studio** | Iguana (2023.3) or newer | Matches the bundled AGP / Kotlin plugin versions. |
| **Gradle** | 8.4 (via wrapper) | Don't upgrade the wrapper casually — AGP 8.2 requires Gradle 8.x. |
| **minSdk** | 26 (Android 8.0) | Don't lower this without a team discussion. |

### Quick start

```bash
# 1. Prereqs
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk   # must have platforms;android-34 + build-tools;34.0.0

# 2. Clone
git clone https://github.com/ansaribilal14/babymomo.git
cd babymomo

# 3. Build the debug APK
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk

# 4. (Optional) Install on a connected device / emulator
./gradlew installDebug

# 5. (Optional) Run unit tests
./gradlew test
```

If `ANDROID_HOME` isn't set, Gradle will fall back to `local.properties` — create one with:
```
sdk.dir=/path/to/android-sdk
```

> **No Android Studio required.** The Gradle wrapper handles everything; you can develop
> from VS Code / Vim / IntelliJ CE if you prefer.

---

## 2. Branch strategy

```
main  ──────────────●────────────────●────────●────────  (stable, tagged releases)
                     \              /          /
dev    ───────────────●──●──●──●───●──●──●───●────────  (active development)
                      \  /  /
feat/xyz  ────●──●──●──●/
```

- **`main`** — always builds green. Stable releases are tagged from here (`v0.1.0`, `v0.2.0`, …).
  Never push directly to `main`; PRs only.
- **`dev`** — the integration branch for the next release. Open PRs against `dev`, not `main`.
  When `dev` is stable and the CHANGELOG is ready, a maintainer fast-forwards `main` to `dev`
  and tags a release.
- **`feat/<short-scope>`** — feature branches. Branch off `dev`, rebase before merging.
  Use `fix/<scope>` for bug fixes, `chore/<scope>` for build/tooling, `docs/<scope>` for docs.

---

## 3. Commit message conventions

We follow [Conventional Commits](https://www.conventionalcommits.org/):

| Prefix | Use for | Example |
|---|---|---|
| `feat:` | A new user-facing feature | `feat(memory): add 2-hop graph expansion to recaller` |
| `fix:` | A bug fix | `fix(kernel): simple messages no longer escalate to COMPLEX` |
| `docs:` | Documentation only | `docs: add CONTRIBUTING.md` |
| `refactor:` | Code change that neither fixes a bug nor adds a feature | `refactor(recaller): extract rerank scoring into internal function` |
| `chore:` | Build / tooling / CI / deps | `chore(deps): bump OkHttp to 4.12.0` |
| `test:` | Adding or correcting tests | `test: unit tests for MemoryGraph entity resolution` |

**Format:** `<type>(<scope>): <subject>` — keep the subject line ≤ 72 chars, imperative mood
("add" not "added"), no trailing period. Body paragraphs (optional) explain *why*, not *what*.

**Squash on merge:** PRs are squash-merged, so the commit history on a feature branch doesn't
matter — but each commit should still be reasonably self-contained so reviewers can read them
one at a time.

---

## 4. Pull request process

1. **Branch off `dev`** (or `main` if `dev` doesn't exist yet): `git checkout -b feat/my-thing dev`.
2. **Make your changes**. Keep PRs focused — one feature or one fix per PR. If you find yourself
   touching 4 subsystems, split it into 4 PRs.
3. **Run locally:**
   ```bash
   ./gradlew assembleDebug      # must build cleanly
   ./gradlew test               # unit tests must pass
   ```
4. **Update `CHANGELOG.md`** — add a bullet under `## [Unreleased]` → `### Added` (or
   `### Changed` / `### Fixed` / `### Removed`). One line, prefixed with the subsystem in
   bold (e.g. `**Memory**: ...`). Keep it user-facing — implementation details go in commit
   messages and code comments.
5. **Open the PR** against `dev` (or `main` if `dev` doesn't exist yet). Fill in the
   [PR template](.github/PULL_REQUEST_TEMPLATE.md) — every checkbox should be either ticked
   or explained.
6. **CI must be green.** GitHub Actions runs `./gradlew assembleDebug` on every PR. Failed
   builds block merge.
7. **Review.** A maintainer will review within a few days. Address feedback by pushing new
   commits to the same branch (don't force-push during review — it makes diffs hard to read).

---

## 5. How to extend BABYMOMO

### 5.1 Add a new Skill

Skills are pluggable mini-workflows (Write Article, Summarize, etc.). The Executor agent
picks the matching Skill from `triggerKeywords` and runs it.

1. **Implement the [`Skill` interface](app/src/main/java/com/babymomo/core/skills/Skill.kt):**
   ```kotlin
   @Singleton
   class MyNewSkill @Inject constructor(
       private val llm: LlmProvider,
       private val memoryService: MemoryService
   ) : Skill {
       override val id = "my_new_skill"
       override val displayName = "My New Skill"
       override val description = "Does something useful"
       override val triggerKeywords = listOf("my skill", "do the thing")
       override fun matches(input: String): Boolean =
           triggerKeywords.any { it in input.lowercase() }

       override suspend fun execute(input: String): SkillResult {
           // ... call llm.complete(...), persist via memoryService if relevant ...
           return SkillResult(success = true, output = "...")
       }
   }
   ```
2. **Register it in [`SkillsModule`](app/src/main/java/com/babymomo/core/skills/di/SkillsModule.kt):**
   add it to the `setOf(...)` returned by the `@ElementsIntoSet`-annotated provider. Hilt's
   multi-binding will then inject `Set<Skill>` into `SkillRegistry` automatically.
3. **Add a unit test** under `app/src/test/java/com/babymomo/core/skills/` — test the
   `matches()` logic and the prompt construction (you can stub the LLM with `MockLlmProvider`).

### 5.2 Add a new Agent

Agents are the orchestrated specialists (Planner, Researcher, Memory, Critic, Executor).
Adding one is more invasive than a Skill — you have to decide where in the pipeline it runs.

1. **Implement the [`Agent` interface](app/src/main/java/com/babymomo/core/agents/Agent.kt):**
   ```kotlin
   @Singleton
   class MyAgent @Inject constructor(private val llm: LlmProvider) : Agent {
       override val id = "my_agent"
       override val displayName = "My Agent"
       override val description = "..."
       override suspend fun isAvailable(): Boolean = llm.isAvailable()
       override suspend fun run(task: AgentTask): AgentResult { ... }
   }
   ```
2. **Wire it into [`AgentOrchestrator`](app/src/main/java/com/babymomo/core/agents/AgentOrchestrator.kt):**
   add a constructor param (Hilt will inject it) and a `if (routing.needX) { ... }` block in
   `run()`. Decide:
   - Should it run before or after the existing agents?
   - What `RoutingDecision` flag triggers it? (Add one to
     [`RoutingDecision`](app/src/main/java/com/babymomo/core/kernel/RequestClassifier.kt) if needed,
     plus a keyword list in `RequestClassifier.classify`.)
3. **Update `RequestClassifier`** if you added a new routing flag — add the keyword list,
   set the flag, escalate complexity as appropriate.
4. **Add a unit test** for the agent's prompt construction and for any new routing logic in
   `RequestClassifier`.

### 5.3 Add a new LLM provider

Providers implement [`LlmProvider`](app/src/main/java/com/babymomo/core/llm/LlmProvider.kt) —
the single interface for all LLM access (local, LAN, remote, mock). The chain tries them in
priority order until one succeeds.

1. **Implement `LlmProvider`:**
   ```kotlin
   @Singleton
   class MyLlmProvider @Inject constructor() : LlmProvider {
       override val name = "my_provider"
       override suspend fun isAvailable(): Boolean = ...
       override suspend fun status(): String = ...
       override suspend fun complete(messages: List<LlmMessage>, config: LlmGenerationConfig): Result<LlmResponse> { ... }
       override fun streamComplete(messages: List<LlmMessage>, config: LlmGenerationConfig): Flow<String> { ... }
   }
   ```
2. **Register it in [`LlmModule`](app/src/main/java/com/babymomo/core/llm/di/LlmModule.kt):**
   add a `@Provides @Singleton @MyLlm` provider method (mirroring the existing `@LocalLlm` /
   `@RemoteLlm` / `@MockLlm` qualifiers). Add a new qualifier annotation in the same file.
3. **Decide where in the chain it goes.** The chain order is set in
   [`LlmProviderChain.activeChain()`](app/src/main/java/com/babymomo/core/llm/LlmProviderChain.kt):
   by default it tries Local → Remote → Mock. Insert your provider at the right priority:
   - Before `localProvider` if it should preempt on-device inference (e.g. a low-latency LAN model).
   - Between `localProvider` and `remoteProvider` if it's a fallback for local.
   - After `remoteProvider` (before `mockProvider`) if it's a cheaper remote alternative.
   Make sure to wrap it in `WrappedLlmProvider` so it gets memory + project context enrichment
   (see how `wrappedLocal` / `wrappedRemote` / `wrappedMock` are constructed).
4. **Streaming must be REAL.** If your provider fakes streaming by chunking a completed
   response with `delay()`, that violates BABYMOMO's design (see
   `docs/architecture-decisions.md` §1). Use the underlying provider's native streaming
   mechanism (SSE for OpenAI-compatible, partial-result callbacks for MediaPipe, etc.).
5. **Add a unit test** for any non-trivial logic (URL construction, message serialization,
   response parsing). Streaming is hard to unit-test — leave that for instrumented tests.

### 5.4 Add a new embedder (optional)

Embedders implement [`Embedder`](app/src/main/java/com/babymomo/core/memory/Embedder.kt).
The routing logic lives in [`EmbedderProvider`](app/src/main/java/com/babymomo/core/memory/EmbedderProvider.kt).
Adding one is similar to adding an LLM provider — implement the interface, register in
[`MemoryModule`](app/src/main/java/com/babymomo/core/memory/di/MemoryModule.kt), and update
`EmbedderProvider.current()` to consider it. **If you change the embedding dimension**, you
must re-embed all stored memories (the `embedding_model` meta key tracks staleness — see
`MemoryMaintenance.ensureMetaKeys()`).

---

## 6. How to run tests

```bash
# Unit tests (JVM, no device needed) — fast, ~10s
./gradlew test

# Unit tests for a single class
./gradlew test --tests "com.babymomo.core.memory.MemoryGraphTest"

# Instrumented tests (require a connected device / emulator)
./gradlew connectedAndroidTest

# Build the debug APK (CI runs this)
./gradlew assembleDebug

# Lint
./gradlew lintDebug
```

CI currently runs `./gradlew assembleDebug` on every push. Running `./gradlew test` in CI is
planned for v0.3 (see `docs/architecture-decisions.md` → "Testing strategy").

---

## 7. How to write tests

BABYMOMO's testing philosophy (see `docs/architecture-decisions.md` → "Testing strategy"):

### Prefer hand-written in-memory DAOs over MockK

Room DAOs are interfaces with `@Query` annotations. Mocking them with MockK tests that
"the mock was called with these args" — not that the production code actually works against
real SQL semantics. Instead, **write a tiny in-memory implementation** of the DAO interface
using `mutableListOf` / `mutableMapOf`, faithfully mirroring the SQL semantics (LIKE substring
matching, `validUntil IS NULL` filtering, etc.). This tests real behavior, adds no
dependency, and is ~30 lines per DAO.

See [`MemoryGraphTest.kt`](app/src/test/java/com/babymomo/core/memory/MemoryGraphTest.kt)
for the pattern: `InMemoryEntityDao`, `InMemoryRelationDao`, `InMemoryLinkDao` are
hand-written, ~30 lines each, and exercise the real `MemoryGraph` logic without any
mocking framework.

### Test the math, not the framework

If a piece of logic is a pure formula (cosine similarity, rerank scoring, complexity
escalation), extract it into a small `internal` function and test it directly. Don't spin
up the whole DI graph + embedder + vector index + memory service just to verify a
weighted sum.

See:
- [`MemoryRecallerRerankTest.kt`](app/src/test/java/com/babymomo/core/memory/MemoryRecallerRerankTest.kt)
  — tests the 4-signal rerank formula via the extracted `internal` companion function
  `MemoryRecaller.computeRerankScore(...)`.
- [`FlatVectorIndexCosineTest.kt`](app/src/test/java/com/babymomo/core/memory/FlatVectorIndexCosineTest.kt)
  — tests cosine similarity + byte decoding through the public `search()` / `rebuild()` API
  (no API surface widening required).
- [`RequestClassifierTest.kt`](app/src/test/java/com/babymomo/core/kernel/RequestClassifierTest.kt)
  — pure-function routing rules; no DI.

### Use JUnit 4 + `kotlinx-coroutines-test`

- JUnit 4 (`org.junit.Test`, `org.junit.Assert.*`) — already in `app/build.gradle.kts` as
  `testImplementation(libs.junit)`.
- For `suspend` functions, wrap the test body in `runTest { ... }` from
  `kotlinx-coroutines-test` — already a `testImplementation` dep.
- Use `assertEquals(expected, actual, delta)` for floats — never exact equality on
  floating-point math.

### What to test

| Component | What to test | How |
|---|---|---|
| `MemoryGraph` | Entity resolution, dedup, alias merge, bi-temporal invalidation, 1-/2-hop expansion | In-memory DAOs + `runTest` |
| `MemoryRecaller` | The 4-signal scoring formula | Extracted `internal` function, pure math |
| `FlatVectorIndex` | Cosine similarity + byte decoding | Public `search()` / `rebuild()` API |
| `RequestClassifier` | Routing heuristics + complexity escalation | Pure function, no DI |
| `MomoKernel` | (Planned for v0.3) — full pipeline with stubbed LLM | TBD |
| `Skills` | `matches()` logic + prompt construction | Stubbed `LlmProvider` |
| `Agents` | (Planned for v0.3) — agent prompt construction | Stubbed `LlmProvider` |
| Compose UI | (Planned for v0.3) — Compose UI tests | `createAndroidComposeRule` |

### What NOT to test

- Room SQL itself — Room's annotation processor generates the implementation; testing that
  `@Query("SELECT * FROM ...")` returns rows is testing Room, not BABYMOMO. Trust Room.
- Hilt DI graph — Hilt validates the graph at compile time. If the app builds, the graph is valid.
- Android framework APIs — `Context`, `WorkManager`, `AssetManager` are tested by Google.

---

## 8. Code style

- **Kotlin official style** (the IDE default for Kotlin). Run `./gradlew spotlessApply` if
  you want auto-formatting (plugin not yet wired; planned).
- **4-space indentation.** No tabs.
- **No wildcard imports** — except for Compose (`androidx.compose.material3.*` is OK in
  Compose files because the API surface is huge and the imports are stable).
- **Line length:** 120 chars (soft). Don't break strings just to fit.
- **KDoc on every public class and function.** Private functions get a one-liner if the
  intent isn't obvious from the name.
- **`data class` for value types.** `class` (with `@Singleton` if needed) for services.
- **Prefer `val` over `var`.** Mutability should be explicit and rare.
- **Prefer sealed classes / enums over string constants** for closed sets (e.g.
  `EntityType`, `RelationType`, `MemoryType`).
- **Tests use backtick-quoted function names** (e.g. `` @Test fun `resolveOrCreate creates a new entity`() ``)
  so the test runner output reads like a spec.

---

## 9. Where to ask questions

- **[GitHub Discussions](https://github.com/ansaribilal14/babymomo/discussions)** — for
  "how do I…?" questions, design proposals, and architecture deep-dives. Please search
  existing discussions before opening a new one.
- **[GitHub Issues](https://github.com/ansaribilal14/babymomo/issues)** — for concrete
  bugs and feature requests. Use the issue template; include reproduction steps and
  expected vs. actual behavior.
- **Pull request reviews** — for code-specific questions on an open PR, comment on the
  relevant line.

## 10. License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).
