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
    @param:Json(name = "other_user") val otherUser: UserEntity? = null,
    val name: String? = null,
    @param:Json(name = "created_by") val createdBy: Int? = null,
    val avatar: String? = null,
    @param:Json(name = "last_message") val lastMessage: MessageEntity,
    @param:Json(name = "count_msg") val countMsg: Int,
    @param:Json(name = "unread_count") val unreadCount: Int,
    @param:Json(name = "is_owner") val isOwner: Boolean,
    @param:Json(name = "can_delete") val canDelete: Boolean,
    @param:Json(name = "auto_delete_interval") val autoDeleteInterval: Int
) {
    fun toConversation() : Conversation = Conversation(
        type = type, id = id, key = key, otherUser = otherUser?.toUser(), name = name,
        createdBy = createdBy, avatar = avatar, lastMessage = lastMessage.toLastMessage(), countMsg = countMsg,
        unreadCount = unreadCount, isOwner = isOwner, canDelete = canDelete, autoDeleteInterval = autoDeleteInterval)
}

@JsonClass(generateAdapter = true)
data class MessageEntity(
    val text: String?,
    val timestamp: Long?,
    @param:Json(name = "is_read") val isRead: Boolean?
) {
    fun toLastMessage(): LastMessage {
        return LastMessage(text = text, timestamp = timestamp, isRead = isRead)
    }
}
@JsonClass(generateAdapter = true)
data class UserEntity(
    val id: Int,
    val name: String,
    val username: String,
    val avatar: String? = null,
    @param:Json(name = "last_session") val lastSession: Long? = 0
) {
    fun toUser() : User = User(id = id, name = name, username = username, avatar = avatar, lastSession = lastSession)
}