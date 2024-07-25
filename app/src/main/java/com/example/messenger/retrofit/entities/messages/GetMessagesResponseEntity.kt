package com.example.messenger.retrofit.entities.messages

import com.squareup.moshi.Json

data class GetMessagesResponseEntity(
    val messages: List<Message>
)

data class Message(
    val id: Int,
    @Json(name = "id_sender") val idSender: Int,
    var text: String,
    var images: List<String>,
    var voice: String,
    var file: String,
    var timestamp: Long,
    @Json(name = "is_read") var isRead: Boolean,
    @Json(name = "is_edited") var isEdited: Boolean
)