package com.babymomo.app.core.llm

import android.content.Context
import android.util.Log
import com.babymomo.app.core.llm.model.LlmChunk
import com.babymomo.app.core.llm.model.Message
import com.babymomo.app.core.llm.model.Tool
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device LLM provider using Google LiteRT (AI Edge).
 *
 * This IS Babymomo's brain. Models run locally, privately, no internet needed.
 * Gemma 2B and Phi-3 Mini are the default on-device models.
 *
 * Uses reflection to load the LiteRT runtime so the app compiles even if
 * the exact LiteRT API classes change between versions. If LiteRT is not
 * available or the model format is incompatible, signals unavailability
 * so LlmChain falls through to remote providers.
 */
@Singleton
class LocalLlmProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : LlmProvider {

    companion object {
        private const val TAG = "LocalLlm"
    }

    @Volatile
    private var modelLoaded = false

    @Volatile
    private var activeModelPath: String? = null

    @Volatile
    private var activeModelName: String? = null

    // Cached LiteRT session (via reflection)
    @Volatile
    private var litertSession: Any? = null

    fun setActiveModel(modelPath: String?, modelName: String? = null) {
        activeModelPath = modelPath
        activeModelName = modelName
        modelLoaded = modelPath != null
        // Reset cached session when model changes
        litertSession = null
    }

    fun getActiveModelName(): String = activeModelName ?: "None"

    override fun streamChat(
        systemPrompt: String,
        messages: List<Message>,
        tools: List<Tool>
    ): Flow<LlmChunk> = flow {
        if (!modelLoaded || activeModelPath == null) {
            emit(LlmChunk.Error("On-device model not ready yet."))
            return@flow
        }

        val modelFile = File(activeModelPath!!)
        if (!modelFile.exists()) {
            modelLoaded = false
            emit(LlmChunk.Error("Model file not found."))
            return@flow
        }

        try {
            val fullPrompt = buildPrompt(systemPrompt, messages)
            val result = runLiteRTInference(fullPrompt)

            if (result.isNotEmpty()) {
                // Emit the complete response as a single token stream
                // Real streaming from LiteRT would use generateAsync callback
                emit(LlmChunk.Token(result))
                emit(LlmChunk.Done)
            } else {
                Log.d(TAG, "LiteRT produced empty response, falling through to remote")
                emit(LlmChunk.Error("On-device model produced no output."))
            }
        } catch (e: Exception) {
            Log.d(TAG, "On-device inference error: ${e.message}")
            emit(LlmChunk.Error("On-device inference error: ${e.message}"))
        }
    }.flowOn(Dispatchers.Default)

    override suspend fun complete(prompt: String): String {
        if (!modelLoaded || activeModelPath == null) return ""

        return withContext(Dispatchers.Default) {
            try {
                runLiteRTInference(prompt)
            } catch (e: Exception) {
                Log.d(TAG, "Complete failed: ${e.message}")
                ""
            }
        }
    }

    override fun isAvailable(): Boolean = modelLoaded

    override fun providerName(): String = "LiteRT"

    /**
     * Run inference using LiteRT via reflection.
     * This approach avoids hard dependency on specific LiteRT API class names
     * which may change between SDK versions.
     *
     * Tries: com.google.ai.edge.litert.LlmInferenceSession
     * Falls back to: com.google.ai.edge.litert.LlmInference
     */
    private fun runLiteRTInference(prompt: String): String {
        val modelPath = activeModelPath ?: return ""

        try {
            // Try the LlmInferenceSession API (LiteRT 1.0+)
            val sessionClass = Class.forName("com.google.ai.edge.litert.LlmInferenceSession")
            val fileClass = Class.forName("java.io.File")

            // LlmInferenceSession.createFrom(File)
            val createFromMethod = sessionClass.getMethod("createFrom", fileClass)
            val session = createFromMethod.invoke(null, File(modelPath))

            // Cache the session for reuse
            litertSession = session

            // session.generate(String)
            val generateMethod = sessionClass.getMethod("generate", String::class.java)
            val result = generateMethod.invoke(session, prompt)
            return result as? String ?: ""
        } catch (e: ClassNotFoundException) {
            Log.d(TAG, "LlmInferenceSession not found, trying LlmInference...")
        } catch (e: Exception) {
            Log.d(TAG, "LlmInferenceSession failed: ${e.message}")
        }

        try {
            // Try the LlmInference API (older LiteRT / MediaPipe)
            val inferenceClass = Class.forName("com.google.ai.edge.litert.LlmInference")
            val fileClass = Class.forName("java.io.File")

            // LlmInference.createFromFile(File)
            val createFromMethod = inferenceClass.getMethod("createFromFile", fileClass)
            val inference = createFromMethod.invoke(null, File(modelPath))

            // inference.generate(String)
            val generateMethod = inferenceClass.getMethod("generate", String::class.java)
            val result = generateMethod.invoke(inference, prompt)
            return result as? String ?: ""
        } catch (e: ClassNotFoundException) {
            Log.d(TAG, "LlmInference also not found — LiteRT runtime not available")
        } catch (e: Exception) {
            Log.d(TAG, "LlmInference failed: ${e.message}")
        }

        // LiteRT runtime not available or model format incompatible
        Log.d(TAG, "No LiteRT runtime found — on-device inference unavailable")
        return ""
    }

    /**
     * Build a chat prompt from system + messages.
     * LiteRT models expect a specific chat template format.
     * Gemma format: <start_of_turn>user\n...<end_of_turn>\n<start_of_turn>model\n
     */
    private fun buildPrompt(systemPrompt: String, messages: List<Message>): String {
        val sb = StringBuilder()

        // System prompt as model instruction
        sb.append("<start_of_turn>system\n")
        sb.append(systemPrompt)
        sb.append("<end_of_turn>\n")

        // Conversation history
        for (msg in messages) {
            when (msg.role) {
                "user" -> {
                    sb.append("<start_of_turn>user\n")
                    sb.append(msg.content)
                    sb.append("<end_of_turn>\n")
                }
                "assistant" -> {
                    sb.append("<start_of_turn>model\n")
                    sb.append(msg.content)
                    sb.append("<end_of_turn>\n")
                }
                "tool" -> {
                    sb.append("<start_of_turn>tool\n")
                    sb.append(msg.content)
                    sb.append("<end_of_turn>\n")
                }
            }
        }

        // Start model's turn
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }
}
