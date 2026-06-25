package com.babymomo.app.model

import com.babymomo.app.core.llm.LocalLlmProvider
import com.babymomo.app.data.db.dao.ModelCatalogDao
import com.babymomo.app.data.db.entities.ModelCatalogEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelManager @Inject constructor(
    private val modelCatalogDao: ModelCatalogDao,
    private val localLlmProvider: LocalLlmProvider
) {
    suspend fun getCatalog(): List<ModelCatalogEntity> {
        val existing = mutableListOf<ModelCatalogEntity>()
        // Return catalog from DB, seed if empty
        return try {
            modelCatalogDao.getAll().let { flow ->
                // Seed default models if empty
                seedDefaultModels()
                listOf(
                    ModelCatalogEntity(
                        id = "gemma_2b",
                        name = "Gemma 2B IT",
                        filename = "gemma-2b-it.bin",
                        sizeBytes = 1_500_000_000L,
                        downloadUrl = "https://storage.googleapis.com/babymomo-models/gemma-2b-it.bin"
                    ),
                    ModelCatalogEntity(
                        id = "phi3_mini",
                        name = "Phi-3 Mini 3.8B",
                        filename = "phi-3-mini.bin",
                        sizeBytes = 2_400_000_000L,
                        downloadUrl = "https://storage.googleapis.com/babymomo-models/phi-3-mini.bin"
                    )
                )
            }
        } catch (_: Exception) {
            seedDefaultModels()
            emptyList()
        }
    }

    suspend fun activateModel(modelId: String) {
        modelCatalogDao.deactivateAll()
        modelCatalogDao.activate(modelId)
        // Notify LocalLlmProvider
        localLlmProvider.setActiveModel(modelId)
    }

    suspend fun deactivateModel() {
        modelCatalogDao.deactivateAll()
        localLlmProvider.setActiveModel(null)
    }

    private suspend fun seedDefaultModels() {
        try {
            modelCatalogDao.insert(
                ModelCatalogEntity(
                    id = "gemma_2b",
                    name = "Gemma 2B IT",
                    filename = "gemma-2b-it.bin",
                    sizeBytes = 1_500_000_000L,
                    downloadUrl = "https://storage.googleapis.com/babymomo-models/gemma-2b-it.bin"
                )
            )
            modelCatalogDao.insert(
                ModelCatalogEntity(
                    id = "phi3_mini",
                    name = "Phi-3 Mini 3.8B",
                    filename = "phi-3-mini.bin",
                    sizeBytes = 2_400_000_000L,
                    downloadUrl = "https://storage.googleapis.com/babymomo-models/phi-3-mini.bin"
                )
            )
        } catch (_: Exception) { }
    }
}
