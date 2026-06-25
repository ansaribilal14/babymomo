# 14 — Security & Privacy

## Module Overview

Babymomo is designed with a **privacy-first** philosophy. All user data is encrypted at rest using SQLCipher and EncryptedSharedPreferences. No data is ever sent to any server without the user's explicit opt-in. There is no analytics, no telemetry, no crash reporting, no ads, and no user accounts. The app operates fully offline by default, with internet access disabled until the user explicitly enables it.

**Key Principle:** The user's data stays on the user's device. Always.

---

## 1. Threat Model

| Threat | Mitigation |
|--------|-----------|
| Database extraction from stolen device | SQLCipher AES-256 encryption |
| API key extraction from device | EncryptedSharedPreferences (Android Keystore) |
| Network interception of LLM calls | HTTPS/TLS 1.3 for all remote providers |
| On-device data access by other apps | Android sandbox (app-private storage) |
| Physical access to device | SQLCipher passphrase not stored in plaintext |
| Malicious MCP server | User opt-in, URL validation, network security config |
| Shell command injection | Sandbox isolation, command blocking, timeout |
| Memory data leakage through backups | Encrypted backup, user must provide passphrase |

---

## 2. SQLCipher — Database Encryption

### Configuration

```kotlin
@Module
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        val passphrase = getOrCreatePassphrase(context)
        val factory = SupportFactory(passphrase)

        return Room.databaseBuilder(context, AppDatabase::class.java, "babymomo.db")
            .openHelperFactory(factory)
            .build()
    }

    private fun getOrCreatePassphrase(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            "db_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        var passphrase = prefs.getString("db_passphrase", null)
        if (passphrase == null) {
            passphrase = generateRandomPassphrase()
            prefs.edit().putString("db_passphrase", passphrase).apply()
        }

        return passphrase.toByteArray(Charsets.UTF_8)
    }

    private fun generateRandomPassphrase(): String {
        val bytes = ByteArray(32)
        SecureRandom.getInstanceStrong().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
```

### Encryption Specifications

| Property | Value |
|----------|-------|
| Algorithm | AES-256-CBC |
| Key derivation | PBKDF2-HMAC-SHA512, 256,000 iterations |
| Page size | 4096 bytes |
| KDF iterations | 256,000 (SQLCipher default) |
| Plaintext header size | 0 bytes |

### Passphrase Storage Chain

```
Random 256-bit passphrase
   │
   ▼ Stored in EncryptedSharedPreferences
   │  (encrypted with AES256-GCM using Android Keystore master key)
   │
   ▼ Android Keystore
   │  (hardware-backed on devices with TEE/StrongBox)
   │
   ▼ Device-specific, non-exportable
```

---

## 3. EncryptedSharedPreferences — API Keys

### What's Stored

| Key | Description | Encrypted |
|-----|------------|-----------|
| `openai_api_key` | OpenAI API key | Yes |
| `nim_api_key` | NVIDIA NIM API key | Yes |
| `openrouter_api_key` | OpenRouter API key | Yes |
| `openai_model` | Model string (e.g., "gpt-4o-mini") | No (not sensitive) |
| `nim_model` | NVIDIA model string | No |
| `openrouter_model` | OpenRouter model string | No |
| `openai_base_url` | Base URL | No |
| `nim_base_url` | Base URL | No |
| `openrouter_base_url` | Base URL | No |
| `db_passphrase` | SQLCipher passphrase | Yes |
| `web_search_api_key` | Web search API key (v1.1) | Yes |

### Implementation

```kotlin
@Singleton
class SecureKeyStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "babymomo_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getApiKey(provider: String): String? {
        return prefs.getString("${provider}_api_key", null)
    }

    fun setApiKey(provider: String, key: String) {
        prefs.edit().putString("${provider}_api_key", key).apply()
    }

    fun removeApiKey(provider: String) {
        prefs.edit().remove("${provider}_api_key").apply()
    }

    fun getModel(provider: String): String {
        return prefs.getString("${provider}_model", "") ?: ""
    }

    fun setModel(provider: String, model: String) {
        prefs.edit().putString("${provider}_model", model).apply()
    }
}
```

---

## 4. Network Security

### Default: Internet Off

```kotlin
// In SettingsDao / EncryptedSharedPreferences
// Default value for "internet_enabled" is "false"
// When internet is off:
//   - RemoteLlmProvider.isAvailable() returns false
//   - MCP tools are disabled
//   - WebSearchTool returns placeholder
//   - No network requests are made
```

### Network Security Config

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <!-- Default: no cleartext traffic -->
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>

    <!-- Known providers with certificate pinning (v1.1) -->
    <domain-config>
        <domain includeSubdomains="true">api.openai.com</domain>
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
        <!-- Pin hashes added in v1.1 -->
    </domain-config>

    <domain-config>
        <domain includeSubdomains="true">integrate.api.nvidia.com</domain>
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </domain-config>

    <domain-config>
        <domain includeSubdomains="true">openrouter.ai</domain>
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </domain-config>
</network-security-config>
```

### HTTP Client Configuration

```kotlin
// Ktor client with security defaults
val client = HttpClient(OkHttp) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    timeout {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 10_000
    }
    // Follow redirects but limit to prevent redirect loops
    followRedirects = true
    maxRedirects = 3
}

// OkHttp engine with TLS settings
val engine = OkHttp.create {
    config {
        // TLS 1.3 only
        connectionSpecs(listOf(
            ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_3)
                .cipherSuites(
                    CipherSuite.TLS_AES_128_GCM_SHA256,
                    CipherSuite.TLS_AES_256_GCM_SHA384,
                    CipherSuite.TLS_CHACHA20_POLY1305_SHA256
                )
                .build()
        ))
    }
}
```

---

## 5. Linux Sandbox Security

### Isolation Guarantees

| Guarantee | Mechanism |
|-----------|-----------|
| No host filesystem access | proot chroot to app private directory |
| No root access | proot runs in userspace, no real root |
| No network access (default) | Alpine rootfs has no network config |
| Command timeout | 30-second kill timer |
| Command blocking | Dangerous commands filtered |

### Blocked Commands

```kotlin
private val BLOCKED_COMMANDS = setOf(
    "rm -rf /",
    "rm -rf /*",
    "mkfs",
    "dd if=",
    ":(){:|:&};:",       // fork bomb
    "chmod 777 /",
    "chown root"
)
```

### Sandbox Directory Isolation

```
Android Sandbox Boundary
   │
   ▼ App Private Storage: /data/data/com.babymomo.app/
      │
      ▼ alpine_sandbox/  (proot chroot root)
         ├── bin/        (Alpine binaries only)
         ├── lib/        (Alpine libraries only)
         ├── usr/        (User-installed packages)
         ├── tmp/        (Temporary files)
         ├── home/       (User home directory)
         └── etc/        (Alpine config)

   No access to:
   - /sdcard/
   - /system/
   - /data/data/ (other apps)
   - Any path outside alpine_sandbox/
```

---

## 6. Data Retention & Deletion

### User Controls

| Control | Mechanism |
|---------|-----------|
| Delete single memory | Swipe to delete in MemoryScreen |
| Delete conversation | Overflow menu → Delete conversation |
| Delete project | Project detail → Delete |
| Clear all data | Settings → Clear All Data (reinstalls database) |
| Export data | Settings → Export JSON (user owns their data) |

### Data Never Collected

| Data Type | Policy |
|-----------|--------|
| Analytics | **Never**. No Firebase, no GA, no Mixpanel |
| Crash reports | **Never**. No Crashlytics, no Sentry |
| Telemetry | **Never**. No usage tracking |
| Device info | **Never**. No device fingerprinting |
| User accounts | **Never**. No sign-up, no auth |
| Ad IDs | **Never**. No advertising SDK |
| Location | **Never**. No location permissions |

---

## 7. Permissions

### Required Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<!-- Optional — only used when user enables internet -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<!-- For WorkManager rescheduling after reboot -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<!-- For heartbeat and tool notifications -->
<uses-permission android:name="android.permission.READ_CALENDAR" />
<!-- For CalendarTool — requested at runtime -->
<uses-permission android:name="android.permission.WRITE_CALENDAR" />
<!-- For CalendarCreateTool — requested at runtime -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<!-- For potential foreground services -->
<uses-permission android:name="android.permission.VIBRATE" />
<!-- For notification vibration -->
```

### Permission Request Strategy

| Permission | When Requested | Rationale |
|-----------|---------------|-----------|
| INTERNET | Never (manifest, always declared) | Used only when user enables internet |
| POST_NOTIFICATIONS | First heartbeat or tool use | Required for notifications |
| READ_CALENDAR | First calendar tool use | Required to read events |
| WRITE_CALENDAR | First calendar create use | Required to create events |
| RECEIVED_BOOT_COMPLETED | Never (manifest) | Background, no user action needed |

### Runtime Permission Flow

```kotlin
// In CalendarTool
override suspend fun execute(input: JsonObject): String {
    return try {
        // ... calendar query ...
    } catch (e: SecurityException) {
        "Calendar permission not granted. Please enable it in Settings."
    }
}
```

No permission is requested proactively. All runtime permissions are requested at the point of first use, with clear rationale shown to the user.

---

## 8. Backup Security

### Export

```kotlin
// Export creates an encrypted JSON file
suspend fun exportData(context: Context, passphrase: String): File {
    val data = collectAllData() // Conversations, memories, projects, settings
    val json = Json.encodeToString(data)
    val encrypted = encrypt(json, passphrase)
    val file = File(context.cacheDir, "babymomo_backup.enc")
    file.writeBytes(encrypted)
    return file
}
```

### Import

```kotlin
suspend fun importData(context: Context, file: File, passphrase: String): Boolean {
    return try {
        val encrypted = file.readBytes()
        val json = decrypt(encrypted, passphrase)
        val data = Json.decodeFromString<BackupData>(json)
        restoreAllData(data)
        true
    } catch (_: Exception) {
        false  // Wrong passphrase or corrupt file
    }
}
```

---

## 9. Proguard / R8 Rules

```proguard
# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper

# LiteRT
-keep class com.google.ai.edge.litert.** { *; }
```

---

## 10. Test Scenarios

| Test | Description | Expected |
|------|------------|----------|
| `sqlCipher_encryption` | Try opening DB without passphrase | SQLiteException thrown |
| `sqlCipher_correctPassphrase` | Open DB with correct key | Database accessible |
| `encryptedPrefs_storeAndRetrieve` | Store and get API key | Same value returned |
| `encryptedPrefs_afterAppRestart` | Store key, restart app | Key still accessible |
| `network_noCleartext` | Try HTTP (non-HTTPS) request | Connection refused |
| `internetDisabled_noNetworkCalls` | Internet off, trigger remote provider | No network request made |
| `sandbox_noHostAccess` | Try "cat /etc/hosts" in sandbox | Reads sandbox file, not host |
| `sandbox_blockedCommand` | Try "rm -rf /" | Command blocked |
| `permission_calendarDenied` | Deny calendar permission | Tool returns "permission not granted" |
| `permission_notificationDenied` | Deny notification permission | Notification not posted, no crash |
| `exportImport_roundTrip` | Export → import with passphrase | Data preserved exactly |
| `exportImport_wrongPassphrase` | Import with wrong key | Returns false, no data loaded |
| `proguard_reflectionWorks` | Release build with R8 | Room, serialization, Hilt work |
| `noTelemetry_networkInspection` | Monitor network traffic | Zero analytics/telemetry calls |
