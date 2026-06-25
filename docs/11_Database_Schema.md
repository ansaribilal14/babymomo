# 11 — Database Schema

## Module Overview

All persistent data in Babymomo lives in a single SQLCipher-encrypted Room database (`babymomo.db`). This document specifies every entity, its columns, constraints, indices, relationships, and the DAOs that access them. It also covers the SQLCipher integration, migration strategy, and database initialization.

**Key Principle:** The database is the single source of truth for all state. ViewModels observe Room Flows and never hold mutable state independently.

---

## 1. Database Configuration

### AppDatabase

```kotlin
@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MemoryEntity::class,
        MemoryVectorEntity::class,
        EntityEntity::class,
        RelationEntity::class,
        ProjectEntity::class,
        ModelCatalogEntity::class,
        SettingsEntity::class,
        HeartbeatLogEntity::class,
        McpServerEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun memoryVectorDao(): MemoryVectorDao
    abstract fun entityDao(): EntityDao
    abstract fun relationDao(): RelationDao
    abstract fun projectDao(): ProjectDao
    abstract fun modelCatalogDao(): ModelCatalogDao
    abstract fun settingsDao(): SettingsDao
    abstract fun heartbeatLogDao(): HeartbeatLogDao
    abstract fun mcpServerDao(): McpServerDao
}
```

### SQLCipher Integration

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
            "db_prefs",
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
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
```

---

## 2. All Entities

### 2A. conversations

```kotlin
@Entity(
    tableName = "conversations",
    indices = [Index("updatedAt")]
)
data class ConversationEntity(
    @PrimaryKey val id: String,           // UUID
    val title: String,                     // Auto-generated from first message
    val createdAt: Long,                   // epoch ms
    val updatedAt: Long                    // epoch ms, updated on every new message
)
```

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| id | TEXT (PK) | No | UUID |
| title | TEXT | No | Auto-generated conversation title |
| createdAt | INTEGER | No | Creation timestamp (epoch ms) |
| updatedAt | INTEGER | No | Last update timestamp (epoch ms) |

### 2B. messages

```kotlin
@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("conversationId"), Index("timestamp")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,                     // "user" | "assistant" | "tool"
    val content: String,
    val timestamp: Long,
    val routingReason: String? = null,    // e.g. "PlannerAgent + WebSearchTool"
    val imageUri: String? = null          // Optional attached image
)
```

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| id | TEXT (PK) | No | UUID |
| conversationId | TEXT (FK) | No | References conversations.id, CASCADE delete |
| role | TEXT | No | "user", "assistant", or "tool" |
| content | TEXT | No | Message text content |
| timestamp | INTEGER | No | Message timestamp (epoch ms) |
| routingReason | TEXT | Yes | Which agents/tools handled this turn |
| imageUri | TEXT | Yes | URI of attached image |

### 2C. memories (Bi-temporal)

```kotlin
@Entity(
    tableName = "memories",
    indices = [Index("type"), Index("isInSystemPrompt"), Index("validFrom")]
)
data class MemoryEntity(
    @PrimaryKey val id: String,
    val content: String,
    val type: String,                     // WORKING | EPISODIC | SEMANTIC | PROCEDURAL
    val confidence: Double = 1.0,         // 0.0 – 1.0
    val hitCount: Int = 0,                // Number of times recalled
    val isInSystemPrompt: Boolean = false, // Promoted to permanent context
    val validFrom: Long,                  // epoch ms (transaction time)
    val validTo: Long? = null,            // null = currently valid
    val createdAt: Long,
    val sourceMessageId: String? = null   // References messages.id
)
```

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| id | TEXT (PK) | No | "mem_{timestamp}_{hash}" |
| content | TEXT | No | The memory text |
| type | TEXT | No | WORKING, EPISODIC, SEMANTIC, PROCEDURAL |
| confidence | REAL | No | 0.0–1.0, set by extractor |
| hitCount | INTEGER | No | Incremented on each recall |
| isInSystemPrompt | INTEGER | No | 0 or 1, true when promoted |
| validFrom | INTEGER | No | When this fact became valid |
| validTo | INTEGER | Yes | null = currently valid |
| createdAt | INTEGER | No | Row creation timestamp |
| sourceMessageId | TEXT | Yes | Origin message reference |

### 2D. memory_vectors

```kotlin
@Entity(
    tableName = "memory_vectors",
    foreignKeys = [ForeignKey(
        entity = MemoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["memoryId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("memoryId")]
)
data class MemoryVectorEntity(
    @PrimaryKey val id: String,
    val memoryId: String,
    val embedding: ByteArray,             // FloatArray serialized, 384-dim
    val dimension: Int = 384
)
```

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| id | TEXT (PK) | No | "vec_{memoryId}" |
| memoryId | TEXT (FK) | No | References memories.id, CASCADE delete |
| embedding | BLOB | No | Serialized FloatArray, 384 floats |
| dimension | INTEGER | No | Embedding dimension (384) |

### 2E. entities (Knowledge Graph)

```kotlin
@Entity(
    tableName = "entities",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("type"), Index("projectId")]
)
data class EntityEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,                     // PERSON | PLACE | CONCEPT | PROJECT | THING
    val description: String? = null,
    val createdAt: Long,
    val projectId: String? = null         // Optional link to project
)
```

### 2F. relations (Bi-temporal Knowledge Graph)

```kotlin
@Entity(
    tableName = "relations",
    foreignKeys = [
        ForeignKey(entity = EntityEntity::class, parentColumns = ["id"], childColumns = ["fromEntityId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = EntityEntity::class, parentColumns = ["id"], childColumns = ["toEntityId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("fromEntityId"), Index("toEntityId")]
)
data class RelationEntity(
    @PrimaryKey val id: String,
    val fromEntityId: String,
    val toEntityId: String,
    val type: String,                     // e.g. "WORKS_AT", "KNOWS", "OWNS"
    val weight: Double = 1.0,
    val validFrom: Long,
    val validTo: Long? = null
)
```

### 2G. projects

```kotlin
@Entity(
    tableName = "projects",
    indices = [Index("status")]
)
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    val status: String = "ACTIVE",        // ACTIVE | COMPLETED | ARCHIVED
    val tasks: String? = null,            // JSON array of task strings
    val graphEntityId: String? = null,    // Link to knowledge graph entity
    val createdAt: Long,
    val updatedAt: Long
)
```

### 2H. model_catalog

```kotlin
@Entity(tableName = "model_catalog")
data class ModelCatalogEntity(
    @PrimaryKey val id: String,
    val name: String,                     // "Gemma 2B", "Phi-3 Mini"
    val filename: String,                 // "gemma-2b-it.bin"
    val sizeBytes: Long,
    val downloadUrl: String,
    val isDownloaded: Boolean = false,
    val isActive: Boolean = false,
    val downloadedAt: Long? = null
)
```

### 2I. settings

```kotlin
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val key: String,
    val value: String                     // JSON-encoded value
)
```

### 2J. heartbeat_log

```kotlin
@Entity(tableName = "heartbeat_log", indices = [Index("timestamp")])
data class HeartbeatLogEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val summary: String,
    val notified: Boolean = false,
    val message: String? = null
)
```

### 2K. mcp_servers

```kotlin
@Entity(tableName = "mcp_servers")
data class McpServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val isEnabled: Boolean = true,
    val isCurated: Boolean = false,
    val addedAt: Long
)
```

---

## 3. All DAOs

### ConversationDao

```kotlin
@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity)

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: String): ConversationEntity?

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)
}
```

### MessageDao

```kotlin
@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getByConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(conversationId: String): MessageEntity?

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: String)
}
```

### MemoryDao

```kotlin
@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity)

    @Query("SELECT * FROM memories WHERE validTo IS NULL ORDER BY createdAt DESC")
    fun getAllActive(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE type = :type AND validTo IS NULL ORDER BY createdAt DESC")
    fun getByType(type: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE isInSystemPrompt = 1")
    suspend fun getPromoted(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getById(id: String): MemoryEntity?

    @Query("UPDATE memories SET hitCount = hitCount + 1 WHERE id = :id")
    suspend fun incrementHitCount(id: String)

    @Query("UPDATE memories SET isInSystemPrompt = 1, validTo = :validTo WHERE id = :id")
    suspend fun promote(id: String, validTo: Long)

    @Query("SELECT * FROM memories WHERE content LIKE '%' || :query || '%' AND validTo IS NULL")
    suspend fun search(query: String): List<MemoryEntity>

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM memories WHERE validTo IS NULL")
    suspend fun getActiveCount(): Int

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM memories WHERE isInSystemPrompt = 1")
    suspend fun getPromotedCount(): Int
}
```

### MemoryVectorDao

```kotlin
@Dao
interface MemoryVectorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vector: MemoryVectorEntity)

    @Query("SELECT * FROM memory_vectors")
    suspend fun getAll(): List<MemoryVectorEntity>

    @Query("DELETE FROM memory_vectors WHERE memoryId = :memoryId")
    suspend fun deleteByMemoryId(memoryId: String)
}
```

### EntityDao, RelationDao, ProjectDao, ModelCatalogDao, SettingsDao, HeartbeatLogDao, McpServerDao

*(Full implementations as defined in the codebase — see Daos.kt)*

---

## 4. Type Converters

```kotlin
class Converters {
    @TypeConverter
    fun fromByteArray(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)

    @TypeConverter
    fun toByteArray(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
}
```

---

## 5. Migration Strategy

### Version 1 → 2 (Example)

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add new column for memory tags
        db.execSQL("ALTER TABLE memories ADD COLUMN tags TEXT DEFAULT NULL")
    }
}
```

### Migration Rules

1. Every schema change bumps `version` in `@Database`
2. A `Migration` object is created for each version transition
3. Migrations are tested with `MigrationTestHelper`
4. `fallbackToDestructiveMigration()` is NOT used — data must be preserved
5. Export schema is enabled (`exportSchema = true`) for testing

---

## 6. Database Initialization

### Seed Data

On first launch, the database is populated with:

```kotlin
private suspend fun seedDatabase(database: AppDatabase) {
    // Model catalog
    database.modelCatalogDao().insert(ModelCatalogEntity(
        id = "model_gemma_2b",
        name = "Gemma 2B IT",
        filename = "gemma-2b-it.bin",
        sizeBytes = 1_500_000_000,
        downloadUrl = "https://storage.googleapis.com/babymomo-models/gemma-2b-it.bin"
    ))
    database.modelCatalogDao().insert(ModelCatalogEntity(
        id = "model_phi3_mini",
        name = "Phi-3 Mini 3.8B",
        filename = "phi-3-mini.bin",
        sizeBytes = 2_400_000_000,
        downloadUrl = "https://storage.googleapis.com/babymomo-models/phi-3-mini.bin"
    ))

    // Default settings
    database.settingsDao().insert(SettingsEntity("internet_enabled", "false"))
    database.settingsDao().insert(SettingsEntity("sandbox_enabled", "false"))
    database.settingsDao().insert(SettingsEntity("provider_priority", """["openai","nim","openrouter"]"""))
}
```

---

## 7. Entity Relationship Diagram

```
conversations ──1:N──► messages
       │                    │
       │                    └──► memories.sourceMessageId (weak ref)
       │
       └──────────────────────────────────────────┐
                                                  │
memories ──1:1──► memory_vectors                  │
    │                                             │
    │ (type: WORKING/EPISODIC/SEMANTIC/PROCEDURAL)│
    │                                             │
    └──hitCount≥5──► isInSystemPrompt=true        │
                                                  │
entities ──N:N──► relations (bi-temporal)         │
    │                                             │
    └──projectId──► projects                      │
                       │                          │
                       └──graphEntityId──► entities (self-ref)
                                                  │
model_catalog (standalone)                        │
settings (standalone key-value)                   │
heartbeat_log (standalone)                        │
mcp_servers (standalone)                          │
                                                  │
projects ◄────────────────────────────────────────┘
```

---

## 8. Test Scenarios

| Test | Description | Expected |
|------|------------|----------|
| `db_createAndOpen` | Create database with SQLCipher | Opens successfully |
| `db_wrongPassphrase` | Try wrong key | Throws SQLiteException |
| `conversation_insertAndGet` | Insert conversation, query by ID | Matching entity returned |
| `conversation_cascadeDelete` | Delete conversation | All messages deleted too |
| `message_getByConversation` | Insert 5 messages, query | 5 messages in time order |
| `memory_insertAndSearch` | Insert memory with "cat", search "cat" | Memory found |
| `memory_incrementHitCount` | Increment 5 times | hitCount = 5 |
| `memory_promote` | Promote a memory | isInSystemPrompt=true, validTo set |
| `memory_biTemporalQuery` | Query valid-at-timestamp | Only valid memories returned |
| `vector_insertAndRetrieve` | Insert 384-dim vector, retrieve | Same bytes returned |
| `entity_findOrCreate` | Insert same name twice | Same entity returned |
| `relation_addAndGet` | Add relation, query by entity | Relation found |
| `project_crud` | Create, update, delete project | All operations succeed |
| `settings_keyValue` | Store and retrieve JSON value | Same value returned |
| `heartbeatLog_insertAndQuery` | Log 10 heartbeats | 10 entries in time order |
| `mcpServer_enableDisable` | Toggle server | isEnabled updates correctly |
| `migration_v1tov2` | Run migration | New column exists, data preserved |
