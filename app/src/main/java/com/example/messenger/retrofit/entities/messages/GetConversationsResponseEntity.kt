package com.example.messenger.retrofit.entities.messages

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GetConversationsResponseEntity(
    val conversations: List<ConversationEntity>
)

@JsonClass(generateAdapter = true)
data class ConversationEntity(
    val type: String,
    val id: Long,
    val key: String? = null,
    @Json(name = "other_user") val otherUser: UserEntity? = null,
    val name: String? = null,
    @Json(name = "created_by") val createdBy: Long? = null,
    val avatar: String? = null,
    val lastMessage: MessageEntity?,
    @Json(name = "count_msg") val countMsg: Int
)

@JsonClass(generateAdapter = true)
data class MessageEntity(
    val text: String?,
    val timestamp: Long?,
    @Json(name = "is_read") val isRead: Boolean?
)
@JsonClass(generateAdapter = true)
data class UserEntity(
    val id: Long,
    val name: String,
    val username: String,
    val avatar: String? = null
)
