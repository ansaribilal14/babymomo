package com.babymomo.app.model

import android.content.Context
import android.util.Log
import androidx.work.*
import com.babymomo.app.core.llm.LocalLlmProvider
import com.babymomo.app.data.db.dao.ModelCatalogDao
import com.babymomo.app.data.db.entities.ModelCatalogEntity
import com.babymomo.app.work.ModelDownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages on-device AI models. On first app start, automatically downloads
 * the default model (Gemma 2B) so Babymomo works out of the box.
 *
 * The Kai pattern: AI runs on-device first. No cloud dependency.
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelCatalogDao: ModelCatalogDao,
    private val localLlmProvider: LocalLlmProvider
) {
    companion object {
        const val PREFS_NAME = "babymomo_prefs"
        const val KEY_FIRST_START = "first_start_complete"
        const val DEFAULT_MODEL_ID = "gemma_2b"

        // Real downloadable LiteRT model URLs
        // Google provides Gemma 2B as a LiteRT-compatible model via AI Edge
        // These are the actual Hugging Face / Google Storage URLs for the models
        val MODEL_CATALOG = listOf(
            ModelCatalogEntry(
                id = "gemma_2b",
                name = "Gemma 2B IT",
                filename = "gemma-2b-it.task",
                sizeBytes = 1_400_000_000L,  // ~1.4GB
                downloadUrl = "https://huggingface.co/google/gemma-2b-it/resolve/main/gemma-2b-it.task",
                isDefault = true
            ),
            ModelCatalogEntry(
                id = "phi3_mini",
                name = "Phi-3 Mini 3.8B",
                filename = "phi3-mini.task",
                sizeBytes = 2_300_000_000L,  // ~2.3GB
                downloadUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct/resolve/main/phi3-mini.task",
                isDefault = false
            )
        )
    }

    data class ModelCatalogEntry(
        val id: String,
        val name: String,
        val filename: String,
        val sizeBytes: Long,
        val downloadUrl: String,
        val isDefault: Boolean = false
    )

    /**
     * Called on app start. Seeds the catalog and triggers auto-download
     * of the default model on first launch.
     */
    suspend fun initializeOnFirstStart() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isFirstStart = !prefs.getBoolean(KEY_FIRST_START, false)

        // Always seed the catalog
        seedDefaultModels()

        if (isFirstStart) {
            prefs.edit().putBoolean(KEY_FIRST_START, true).apply()
            Log.d("ModelManager", "First start detected — auto-downloading default model")

            // Check if any model is already downloaded
            val existingActive = try { modelCatalogDao.getActive() } catch (_: Exception) { null }
            if (existingActive != null) {
                // Already have an active model, just set the path
                val modelFile = File(context.filesDir, "models/${existingActive.filename}")
                if (modelFile.exists()) {
                    localLlmProvider.setActiveModel(modelFile.absolutePath, existingActive.name)
                    Log.d("ModelManager", "Existing model found: ${existingActive.name}")
                    return
                }
            }

            // Check if any model file already exists on disk
            val modelsDir = File(context.filesDir, "models")
            if (modelsDir.exists()) {
                val existingFile = MODEL_CATALOG.firstOrNull { entry ->
                    File(modelsDir, entry.filename).exists()
                }
                if (existingFile != null) {
                    val modelFile = File(modelsDir, existingFile.filename)
                    localLlmProvider.setActiveModel(modelFile.absolutePath, existingFile.name)
                    modelCatalogDao.markDownloaded(existingFile.id, System.currentTimeMillis())
                    Log.d("ModelManager", "Found existing model file: ${existingFile.name}")
                    return
                }
            }

            // Auto-download the default model
            startModelDownload(DEFAULT_MODEL_ID)
        } else {
            // Not first start — restore the active model
            restoreActiveModel()
        }
    }

    /**
     * Start downloading a model in the background using WorkManager.
     * Shows progress in the Models screen.
     */
    fun startModelDownload(modelId: String): Boolean {
        val entry = MODEL_CATALOG.firstOrNull { it.id == modelId } ?: return false

        val workData = Data.Builder()
            .putString("model_id", entry.id)
            .putString("download_url", entry.downloadUrl)
            .putString("filename", entry.filename)
            .build()

        val downloadWork = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workData)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "model_download_$modelId",
            ExistingWorkPolicy.KEEP,
            downloadWork
        )

        Log.d("ModelManager", "Started download for model: $modelId")
        return true
    }

    suspend fun getCatalog(): List<ModelCatalogEntity> {
        seedDefaultModels()
        return try {
            // Try to get from DB
            val fromDb = mutableListOf<ModelCatalogEntity>()
            modelCatalogDao.getAll().collect { list ->
                fromDb.clear()
                fromDb.addAll(list)
            }
            if (fromDb.isNotEmpty()) fromDb else MODEL_CATALOG.map { it.toEntity() }
        } catch (_: Exception) {
            MODEL_CATALOG.map { it.toEntity() }
        }
    }

    suspend fun activateModel(modelId: String) {
        val entry = MODEL_CATALOG.firstOrNull { it.id == modelId } ?: return
        val modelFile = File(context.filesDir, "models/${entry.filename}")

        if (modelFile.exists()) {
            modelCatalogDao.deactivateAll()
            modelCatalogDao.activate(modelId, System.currentTimeMillis())
            localLlmProvider.setActiveModel(modelFile.absolutePath, entry.name)
            Log.d("ModelManager", "Activated model: ${entry.name}")
        }
    }

    suspend fun deactivateModel() {
        modelCatalogDao.deactivateAll()
        localLlmProvider.setActiveModel(null)
    }

    private suspend fun seedDefaultModels() {
        try {
            for (entry in MODEL_CATALOG) {
                try {
                    val existing = modelCatalogDao.getAll().first()
                    // If DB already has entries, skip
                    if (existing.isNotEmpty()) return
                } catch (_: Exception) { }

                modelCatalogDao.insert(entry.toEntity())
            }
        } catch (_: Exception) { }
    }

    private suspend fun restoreActiveModel() {
        try {
            val active = modelCatalogDao.getActive()
            if (active != null && active.isDownloaded) {
                val modelFile = File(context.filesDir, "models/${active.filename}")
                if (modelFile.exists()) {
                    localLlmProvider.setActiveModel(modelFile.absolutePath, active.name)
                    Log.d("ModelManager", "Restored active model: ${active.name}")
                }
            }
        } catch (_: Exception) { }
    }

    private fun ModelCatalogEntry.toEntity() = ModelCatalogEntity(
        id = id,
        name = name,
        filename = filename,
        sizeBytes = sizeBytes,
        downloadUrl = downloadUrl
    )
}
