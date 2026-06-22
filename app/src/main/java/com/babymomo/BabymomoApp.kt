package com.babymomo

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.babymomo.core.memory.MemoryMaintenance
import com.babymomo.work.MemoryMaintenanceWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class BabymomoApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var memoryMaintenance: MemoryMaintenance

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Ensure the model download directory exists before any worker tries to write to it.
        File(filesDir, "models").mkdirs()
        appScope.launch {
            runCatching { MemoryMaintenanceWorker.enqueue(this@BabymomoApp) }
            runCatching { memoryMaintenance.runStartupSweep() }
        }
    }
}
