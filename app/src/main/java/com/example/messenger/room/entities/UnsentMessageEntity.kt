package com.example.messenger.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.messenger.model.Message

@Entity(tableName = "unsent_messages")
class UnsentMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "id_dialog") val idDialog: Int,
    @ColumnInfo(name = "id_sender") val idSender: Int = -5,
    val text: String?,
    val images: List<String>?,
    val voice: String?,
    val file: String?,
    @ColumnInfo(name = "is_read") val isRead: Boolean = false,
    @ColumnInfo(name = "is_edited") val isEdited: Boolean = false,
    val timestamp: Long = 0,
    @ColumnInfo(name = "is_forwarded") val isForwarded: Boolean = false,
    @ColumnInfo(name = "reference_to_message_id") val referenceToMessageId: Int?,
    @ColumnInfo(name = "username_author_original") val usernameAuthorOriginal: String?,
    @ColumnInfo(name = "is_unsent") val isUnsent: Boolean? = false
) {
    fun toMessage(): Message = Message(
        id = id, idSender = idSender, text = text, images = images, voice = voice, file = file,
        timestamp = timestamp, isRead = isRead, isEdited = isEdited,
        referenceToMessageId = referenceToMessageId, isForwarded = isForwarded,
        usernameAuthorOriginal = usernameAuthorOriginal, isUnsent = isUnsent
    )
    companion object {
        fun fromUserInput(idDialog: Int, message: Message): UnsentMessageEntity = UnsentMessageEntity(
            idDialog = idDialog, idSender = message.idSender, text = message.text,
            images = message.images, voice = message.voice, file = message.file,
            isRead = message.isRead, isEdited = message.isEdited, timestamp = message.timestamp,
            isForwarded = message.isForwarded, referenceToMessageId = message.referenceToMessageId,
            usernameAuthorOriginal = message.usernameAuthorOriginal, isUnsent = message.isUnsent
        )
    }
}