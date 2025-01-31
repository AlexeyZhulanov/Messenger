package com.example.messenger.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.messenger.model.User

@Entity(tableName = "group_members")
data class GroupMemberDbEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "user_id") val userId: Int,
    @ColumnInfo(name = "group_id") val groupId: Int,
    val name: String,
    val username: String,
    val avatar: String?,
    @ColumnInfo(name = "last_session") val lastSession: Long? = 0
) {
    fun toUser(): User = User(id = userId, name = name, username = username,
        avatar = avatar, lastSession = lastSession)

    companion object {
        fun fromUserInput(groupId: Int, user: User): GroupMemberDbEntity = GroupMemberDbEntity(
            userId = user.id, groupId = groupId, name = user.name, username = user.username,
            avatar = user.avatar, lastSession = user.lastSession
        )
    }
}