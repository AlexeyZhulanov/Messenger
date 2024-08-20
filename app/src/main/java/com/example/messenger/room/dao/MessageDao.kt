package com.example.messenger.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.messenger.room.entities.MessageDbEntity

@Dao
interface MessageDao {
    @Transaction
    @Query("SELECT * FROM messages WHERE id_dialog = :idDialog ORDER BY timestamp ASC")
    suspend fun getMessages(idDialog: Int): List<MessageDbEntity>

    @Transaction
    suspend fun replaceMessages(idDialog: Int, newMessages: List<MessageDbEntity>) {
        deleteMessagesByDialogId(idDialog)
        insertMessages(newMessages)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageDbEntity>)

    @Query("DELETE FROM messages WHERE id_dialog = :idDialog")
    suspend fun deleteMessagesByDialogId(idDialog: Int)
}