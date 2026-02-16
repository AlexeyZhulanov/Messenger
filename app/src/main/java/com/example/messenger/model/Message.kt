package com.example.messenger.model

import android.os.Parcelable
import com.squareup.moshi.Json
import kotlinx.parcelize.Parcelize

@Parcelize
data class Message(
    val id: Int,
    @param:Json(name = "id_sender") val idSender: Int,
    var text: String? = null,
    var images: List<String>? = null,
    var voice: String? = null,
    var file: String? = null,
    var code: String? = null,
    @param:Json(name = "code_language") var codeLanguage: String? = null,
    var timestamp: Long,
    @param:Json(name = "is_read") var isRead: Boolean = false,
    @param:Json(name = "is_personal_unread") var isPersonalUnread: Boolean? = null,
    @param:Json(name = "is_edited") var isEdited: Boolean,
    @param:Json(name = "reference_to_message_id") var referenceToMessageId: Int? = null,
    @param:Json(name = "is_forwarded") var isForwarded: Boolean,
    @param:Json(name = "is_url") var isUrl: Boolean? = false,
    @param:Json(name = "username_author_original") var usernameAuthorOriginal: String? = null,
    var waveform: List<Int>? = null,
    var isUnsent: Boolean? = false,
    var localFilePaths: List<String>? = null
) : Parcelable

@Parcelize
data class LastMessage(
    val text: String? = null,
    val timestamp: Long? = null,
    val isRead: Boolean? = null
) : Parcelable