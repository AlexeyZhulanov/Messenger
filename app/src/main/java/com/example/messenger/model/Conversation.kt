package com.example.messenger.model

data class Conversation(
    val type: String,
    val id: Int,
    val key: String? = null,
    val otherUser: User? = null,
    val name: String? = null,
    val createdBy: Int? = null,
    val avatar: String? = null,
    val lastMessage: LastMessage? = null,
    val countMsg: Int
) {
    fun toDialog() : Dialog = Dialog(
        id = id, key = key, otherUser = otherUser!!, lastMessage = lastMessage, countMsg = countMsg)

    fun toGroup() : Group = Group(
        id = id, name = name!!, createdBy = createdBy!!,
        avatar = avatar, lastMessage = lastMessage, countMsg = countMsg)
}
data class Dialog(
    val id: Int,
    val key: String? = null,
    val otherUser: User,
    val lastMessage: LastMessage? = null,
    val countMsg: Int
)

data class Group(
    val id: Int,
    val name: String,
    val createdBy: Int,
    val avatar: String? = null,
    val lastMessage: LastMessage? = null,
    val countMsg: Int
)

data class ConversationSettings(
    val canDelete: Boolean = false,
    val autoDeleteInterval: Int = 0
)