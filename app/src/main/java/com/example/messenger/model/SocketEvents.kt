package com.example.messenger.model

import com.squareup.moshi.Json

data class DeletedMessagesEvent(
    @Json(name = "deleted_message_ids") val deletedMessagesIds: List<Int>
)

data class ReadMessagesEvent(
    @Json(name = "messages_read_ids") val messagesReadIds: List<Int>
)

data class UserSessionUpdatedEvent(
    @Json(name = "user_id") val userId: Int,
    @Json(name = "last_session") val lastSession: Long
)

data class ChatMessageEvent(
    @Json(name = "chat_id") val chatId: Int,
    @Json(name = "message_id") val messageId: Int,
    val text: String? = null,
    val images: List<String>? = null,
    val voice: String? = null,
    val file: String? = null,
    @Json(name = "id_sender") val idSender: Int,
    @Json(name = "sender_name") val senderName: String,
    val avatar: String? = null,
    @Json(name = "is_group") val isGroup: Boolean,
    @Json(name = "group_name") val groupName: String? = null,
) {
    fun toShortMessage(timestamp: Long) = ShortMessage(chatId, isGroup, text, senderName, timestamp)
}

data class ShortMessage(
    val chatId: Int,
    val isGroup: Boolean,
    var text: String? = null,
    val senderName: String,
    val timestamp: Long
)

data class NewsEvent(
    @Json(name = "header_text") var headerText: String,
    var text: String? = null,
    var images: List<String>? = null,
    var voices: List<String>? = null,
    var files: List<String>? = null,
)