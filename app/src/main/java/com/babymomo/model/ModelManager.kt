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

    /** The model that auto-downloads on first launch (CoD/PUBG-style onboarding). */
    val firstLaunchModelId: String = "smollm-135m-mediapipe"

    companion object {
        val DEFAULT_CATALOG = listOf(
            // === DEFAULT AUTO-DOWNLOAD MODEL ===
            // SmolLM-135M-Instruct — 159 MB, q8, Apache 2.0, NON-GATED, .task format
            // This is the model that downloads automatically on first launch.
            // Small enough for any phone (even 2GB RAM), fast to download (~30s on 4G),
            // produces real AI responses. Users can upgrade to a bigger model later.
            ModelEntity(
                id = "smollm-135m-mediapipe",
                displayName = "SmolLM 135M (Default — auto-downloads on first launch)",
                runtime = ModelRuntime.MEDIAPIPE_GENAI,
                huggingfaceRepo = "litert-community/SmolLM-135M-Instruct",
                filename = "SmolLM-135M-Instruct_multi-prefill-seq_q8_ekv1280.task",
                downloadUrl = "https://huggingface.co/litert-community/SmolLM-135M-Instruct/resolve/main/SmolLM-135M-Instruct_multi-prefill-seq_q8_ekv1280.task",
                sizeBytes = 166_754_726L,
                quantization = "q8",
                contextLength = 2048,
                minRamMb = 2048,
                license = "Apache 2.0",
                description = "HuggingFace SmolLM 135M Instruct — the default BABYMOMO brain. 159 MB, runs on ANY phone (2GB+ RAM). Auto-downloads on first launch. Apache 2.0 license, no login required."
            ),
            // === UPGRADE OPTIONS (user can download manually from Models tab) ===
            // Qwen2.5-0.5B — 521 MB, q8, Apache 2.0, NON-GATED, .task format
            // Better quality than SmolLM-135M, still fits mid-range phones.
            ModelEntity(
                id = "qwen2.5-0.5b-mediapipe",
                displayName = "Qwen 2.5 0.5B (Upgrade — better quality)",
                runtime = ModelRuntime.MEDIAPIPE_GENAI,
                huggingfaceRepo = "litert-community/Qwen2.5-0.5B-Instruct",
                filename = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
                downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
                sizeBytes = 546_660_344L,
                quantization = "q8",
                contextLength = 4096,
                minRamMb = 3072,
                license = "Apache 2.0",
                description = "Alibaba's Qwen 2.5 0.5B Instruct — a quality upgrade over the default SmolLM. 521 MB, q8 quantized. Better reasoning and instruction-following. Apache 2.0, no login required."
            ),
            // Gemma 4 E2B — 1.9 GB, NON-GATED, .task format
            // Best quality option. For flagship phones (4GB+ RAM).
            ModelEntity(
                id = "gemma-4-e2b-mediapipe",
                displayName = "Gemma 4 E2B (Best quality — flagship phones)",
                runtime = ModelRuntime.MEDIAPIPE_GENAI,
                huggingfaceRepo = "litert-community/gemma-4-E2B-it-litert-lm",
                filename = "gemma-4-E2B-it-web.task",
                downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-web.task",
                sizeBytes = 2_003_697_664L,
                quantization = "mixed-q4_8",
                contextLength = 8192,
                minRamMb = 4096,
                license = "Gemma",
                description = "Google's Gemma 4 E2B Instruct (effective 2B params) — the highest-quality option. 1.9 GB, needs a flagship phone (4GB+ RAM). Non-gated HuggingFace mirror, no login required."
            ),
            // TinyLlama-1.1B — 1.1 GB, Apache 2.0, NON-GATED, .task format
            ModelEntity(
                id = "tinyllama-1.1b-mediapipe",
                displayName = "TinyLlama 1.1B (Mid-range option)",
                runtime = ModelRuntime.MEDIAPIPE_GENAI,
                huggingfaceRepo = "litert-community/TinyLlama-1.1B-Chat-v1.0",
                filename = "TinyLlama-1.1B-Chat-v1.0_multi-prefill-seq_q8_ekv1280.task",
                downloadUrl = "https://huggingface.co/litert-community/TinyLlama-1.1B-Chat-v1.0/resolve/main/TinyLlama-1.1B-Chat-v1.0_multi-prefill-seq_q8_ekv1280.task",
                sizeBytes = 1_095_000_000L,
                quantization = "q8",
                contextLength = 2048,
                minRamMb = 4096,
                license = "Apache 2.0",
                description = "TinyLlama 1.1B Chat — a mid-range option between SmolLM and Gemma. 1.1 GB, Apache 2.0, no login required."
            )
        )
    }
}
