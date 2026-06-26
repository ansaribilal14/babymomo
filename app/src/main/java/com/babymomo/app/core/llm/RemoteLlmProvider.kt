package com.babymomo.app.core.llm

import android.content.Context
import android.content.SharedPreferences
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
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<Map<String, String>>,
    val stream: Boolean = true,
    val tools: List<JsonObject>? = null
)

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
            ?: throw IllegalStateException("No remote API key configured. This is optional — use on-device models for full privacy.")

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
            val response = client.post("${config.baseUrl}/v1/chat/completions") {
                header("Authorization", "Bearer ${config.apiKey}")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                setBody(requestJson.toString())
            }

            val responseBody = response.bodyAsText()
            // Parse SSE lines
            responseBody.split("\n").filter { it.startsWith("data: ") }.forEach { line ->
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") {
                    emit(LlmChunk.Done)
                    return@forEach
                }
                try {
                    val chunk = json.parseToJsonElement(data).jsonObject
                    val delta = chunk["choices"]?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("delta")?.jsonObject
                    val content = delta?.get("content")?.toString()?.trim('"') ?: ""
                    if (content.isNotEmpty()) {
                        emit(LlmChunk.Token(content))
                    }
                } catch (_: Exception) { /* skip malformed chunks */ }
            }
            emit(LlmChunk.Done)
        } catch (e: Exception) {
            emit(LlmChunk.Error("Remote LLM error: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun complete(prompt: String): String {
        val configs = getProviderConfigs()
        val config = configs.firstOrNull() ?: return "No remote LLM provider configured"

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
                ?.get("content")?.toString()?.trim('"') ?: "No response"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    override fun isAvailable(): Boolean = getProviderConfigs().isNotEmpty()

    override fun providerName(): String = "Remote"
}


