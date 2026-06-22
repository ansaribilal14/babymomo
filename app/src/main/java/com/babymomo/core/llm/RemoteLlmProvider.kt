package com.babymomo.core.llm

import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.buffer
import okio.source
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = true)
data class ChatCompletionRequest(
    val model: String,
    val messages: List<Map<String, String>>,
    val temperature: Float = 0.7f,
    val top_p: Float = 0.95f,
    val max_tokens: Int = 1024,
    val stream: Boolean = false,
    val stop: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class ChatCompletionResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null
) {
    @JsonClass(generateAdapter = true) data class Choice(val message: Msg? = null, val finish_reason: String? = null)
    @JsonClass(generateAdapter = true) data class Msg(val role: String? = null, val content: String? = null)
    @JsonClass(generateAdapter = true) data class Usage(val prompt_tokens: Int = 0, val completion_tokens: Int = 0, val total_tokens: Int = 0)
}

@Singleton
class RemoteLlmProvider @Inject constructor(private val client: OkHttpClient) : LlmProvider {
    override val name: String = "remote"

    @Volatile private var baseUrl: String = "https://api.openai.com/v1"
    @Volatile private var apiKey: String = ""
    @Volatile private var modelName: String = "gpt-4o-mini"

    fun configure(baseUrl: String, apiKey: String, modelName: String) {
        this.baseUrl = baseUrl.trimEnd('/'); this.apiKey = apiKey; this.modelName = modelName
    }

    override suspend fun isAvailable(): Boolean = apiKey.isNotBlank()
    override suspend fun status(): String = if (apiKey.isBlank()) "Remote provider not configured" else "Remote: $modelName @ ${baseUrl.take(40)}"

    override suspend fun complete(messages: List<LlmMessage>, config: LlmGenerationConfig): Result<LlmResponse> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalStateException("Remote not configured"))
        val t0 = System.currentTimeMillis()
        val body = moshi.adapter(ChatCompletionRequest::class.java).toJson(
            ChatCompletionRequest(model = modelName, messages = messages.map { mapOf("role" to it.role.name.lowercase(), "content" to it.content) },
                temperature = config.temperature, top_p = config.topP, max_tokens = config.maxTokens, stream = false, stop = config.stopSequences.ifEmpty { null })
        )
        val req = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}: ${resp.body?.string()?.take(200)}")
                val parsed = moshi.adapter(ChatCompletionResponse::class.java).fromJson(resp.body!!.source().buffer().readUtf8()) ?: error("Empty body")
                LlmResponse(content = parsed.choices.firstOrNull()?.message?.content.orEmpty(),
                    tokensIn = parsed.usage?.prompt_tokens ?: 0, tokensOut = parsed.usage?.completion_tokens ?: 0,
                    latencyMs = System.currentTimeMillis() - t0, providerName = name, modelName = modelName)
            }
        }
    }

    override fun streamComplete(messages: List<LlmMessage>, config: LlmGenerationConfig): Flow<String> = flow {
        if (apiKey.isBlank()) { emit("[remote provider not configured]"); return@flow }
        val body = moshi.adapter(ChatCompletionRequest::class.java).toJson(
            ChatCompletionRequest(model = modelName, messages = messages.map { mapOf("role" to it.role.name.lowercase(), "content" to it.content) },
                temperature = config.temperature, top_p = config.topP, max_tokens = config.maxTokens, stream = true, stop = config.stopSequences.ifEmpty { null })
        )
        val req = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp: Response ->
            if (!resp.isSuccessful) { emit("[HTTP ${resp.code}]"); return@flow }
            val source = resp.body!!.source()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val payload = line.removePrefix("data: ").trim()
                if (payload == "[DONE]") break
                val parsed = runCatching { moshi.adapter(ChatCompletionResponse::class.java).fromJson(payload) }.getOrNull() ?: continue
                val delta = parsed.choices.firstOrNull()?.message?.content
                if (!delta.isNullOrEmpty()) emit(delta)
            }
        }
    }.flowOn(Dispatchers.IO)

    companion object { private val moshi = com.squareup.moshi.Moshi.Builder().build() }
}
