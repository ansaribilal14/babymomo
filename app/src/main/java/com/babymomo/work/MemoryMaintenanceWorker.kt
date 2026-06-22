package com.babymomo.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.babymomo.core.memory.MemoryMaintenance
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class MemoryMaintenanceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val maintenance: MemoryMaintenance
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        maintenance.runPeriodicSweep(); Result.success()
    }.getOrElse { Result.retry() }

    companion object {
        private const val UNIQUE_NAME = "babymomo.memory.maintenance"
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<MemoryMaintenanceWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
