package com.babymomo.app.core.mcp

import com.babymomo.app.data.db.dao.McpServerDao
import com.babymomo.app.data.db.entities.McpServerEntity
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpClient @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
    }

    suspend fun callTool(serverUrl: String, toolName: String, arguments: Map<String, String>): String {
        return try {
            val response = client.post("$serverUrl/tools/call") {
                contentType(ContentType.Application.Json)
                setBody(buildString {
                    append("{\"name\":\"$toolName\",\"arguments\":{")
                    arguments.entries.forEachIndexed { i, (k, v) ->
                        append("\"$k\":\"$v\"")
                        if (i < arguments.size - 1) append(",")
                    }
                    append("}}")
                })
            }
            response.bodyAsText()
        } catch (e: Exception) {
            "MCP error: ${e.message}"
        }
    }
}

@Singleton
class McpServerRegistry @Inject constructor(
    private val mcpServerDao: McpServerDao
) {
    suspend fun addServer(name: String, url: String, isCurated: Boolean = false): McpServerEntity {
        val server = McpServerEntity(
            id = "mcp_${System.currentTimeMillis()}_${name.hashCode()}",
            name = name,
            url = url,
            isEnabled = true,
            isCurated = isCurated,
            addedAt = System.currentTimeMillis()
        )
        mcpServerDao.insert(server)
        return server
    }

    suspend fun getEnabledServers(): List<McpServerEntity> {
        return mcpServerDao.getEnabled()
    }

    fun getCuratedServers(): List<Pair<String, String>> {
        return listOf(
            "Fetch" to "https://mcp.fetch.sh",
            "DeepWiki" to "https://mcp.deepwiki.com",
            "Context7" to "https://mcp.context7.com"
        )
    }
}
