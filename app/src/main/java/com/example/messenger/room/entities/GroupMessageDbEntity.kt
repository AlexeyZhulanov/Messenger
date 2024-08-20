package com.example.messenger.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.messenger.model.GroupMessage

@Entity(tableName = "group_messages")
data class GroupMessageDbEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "group_id") val groupId: Int,
    @ColumnInfo(name = "id_sender") val idSender: Int,
    val text: String?,
    val images: List<String>?,
    val voice: String?,
    val file: String?,
    @ColumnInfo(name = "is_read") val isRead: Boolean = false,
    val timestamp: Long,
    @ColumnInfo(name = "is_edited") val isEdited: Boolean = false,
    @ColumnInfo(name = "is_forwarded") val isForwarded: Boolean = false,
    @ColumnInfo(name = "reference_to_message_id") val referenceToMessageId: Int?,
    @ColumnInfo(name = "username_author_original") val usernameAuthorOriginal: String?
) {
    fun toGroupMessage(): GroupMessage = GroupMessage(
        id = id, senderId = idSender, groupId = groupId, text = text, images = images,
        voice = voice, file = file, timestamp = timestamp, isRead = isRead, isEdited = isEdited,
        referenceToMessageId = referenceToMessageId, isForwarded = isForwarded,
        usernameAuthorOriginal = usernameAuthorOriginal
    )
    companion object {
        fun fromUserInput(groupMessage: GroupMessage): GroupMessageDbEntity = groupMessage.let {
            GroupMessageDbEntity(
                id = it.id, groupId = it.groupId, idSender = it.senderId, text = it.text,
                images = it.images, voice = it.voice, file = it.file, isRead = it.isRead,
                timestamp = it.timestamp, isEdited = it.isEdited, isForwarded = it.isForwarded,
                referenceToMessageId = it.referenceToMessageId,
                usernameAuthorOriginal = it.usernameAuthorOriginal
            )
        }
    }
}