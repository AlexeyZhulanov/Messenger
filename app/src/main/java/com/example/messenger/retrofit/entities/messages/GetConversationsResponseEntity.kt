package com.example.messenger.retrofit.entities.messages

import com.example.messenger.model.Conversation
import com.example.messenger.model.LastMessage
import com.example.messenger.model.User
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ConversationEntity(
    val type: String,
    val id: Int,
    val key: String? = null,
    @Json(name = "other_user") val otherUser: UserEntity? = null,
    val name: String? = null,
    @Json(name = "created_by") val createdBy: Int,
    val avatar: String? = null,
    val lastMessage: MessageEntity?,
    @Json(name = "count_msg") val countMsg: Int
) {
    fun toConversation() : Conversation = Conversation(
        type = type, id = id, key = key, otherUser = otherUser?.toUser(), name = name, createdBy = createdBy,
        avatar = avatar, lastMessage = lastMessage?.toLastMessage(), countMsg = countMsg
    )
}

@JsonClass(generateAdapter = true)
data class MessageEntity(
    val text: String?,
    val timestamp: Long,
    @Json(name = "is_read") val isRead: Boolean
) {
    fun toLastMessage() : LastMessage = LastMessage(text = text, timestamp = timestamp, isRead = isRead)
}
@JsonClass(generateAdapter = true)
data class UserEntity(
    val id: Int,
    val name: String,
    val username: String,
    val avatar: String? = null
) {
    fun toUser() : User = User(id = id, name = name, username = username, avatar = avatar)
}
