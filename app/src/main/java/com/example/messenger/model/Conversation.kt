package com.example.messenger.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

data class Conversation(
    val type: String,
    val id: Int,
    val key: String? = null,
    val otherUser: User? = null,
    val name: String? = null,
    val createdBy: Int? = null,
    val avatar: String? = null,
    val lastMessage: LastMessage,
    val countMsg: Int,
    val unreadCount: Int,
    val isOwner: Boolean,
    val canDelete: Boolean,
    val autoDeleteInterval: Int
) {
    fun toDialog() : Dialog = Dialog(
        id = id, key = key, otherUser = otherUser!!, lastMessage = lastMessage, countMsg = countMsg,
        unreadCount = unreadCount, isOwner = isOwner, canDelete = canDelete, autoDeleteInterval = autoDeleteInterval)

    fun toGroup() : Group = Group(
        id = id, key = key, name = name!!, createdBy = createdBy!!, avatar = avatar, lastMessage = lastMessage,
        countMsg = countMsg, unreadCount = unreadCount, isOwner = isOwner, canDelete = canDelete, autoDeleteInterval = autoDeleteInterval)
}

@Parcelize
data class Dialog(
    val id: Int,
    val key: String? = null,
    val otherUser: User,
    val lastMessage: LastMessage,
    val countMsg: Int,
    val unreadCount: Int,
    val isOwner: Boolean,
    val canDelete: Boolean,
    val autoDeleteInterval: Int
) : Parcelable

@Parcelize
data class Group(
    val id: Int,
    val key: String? = null,
    val name: String,
    val createdBy: Int,
    val avatar: String? = null,
    val lastMessage: LastMessage,
    val countMsg: Int,
    val unreadCount: Int,
    val isOwner: Boolean,
    val canDelete: Boolean,
    val autoDeleteInterval: Int
) : Parcelable
