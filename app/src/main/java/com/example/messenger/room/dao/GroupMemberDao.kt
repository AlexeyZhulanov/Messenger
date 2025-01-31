package com.example.messenger.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.messenger.room.entities.GroupMemberDbEntity

@Dao
interface GroupMemberDao {
    @Transaction
    @Query("SELECT * FROM group_members WHERE group_id = :groupId ORDER BY username ASC")
    suspend fun getMembers(groupId: Int): List<GroupMemberDbEntity>

    @Transaction
    suspend fun replaceMembers(groupId: Int, newMembers: List<GroupMemberDbEntity>) {
        deleteMembersByGroupId(groupId)
        insertMembers(newMembers)
    }

    @Query("DELETE FROM group_members WHERE group_id = :groupId")
    suspend fun deleteMembersByGroupId(groupId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(messages: List<GroupMemberDbEntity>)
}