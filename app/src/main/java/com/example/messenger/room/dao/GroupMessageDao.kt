package com.example.messenger.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.messenger.room.entities.GroupMessageDbEntity
import com.example.messenger.room.entities.MessageDbEntity

@Dao
interface GroupMessageDao {
    @Transaction
    @Query("SELECT * FROM group_messages WHERE group_id = :groupId ORDER BY timestamp ASC")
    suspend fun getGroupMessages(groupId: Int): List<GroupMessageDbEntity>

    @Transaction
    suspend fun replaceGroupMessages(groupId: Int, newGroupMessages: List<GroupMessageDbEntity>) {
        deleteGroupMessagesByGroupId(groupId)
        insertGroupMessages(newGroupMessages)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMessages(groupMessages: List<GroupMessageDbEntity>)

    @Query("DELETE FROM group_messages WHERE group_id = :groupId")
    suspend fun deleteGroupMessagesByGroupId(groupId: Int)
}