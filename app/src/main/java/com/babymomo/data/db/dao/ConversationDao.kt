package com.babymomo.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.babymomo.data.db.entity.ConversationEntity
import com.babymomo.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(c: ConversationEntity)

    @Update
    suspend fun updateConversation(c: ConversationEntity)

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversation(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE isArchived = 0 ORDER BY updatedAt DESC")
    fun activeConversationsFlow(): Flow<List<ConversationEntity>>

    @Query("UPDATE conversations SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: String)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(m: MessageEntity)

    @Update
    suspend fun updateMessage(m: MessageEntity)

    @Query("SELECT * FROM messages WHERE conversationId = :cid ORDER BY createdAt ASC")
    fun messagesFlow(cid: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :cid ORDER BY createdAt ASC")
    suspend fun messages(cid: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :cid ORDER BY createdAt DESC LIMIT :n")
    suspend fun recentMessages(cid: String, n: Int): List<MessageEntity>

    @Query("DELETE FROM messages WHERE conversationId = :cid")
    suspend fun deleteMessagesFor(cid: String)
}
