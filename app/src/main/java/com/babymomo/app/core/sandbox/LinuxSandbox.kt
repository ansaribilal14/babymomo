package com.babymomo.app.core.sandbox

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LinuxSandbox @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sandboxDir = File(context.filesDir, "alpine_sandbox")
    private var process: Process? = null
    private var ready = false

    fun isReady(): Boolean = ready && sandboxDir.exists()

    suspend fun install(): Boolean {
        if (sandboxDir.exists()) {
            ready = true
            return true
        }
        sandboxDir.mkdirs()
        // In production, download Alpine rootfs from CDN
        // For now, create minimal sandbox structure
        File(sandboxDir, "bin").mkdirs()
        File(sandboxDir, "tmp").mkdirs()
        File(sandboxDir, "home").mkdirs()
        ready = true
        return true
    }

    fun execute(command: String): String {
        if (!isReady()) return "Sandbox not installed. Enable in Settings."
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf("sh", "-c", command),
                null,
                sandboxDir
            )
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (output.isNotEmpty()) output else error
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun destroy() {
        process?.destroy()
        ready = false
    }
}
