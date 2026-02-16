package com.example.messenger.retrofit.entities.messages

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

@JsonClass(generateAdapter = true)
data class Message(
    val id: Int,
    @param:Json(name = "id_sender") val idSender: Int,
    var text: String? = null,
    var images: List<String>? = null,
    var voice: String? = null,
    var file: String? = null,
    var code: String? = null,
    @param:Json(name = "code_language") var codeLanguage: String? = null,
    var timestamp: String,
    @param:Json(name = "is_read") var isRead: Boolean,
    @param:Json(name = "is_personal_unread") var isPersonalUnread: Boolean? = null,
    @param:Json(name = "is_edited") var isEdited: Boolean,
    @param:Json(name = "is_forwarded") var isForwarded: Boolean,
    @param:Json(name = "is_url") var isUrl: Boolean? = null,
    @param:Json(name = "reference_to_message_id") var referenceToMessageId: Int? = null,
    @param:Json(name = "username_author_original") var usernameAuthorOriginal: String? = null,
    val waveform: List<Int>? = null,
    var position: Int? = null
) {
    fun toMessage(): com.example.messenger.model.Message {
        val longTimestamp = parseTimestampToLong(timestamp)
        return com.example.messenger.model.Message(
            id = id, idSender = idSender, text = text, images = images, voice = voice, file = file, code = code, codeLanguage = codeLanguage,
            timestamp = longTimestamp, isRead = isRead, isPersonalUnread = isPersonalUnread,  isEdited = isEdited, isForwarded = isForwarded,
            isUrl = isUrl, referenceToMessageId = referenceToMessageId, usernameAuthorOriginal = usernameAuthorOriginal, waveform = waveform
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