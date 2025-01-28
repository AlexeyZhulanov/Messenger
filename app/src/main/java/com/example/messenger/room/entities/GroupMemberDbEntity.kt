package com.example.messenger.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "group_members")
data class GroupMemberDbEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "group_id") val groupId: Int,
    val username: String,
    val avatar: String?
) {
    fun toMember(): Pair<String, String?> = Pair(username, avatar)

    companion object {
        fun fromUserInput(groupId: Int, username: String, avatar: String?): GroupMemberDbEntity = GroupMemberDbEntity(
            groupId = groupId, username = username, avatar = avatar
        )
    }
}