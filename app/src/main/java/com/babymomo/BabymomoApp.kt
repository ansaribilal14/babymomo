package com.babymomo

import android.app.Application
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
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        File(filesDir, "models").mkdirs()
        appScope.launch {
            // Seed the model catalog so the onboarding screen can find the default model
            runCatching { modelManager.seedCatalogIfEmpty() }
            // Apply persisted remote LLM settings to the live provider on startup
            runCatching {
                val s = settingsRepo.settings.first()
                if (s.remoteEnabled && s.remoteApiKey.isNotBlank()) {
                    remoteProvider.configure(s.remoteBaseUrl, s.remoteApiKey, s.remoteModel)
                }
            }
            runCatching { MemoryMaintenanceWorker.enqueue(this@BabymomoApp) }
            runCatching { memoryMaintenance.runStartupSweep() }
        }
    }
}
