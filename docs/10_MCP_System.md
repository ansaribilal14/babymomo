# 10 — MCP (Model Context Protocol) System

## Module Overview

The MCP System allows Babymomo to connect to external MCP (Model Context Protocol) servers, discover their tools, and use those tools alongside built-in tools in the LLM's function-calling loop. MCP uses a Streamable HTTP transport pattern. Users can add custom server URLs or choose from a curated list of pre-configured servers (Fetch, DeepWiki, Context7). Each connected server's tools are adapted into `McpTool` instances and registered in `ToolRegistry`.

**Key Principle:** MCP extends Babymomo's capabilities without modifying the app. Any MCP-compatible server adds new tools that the LLM can use automatically.

---

## 1. Architecture Overview

```
┌──────────────────────────────────────────────────────────────────┐
│                        Babymomo App                              │
│                                                                  │
│  ┌──────────────┐     ┌─────────────────┐    ┌───────────────┐  │
│  │ ToolRegistry  │◄────│ McpServerRegistry│    │ McpScreen     │  │
│  │              │     │                 │    │ (UI)          │  │
│  │ - web_search │     │ - getEnabled()  │    │ - Add server  │  │
│  │ - notify     │     │ - addServer()   │    │ - Toggle on/off│ │
│  │ - calendar   │     │ - getCurated()  │    │ - View tools  │  │
│  │ - shell      │     └────────┬────────┘    └───────────────┘  │
│  │ - memory     │              │                                 │
│  │ - mcp_fetch  │◄─────────────┤                                 │
│  │ - mcp_wiki   │              │                                 │
│  └──────┬───────┘     ┌────────▼────────┐                        │
│         │             │ McpClient        │                        │
│         │             │                 │                        │
│         │             │ - callTool()    │                        │
│         │             │ - listTools()   │                        │
│         │             │ - discover()    │                        │
│         │             └────────┬────────┘                        │
│         │                      │                                 │
└─────────┼──────────────────────┼─────────────────────────────────┘
          │                      │
          │              HTTP POST /tools/call
          │              HTTP GET  /tools/list
          │                      │
   ┌──────▼──────┐    ┌─────────▼─────────┐
   │  LLM Tool   │    │  MCP Servers      │
   │  Loop       │    │                   │
   │             │    │  Fetch MCP        │
   └─────────────┘    │  DeepWiki MCP     │
                      │  Context7 MCP     │
                      │  Custom servers   │
                      └───────────────────┘
```

---

## 2. McpClient

### Implementation

```kotlin
@Singleton
class McpClient @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        timeout {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }
    }

    /**
     * List all tools available on an MCP server.
     */
    suspend fun listTools(serverUrl: String): List<McpToolInfo> {
        return try {
            val response = client.get("$serverUrl/tools/list")
            val body = response.bodyAsText()
            parseToolList(body)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Call a tool on an MCP server.
     */
    suspend fun callTool(
        serverUrl: String,
        toolName: String,
        arguments: Map<String, String>
    ): String {
        return try {
            val response = client.post("$serverUrl/tools/call") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("name", toolName)
                    put("arguments", buildJsonObject {
                        arguments.forEach { (k, v) -> put(k, v) }
                    })
                })
            }
            response.bodyAsText()
        } catch (e: Exception) {
            "MCP error: ${e.message}"
        }
    }

    /**
     * Discover and return all tools from a server.
     */
    suspend fun discover(serverUrl: String): List<McpToolInfo> {
        return listTools(serverUrl)
    }

    private fun parseToolList(response: String): List<McpToolInfo> {
        return try {
            val parsed = json.parseToJsonElement(response).jsonObject
            val tools = parsed["tools"]?.jsonArray ?: return emptyList()
            tools.mapNotNull { element ->
                val obj = element.jsonObject
                val name = obj["name"]?.toString()?.trim('"') ?: return@mapNotNull null
                val description = obj["description"]?.toString()?.trim('"') ?: ""
                val parameters = obj["inputSchema"]?.toString() ?: "{}"
                McpToolInfo(name, description, parameters)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

data class McpToolInfo(
    val name: String,
    val description: String,
    val parameters: String  // JSON Schema string
)
```

---

## 3. McpServerRegistry

### Implementation

```kotlin
@Singleton
class McpServerRegistry @Inject constructor(
    private val mcpServerDao: McpServerDao,
    private val mcpClient: McpClient
) {
    /**
     * Add a new MCP server.
     */
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

    /**
     * Get all enabled MCP servers.
     */
    suspend fun getEnabledServers(): List<McpServerEntity> {
        return mcpServerDao.getEnabled()
    }

    /**
     * Get curated (pre-configured) server list.
     */
    fun getCuratedServers(): List<Pair<String, String>> {
        return listOf(
            "Fetch" to "https://mcp.fetch.sh",
            "DeepWiki" to "https://mcp.deepwiki.com",
            "Context7" to "https://mcp.context7.com"
        )
    }

    /**
     * Toggle a server on/off.
     */
    suspend fun toggleServer(id: String, enabled: Boolean) {
        mcpServerDao.setEnabled(id, enabled)
    }

    /**
     * Remove a server.
     */
    suspend fun removeServer(id: String) {
        mcpServerDao.delete(id)
    }

    /**
     * Discover all tools from all enabled servers.
     * Used to populate ToolRegistry with MCP tools.
     */
    suspend fun discoverAllTools(): List<McpTool> {
        val servers = getEnabledServers()
        val tools = mutableListOf<McpTool>()

        for (server in servers) {
            val toolInfos = mcpClient.listTools(server.url)
            for (info in toolInfos) {
                tools.add(McpTool(
                    name = "mcp_${server.name.lowercase()}_${info.name}",
                    description = info.description,
                    parameters = parseParameters(info.parameters),
                    mcpClient = mcpClient,
                    serverUrl = server.url,
                    originalName = info.name
                ))
            }
        }

        return tools
    }

    private fun parseParameters(schema: String): JsonObject {
        return try {
            Json.parseToJsonElement(schema).jsonObject
        } catch (_: Exception) {
            buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {})
            }
        }
    }
}
```

---

## 4. McpTool — Adapting MCP Tools to ToolRegistry

```kotlin
class McpTool(
    override val name: String,
    override val description: String,
    override val parameters: JsonObject,
    private val mcpClient: McpClient,
    private val serverUrl: String,
    private val originalName: String  // The tool name on the MCP server
) : Tool {

    override suspend fun execute(input: JsonObject): String {
        val arguments = input.mapValues { (_, value) ->
            value.toString().trim('"')
        }
        return mcpClient.callTool(serverUrl, originalName, arguments)
    }
}
```

### Tool Name Convention

MCP tools are prefixed with `mcp_{servername}_` to avoid collisions with built-in tools:

| MCP Server | Original Tool | Registered Name |
|-----------|--------------|-----------------|
| Fetch | `fetch` | `mcp_fetch_fetch` |
| DeepWiki | `read_wiki_structure` | `mcp_deepwiki_read_wiki_structure` |
| Context7 | `search` | `mcp_context7_search` |

---

## 5. Curated Servers

### Fetch MCP

| Property | Value |
|----------|-------|
| Name | Fetch |
| URL | `https://mcp.fetch.sh` |
| Description | Fetch web page content and extract text |
| Tools | `fetch(url: string)` → page content |

### DeepWiki MCP

| Property | Value |
|----------|-------|
| Name | DeepWiki |
| URL | `https://mcp.deepwiki.com` |
| Description | Read Wikipedia-style wiki structures |
| Tools | `read_wiki_structure(topic: string)`, `get_article(title: string)` |

### Context7 MCP

| Property | Value |
|----------|-------|
| Name | Context7 |
| URL | `https://mcp.context7.com` |
| Description | Search and retrieve contextual information |
| Tools | `search(query: string)`, `get_context(id: string)` |

---

## 6. MCP Screen — UI Specification

```
┌─────────────────────────────────────────┐
│  MCP Servers                            │
│                                         │
│  ── Curated ──                          │
│  ┌─────────────────────────────────────┐│
│  │ ✅ Fetch     https://mcp.fetch.sh   ││
│  │    2 tools available                ││
│  └─────────────────────────────────────┘│
│  ┌─────────────────────────────────────┐│
│  │ ☐ DeepWiki   https://mcp.deepwiki.com│
│  │    Not connected                    ││
│  └─────────────────────────────────────┘│
│  ┌─────────────────────────────────────┐│
│  │ ☐ Context7   https://mcp.context7.com│
│  │    Not connected                    ││
│  └─────────────────────────────────────┘│
│                                         │
│  ── Custom ──                           │
│  ┌─────────────────────────────────────┐│
│  │ ✅ My Server  https://my-mcp.example││
│  │    3 tools available                ││
│  └─────────────────────────────────────┘│
│                                         │
│  [+ Add Custom Server]                  │
│                                         │
│  ┌─────────────────────────────────────┐│
│  │ Add MCP Server                      ││
│  │ Name:  [________________]           ││
│  │ URL:   [________________]           ││
│  │      [Test Connection] [Add]        ││
│  └─────────────────────────────────────┘│
└─────────────────────────────────────────┘
```

---

## 7. Data Flow — MCP Tool Execution

```
User: "Fetch the content of example.com"
   │
   ▼ LLM generates ToolCall(name="mcp_fetch_fetch", input={url: "https://example.com"})
   │
   ▼ ToolRegistry.execute("mcp_fetch_fetch", {url: "https://example.com"})
   │
   ▼ McpTool.execute(input)
   │  → mcpClient.callTool("https://mcp.fetch.sh", "fetch", {url: "https://example.com"})
   │
   ▼ HTTP POST https://mcp.fetch.sh/tools/call
   │  Body: {"name": "fetch", "arguments": {"url": "https://example.com"}}
   │
   ▼ Response: {"content": "Example Domain. This domain is for use..."}
   │
   ▼ Return to LLM → LLM continues response
   │
   ▼ "I fetched example.com for you. It says: Example Domain..."
```

---

## 8. MCP Tool Discovery Flow

```
App Start / Settings Changed
   │
   ▼ McpServerRegistry.discoverAllTools()
   │
   ▼ For each enabled server:
   │  ├─► McpClient.listTools(serverUrl)
   │  │      GET https://server/tools/list
   │  │      → [McpToolInfo("fetch", "Fetch a URL", schema), ...]
   │  │
   │  └─► Create McpTool for each discovered tool
   │         name = "mcp_{servername}_{toolname}"
   │
   ▼ Register all McpTools in ToolRegistry
   │
   ▼ LLM receives updated tool list on next call
```

---

## 9. Error Handling

| Scenario | Recovery |
|----------|----------|
| MCP server unreachable | Skip server, tools not registered, show "Not connected" in UI |
| Tool call times out (30s) | Return "MCP error: timeout" to LLM |
| Tool call returns error | Return error message to LLM, LLM can retry or respond |
| Tool list parsing fails | Return empty tool list for that server |
| Server URL invalid | Reject in UI validation, don't add |
| Duplicate server URL | Reject with "Server already added" |
| All MCP servers down | Only built-in tools available, no crash |
| MCP tool name collision | Prefix with server name ensures uniqueness |

---

## 10. Test Scenarios

| Test | Description | Expected |
|------|------------|----------|
| `addServer_curated` | Add Fetch server | Server persisted, tools discoverable |
| `addServer_custom` | Add custom URL | Server persisted |
| `addServer_duplicate` | Add same URL twice | Rejected |
| `toggleServer_off` | Disable enabled server | Tools removed from registry |
| `toggleServer_on` | Enable disabled server | Tools re-registered |
| `removeServer` | Delete a server | Removed from DB and registry |
| `listTools_success` | List tools from Fetch server | Returns tool info list |
| `listTools_unreachable` | Server down | Returns empty list |
| `callTool_success` | Call fetch tool | Returns page content |
| `callTool_timeout` | Server slow response | "MCP error: timeout" |
| `discoverAllTools` | 2 enabled servers | All tools from both registered |
| `mcpTool_namePrefix` | Check tool name | "mcp_fetch_fetch" format |
| `mcpTool_delegatesToClient` | Execute MCP tool | McpClient.callTool called |
| `curatedServers_list` | getCuratedServers() | 3 curated entries |
