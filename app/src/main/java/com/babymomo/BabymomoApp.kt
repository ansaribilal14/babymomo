package com.babymomo

import android.app.Application
import android.util.Log

/**
 * Minimal Application — no Hilt, no DI, no native libs.
 * This is a safe-mode build to diagnose the crash.
 * If THIS crashes, the problem is in the build/native libs.
 * If this works, the problem is in the Hilt DI graph.
 */
class BabymomoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("BABYMOMO", "Application.onCreate — safe mode")
    }
}
