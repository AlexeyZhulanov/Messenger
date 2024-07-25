package com.example.messenger.model

data class Message(
    val id: Int,
    val idSender: Int,
    var text: String,
    var images: List<String>,
    var voice: String,
    var file: String,
    var timeStamp: Long,
    var isRead: Boolean,
    var isEdited: Boolean
)