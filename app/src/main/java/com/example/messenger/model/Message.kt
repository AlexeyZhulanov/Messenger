package com.example.messenger.model

data class Message(
    val id: Int,
    val idSender: Int,
    var text: String? = null,
    var images: List<String>? = null,
    var voice: String? = null,
    var file: String? = null,
    var timestamp: Long,
    var isRead: Boolean,
    var isEdited: Boolean
)

data class LastMessage(
    val text: String? = null,
    val timestamp: Long,
    val isRead: Boolean
)