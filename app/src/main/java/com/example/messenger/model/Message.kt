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
    var isEdited: Boolean,
    var referenceToMessageId: Int? = null,
    var isForwarded: Boolean,
    var usernameAuthorOriginal: String? = null
)

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