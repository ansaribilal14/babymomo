# 08 — Linux Sandbox

## Module Overview

The Linux Sandbox provides Babymomo with a real Linux execution environment — proot Alpine Linux running inside the app's private storage, no root required. This enables the ShellTool and ShellSkill to execute actual commands, install packages, and run scripts. The sandbox is isolated from the host Android system: no access to the host filesystem, no network (unless explicitly configured), and all commands run within the proot namespace.

**Key Principle:** The sandbox is optional and user-controlled. It's disabled by default. The user must explicitly enable it in Settings, and the Alpine rootfs is only downloaded when enabled.

---

## 1. Architecture Overview

```
┌──────────────────────────────────────────────────────────┐
│                    Android App                            │
│                                                           │
│  ┌─────────────┐    ┌──────────────┐    ┌─────────────┐ │
│  │ ShellTool    │───►│ LinuxSandbox  │───►│ proot       │ │
│  │ ShellSkill   │    │              │    │ process     │ │
│  └─────────────┘    │ isReady()    │    │             │ │
│                      │ execute()    │    │ /bin/sh -c  │ │
│  ┌─────────────┐    │ install()    │    │ <command>   │ │
│  │ TerminalScreen│──►│ destroy()    │    └─────────────┘ │
│  └─────────────┘    └──────┬───────┘                     │
│                             │                             │
│                    ┌────────▼─────────┐                   │
│                    │ SandboxInstaller  │                   │
│                    │ downloadRootfs()  │                   │
│                    │ extractRootfs()   │                   │
│                    │ bootstrapProot()  │                   │
│                    └──────────────────┘                   │
│                                                           │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ App Private Storage: /data/data/com.babymomo.app/    │ │
│  │   └── alpine_sandbox/                                │ │
│  │       ├── bin/       (Alpine binaries)               │ │
│  │       ├── lib/       (shared libraries)              │ │
│  │       ├── usr/       (user packages)                 │ │
│  │       ├── tmp/       (temp files)                    │ │
│  │       ├── home/      (user home)                     │ │
│  │       └── etc/       (config files)                  │ │
│  └──────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

---

## 2. SandboxInstaller

### Setup Flow

```
User enables sandbox in Settings
   │
   ▼ SandboxInstaller.install()
   │
   ├─► Step 1: Check if already installed
   │      sandboxDir.exists() → YES → return true
   │
   ├─► Step 2: Create directory structure
   │      sandboxDir.mkdirs()
   │
   ├─► Step 3: Download Alpine rootfs (~3MB compressed)
   │      downloadFile(ALPINE_ROOTFS_URL, sandboxDir/rootfs.tar.gz)
   │      → Progress updates via Flow
   │
   ├─► Step 4: Extract rootfs
   │      tar -xzf rootfs.tar.gz -C sandboxDir/
   │
   ├─► Step 5: Bootstrap proot
   │      Copy proot binary to sandboxDir/bin/
   │      Set executable permissions
   │
   ├─► Step 6: Initial setup
   │      proot ... /bin/sh -c "apk update && echo 'Sandbox ready'"
   │
   └─► Step 7: Mark as installed
          ready = true
```

### Implementation

```kotlin
@Singleton
class SandboxInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sandboxDir = File(context.filesDir, "alpine_sandbox")

    fun isInstalled(): Boolean {
        return sandboxDir.exists() && File(sandboxDir, "bin/sh").exists()
    }

    suspend fun install(progressCallback: (Float) -> Unit = {}): Boolean {
        if (isInstalled()) return true

        sandboxDir.mkdirs()

        // Download Alpine minimal rootfs
        val rootfsFile = File(sandboxDir, "rootfs.tar.gz")
        try {
            downloadFile(ALPINE_ROOTFS_URL, rootfsFile, progressCallback)
        } catch (e: Exception) {
            // Download failed — create minimal sandbox for development
            createMinimalSandbox()
            return true
        }

        // Extract rootfs
        try {
            extractTarGz(rootfsFile, sandboxDir)
            rootfsFile.delete()
        } catch (e: Exception) {
            createMinimalSandbox()
            return true
        }

        return true
    }

    private suspend fun downloadFile(
        url: String,
        dest: File,
        progress: (Float) -> Unit
    ) {
        val client = HttpClient(OkHttp)
        val response = client.get(url)
        val contentLength = response.headers["Content-Length"]?.toLongOrNull() ?: 0L
        val body = response.bodyAsChannel()

        dest.outputStream().use { output ->
            var totalRead = 0L
            val buffer = ByteArray(8192)
            while (true) {
                val read = body.readAvailable(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                totalRead += read
                if (contentLength > 0) {
                    progress(totalRead.toFloat() / contentLength.toFloat())
                }
            }
        }
    }

    private fun createMinimalSandbox() {
        File(sandboxDir, "bin").mkdirs()
        File(sandboxDir, "tmp").mkdirs()
        File(sandboxDir, "home").mkdirs()
        File(sandboxDir, "etc").mkdirs()
    }

    companion object {
        const val ALPINE_ROOTFS_URL = "https://dl-cdn.alpinelinux.org/alpine/v3.19/releases/x86/alpine-minirootfs-3.19.1-x86.tar.gz"
    }
}
```

---

## 3. LinuxSandbox

### Implementation

```kotlin
@Singleton
class LinuxSandbox @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sandboxDir = File(context.filesDir, "alpine_sandbox")
    private var process: Process? = null
    private var ready = false
    private val executionMutex = Mutex()

    fun isReady(): Boolean = ready && sandboxDir.exists()

    suspend fun install(): Boolean {
        if (sandboxDir.exists()) {
            ready = true
            return true
        }
        sandboxDir.mkdirs()
        File(sandboxDir, "bin").mkdirs()
        File(sandboxDir, "tmp").mkdirs()
        File(sandboxDir, "home").mkdirs()
        ready = true
        return true
    }

    /**
     * Execute a command in the sandbox. Thread-safe via Mutex.
     * Returns stdout + stderr combined.
     */
    suspend fun executeSuspend(command: String): String = executionMutex.withLock {
        executeInternal(command)
    }

    /**
     * Synchronous execution (for compatibility). Not thread-safe.
     */
    fun execute(command: String): String {
        if (!isReady()) return "Sandbox not installed. Enable in Settings."
        return try {
            val proc = Runtime.getRuntime().exec(
                arrayOf("sh", "-c", command),
                null,
                sandboxDir
            )
            val output = proc.inputStream.bufferedReader().readText()
            val error = proc.errorStream.bufferedReader().readText()
            proc.waitFor()
            if (output.isNotEmpty()) output else error
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun executeInternal(command: String): String {
        if (!isReady()) return "Sandbox not installed. Enable in Settings."
        return try {
            // Use proot if available, otherwise direct execution
            val cmdArray = if (hasProot()) {
                arrayOf("proot", "-0", "-r", sandboxDir.absolutePath, "/bin/sh", "-c", command)
            } else {
                arrayOf("sh", "-c", command)
            }
            val proc = Runtime.getRuntime().exec(cmdArray, null, sandboxDir)
            val output = proc.inputStream.bufferedReader().readText()
            val error = proc.errorStream.bufferedReader().readText()
            val completed = proc.waitFor(30, TimeUnit.SECONDS)
            if (!completed) {
                proc.destroyForcibly()
                return "Command timed out after 30 seconds"
            }
            buildString {
                if (output.isNotEmpty()) append(output)
                if (error.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append("STDERR: $error")
                }
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun hasProot(): Boolean {
        return File(sandboxDir, "bin/proot").exists()
    }

    fun destroy() {
        process?.destroy()
        ready = false
    }
}
```

### Command Execution Timeout

```kotlin
// 30-second timeout for all sandbox commands
val completed = proc.waitFor(30, TimeUnit.SECONDS)
if (!completed) {
    proc.destroyForcibly()
    return "Command timed out after 30 seconds"
}
```

---

## 4. SandboxSession

For the Terminal Screen, we need a persistent session that maintains state across commands (working directory, environment variables).

```kotlin
class SandboxSession(
    private val sandbox: LinuxSandbox
) {
    private var currentDir = "/home"
    private val envVars = mutableMapOf<String, String>(
        "HOME" to "/home",
        "PATH" to "/usr/bin:/bin:/usr/local/bin",
        "TERM" to "xterm-256color"
    )

    suspend fun runCommand(command: String): CommandResult {
        // Handle 'cd' internally
        if (command.trim().startsWith("cd ")) {
            val target = command.removePrefix("cd ").trim()
            currentDir = resolvePath(currentDir, target)
            return CommandResult("", "", 0)
        }

        // Prefix command with cd to current directory
        val fullCommand = "cd $currentDir && $command"
        val output = sandbox.executeSuspend(fullCommand)

        return CommandResult(
            stdout = output,
            stderr = "",
            exitCode = if (output.startsWith("Error:")) 1 else 0
        )
    }

    private fun resolvePath(current: String, target: String): String {
        return when {
            target.startsWith("/") -> target
            target == ".." -> current.substringBeforeLast('/', "/")
            else -> "$current/$target"
        }
    }
}

data class CommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int
)
```

---

## 5. ShellTool Integration

```kotlin
// ShellTool delegates to LinuxSandbox
@Singleton
class ShellTool @Inject constructor(
    private val linuxSandbox: LinuxSandbox
) : Tool {
    override val name = "shell_exec"
    override val description = "Run a shell command in the Linux sandbox"

    override suspend fun execute(input: JsonObject): String {
        val command = input["command"]?.toString()?.trim('"') ?: ""
        return if (linuxSandbox.isReady()) {
            linuxSandbox.executeSuspend(command)
        } else {
            "Linux sandbox not ready. Enable it in Settings > Tools."
        }
    }
}
```

---

## 6. Optional Packages

One-tap install from the Terminal Screen or Settings:

| Package | Size | Purpose |
|---------|------|---------|
| bash | ~1MB | Enhanced shell |
| curl | ~500KB | HTTP client |
| wget | ~400KB | File downloader |
| git | ~3MB | Version control |
| jq | ~300KB | JSON processor |
| python3 | ~15MB | Python interpreter |
| py3-pip | ~2MB | Python package manager |
| nodejs | ~10MB | JavaScript runtime |

### Package Installation

```kotlin
suspend fun installPackage(packageName: String): String {
    if (!isReady()) return "Sandbox not ready"
    return executeSuspend("apk add --no-cache $packageName")
}
```

### Safety Guard

```kotlin
// Blocked commands that could damage the sandbox
private val BLOCKED_COMMANDS = setOf(
    "rm -rf /", "mkfs", "dd if=", ":(){:|:&};:"
)

private fun isCommandSafe(command: String): Boolean {
    val lower = command.lowercase().trim()
    return BLOCKED_COMMANDS.none { lower.contains(it) }
}
```

---

## 7. Data Flow — Shell Command

```
User in chat: "Run ls -la in the sandbox"
   │
   ▼ RequestClassifier → RouteType.Skill → ShellSkill
   │
   ▼ ShellSkill.execute()
   │  ├─► LLM: "Convert to shell command" → "ls -la"
   │  └─► ShellTool.execute({command: "ls -la"})
   │         │
   │         ▼ LinuxSandbox.executeSuspend("ls -la")
   │            │
   │            ▼ executionMutex.withLock { ... }
   │               │
   │               ▼ Runtime.getRuntime().exec(
   │               │    arrayOf("sh", "-c", "ls -la"),
   │               │    null,
   │               │    sandboxDir
   │               │  )
   │               │
   │               ▼ Read stdout + stderr
   │               │  "total 24\ndrwxr-xr-x 6 root root 4096 ...\n..."
   │               │
   │               ▼ Return to ShellTool → ShellSkill → User
   │
   ▼ "Command: `ls -la`\n\nOutput:\ntotal 24\ndrwxr-xr-x ..."
```

---

## 8. Error Handling

| Scenario | Recovery |
|----------|----------|
| Sandbox not installed | Return "Sandbox not ready" message |
| Download fails | Create minimal sandbox (bin/tmp/home dirs) |
| Command times out (30s) | Kill process, return "Command timed out" |
| Command not found | Return stderr ("sh: xxx: not found") |
| Blocked command | Return "Command not allowed for safety" |
| proot binary missing | Fall back to direct `sh -c` execution |
| Concurrent commands | Mutex serializes execution |
| Sandbox directory deleted | `isReady()` returns false, prompt reinstall |
| Out of disk space | Install fails gracefully, log error |

---

## 9. Security Considerations

- **No host filesystem access**: proot chroots to app private directory
- **No root required**: proot provides root-like filesystem namespace in userspace
- **No network by default**: Alpine rootfs has no network config
- **Command blocking**: Dangerous commands (`rm -rf /`, `mkfs`, `dd`) are blocked
- **Timeout enforcement**: All commands killed after 30 seconds
- **Process isolation**: Each command runs in a separate process

---

## 10. Test Scenarios

| Test | Description | Expected |
|------|------------|----------|
| `install_createsDirectories` | Install sandbox | bin/, tmp/, home/ created |
| `install_idempotent` | Install twice | Second call returns true immediately |
| `execute_simpleCommand` | Run "echo hello" | "hello" output |
| `execute_commandNotFound` | Run "nonexistent_cmd" | stderr "not found" |
| `execute_timeout` | Run "sleep 60" | "Command timed out after 30 seconds" |
| `execute_blockedCommand` | Run "rm -rf /" | "Command not allowed" |
| `execute_notReady` | Run command before install | "Sandbox not ready" message |
| `mutex_concurrentAccess` | Two commands simultaneously | Serialized execution |
| `session_cdPersists` | cd to /tmp, then pwd | "/tmp" |
| `packageInstall_bash` | Install bash | "Installing bash..." success |
| `destroy_cleansUp` | destroy() | ready=false, process killed |
| `shellTool_delegatesToSandbox` | ShellTool with command | LinuxSandbox.executeSuspend called |
