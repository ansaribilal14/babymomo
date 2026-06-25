package com.babymomo.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.babymomo.app.data.db.dao.*
import com.babymomo.app.data.db.entities.*
import dagger.hilt.android.qualifiers.ApplicationContext
import net.sqlcipher.database.SupportFactory
import javax.inject.Inject
import javax.inject.Singleton

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
    exportSchema = false
)
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

@Singleton
class DatabaseProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(passphrase: ByteArray = "babymomo_default_key_2026".toByteArray()): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            val factory = SupportFactory(passphrase)
            Room.databaseBuilder(context, AppDatabase::class.java, "babymomo.db")
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
        }
    }
}
