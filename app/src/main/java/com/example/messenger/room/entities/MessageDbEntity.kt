package com.example.messenger.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.messenger.model.Message

@Entity(tableName = "messages")
data class MessageDbEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "id_dialog") val idDialog: Int,
    @ColumnInfo(name = "id_sender") val idSender: Int,
    val text: String?,
    val images: List<String>?,
    val voice: String?,
    val file: String?,
    val code: String?,
    @ColumnInfo(name = "code_language") val codeLanguage: String?,
    @ColumnInfo(name = "is_read") val isRead: Boolean = false,
    @ColumnInfo(name = "is_edited") val isEdited: Boolean = false,
    @ColumnInfo(name = "is_url") val isUrl: Boolean? = false,
    val timestamp: Long,
    @ColumnInfo(name = "is_forwarded") val isForwarded: Boolean = false,
    @ColumnInfo(name = "reference_to_message_id") val referenceToMessageId: Int?,
    @ColumnInfo(name = "username_author_original") val usernameAuthorOriginal: String?
) {
    fun toMessage(): Message = Message(
        id = id, idSender = idSender, text = text, images = images, voice = voice, file = file,
        code = code, codeLanguage = codeLanguage, timestamp = timestamp, isRead = isRead, isEdited = isEdited,
        referenceToMessageId = referenceToMessageId, isForwarded = isForwarded, isUrl = isUrl,
        usernameAuthorOriginal = usernameAuthorOriginal
    )
    companion object {
        fun fromUserInput(message: Message, idDialog: Int): MessageDbEntity = MessageDbEntity(
            id = message.id, idDialog = idDialog, idSender = message.idSender, text = message.text,
            images = message.images, voice = message.voice, file = message.file, code = message.code,
            codeLanguage = message.codeLanguage, isRead = message.isRead, isEdited = message.isEdited,
            timestamp = message.timestamp, isForwarded = message.isForwarded,
            referenceToMessageId = message.referenceToMessageId,
            usernameAuthorOriginal = message.usernameAuthorOriginal, isUrl = message.isUrl
        )
    }
}