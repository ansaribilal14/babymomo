package com.babymomo.app.core.llm

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.babymomo.app.core.llm.model.LlmChunk
import com.babymomo.app.core.llm.model.Message
import com.babymomo.app.core.llm.model.Tool
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote LLM provider with SSE streaming support.
 *
 * Supports OpenAI-compatible APIs: OpenAI, NVIDIA NIM, OpenRouter, Groq, etc.
 * API keys are stored in EncryptedSharedPreferences — always optional.
 *
 * The fallback free endpoint ensures Babymomo works on FIRST LAUNCH
 * before the user configures any API keys.
 */
@Singleton
class RemoteLlmProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : LlmProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "babymomo_llm_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    data class ProviderConfig(
        val name: String,
        val baseUrl: String,
        val apiKey: String,
        val model: String
    )

    fun getProviderConfigs(): List<ProviderConfig> {
        val configs = mutableListOf<ProviderConfig>()
        val providers = listOf(
            Triple("openai", "https://api.openai.com", "gpt-4o-mini"),
            Triple("nvidia", "https://integrate.api.nvidia.com", "meta/llama-3.1-8b-instruct"),
            Triple("openrouter", "https://openrouter.ai/api", "openai/gpt-4o-mini")
        )

        for ((key, baseUrl, defaultModel) in providers) {
            val apiKey = encryptedPrefs.getString("${key}_api_key", null)
            val model = encryptedPrefs.getString("${key}_model", defaultModel)
            if (apiKey != null && model != null) {
                configs.add(ProviderConfig(key, baseUrl, apiKey, model))
            }
        }
        return configs
    }

    fun saveProviderConfig(name: String, apiKey: String, model: String) {
        encryptedPrefs.edit()
            .putString("${name}_api_key", apiKey)
            .putString("${name}_model", model)
            .apply()
    }

    override fun streamChat(
        systemPrompt: String,
        messages: List<Message>,
        tools: List<Tool>
    ): Flow<LlmChunk> = flow {
        val configs = getProviderConfigs()
        val config = configs.firstOrNull()
            ?: throw IllegalStateException("No remote API key configured. On-device model or free fallback will be used instead.")

        streamFromProvider(config, systemPrompt, messages).collect { emit(it) }
    }.flowOn(Dispatchers.IO)

    override suspend fun complete(prompt: String): String {
        val configs = getProviderConfigs()
        val config = configs.firstOrNull() ?: return ""

        return try {
            val response = client.post("${config.baseUrl}/v1/chat/completions") {
                header("Authorization", "Bearer ${config.apiKey}")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("model", config.model)
                    put("messages", buildJsonArray {
                        add(buildJsonObject {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                    put("stream", false)
                }.toString())
            }
            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["choices"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")?.jsonObject
                ?.get("content")?.toString()?.trim('"') ?: ""
        } catch (e: Exception) {
            Log.e("RemoteLlm", "Complete failed: ${e.message}")
            ""
        }
    }

    override fun isAvailable(): Boolean = getProviderConfigs().isNotEmpty()

    override fun providerName(): String = "Remote"

    /**
     * Fallback streaming — used when no user API key is configured.
     * Uses Pollinations AI free endpoint — no API key required.
     * App works on FIRST LAUNCH before any setup.
     */
    fun streamWithFallback(
        systemPrompt: String,
        messages: List<Message>,
        tools: List<Tool>
    ): Flow<LlmChunk> = flow {
        // Pollinations AI — completely free, no API key needed
        // OpenAI-compatible API at https://text.pollinations.ai/openai
        val fallbackConfig = ProviderConfig(
            name = "fallback",
            baseUrl = "https://text.pollinations.ai/openai",
            apiKey = "no-key-needed",
            model = "openai"
        )

        try {
            streamFromProvider(fallbackConfig, systemPrompt, messages).collect { emit(it) }
        } catch (e: Exception) {
            Log.e("RemoteLlm", "Fallback stream failed: ${e.message}")
            // If even the fallback fails, emit a helpful message
            emit(LlmChunk.Token(
                "I'm Babymomo — your private AI companion. I'm having trouble connecting right now. " +
                "You can add an API key in Settings (OpenAI, NVIDIA NIM, or OpenRouter) for a more reliable connection. " +
                "An on-device model is also downloading in the background for full offline use."
            ))
            emit(LlmChunk.Done)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Fallback completion — non-streaming version.
     */
    suspend fun completeWithFallback(prompt: String): String {
        return try {
            val response = client.post("https://text.pollinations.ai/openai/chat/completions") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("model", "openai")
                    put("messages", buildJsonArray {
                        add(buildJsonObject { put("role", "user"); put("content", prompt) })
                    })
                    put("stream", false)
                }.toString())
            }
            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["choices"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")?.jsonObject
                ?.get("content")?.toString()?.trim('"') ?: ""
        } catch (e: Exception) {
            Log.e("RemoteLlm", "Fallback complete failed: ${e.message}")
            ""
        }
    }

    /**
     * SSE streaming from any OpenAI-compatible provider.
     * Reads the HTTP response as a stream (not all at once)
     * and emits tokens as they arrive.
     */
    private fun streamFromProvider(
        config: ProviderConfig,
        systemPrompt: String,
        messages: List<Message>
    ): Flow<LlmChunk> = flow {
        val apiMessages = buildList {
            add(mapOf("role" to "system", "content" to systemPrompt))
            messages.forEach { msg ->
                add(mapOf("role" to msg.role, "content" to msg.content))
            }
        }

        val requestJson = buildJsonObject {
            put("model", config.model)
            put("messages", buildJsonArray {
                apiMessages.forEach { msg ->
                    add(buildJsonObject {
                        put("role", msg["role"]!!)
                        put("content", msg["content"]!!)
                    })
                }
            })
            put("stream", true)
        }

        try {
            val channel: ByteReadChannel = client.post("${config.baseUrl}/v1/chat/completions") {
                if (config.apiKey != "no-key-needed") {
                    header("Authorization", "Bearer ${config.apiKey}")
                }
                header("HTTP-Referer", "https://babymomo.app")
                header("X-Title", "Babymomo")
                contentType(ContentType.Application.Json)
                setBody(requestJson.toString())
            }.bodyAsChannel()

            var hasEmittedToken = false

            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: continue
                if (!line.startsWith("data: ")) continue

                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") {
                    if (hasEmittedToken) {
                        emit(LlmChunk.Done)
                    }
                    return@flow
                }

                try {
                    val chunk = json.parseToJsonElement(data).jsonObject
                    val delta = chunk["choices"]?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("delta")?.jsonObject
                    val content = delta?.get("content")?.toString()?.trim('"') ?: ""
                    if (content.isNotEmpty()) {
                        hasEmittedToken = true
                        emit(LlmChunk.Token(content))
                    }
                } catch (_: Exception) { /* skip malformed chunks */ }
            }

            // If we got tokens but no explicit [DONE], close the stream
            if (hasEmittedToken) {
                emit(LlmChunk.Done)
            } else {
                emit(LlmChunk.Error("No response received from ${config.name}."))
            }
        } catch (e: Exception) {
            Log.e("RemoteLlm", "Stream from ${config.name} failed: ${e.message}")
            emit(LlmChunk.Error("Connection to ${config.name} failed: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)
}
