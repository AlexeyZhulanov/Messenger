package com.example.messenger.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.messenger.room.entities.LastReadMessageEntity

@Dao
interface LastReadMessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun saveLastReadMessage(lastReadMessage: LastReadMessageEntity)

    @Query("SELECT * FROM last_read_message WHERE dialog_id = :dialogId LIMIT 1")
    suspend fun getLastReadMessage(dialogId: Int): LastReadMessageEntity?

    @Update
    suspend fun updateLastReadMessage(lastReadMessageEntity: LastReadMessageEntity)
}