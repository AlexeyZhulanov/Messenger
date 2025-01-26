package com.example.messenger.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.messenger.room.entities.UnsentMessageEntity

@Dao
interface UnsentMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnsentMessage(unsentMessage: UnsentMessageEntity) : Long

    @Query("SELECT * FROM unsent_messages WHERE id_dialog = :idDialog ORDER BY id ASC")
    suspend fun getUnsentMessages(idDialog: Int): List<UnsentMessageEntity>?

    @Query("SELECT * FROM unsent_messages WHERE id_group = :idGroup ORDER BY id ASC")
    suspend fun getUnsentMessagesGroup(idGroup: Int): List<UnsentMessageEntity>?

    @Query("DELETE FROM unsent_messages WHERE id = :messageId")
    suspend fun deleteUnsentMessage(messageId: Int)
}