package com.example.messenger.retrofit.entities.messages

import com.squareup.moshi.Json

data class GetMessagesResponseEntity(
    val messages: List<Message>
) {
    fun toMessages() : List<com.example.messenger.model.Message> {
        val list = mutableListOf<com.example.messenger.model.Message>()
        messages.forEach {
            list.add(it.toMessage())
        }
        return list
    }
}

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
) {
    fun toMessage(): com.example.messenger.model.Message = com.example.messenger.model.Message(
        id = id, idSender = idSender, text = text, images = images, voice = voice, file = file,
        timestamp = timestamp, isRead = isRead, isEdited = isEdited
    )
}