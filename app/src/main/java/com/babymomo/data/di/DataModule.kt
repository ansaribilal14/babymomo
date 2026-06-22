package com.babymomo.data.di

import android.content.Context
import androidx.room.Room
import com.babymomo.data.db.BabymomoDatabase
import com.babymomo.data.db.dao.ConversationDao
import com.babymomo.data.db.dao.EntityDao
import com.babymomo.data.db.dao.MemoryDao
import com.babymomo.data.db.dao.MemoryEntityLinkDao
import com.babymomo.data.db.dao.MetaDao
import com.babymomo.data.db.dao.ModelDao
import com.babymomo.data.db.dao.ProjectDao
import com.babymomo.data.db.dao.RelationDao
import com.babymomo.data.db.dao.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): BabymomoDatabase =
        Room.databaseBuilder(ctx, BabymomoDatabase::class.java, BabymomoDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideMemoryDao(db: BabymomoDatabase): MemoryDao = db.memoryDao()
    @Provides fun provideEntityDao(db: BabymomoDatabase): EntityDao = db.entityDao()
    @Provides fun provideRelationDao(db: BabymomoDatabase): RelationDao = db.relationDao()
    @Provides fun provideMemoryEntityLinkDao(db: BabymomoDatabase): MemoryEntityLinkDao = db.memoryEntityLinkDao()
    @Provides fun provideMetaDao(db: BabymomoDatabase): MetaDao = db.metaDao()
    @Provides fun provideConversationDao(db: BabymomoDatabase): ConversationDao = db.conversationDao()
    @Provides fun provideProjectDao(db: BabymomoDatabase): ProjectDao = db.projectDao()
    @Provides fun provideTaskDao(db: BabymomoDatabase): TaskDao = db.taskDao()
    @Provides fun provideModelDao(db: BabymomoDatabase): ModelDao = db.modelDao()
}
