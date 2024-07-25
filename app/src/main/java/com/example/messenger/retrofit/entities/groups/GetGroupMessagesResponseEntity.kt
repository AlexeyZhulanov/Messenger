package com.example.messenger.retrofit.entities.groups

import com.squareup.moshi.Json

data class GetGroupMessagesResponseEntity(
    val messages: List<Message>
)

data class Message(
    val id: Int,
    @Json(name = "sender_id") val senderId: Int,
    @Json(name = "group_id") val groupId: Int,
    var text: String,
    var images: List<String>,
    var voice: String,
    var file: String,
    var timestamp: Long,
    @Json(name = "is_read") var isRead: Boolean,
    @Json(name = "is_edited") var isEdited: Boolean
)