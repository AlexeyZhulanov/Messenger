package com.example.messenger.room.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.messenger.model.User

@Entity(
    tableName = "current_user",
    indices = [
        Index("name", unique = true)
    ]
)
data class UserDbEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 1,
    val name: String,
    val username: String,
    val avatar: String? = null
) {
    fun toUser() : User = User(
        id = 1, name = name, username = username, avatar = avatar
    )

    companion object {
        fun fromUserInput(user: User) : UserDbEntity = UserDbEntity(
            id = 1, name = user.name, username = user.username, avatar = user.avatar)
    }
}