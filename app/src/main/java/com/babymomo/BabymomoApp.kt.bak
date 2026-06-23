package com.babymomo

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.babymomo.core.common.SettingsRepository
import com.babymomo.core.llm.RemoteLlmProvider
import com.babymomo.core.memory.MemoryMaintenance
import com.babymomo.work.MemoryMaintenanceWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class BabymomoApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var memoryMaintenance: MemoryMaintenance
    @Inject lateinit var settingsRepo: SettingsRepository
    @Inject lateinit var remoteProvider: RemoteLlmProvider
    @Inject lateinit var modelManager: com.babymomo.model.ModelManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Global uncaught exception handler — log crashes to logcat so we can diagnose
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("BABYMOMO", "Uncaught exception on ${thread.name}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }

        File(filesDir, "models").mkdirs()
        appScope.launch {
            // Seed the model catalog so the onboarding screen can find the default model
            runCatching { modelManager.seedCatalogIfEmpty() }
                .onFailure { Log.e("BABYMOMO", "seedCatalogIfEmpty failed", it) }
            // Apply persisted remote LLM settings to the live provider on startup
            runCatching {
                val s = settingsRepo.settings.first()
                if (s.remoteEnabled && s.remoteApiKey.isNotBlank()) {
                    remoteProvider.configure(s.remoteBaseUrl, s.remoteApiKey, s.remoteModel)
                }
            }.onFailure { Log.e("BABYMOMO", "remote settings apply failed", it) }
            runCatching { MemoryMaintenanceWorker.enqueue(this@BabymomoApp) }
                .onFailure { Log.e("BABYMOMO", "MemoryMaintenanceWorker enqueue failed", it) }
            runCatching { memoryMaintenance.runStartupSweep() }
                .onFailure { Log.e("BABYMOMO", "memoryMaintenance sweep failed", it) }
        }
    }
}
