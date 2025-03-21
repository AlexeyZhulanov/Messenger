package com.example.messenger.retrofit.entities.messages

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

@JsonClass(generateAdapter = true)
data class Message(
    val id: Int,
    @Json(name = "id_sender") val idSender: Int,
    var text: String? = null,
    var images: List<String>? = null,
    var voice: String? = null,
    var file: String? = null,
    var timestamp: String,
    @Json(name = "is_read") var isRead: Boolean,
    @Json(name = "is_personal_unread") var isPersonalUnread: Boolean? = null,
    @Json(name = "is_edited") var isEdited: Boolean,
    @Json(name = "is_forwarded") var isForwarded: Boolean,
    @Json(name = "reference_to_message_id") var referenceToMessageId: Int? = null,
    @Json(name = "username_author_original") var usernameAuthorOriginal: String? = null,
    var position: Int? = null
) {
    fun toMessage(): com.example.messenger.model.Message  {
        val longTimestamp = parseTimestampToLong(timestamp)
        return com.example.messenger.model.Message(
            id = id, idSender = idSender, text = text, images = images, voice = voice, file = file,
            timestamp = longTimestamp, isRead = isRead, isPersonalUnread = isPersonalUnread,  isEdited = isEdited, isForwarded = isForwarded,
            referenceToMessageId = referenceToMessageId, usernameAuthorOriginal = usernameAuthorOriginal
        )
    }
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