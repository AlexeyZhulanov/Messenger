package com.example.messenger.retrofit.entities.messages

import com.example.messenger.model.Conversation
import com.example.messenger.model.LastMessage
import com.example.messenger.model.User
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

@JsonClass(generateAdapter = true)
data class ConversationEntity(
    val type: String,
    val id: Int,
    val key: String? = null,
    @Json(name = "other_user") val otherUser: UserEntity? = null,
    val name: String? = null,
    @Json(name = "created_by") val createdBy: Int? = null,
    val avatar: String? = null,
    @Json(name = "last_message") val lastMessage: MessageEntity,
    @Json(name = "count_msg") val countMsg: Int,
    @Json(name = "unread_count") val unreadCount: Int,
    @Json(name = "is_owner") val isOwner: Boolean,
    @Json(name = "can_delete") val canDelete: Boolean,
    @Json(name = "auto_delete_interval") val autoDeleteInterval: Int
) {
    fun toConversation() : Conversation = Conversation(
        type = type, id = id, key = key, otherUser = otherUser?.toUser(), name = name,
        createdBy = createdBy, avatar = avatar, lastMessage = lastMessage.toLastMessage(), countMsg = countMsg,
        unreadCount = unreadCount, isOwner = isOwner, canDelete = canDelete, autoDeleteInterval = autoDeleteInterval)
}

@JsonClass(generateAdapter = true)
data class MessageEntity(
    val text: String?,
    val timestamp: String?,
    @Json(name = "is_read") val isRead: Boolean?
) {
    fun toLastMessage(): LastMessage {
        val timestampLong = if(timestamp != null) parseTimestampToLong(timestamp) else null
        return LastMessage(text = text, timestamp = timestampLong, isRead = isRead)
    }
}
@JsonClass(generateAdapter = true)
data class UserEntity(
    val id: Int,
    val name: String,
    val username: String,
    val avatar: String? = null,
    @Json(name = "last_session") val lastSession: Long? = 0
) {
    fun toUser() : User = User(id = id, name = name, username = username, avatar = avatar, lastSession = lastSession)
}

private fun parseTimestampToLong(timestamp: String): Long {
    return try {
        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
        format.parse(timestamp)?.time ?: 0L
    } catch (e: ParseException) {
        e.printStackTrace()
        0L
    }
}