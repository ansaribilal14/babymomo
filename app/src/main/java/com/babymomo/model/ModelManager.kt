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
                description = "HuggingFace's SmolLM2 — ultra-light, works on 4 GB RAM phones.")
        )
    }
}
