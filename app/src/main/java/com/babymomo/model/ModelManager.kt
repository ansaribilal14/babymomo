package com.babymomo.model

import com.babymomo.data.db.dao.ModelDao
import com.babymomo.data.db.entity.ModelEntity
import com.babymomo.data.db.entity.ModelRuntime
import com.babymomo.data.db.entity.ModelStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelManager @Inject constructor(private val modelDao: ModelDao) {
    fun allModelsFlow(): Flow<List<ModelEntity>> = modelDao.allFlow()
    fun activeModelFlow(): Flow<ModelEntity?> = modelDao.activeModelFlow()

    suspend fun activeModelPath(): String? {
        val m = modelDao.activeModel() ?: return null
        return if (m.status == ModelStatus.READY) m.localPath else null
    }

    suspend fun activate(modelId: String) {
        modelDao.deactivateAll(); modelDao.activate(modelId)
    }

    suspend fun markDownloaded(modelId: String, path: String) {
        modelDao.markDownloaded(modelId, ModelStatus.READY, path, System.currentTimeMillis())
    }

    suspend fun seedCatalogIfEmpty() {
        val existing = modelDao.allFlow().first { it.isNotEmpty() }
        if (existing.isNotEmpty()) return
        modelDao.upsertAll(DEFAULT_CATALOG.map { it.copy() })
    }

    companion object {
        val DEFAULT_CATALOG = listOf(
            ModelEntity(id = "gemma-2b-it-q4", displayName = "Gemma 2B Instruct (Q4_K_M)",
                runtime = ModelRuntime.LLAMA_CPP, huggingfaceRepo = "bartowski/gemma-2-2b-it-GGUF",
                filename = "gemma-2-2b-it-Q4_K_M.gguf",
                downloadUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
                sizeBytes = 1_800_000_000L, quantization = "Q4_K_M", contextLength = 8192,
                minRamMb = 6144, license = "Gemma",
                description = "Google's Gemma 2B — solid general-purpose model, fits mid-range phones."),
            ModelEntity(id = "phi-3-mini-4k-q4", displayName = "Phi-3 Mini 4K Instruct (Q4_K_M)",
                runtime = ModelRuntime.LLAMA_CPP, huggingfaceRepo = "bartowski/Phi-3-mini-4k-instruct-GGUF",
                filename = "Phi-3-mini-4k-instruct-Q4_K_M.gguf",
                downloadUrl = "https://huggingface.co/bartowski/Phi-3-mini-4k-instruct-GGUF/resolve/main/Phi-3-mini-4k-instruct-Q4_K_M.gguf",
                sizeBytes = 2_200_000_000L, quantization = "Q4_K_M", contextLength = 4096,
                minRamMb = 6144, license = "MIT",
                description = "Microsoft's Phi-3 — excellent reasoning for its size, 4K context."),
            ModelEntity(id = "qwen2.5-1.5b-q4", displayName = "Qwen 2.5 1.5B Instruct (Q4_K_M)",
                runtime = ModelRuntime.LLAMA_CPP, huggingfaceRepo = "bartowski/Qwen2.5-1.5B-Instruct-GGUF",
                filename = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
                downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
                sizeBytes = 1_000_000_000L, quantization = "Q4_K_M", contextLength = 32768,
                minRamMb = 4096, license = "Apache 2.0",
                description = "Alibaba's Qwen — best quality at the 1B size class, 32K context."),
            ModelEntity(id = "llama-3.2-3b-q4", displayName = "Llama 3.2 3B Instruct (Q4_K_M)",
                runtime = ModelRuntime.LLAMA_CPP, huggingfaceRepo = "bartowski/Llama-3.2-3B-Instruct-GGUF",
                filename = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                sizeBytes = 2_500_000_000L, quantization = "Q4_K_M", contextLength = 4096,
                minRamMb = 8192, license = "Llama 3.2",
                description = "Meta's Llama 3.2 3B — best quality at this size; needs flagship phone."),
            ModelEntity(id = "smollm2-1.7b-q4", displayName = "SmolLM2 1.7B Instruct (Q4_K_M)",
                runtime = ModelRuntime.LLAMA_CPP, huggingfaceRepo = "bartowski/smollm2-1.7b-instruct-v0.1-GGUF",
                filename = "smollm2-1.7b-instruct-v0.1-Q4_K_M.gguf",
                downloadUrl = "https://huggingface.co/bartowski/smollm2-1.7b-instruct-v0.1-GGUF/resolve/main/smollm2-1.7b-instruct-v0.1-Q4_K_M.gguf",
                sizeBytes = 1_000_000_000L, quantization = "Q4_K_M", contextLength = 8192,
                minRamMb = 4096, license = "Apache 2.0",
                description = "HuggingFace's SmolLM2 — ultra-light, works on 4 GB RAM phones."),
            // --- MediaPipe GenAI / LiteRT-LM (.task format) — wired in v0.2 by LocalLlmProvider → MediapipeLlmEngine ---
            // URLs verified working (curl -sIL → 302 → 200) on 2026-06-22. The legacy Google-hosted
            // storage.googleapis.com/mediapipe-models/text_generator/gemma/... URLs from v0.2 all 404
            // (Google retired them) and Google now gates ALL Gemma-2 / Gemma-3 .task files behind
            // HuggingFace login (HTTP 401 GatedRepo to unauthenticated clients — see
            // https://huggingface.co/litert-community/Gemma2-2B-IT & Gemma3-1B-IT, both `gated: auto`).
            // The official MediaPipe samples app (examples/llm_inference/android/.../Model.kt) uses
            // those gated URLs with needsAuth=true + a HF login flow; BABYMOMO's ModelDownloadWorker
            // has no HF auth, so gated URLs would fail at runtime.
            //
            // The ONLY non-gated Gemma .task files on HuggingFace as of 2026-06 are the Gemma-4 E2B
            // and E4B builds in litert-community/gemma-4-E*-it-litert-lm (both `gated: False`). These
            // are the URLs below. "E2B"/"E4B" = effective 2B / 4B params via selective parameter
            // activation (same family as Gemma-3n); they use Gemma-4's mixed 2/4/8-bit mobile
            // quantization. The .task extension is retained for LiteRT-LM / MediaPipe LLM Inference
            // API compatibility — see CHANGELOG [Unreleased] → Fixed for the full format caveat.
            // NOTE: md5 intentionally left blank — ModelDownloadWorker skips integrity verification
            // when md5 is empty (verified in ModelDownloadWorker.doWork: `model.md5.takeIf{.isNotBlank()}`).
            // Computing the MD5 would require downloading ~5 GB; the files are not checksum-published
            // by the upstream repo, so we rely on HTTPS transport integrity + Content-Length checks.
            ModelEntity(id = "gemma-2b-it-mediapipe",
                displayName = "Gemma 4 E2B Instruct (MediaPipe .task)",
                runtime = ModelRuntime.MEDIAPIPE_GENAI,
                huggingfaceRepo = "litert-community/gemma-4-E2B-it-litert-lm",
                filename = "gemma-4-E2B-it-web.task",
                downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-web.task",
                sizeBytes = 2_003_697_664L, quantization = "mixed-q4_8", contextLength = 8192,
                minRamMb = 4096, license = "Gemma",
                description = "Gemma 4 E2B Instruct (effective 2B params via selective activation) in MediaPipe/LiteRT-LM .task format. Non-gated HuggingFace mirror (litert-community) — downloads without login. ~2.0 GB on disk, ~1.7 GB peak RSS on a flagship CPU (S26 Ultra benchmark). Uses Gemma-4 mixed 2/4/8-bit mobile quantization. Supports up to 32k context. Requires ~4 GB RAM phone. Officially blessed by Google AI Edge for on-device use."),
            ModelEntity(id = "gemma-1b-it-mediapipe",
                displayName = "Gemma 4 E4B Instruct (MediaPipe .task, higher quality)",
                runtime = ModelRuntime.MEDIAPIPE_GENAI,
                huggingfaceRepo = "litert-community/gemma-4-E4B-it-litert-lm",
                filename = "gemma-4-E4B-it-web.task",
                downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it-web.task",
                sizeBytes = 2_964_324_352L, quantization = "mixed-q4_8", contextLength = 8192,
                minRamMb = 6144, license = "Gemma",
                description = "Gemma 4 E4B Instruct (effective 4B params) — the larger/higher-quality sibling of the E2B build, in MediaPipe/LiteRT-LM .task format. Non-gated HuggingFace mirror. ~2.96 GB on disk, ~3.3 GB peak RSS on a flagship CPU (S26 Ultra benchmark). Uses Gemma-4 mixed 2/4/8-bit mobile quantization. Supports up to 32k context. Requires ~6 GB RAM phone (flagship). NOTE: v0.2 originally shipped this slot as a 'low-RAM int4' option; it has been repurposed to the larger Gemma 4 E4B because no non-gated small Gemma .task exists — low-RAM users should use the GGUF catalog entries (SmolLM2 1.7B, Qwen 2.5 1.5B) with the llama.cpp runtime instead.")
        )
    }
}
