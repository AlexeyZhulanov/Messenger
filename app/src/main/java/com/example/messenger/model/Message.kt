package com.example.messenger.model

import android.os.Parcelable
import com.squareup.moshi.Json
import kotlinx.parcelize.Parcelize

@Parcelize
data class Message(
    val id: Int,
    @Json(name = "id_sender") val idSender: Int,
    var text: String? = null,
    var images: List<String>? = null,
    var voice: String? = null,
    var file: String? = null,
    var timestamp: Long,
    @Json(name = "is_read") var isRead: Boolean = false,
    @Json(name = "is_edited") var isEdited: Boolean,
    @Json(name = "reference_to_message_id") var referenceToMessageId: Int? = null,
    @Json(name = "is_forwarded") var isForwarded: Boolean,
    @Json(name = "username_author_original") var usernameAuthorOriginal: String? = null
) : Parcelable

data class LastMessage(
    val text: String? = null,
    val timestamp: Long,
    val isRead: Boolean
)

data class GroupMessage(
    val id: Int,
    val senderId: Int,
    val groupId: Int,
    var text: String? = null,
    var images: List<String>? = null,
    var voice: String? = null,
    var file: String? = null,
    var timestamp: Long,
    var isRead: Boolean,
    var isEdited: Boolean,
    var referenceToMessageId: Int? = null,
    var isForwarded: Boolean,
    var usernameAuthorOriginal: String? = null
)