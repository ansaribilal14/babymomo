package com.babymomo.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.babymomo.data.db.dao.ConversationDao
import com.babymomo.data.db.dao.EntityDao
import com.babymomo.data.db.dao.MemoryDao
import com.babymomo.data.db.dao.MemoryEntityLinkDao
import com.babymomo.data.db.dao.MetaDao
import com.babymomo.data.db.dao.ModelDao
import com.babymomo.data.db.dao.ProjectDao
import com.babymomo.data.db.dao.RelationDao
import com.babymomo.data.db.dao.TaskDao
import com.babymomo.data.db.entity.ConversationEntity
import com.babymomo.data.db.entity.EntityEntity
import com.babymomo.data.db.entity.EntityType
import com.babymomo.data.db.entity.MemoryEntity
import com.babymomo.data.db.entity.MemoryEntityLink
import com.babymomo.data.db.entity.MemorySource
import com.babymomo.data.db.entity.MemoryType
import com.babymomo.data.db.entity.MessageEntity
import com.babymomo.data.db.entity.MessageRole
import com.babymomo.data.db.entity.MessageStatus
import com.babymomo.data.db.entity.MetaEntity
import com.babymomo.data.db.entity.ModelEntity
import com.babymomo.data.db.entity.ModelRuntime
import com.babymomo.data.db.entity.ModelStatus
import com.babymomo.data.db.entity.ProjectEntity
import com.babymomo.data.db.entity.ProjectStatus
import com.babymomo.data.db.entity.RelationEntity
import com.babymomo.data.db.entity.RelationType
import com.babymomo.data.db.entity.TaskEntity
import com.babymomo.data.db.entity.TaskPriority
import com.babymomo.data.db.entity.TaskStatus

class BabymomoConverters {
    @TypeConverter fun memTypeToString(t: MemoryType): String = t.name
    @TypeConverter fun stringToMemType(s: String): MemoryType = runCatching { MemoryType.valueOf(s) }.getOrDefault(MemoryType.SEMANTIC)
    @TypeConverter fun memSrcToString(s: MemorySource): String = s.name
    @TypeConverter fun stringToMemSrc(s: String): MemorySource = runCatching { MemorySource.valueOf(s) }.getOrDefault(MemorySource.LLM_INFERRED)
    @TypeConverter fun entityTypeToString(t: EntityType): String = t.name
    @TypeConverter fun stringToEntityType(s: String): EntityType = runCatching { EntityType.valueOf(s) }.getOrDefault(EntityType.NOTE)
    @TypeConverter fun relTypeToString(t: RelationType): String = t.name
    @TypeConverter fun stringToRelType(s: String): RelationType = runCatching { RelationType.valueOf(s) }.getOrDefault(RelationType.RELATED_TO)
    @TypeConverter fun msgRoleToString(r: MessageRole): String = r.name
    @TypeConverter fun stringToMsgRole(s: String): MessageRole = runCatching { MessageRole.valueOf(s) }.getOrDefault(MessageRole.USER)
    @TypeConverter fun msgStatusToString(s: MessageStatus): String = s.name
    @TypeConverter fun stringToMsgStatus(s: String): MessageStatus = runCatching { MessageStatus.valueOf(s) }.getOrDefault(MessageStatus.COMPLETE)
    @TypeConverter fun projectStatusToString(s: ProjectStatus): String = s.name
    @TypeConverter fun stringToProjectStatus(s: String): ProjectStatus = runCatching { ProjectStatus.valueOf(s) }.getOrDefault(ProjectStatus.ACTIVE)
    @TypeConverter fun taskStatusToString(s: TaskStatus): String = s.name
    @TypeConverter fun stringToTaskStatus(s: String): TaskStatus = runCatching { TaskStatus.valueOf(s) }.getOrDefault(TaskStatus.TODO)
    @TypeConverter fun taskPriorityToString(p: TaskPriority): String = p.name
    @TypeConverter fun stringToTaskPriority(s: String): TaskPriority = runCatching { TaskPriority.valueOf(s) }.getOrDefault(TaskPriority.MEDIUM)
    @TypeConverter fun modelRuntimeToString(r: ModelRuntime): String = r.name
    @TypeConverter fun stringToModelRuntime(s: String): ModelRuntime = runCatching { ModelRuntime.valueOf(s) }.getOrDefault(ModelRuntime.MOCK)
    @TypeConverter fun modelStatusToString(s: ModelStatus): String = s.name
    @TypeConverter fun stringToModelStatus(s: String): ModelStatus = runCatching { ModelStatus.valueOf(s) }.getOrDefault(ModelStatus.NOT_DOWNLOADED)
}

@Database(
    entities = [
        MemoryEntity::class, EntityEntity::class, RelationEntity::class,
        MemoryEntityLink::class, MetaEntity::class, ConversationEntity::class,
        MessageEntity::class, ProjectEntity::class, TaskEntity::class, ModelEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(BabymomoConverters::class)
abstract class BabymomoDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun entityDao(): EntityDao
    abstract fun relationDao(): RelationDao
    abstract fun memoryEntityLinkDao(): MemoryEntityLinkDao
    abstract fun metaDao(): MetaDao
    abstract fun conversationDao(): ConversationDao
    abstract fun projectDao(): ProjectDao
    abstract fun taskDao(): TaskDao
    abstract fun modelDao(): ModelDao

    companion object { const val NAME = "babymomo_memory.db" }
}
