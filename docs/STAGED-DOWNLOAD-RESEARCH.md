# BABYMOMO Staged-Download Onboarding Research

**Task:** RESEARCH-1 — Evaluate whether BABYMOMO can adopt a CoD/PUBG-style staged-download onboarding architecture (small Play Store APK + bulk first-launch download).

**Author:** Research sub-agent
**Date:** 2026-06-22 (BABYMOMO v0.3.0 baseline)
**Status:** Research only — no source files modified, no implementation performed.

---

## 1. Executive summary (TL;DR)

BABYMOMO's 211 MB APK is large enough to materially depress Play Store install conversion and is awkward for GitHub-release sideloading, but **the codebase already ships 90% of the infrastructure needed for a CoD/PUBG-style staged-download flow**: `ModelDownloadWorker` is a production-grade OkHttp + WorkManager + foreground-service downloader with progress reporting, MD5 verification, retry/backoff, and atomic temp→final file rename, and `OnnxEmbedder` / `EmbedderProvider` already implement the "real-model-or-graceful-mock" fallback pattern. The recommended phased path is: **(Phase 1, ~1 day) enable ABI splits in `app/build.gradle.kts` to ship only `arm64-v8a` to real devices and `x86_64` to emulators — cuts ~50 MB immediately; (Phase 2, ~2–3 days) extend the existing `ModelDownloadWorker` pattern to fetch the BGE-small ONNX model (~33 MB) from HuggingFace on first launch instead of bundling it as an app asset, leveraging `OnnxEmbedder`'s already-wired "asset missing → mock fallback → reload from `filesDir`" path; (Phase 3, optional) move the MediaPipe `tasks-genai` AAR's native libs out of the base APK and into a Dynamic Feature Module or first-launch download for users who actually want on-device LLM inference.** Google Play Asset Delivery (PAD) install-time / on-demand asset packs are the *right* answer for Play-Store-distributed builds, but since BABYMOMO is currently GitHub-sideloaded, **Option D (in-app first-launch download) is the highest-leverage first move** — it works identically for Play Store and sideloaded APKs and reuses existing code. PAD becomes the polish layer once the app actually ships on Play Store.

---

## 2. The CoD/PUBG pattern explained

When a user installs Call of Duty: Mobile or PUBG Mobile from Google Play, they get a **small bootstrap APK** (CoD:M ≈ 1.0–1.5 GB initial install, PUBG Mobile similar, Fortnite historically ~120 MB sideloaded installer) and then on first launch the app presents an **onboarding screen** that downloads the bulk of the game's assets (HD textures, maps, audio packs, anti-cheat modules) — typically 5–13 GB additional — into the app's internal/external storage before allowing gameplay. The pattern has four properties:

1. **The Play Store download is the floor, not the ceiling.** The store delivers the bootstrap binary + (optionally) a subset of "install-time" asset packs. Everything else is fetched by the app itself on first run, after the user has already opened it.
2. **The app owns the download UX.** A progress bar, percentage, phase labels ("Downloading resources…", "Verifying…", "Extracting…"), wifi-vs-cellular prompts, retry-on-failure, and "tap to begin" gates are all implemented in-app — they are not Play Store UI.
3. **Asset delivery is decoupled from binary delivery.** Game studios ship hotfixes, seasonal content, and per-device texture-format-targeted assets as new asset-pack versions without touching the base APK. This means small delta updates and per-device-architecture delivery (e.g., ASTC vs ETC2 textures, arm64-v8a vs armeabi-v7a native libs) become tractable.
4. **It's not magic — it's three Google Play mechanisms + one self-hosted mechanism.** The legitimate toolbox is: (a) Android App Bundle (AAB) with automatic per-ABI / per-density / per-language splits, (b) **Play Asset Delivery (PAD)** asset packs in three flavors (install-time, fast-follow, on-demand), (c) **Play Feature Delivery** (Dynamic Feature Modules) for on-demand code, and (d) **first-launch in-app download** from a CDN/HuggingFace (the Fortnite-bypassed-Play-Store approach, also used by any app sideloaded outside the Play Store).

**Why games don't just ship a 2 GB APK:** (1) Google Play's **base AAB compressed-download limit is 200 MB** — you *can't* ship a 2 GB base APK on the Play Store without using PAD or expansion files (and OBBs are deprecated for AABs). (2) Google's own research (Sam Tolomei, "Shrinking APKs, growing installs") found that "smaller APK sizes correlate with higher install conversion rates, with an even larger impact on conversion rates for users in emerging markets" — every 10 MB shaved off the download measurably lifts installs. (3) Mobile data is expensive regionally — a 2 GB download prompts users on metered connections to defer or abandon. (4) Update delta size: a 2 GB APK means a 2 GB re-download for any code change, vs a 50 MB delta if assets are split out. (5) Storage on low-end phones is constrained; giving the user a "download only what you need" choice (CoD:M's "DOWNLOAD OPTIONS TO REDUCE APP SIZE" feature) is itself a conversion feature.

---

## 3. Google Play delivery options

### 3.1 Android App Bundle (AAB) + automatic per-device splits

Every Play Store app since August 2021 must be published as an `.aab`. Google Play then generates per-device split APKs containing only the code/resources/ABIs that device needs. **The compressed-download-size limit for the base module is 200 MB.** The cumulative compressed download size any device receives (base + all asset packs + all feature modules) is capped at **4 GB**. Source: <https://developer.android.com/guide/app-bundle/faq>.

AAB alone (no asset packs, no feature modules) already gives you three free wins for BABYMOMO:
- **ABI split**: ship only `arm64-v8a` to real devices (~99% of modern Android), `x86_64` only to emulators. MediaPipe's `tasks-genai` AAR ships native libs for 3 ABIs (~80 MB total); ABI splitting alone cuts this to ~27 MB per device.
- **Density split**: only ship the resource density the device needs (already automatic for drawable resources).
- **Language split**: only ship the strings/locale the device needs.

### 3.2 Play Asset Delivery (PAD) — asset packs

PAD is the successor to OBB expansion files for AAB-published apps. Source: <https://developer.android.com/guide/playcore/asset-delivery>. Key facts:

| Delivery mode   | When downloaded                                          | Per-pack size limit | Total limit              |
|-----------------|----------------------------------------------------------|---------------------|--------------------------|
| `install-time`  | Automatically with the APK, before first launch         | 1 GB                | 1 GB combined (1.5 GB if enrolled in Google Play Partner Program for Games per a 2024 community-forum answer) |
| `fast-follow`   | Immediately after install, in parallel with first launch | 512 MB              | "a few hundred extra MBs" total |
| `on-demand`     | When the app explicitly requests it at runtime          | 512 MB              | Up to 4 GB cumulative per device |

Asset packs contain **assets only — no executable code** (no `.so`, no `.dex`). They're hosted and served by Google Play at no charge — no CDN bill, automatic delta patching, automatic updates. Texture Compression Format Targeting lets the same logical asset pack deliver ASTC vs ETC2 vs ATC textures per device GPU family, multiplying the savings.

Access from app code is via the Play Core `AssetPackManager` API — asset packs surface as regular filesystem paths under the app's external storage, and you read them with normal `File`/`InputStream` operations. The TCF targeting API additionally lets you query which texture format was delivered.

### 3.3 Play Feature Delivery (Dynamic Feature Modules)

Dynamic Feature Modules (DFMs) are the *code-carrying* counterpart to asset packs. Source: <https://developer.android.com/guide/playcore/feature-delivery>. They split features (Kotlin/Java classes, native libs, resources) into separate APKs that can be:

- `install-time` — installed with the base APK (but uninstallable later)
- `on-demand` — downloaded at runtime via `SplitInstallManager.startInstall()`
- `conditional` — installed only on devices matching certain capabilities (e.g., only on devices with rear camera, only on Android 12+, only on arm64)
- `instant` — for instant apps

This is what you want when the *code itself* is large. For BABYMOMO the obvious candidate is **MediaPipe GenAI**: the `com.google.mediapipe:tasks-genai:0.10.35` AAR brings ~80 MB of native libraries that only matter if the user wants on-device LLM inference (a minority use case — most users will start with the remote provider or mock). Splitting MediaPipe into an on-demand DFM means users who never run a local LLM never download 80 MB.

### 3.4 First-launch in-app download (no Play machinery)

The fourth option is the one CoD/PUBG/Genshin/Fortnite all actually use for *most* of their bulk assets (HD packs, maps, voiceover packs): **the app downloads files from its own CDN or HuggingFace to internal storage on first launch.** This is exactly the pattern BABYMOMO's `ModelDownloadWorker` already implements. It works for Play Store installs AND for sideloaded APKs (which is critical for BABYMOMO today, since the app is GitHub-released). The trade-off: you pay bandwidth costs (or use HuggingFace's free-but-rate-limited hosting) and you build your own progress UI, retry logic, integrity verification, and storage management.

---

## 4. Option-by-option evaluation for BABYMOMO

BABYMOMO v0.3.0 baseline (verified from the codebase + filesystem):
- `BABYMOMO-v0.3.0-debug.apk` = **211 MB** (debug build; release with R8+resource shrinking would be smaller but not dramatically so for the native-lib portion)
- `BABYMOMO-v0.2.0-debug.apk` = **92 MB** (before BGE ONNX was bundled — so v0.3 added ~119 MB: ~33 MB BGE model + ~80 MB MediaPipe AAR + ~6 MB misc)
- `assets/models/bge-small-en-v1.5-int8.onnx` = **33 MB**
- `assets/models/bert-base-uncased-vocab.txt` = **227 KB**
- `ModelManager.DEFAULT_CATALOG` already points to 7 LLM models on HuggingFace (`bartowski/...-GGUF`) and Google Cloud Storage (`storage.googleapis.com/mediapipe-models/...`)
- `ModelDownloadWorker` already implements: OkHttp download → temp file → MD5 verify → atomic rename → `filesDir/models/` → progress reporting (`KEY_BYTES_DOWNLOADED`, `KEY_TOTAL_BYTES`, `KEY_PHASE`) → foreground notification → retry with backoff → cancellation
- `OnnxEmbedder.extractAssetIfPresent()` already returns `null` when the asset is missing, which sets `unavailable = true`, which causes `EmbedderProvider.current()` to fall back to `MockEmbedder` — **the graceful-degradation path is already built**
- `EmbedderProvider.current()` already does the runtime routing (`if (onnx.isReady() || onnx.ensureLoaded()) onnx else mock`)

### Option A — App Bundles (AAB) + ABI splits

| Criterion | Assessment |
|---|---|
| Feasibility | **Trivial.** Already 90% of the way there — `app/build.gradle.kts` already builds the AAB by default (it's the Play Store format). Just add `splits { abi { isEnable = true; reset(); include("arm64-v8a", "x86_64"); universalApk = false } }` *or* (preferred for Play Store) rely on AAB's automatic per-ABI splitting, optionally constrained via `ndk { abiFilters += listOf("arm64-v8a") }` for the release flavor to drop 32-bit ARM entirely. |
| Effort | **~1 day.** Touch `app/build.gradle.kts`, test on emulator (x86_64) + physical device (arm64-v8a), verify native libs load. |
| Size reduction | Cuts MediaPipe's native libs from ~80 MB → ~27 MB per device (only arm64-v8a shipped to real phones). Same applies to ONNX Runtime Android (~10 MB → ~3 MB). Estimated **50 MB shaved** off the per-device download. |
| Play Store compliance | **Fully compliant** — AAB is the required format. |
| User experience | Transparent — users get a smaller download, no UI changes needed. |
| Trade-offs | Doesn't help with the 33 MB BGE model (model files aren't ABI-specific). Doesn't help with the Kotlin/Compose bytecode (~80 MB). Doesn't help sideloaded APKs unless you also produce a universal APK with `universalApk = true` or use `bundle` ABI filtering. |

**Verdict: do this first, no matter what else you do.** It's a one-day change with no downside.

### Option B — Google Play Asset Delivery (PAD) install-time asset packs

| Criterion | Assessment |
|---|---|
| Feasibility | **Feasible.** Move `bge-small-en-v1.5-int8.onnx` (33 MB) + `bert-base-uncased-vocab.txt` (227 KB) into an `install-time` asset pack. Asset packs are Gradle modules with `com.android.asset-pack` plugin; you declare them in `settings.gradle.kts` and reference them from the app module. |
| Effort | **Medium.** ~2 days: new Gradle module, Play Console configuration, change `OnnxEmbedder.extractAssetIfPresent()` to look up the asset pack path via `AssetPackManager` instead of `AssetManager`. |
| Size reduction | Removes 33 MB from the base APK. Net Play Store install size: ~178 MB base + 33 MB asset pack = same total, but the *base* is under 200 MB (currently we're already under 200 MB so this isn't strictly necessary for compliance — yet). |
| Play Store compliance | **Fully compliant.** PAD is Google's recommended path for >200 MB apps. |
| User experience | User sees "Installing…" on the Play Store screen slightly longer, then opens the app with the model ready. No in-app download UI needed. |
| Trade-offs | **Won't work for sideloaded APKs from GitHub releases** — PAD is Play-Store-only. Would require maintaining two build flavors: one with the asset bundled (for sideload) and one with the asset pack (for Play). Also requires shipping via Play Console (testing tracks, signing, review process). |

**Verdict: defer until BABYMOMO actually ships on Play Store.** The maintenance burden of two flavors isn't worth it while the app is GitHub-only.

### Option C — Google Play Asset Delivery (PAD) on-demand asset packs

| Criterion | Assessment |
|---|---|
| Feasibility | **Feasible but heavier.** Same asset-pack module structure as Option B, but with `delivery: on-demand` and a first-launch onboarding screen that calls `AssetPackManager.fetch()` with a `SessionStateListener`, surfaces progress, and only proceeds once `COMPLETED`. |
| Effort | **Medium-high.** ~3–5 days: new Gradle module, onboarding UI (a Compose screen with progress bar + "Downloading AI brain…"), state-machine for download/verify/extract phases, integration with `EmbedderProvider` to switch from mock → real once the asset pack is available, retry/error UI. |
| Size reduction | Same 33 MB removed from base APK; user pays it on first launch instead. |
| Play Store compliance | **Fully compliant.** |
| User experience | Closest to the CoD/PUBG pattern — small initial install, in-app onboarding, "real AI" unlocks once download finishes. |
| Trade-offs | **Still Play-Store-only** — sideloaded APKs need a parallel path. Asset access API (`AssetPackManager.getAssetLocation()`) is more involved than `AssetManager.open()`. The 33 MB BGE model is borderline-worth-it: small enough that bundling is reasonable, large enough that removing it noticeably shrinks the APK. |

**Verdict: attractive but overkill for the 33 MB BGE model alone.** Worth it only if combined with Option E (split out MediaPipe too) and only after Play Store launch.

### Option D — First-launch in-app download (no Play Asset Delivery)

| Criterion | Assessment |
|---|---|
| Feasibility | **Highest feasibility / lowest risk.** The pattern is already implemented end-to-end in `ModelDownloadWorker.kt` for LLM models. Extending it to the embedding model is mostly a catalog-entry addition (similar to the existing `ModelEntity` rows) + a small change to `OnnxEmbedder` to look in `filesDir/models/bge-small-en-v1.5-int8.onnx` *before* trying `assets/`. The graceful fallback to `MockEmbedder` is already wired. |
| Effort | **Low.** ~1–2 days: (a) add a `BGE_SMALL_ONNX` entry to the model catalog (or a new "embedding assets" catalog), (b) modify `OnnxEmbedder.extractAssetIfPresent()` to first check `File(ctx.filesDir, MODEL_REL_PATH)` and only fall back to `assets/` if the file isn't there, (c) trigger the download on first launch (could be inside `BabymomoApp.onCreate()` or a one-shot WorkManager job gated by a DataStore flag), (d) optional onboarding screen showing progress (or just let `EmbedderProvider` silently fall back to `MockEmbedder` until the download completes). |
| Size reduction | Removes 33 MB from the APK. **Combined with Option A (ABI splits), the APK drops from 211 MB → ~128 MB.** |
| Play Store compliance | **Fully compliant** — no Play machinery involved. |
| User experience | First launch: app opens immediately with `MockEmbedder` (functional, just lower-quality embeddings). Real BGE model downloads in background; once complete, `OnnxEmbedder.ensureLoaded()` finds the file in `filesDir` and `EmbedderProvider` transparently switches. No progress UI strictly required, but a small banner "Downloading AI brain for better memory…" is a nice touch. |
| Trade-offs | **Requires internet on first launch** for real embeddings (mock still works offline). **HuggingFace bandwidth**: BGE-small-en-v1.5 is hosted at `onnx-community/bge-small-en-v1.5-ONNX` — HF doesn't charge per-GB but does rate-limit per-IP (observed ~10 MB/s per connection, occasional 403s under load). For a 33 MB file this is a non-issue. **Works for both Play Store AND sideloaded APKs** — this is the killer advantage while BABYMOMO is GitHub-only. |

**Verdict: do this second (after Option A).** It reuses the most code, helps both distribution channels, and unblocks the path to a ~128 MB APK without any Play Store dependency.

### Option E — Dynamic Feature Modules (on-demand feature delivery)

| Criterion | Assessment |
|---|---|
| Feasibility | **Feasible but heavy.** Split MediaPipe GenAI (`tasks-genai` AAR + the `MediapipeLlmEngine` Kotlin code + `LocalLlmProvider` dispatch) into a DFM with `delivery: on-demand`. Users who never tap "Download local LLM model" never download the 80 MB. |
| Effort | **High.** ~1 week: significant Gradle restructuring (new module with its own `build.gradle.kts`, dependency on `mediapipe-genai-llm-inference` moves there, `LocalLlmProvider` needs to handle the case where the feature module isn't installed → call `SplitInstallManager.startInstall()` and suspend until installed), runtime module-install API integration, UI affordance to trigger install. |
| Size reduction | Removes ~80 MB from the base APK (assuming ABI splits already applied — otherwise ~27 MB). **Combined with Options A + D, base APK could reach ~48 MB** (Kotlin/Compose bytecode + Room + Hilt + a slim shell). |
| Play Store compliance | **Fully compliant** — but **DFMs only work via Play Store**. Sideloaded APKs can't use Dynamic Feature Delivery (there's a third-party library "LocallyDynamic" that emulates it for sideloaded apps, but it's a hobby project). |
| User experience | Best for the user — they get a tiny install and only pay the size cost for features they actually use. But the UX of "your feature is downloading, please wait" mid-app-use is jarring if not handled gracefully. |
| Trade-offs | **Most complex option.** Requires Play Store distribution (deal-breaker for current GitHub-only model). Gradle restructuring risks breaking the build. Hilt DI across module boundaries needs care. Worth it only when MediaPipe is *actually working* (today it's stubbed per the v0.2 changelog: "MediaPipe GenAI on-device LLM inference is STUBBED in v0.2"). |

**Verdict: defer until (a) MediaPipe LLM inference actually works in BABYMOMO, (b) BABYMOMO is on Play Store, and (c) telemetry shows users actually want local LLM.** Today it's premature optimization.

### Option F — Hybrid: small APK + tiered onboarding

| Criterion | Assessment |
|---|---|
| Feasibility | **Feasible** — this is just Options A + D + (optionally) E composed together with a polished onboarding screen. |
| Effort | **Medium.** ~1 week total if you do A + D + onboarding UI; ~2 weeks if you add E. |
| Size reduction | Base APK ~30–50 MB (Compose shell + Room schema + mock providers + ABI-split native libs for ONNX Runtime only). |
| Play Store compliance | **Fully compliant.** |
| User experience | Best — closest to the CoD/PUBG pattern. Splash → "Setting up your AI companion…" progress → main UI with mock brain → background download of BGE + (optionally) MediaPipe → silent upgrade to real AI when ready. |
| Trade-offs | Most engineering work; needs careful state management for the "mock → real" transition (existing memory entries created with mock embeddings need to be re-embedded when the real model arrives, otherwise similarity search is broken — see `OnnxEmbedder.ensureLoaded()` which doesn't currently handle this). |

**Verdict: this is the end-state to aim for, but achieve it incrementally via Phases 1–3 below rather than as a big-bang rewrite.**

---

## 5. Recommended phased approach

Ranked best-to-worst for BABYMOMO's *current* situation (GitHub-sideloaded, v0.3.0, MediaPipe stubbed):

| Rank | Phase | Option | Effort | APK after |
|------|-------|--------|--------|-----------|
| 🥇 | **Phase 1** | **Option A** — enable ABI splits (ship only `arm64-v8a` + `x86_64`) | ~1 day | **~160 MB** (debug) / ~110 MB (release) |
| 🥈 | **Phase 2** | **Option D** — extend `ModelDownloadWorker` pattern to fetch BGE ONNX on first launch; modify `OnnxEmbedder.extractAssetIfPresent()` to check `filesDir` first | ~1–2 days | **~127 MB** (debug) / ~80 MB (release) |
| 🥉 | **Phase 3a (optional, when on Play Store)** | **Option B** — move BGE model into install-time PAD asset pack for Play Store flavor; keep bundled asset for sideload flavor | ~2 days | Same total, but cleaner Play Store delivery |
| 4 | **Phase 3b (optional, when MediaPipe works)** | **Option E** — split MediaPipe into on-demand DFM (Play Store only) | ~1 week | **~50 MB** base for Play Store users who skip local LLM |
| 5 | **Phase 4 (polish)** | **Option F** — onboarding screen with progress UI, "mock → real" transition with re-embedding of early memories | ~1 week | UX parity with CoD/PUBG |

**Concrete next action:** Do Phase 1 immediately (it's a one-line `build.gradle.kts` change with no functional risk), then Phase 2 in the next sprint. Phases 3+ wait until BABYMOMO has a Play Store listing and a working MediaPipe integration.

---

## 6. UX patterns from games

| Pattern | Used by | Requires Play Store? | Works for sideloaded APKs? | Typical first-launch download |
|---------|---------|----------------------|----------------------------|-------------------------------|
| **Splash → progress bar → main menu** | Call of Duty: Mobile, most casual games | No (works either way) | Yes | CoD:M downloads resources in stages after the initial ~1 GB Play Store install; total install reaches ~14 GB with all asset packs |
| **"Select asset quality" → download → main menu** | PUBG Mobile ("HD resource pack" prompt), Genshin Impact (voice pack language selection) | No | Yes | PUBG: ~8 GB total; Genshin: ~15–31 GB total depending on voice packs |
| **Background download during tutorial/exploration** | Genshin Impact (lets you start playing while streaming remaining assets) | No | Yes | Genshin streams open-world assets as you approach them; full install ~26 GB on Android |
| **"Tap to begin download" → progress → ready** | Fortnite (sideloaded installer pattern, pre-Play-Store-return), many indie games | No — this is the *canonical* sideload pattern | Yes (this is what sideload requires) | Fortnite Android beta was 1.88 GB; Epic Games installer is a tiny bootstrap APK that downloads the real game |
| **Install-time asset packs (silent)** | Most Play Store games using PAD install-time | Yes (PAD is Play-only) | No | Transparent — user just sees a longer "Installing…" on Play Store |
| **Fast-follow asset packs (near-silent)** | Many games — packs download immediately after first launch in background | Yes (PAD is Play-only) | No | User can open the app immediately; features requiring the asset show "downloading…" |

**For BABYMOMO specifically:** The "background download during tutorial/exploration" pattern (Genshin-style) maps best to the existing architecture — `MockEmbedder` already provides a functional baseline, so the user can start chatting immediately while the real BGE model downloads in the background via WorkManager. Once the download completes, `OnnxEmbedder.ensureLoaded()` finds the file in `filesDir` and `EmbedderProvider.current()` transparently starts returning real embeddings. **No jarring "tap to download" gate required.**

---

## 7. Real-world size benchmarks

Concrete data from web research (Play Store listings, community reports, official docs):

| App / Game | Initial download (Play Store) | First-launch additional download | Total install size after first launch | Distribution |
|------------|-------------------------------|----------------------------------|----------------------------------------|--------------|
| **Call of Duty: Mobile** | ~1.0–1.5 GB (Play Store APK + install-time assets) | ~5–13 GB (HD resources, maps, anti-cheat — staged: first update 30–50 MB, second 200+ MB, individual maps like Blackout 500 MB each) | ~14 GB with all resources | Play Store + Apple App Store |
| **PUBG Mobile** | ~700 MB–1 GB initial | ~6–7 GB additional (resource packs, maps) | ~8 GB without skins/cosmetics | Play Store + App Store |
| **Genshin Impact** | Initial app download (~3 GB) | Additional resources totaling ~28 GB (character data, map assets, voice packs) on first launch | ~31 GB on Android | Play Store + App Store |
| **Fortnite (Android)** | ~120 MB sideloaded Epic Games installer; ~1.88 GB game download; or ~1.5 GB Play Store APK after return | Periodic season updates (several GB) | ~6–8 GB depending on season | Epic Games direct (2018–2020) → Play Store (2020–2021) → Epic Games direct again (2021+) |
| **LM Studio (mobile)** | Connects to desktop LM Studio over LAN — no model files bundled in app | N/A (models run on the desktop server, not on phone) | Small app shell | App Store / Play Store (companion apps like "LM Mini", "LMSA") |
| **MLC Chat** | Small app shell (~tens of MB — native TVM runtime + UI) | Model weights downloaded on first use from Hugging Face (e.g., Llama-3-8B ~4 GB, Mistral-7B ~3 GB, smaller 1B models ~600 MB–1 GB) | Depends entirely on which models the user picks | App Store + sideloaded APK from MLC project |
| **BABYMOMO v0.3.0 (current)** | **211 MB (debug APK, GitHub sideload)** — bundles BGE ONNX (33 MB) + MediaPipe AAR w/ 3 ABIs (~80 MB) + ONNX Runtime (~10 MB) + Kotlin/Compose/Hilt/Room bytecode (~80 MB) | LLM models (1–2.5 GB each) already via `ModelDownloadWorker` | Same | GitHub releases (sideload) |

**Key takeaway for BABYMOMO:** MLC Chat is the closest analog — a small app shell that downloads model weights on demand. BABYMOMO's *binary* footprint (211 MB) is much larger than MLC Chat's because we bundle (a) all 3 native ABIs of MediaPipe and (b) the BGE embedding model as an asset. Both are fixable via Phase 1 (ABI splits) + Phase 2 (in-app BGE download). After those two phases, BABYMOMO's APK would be roughly comparable to MLC Chat's shell size.

---

## 8. Legal + bandwidth considerations

### 8.1 HuggingFace terms of service + hotlinking

HuggingFace's Terms of Service (<https://huggingface.co/terms-of-service>) and community discussion (<https://discuss.huggingface.co/t/using-huggingface-as-a-hosting-cdn-for-a-pretrained-model/128442>) make the situation clear:

- **Per-GB bandwidth: not charged.** HF Hub does not bill per gigabyte downloaded. There is no metered egress like AWS S3.
- **What actually limits you: request rate, not bandwidth.** Per-IP rate limits throttle aggressive parallel downloads. Observed behavior: ~10 MB/s per connection with parallel connections unlocking more, occasional 403s under high concurrency, no hard cap on total bytes.
- **Hotlinking the `resolve/main/<file>` URL from an app is permitted** for openly-licensed models — this is exactly what BABYMOMO's `ModelManager.DEFAULT_CATALOG` already does (every GGUF entry points at `huggingface.co/bartowski/.../resolve/main/...`).
- **Practical implication for BABYMOMO:** downloading the 33 MB BGE ONNX from `onnx-community/bge-small-en-v1.5-ONNX` on first launch is well within HF's tolerances — it's a one-time, one-file, low-concurrency fetch. Even at thousands of daily active users, this is trivial load. If BABYMOMO ever reaches the scale where HF rate-limiting bites, Cloudflare R2 in front of HF (cache-on-first-fetch) is the standard mitigation.

### 8.2 CDN options + rough costs

For self-hosting model files (if HF rate limits ever become a problem, or for guaranteed-SLA commercial distribution):

| Provider | Storage cost | Egress cost | Notes |
|----------|--------------|-------------|-------|
| **Cloudflare R2** | $0.015 / GB-month | **$0 (free)** — no egress fees through Cloudflare CDN | Best for BABYMOMO: free egress means a 33 MB file served to 10,000 users = $0 bandwidth, $0.50 storage/month. First 10 GB storage free. (<https://developers.cloudflare.com/r2/pricing>) |
| **Backblaze B2** | $0.006 / GB-month (cheaper storage) | $0.01 / GB egress (free through Cloudflare CDN partnership up to 3x storage) | Best for bulk storage of many models. (<https://www.backblaze.com/cloud-storage/pricing>) |
| **AWS CloudFront + S3** | $0.023 / GB-month (S3) | $0.085–$0.12 / GB egress (varies by region) | Most expensive; only justified if already on AWS. |
| **Google Cloud Storage** | $0.020 / GB-month | $0.12 / GB egress | Same caveat as AWS. |
| **HuggingFace Hub** | $0 (free for open models) | $0 (no per-GB charge) | Best default — what BABYMOMO already uses. Rate-limited per-IP, not per-GB. |

**Concrete cost projection for BABYMOMO:** If 1,000 users download the 33 MB BGE model from Cloudflare R2 on first launch: storage $0.0005/month, egress $0 (free). Total monthly cost: effectively $0. Even at 100,000 users, R2 egress is still $0. **Cloudflare R2 is the obvious self-hosting choice if/when HF rate limits become a concern.**

### 8.3 Play Store AAB signing + asset pack signing

- AABs are uploaded to Play Console and signed with an **upload key** (developer-managed). Play then re-signs the distributed APKs with the **app signing key** (Play-managed, opt-in but strongly recommended).
- Asset packs are signed as part of the AAB — no separate signing step.
- Dynamic Feature Modules likewise inherit signing from the AAB.
- For sideloaded APKs from GitHub releases, the APK is signed with the same upload key (or a debug key for debug builds — which is why `BABYMOMO-v0.3.0-debug.apk` is signed with the Android debug keystore).

### 8.4 Redistributing model files via our own CDN

**Gemma (Gemma 1, 2, 3 — used by BABYMOMO's MediaPipe entries):** The [Gemma Terms of Use](https://ai.google.dev/gemma/terms), Section 3.1 "Distribution and Redistribution", explicitly permits redistribution provided you:
1. Include the Section 3.2 use restrictions as an enforceable provision in your downstream license/terms of use,
2. Provide all third-party recipients a copy of the Gemma Agreement,
3. Cause any modified files to carry prominent notices stating you modified them,
4. Accompany all Distributions (other than through a Hosted Service) with a "Notice" text file containing: *"Gemma is provided under and subject to the Gemma Terms of Use found at ai.google.dev/gemma/terms"*.

**Verdict: redistributing Gemma model files via Cloudflare R2 is legally permitted** as long as the notice file ships alongside. Note the controversial clause 3.2: Google reserves the right to "restrict (remotely or otherwise) usage" of Gemma they believe violates the agreement — this is a kill-switch, not a redistribution blocker, but it's worth disclosing to users.

**Gemma 4** ships under a separate "Gemma 4 license" — check it specifically if BABYMOMO ever upgrades.

**Llama 3.2 (Meta):** Llama Community License — permits redistribution with attribution and use-restriction pass-through, similar to Gemma. **Permitted for CDN redistribution.**

**Qwen 2.5 (Alibaba):** Apache 2.0 (unmodified) or Tongyi Qianwen license — Apache 2.0 unambiguously permits redistribution. **Permitted.**

**Phi-3 (Microsoft):** MIT license. **Permitted, no restrictions.**

**SmolLM2 (HuggingFace):** Apache 2.0. **Permitted.**

**BGE-small-en-v1.5 (BAAI):** MIT license. **Permitted.**

**Practical conclusion:** All models currently in BABYMOMO's catalog can legally be redistributed via a self-hosted CDN, provided the appropriate license/notice files are included in the download bundle. The simplest implementation is to package each model + its LICENSE + a NOTICE file into a `.zip` (or to serve the model file alongside a `LICENSE.txt` companion file with the same basename).

---

## 9. What changes for BABYMOMO if we go Play Store vs. stay GitHub-only

| Concern | GitHub-only (current) | Play Store |
|---------|----------------------|------------|
| **APK/AAB format** | `.apk` (debug or release-signed with upload key) | `.aab` required (Play Console generates per-device split APKs) |
| **Base size limit** | None (GitHub allows any file size, though large files hurt download UX) | **200 MB compressed base download** per device |
| **Asset delivery mechanism** | First-launch in-app download only (Option D) | All options available: AAB splits, PAD install-time / fast-follow / on-demand, Dynamic Feature Modules, in-app download |
| **Dynamic Feature Modules** | ❌ Not available (Play-only feature) | ✅ Available |
| **Play Asset Delivery** | ❌ Not available (Play-only feature) | ✅ Available |
| **Update mechanism** | User must manually download new APK from GitHub Releases | Auto-update via Play Store (delta patches, staged rollouts) |
| **Signing** | Upload key (or debug key for debug builds) | Upload key for AAB upload; app signing key (Play-managed) for distributed APKs |
| **Review process** | None | Play Store review (policies, target SDK requirements, privacy declarations) |
| **Discovery / install conversion** | Whoever finds the GitHub repo | Play Store search + listings — APK size matters more here (Google's research: smaller APK → higher install conversion) |
| **Bandwidth costs** | GitHub Releases (free, but 2 GB per-file limit; large files better on external CDN) | Play asset delivery is free (Google hosts/serves asset packs at no charge) |

**Implication for phased plan:** Phases 1 and 2 (ABI splits + in-app BGE download) are valuable *regardless* of distribution channel. Phase 3+ (PAD, DFMs) only unlocks value once BABYMOMO is on Play Store. **The right strategy is to do Phases 1 and 2 now, and treat Play Store launch as the trigger to evaluate Phase 3.**

---

## 10. Final recommendation

**Adopt a phased staged-download architecture, beginning with two changes that are valuable immediately (regardless of distribution channel) and require no Play Store dependency:**

1. **Phase 1 (this week, ~1 day):** Enable ABI splits in `app/build.gradle.kts`. Constrain the release flavor to `arm64-v8a` + `x86_64` (drop `armeabi-v7a` — virtually no modern Android device uses 32-bit ARM, and Google Play has required 64-bit support since August 2019). This single change removes ~50 MB from the per-device download (MediaPipe's `armeabi-v7a` native libs + ONNX Runtime's `armeabi-v7a` native libs disappear). Verify by inspecting the AAB's split APKs with `bundletool`.

2. **Phase 2 (next sprint, ~1–2 days):** Extend the existing `ModelDownloadWorker` pattern to fetch the BGE-small ONNX model (33 MB) from HuggingFace on first launch instead of bundling it as an app asset. Modify `OnnxEmbedder.extractAssetIfPresent()` to first check `File(ctx.filesDir, MODEL_REL_PATH)` and only fall back to `assets/` if the downloaded file isn't there (this preserves backward compatibility with the bundled-asset build flavor if you ever want it). Wire the download as a one-shot WorkManager job triggered from `BabymomoApp.onCreate()` gated by a DataStore `bge_model_fetched` flag. `EmbedderProvider` already falls back to `MockEmbedder` while the download is in flight — no UI work strictly required. Optionally add a small "Downloading AI brain for better memory…" banner. This removes another 33 MB from the APK.

After Phases 1+2, BABYMOMO's debug APK drops from 211 MB → **~128 MB** (release build with R8 + resource shrinking would be smaller still, plausibly ~80 MB). This is below the Play Store's 200 MB base limit, well within GitHub's sideload comfort zone, and removes the largest contributors to APK bloat without touching MediaPipe (which is currently stubbed and shouldn't be optimized prematurely).

**Defer Phase 3 (PAD / Dynamic Feature Modules) until BABYMOMO actually has a Play Store listing and a working MediaPipe integration.** When MediaPipe is unstubbed and BABYMOMO is on Play Store, the highest-value Phase 3 move is to split MediaPipe into an on-demand Dynamic Feature Module — this would remove another ~27 MB (post-ABI-split) of native libs from the base APK for users who never run local LLM inference, getting the base to ~50 MB. Until then, the maintenance cost of two build flavors (one for sideload, one for Play) isn't justified.

**Do NOT pursue Phase 3 install-time/on-demand PAD asset packs for the BGE model alone** — 33 MB is small enough that in-app download (Phase 2) is strictly simpler and works on both distribution channels. PAD becomes worthwhile only when you have a larger asset corpus (HD textures, multiple model variants, voice packs) to deliver — which BABYMOMO doesn't have today.

---

## Appendix A — Sources cited

- **Google Play Asset Delivery overview**: <https://developer.android.com/guide/playcore/asset-delivery>
- **Android App Bundle FAQ (size limits, OBB deprecation)**: <https://developer.android.com/guide/app-bundle/faq>
- **Play Feature Delivery (Dynamic Feature Modules) overview**: <https://developer.android.com/guide/playcore/feature-delivery>
- **Google Play maximum size limits**: <https://support.google.com/googleplay/android-developer/answer/9859372>
- **Introducing Google Play Asset Delivery (Android Developers Blog)**: <https://android-developers.googleblog.com/2020/06/introducing-google-play-asset-delivery.html>
- **PAD install-time size limit confirmation (2024)**: <https://stackoverflow.com/questions/79253280/play-asset-delivery-size-limit-for-install-time-asset-pack-in-2024>
- **Call of Duty: Mobile (Play Store listing)**: <https://play.google.com/store/apps/details?id=com.activision.callofduty.shooter>
- **PUBG Mobile LITE (Play Store listing)**: <https://play.google.com/store/apps/details?id=com.tencent.iglite>
- **Genshin Impact (Play Store listing)**: <https://play.google.com/store/apps/details?id=com.miHoYo.GenshinImpact>
- **Fortnite on Android bypassing Play Store (Game Developer)**: <https://www.gamedeveloper.com/business/epic-games-is-bypassing-the-google-play-store-for-i-fortnite-i-s-android-release>
- **Fortnite Android beta download size (1.88 GB)**: <https://www.facebook.com/Kenyagossipclub.ke/posts/fortnite-has-officially-returned-to-the-google-play-store-for-android-users-worl/929221256507041>
- **MLC LLM Android SDK (downloads model weights from HuggingFace on first use)**: <https://llm.mlc.ai/docs/deploy/android.html>
- **MLC Chat (App Store)**: <https://apps.apple.com/de/app/mlc-chat/id6448482937>
- **HuggingFace Terms of Service**: <https://huggingface.co/terms-of-service>
- **HuggingFace as a CDN discussion (rate limits vs bandwidth)**: <https://discuss.huggingface.co/t/using-huggingface-as-a-hosting-cdn-for-a-pretrained-model/128442>
- **Cloudflare R2 pricing (zero egress)**: <https://developers.cloudflare.com/r2/pricing>
- **Backblaze B2 pricing**: <https://www.backblaze.com/cloud-storage/pricing>
- **Gemma Terms of Use (Section 3.1 — redistribution permitted with notice)**: <https://ai.google.dev/gemma/terms>
- **"Shrinking APKs, growing installs" (Google Play dev article on APK size vs install conversion)**: <https://medium.com/googleplaydev/shrinking-apks-growing-installs-5d3fcba23ce2>
- **Genshin Impact total size benchmark**: <https://gameboost.com/blog/how-big-is-genshin-impact>

## Appendix B — Codebase files inspected (ground truth)

- `/home/z/my-project/babymomo/app/build.gradle.kts` — current build config (no ABI splits configured; `useLegacyPackaging = false`; `isMinifyEnabled = true`, `isShrinkResources = true` already enabled for release; depends on `com.google.mediapipe:tasks-genai:0.10.35` and `com.microsoft.onnxruntime:onnxruntime-android:1.17.0`)
- `/home/z/my-project/babymomo/app/src/main/java/com/babymomo/work/ModelDownloadWorker.kt` — production-grade downloader (OkHttp + WorkManager + foreground service + MD5 + retry/backoff + atomic rename + progress reporting). Already does everything needed for staged asset download.
- `/home/z/my-project/babymomo/app/src/main/java/com/babymomo/core/memory/OnnxEmbedder.kt` — loads BGE ONNX from `assets/models/bge-small-en-v1.5-int8.onnx`, extracts to `filesDir/models/...` for mmap. `extractAssetIfPresent()` already returns `null` when the asset is missing → `unavailable = true` → `EmbedderProvider` falls back to `MockEmbedder`. **Key extension point for Phase 2:** modify this method to first check `File(ctx.filesDir, MODEL_REL_PATH)` before falling back to `assets/`.
- `/home/z/my-project/babymomo/app/src/main/java/com/babymomo/core/memory/EmbedderProvider.kt` — already implements the runtime mock-vs-real routing.
- `/home/z/my-project/babymomo/app/src/main/java/com/babymomo/model/ModelManager.kt` — already has `DEFAULT_CATALOG` with 7 LLM models pointing at HuggingFace + Google Cloud Storage URLs. Extending this to include the embedding model is straightforward.
- `/home/z/my-project/babymomo/gradle/libs.versions.toml` — MediaPipe version `0.10.35`, ONNX Runtime `1.17.0`.
- `/home/z/my-project/babymomo/CHANGELOG.md` — confirms MediaPipe GenAI on-device LLM inference is **still stubbed as of v0.2/v0.3**, which weakens the case for splitting MediaPipe into a Dynamic Feature Module prematurely (Phase 3b should wait until MediaPipe actually works).
- Filesystem: `BABYMOMO-v0.2.0-debug.apk` = 92 MB, `BABYMOMO-v0.3.0-debug.apk` = 211 MB → v0.3 added ~119 MB (33 MB BGE model + ~80 MB MediaPipe AAR + ~6 MB misc).
- `app/src/main/assets/models/bge-small-en-v1.5-int8.onnx` = 33 MB, `bert-base-uncased-vocab.txt` = 227 KB — confirms the bundled-asset sizes.

---

*End of research report. No source files were modified. No implementation was performed. This document is research-only.*
