package com.example.messenger.retrofit.entities.messages

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Message(
    val id: Int,
    @Json(name = "id_sender") val idSender: Int,
    var text: String? = null,
    var images: List<String>? = null,
    var voice: String? = null,
    var file: String? = null,
    var timestamp: Long,
    @Json(name = "is_read") var isRead: Boolean,
    @Json(name = "is_edited") var isEdited: Boolean
) {
    fun toMessage(): com.example.messenger.model.Message = com.example.messenger.model.Message(
        id = id, idSender = idSender, text = text, images = images, voice = voice, file = file,
        timestamp = timestamp, isRead = isRead, isEdited = isEdited
    )
}