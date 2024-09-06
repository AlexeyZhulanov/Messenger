package com.example.messenger.model

import com.squareup.moshi.Json

data class DeletedMessagesEvent(
    @Json(name = "dialog_id") val dialogId: Int,
    @Json(name = "deleted_message_ids") val deletedMessagesIds: List<Int>
)

data class ReadMessagesEvent(
    @Json(name = "dialog_id") val dialogId: Int,
    @Json(name = "messages_read_ids") val messagesReadIds: List<Int>
)

data class DialogCreatedEvent(
    @Json(name = "dialog_id") val dialogId: Int,
    val message: String
)

data class DialogDeletedEvent(@Json(name = "dialog_id") val dialogId: Int)

data class DialogMessagesAllDeleted(@Json(name = "dialog_id") val dialogId: Int)

data class UserSessionUpdatedEvent(
    @Json(name = "user_id") val userId: Int,
    @Json(name = "last_session") val lastSession: Long
)

data class TypingEvent(
    @Json(name = "dialog_id") val dialogId: String,
    @Json(name = "user_id") val userId: String
)
