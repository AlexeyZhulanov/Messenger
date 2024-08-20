package com.example.messenger.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.messenger.room.entities.ConversationDbEntity

@Dao
interface ConversationDao {
    @Transaction
    @Query("SELECT * FROM conversations ORDER BY order_index ASC")
    suspend fun getConversations(): List<ConversationDbEntity>

    @Transaction
    suspend fun replaceConversations(conversations: List<ConversationDbEntity>) {
        deleteAllConversations()
        insertConversations(conversations)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(conversations: List<ConversationDbEntity>)

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()

    @Query("DELETE FROM users WHERE id NOT IN (SELECT DISTINCT other_user_id FROM conversations WHERE other_user_id IS NOT NULL)")
    suspend fun cleanUpUsers()

    @Query("DELETE FROM last_messages WHERE id NOT IN (SELECT DISTINCT last_message_id FROM conversations WHERE last_message_id IS NOT NULL)")
    suspend fun cleanUpLastMessages()
}