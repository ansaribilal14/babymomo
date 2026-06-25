package com.babymomo.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.babymomo.app.work.HeartbeatWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class BabymomoApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()

    override fun onCreate() {
        super.onCreate()
        scheduleHeartbeat()
    }

    private fun scheduleHeartbeat() {
        val heartbeatWork = PeriodicWorkRequestBuilder<HeartbeatWorker>(
            30, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "babymomo_heartbeat",
            ExistingPeriodicWorkPolicy.KEEP,
            heartbeatWork
        )
    }
}
