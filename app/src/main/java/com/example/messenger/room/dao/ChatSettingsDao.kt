package com.example.messenger.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.messenger.room.entities.ChatSettingsDbEntity

@Dao
interface ChatSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatSettings(chatSettings: ChatSettingsDbEntity)

    @Query("DELETE FROM chat_settings WHERE id_chat = :chatId AND type = :type")
    suspend fun deleteChatSettings(chatId: Int, type: Boolean)

    @Query("SELECT * FROM chat_settings WHERE id_chat = :chatId AND type = :type LIMIT 1")
    suspend fun getChatSettings(chatId: Int, type: Boolean): ChatSettingsDbEntity?
}