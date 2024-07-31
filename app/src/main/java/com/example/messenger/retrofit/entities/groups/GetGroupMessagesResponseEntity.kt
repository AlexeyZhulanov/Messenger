package com.example.messenger.retrofit.entities.groups

import com.example.messenger.model.GroupMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Message(
    val id: Int,
    @Json(name = "sender_id") val senderId: Int,
    @Json(name = "group_id") val groupId: Int,
    var text: String? = null,
    var images: List<String>? = null,
    var voice: String? = null,
    var file: String? = null,
    var timestamp: Long,
    @Json(name = "is_read") var isRead: Boolean,
    @Json(name = "is_edited") var isEdited: Boolean
) {
    fun toGroupMessage() : GroupMessage = GroupMessage(
        id = id, senderId = senderId, groupId = groupId, text = text, images = images,
        voice = voice, file = file, timestamp = timestamp, isRead = isRead, isEdited = isEdited
    )
}