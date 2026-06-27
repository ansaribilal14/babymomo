package com.babymomo.app

import android.content.Context
import com.babymomo.app.data.db.AppDatabase
import com.babymomo.app.data.db.DatabaseProvider
import com.babymomo.app.data.db.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabaseProvider(@ApplicationContext context: Context): DatabaseProvider {
        return DatabaseProvider(context)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(databaseProvider: DatabaseProvider): AppDatabase {
        return databaseProvider.getDatabase()
    }

    @Provides
    fun provideConversationDao(db: AppDatabase): ConversationDao = db.conversationDao()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideMemoryDao(db: AppDatabase): MemoryDao = db.memoryDao()

    @Provides
    fun provideMemoryVectorDao(db: AppDatabase): MemoryVectorDao = db.memoryVectorDao()

    @Provides
    fun provideEntityDao(db: AppDatabase): EntityDao = db.entityDao()

    @Provides
    fun provideRelationDao(db: AppDatabase): RelationDao = db.relationDao()

    @Provides
    fun provideProjectDao(db: AppDatabase): ProjectDao = db.projectDao()

    @Provides
    fun provideModelCatalogDao(db: AppDatabase): ModelCatalogDao = db.modelCatalogDao()

    @Provides
    fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()

    @Provides
    fun provideHeartbeatLogDao(db: AppDatabase): HeartbeatLogDao = db.heartbeatLogDao()

    @Provides
    fun provideMcpServerDao(db: AppDatabase): McpServerDao = db.mcpServerDao()
}
