package com.example.messenger.model

import com.squareup.moshi.Json

data class DeletedMessagesEvent(
    @Json(name = "deleted_message_ids") val deletedMessagesIds: List<Int>
)

data class ReadMessagesEvent(
    @Json(name = "messages_read_ids") val messagesReadIds: List<Int>
)

data class DialogCreatedEvent(
    val message: String
)

data class UserSessionUpdatedEvent(
    @Json(name = "user_id") val userId: Int,
    @Json(name = "last_session") val lastSession: Long
)
