package com.example.messenger.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.messenger.model.Message

@Entity(tableName = "unsent_messages")
class UnsentMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "id_dialog") val idDialog: Int?,
    @ColumnInfo(name = "id_group") val idGroup: Int?,
    @ColumnInfo(name = "id_sender") val idSender: Int = -5,
    val text: String?,
    val images: List<String>?,
    val voice: String?,
    val file: String?,
    val code: String?,
    @ColumnInfo(name = "code_language") val codeLanguage: String?,
    @ColumnInfo(name = "is_read") val isRead: Boolean = false,
    @ColumnInfo(name = "is_edited") val isEdited: Boolean = false,
    val timestamp: Long = 0,
    @ColumnInfo(name = "is_forwarded") val isForwarded: Boolean = false,
    @ColumnInfo(name = "is_url") val isUrl: Boolean? = false,
    @ColumnInfo(name = "reference_to_message_id") val referenceToMessageId: Int?,
    @ColumnInfo(name = "username_author_original") val usernameAuthorOriginal: String?,
    @ColumnInfo(name = "is_unsent") val isUnsent: Boolean? = false,
    @ColumnInfo(name = "local_file_paths") val localFilePaths: List<String>? = null
) {
    fun toMessage(): Message = Message(
        id = id+100000, idSender = idSender, text = text, images = images, voice = voice, file = file,
        code = code, codeLanguage = codeLanguage, timestamp = timestamp, isRead = isRead, isEdited = isEdited,
        referenceToMessageId = referenceToMessageId, isForwarded = isForwarded, isUrl = isUrl,
        usernameAuthorOriginal = usernameAuthorOriginal, isUnsent = isUnsent, localFilePaths = localFilePaths
    )
    companion object {
        fun fromUserInput(message: Message, idDialog: Int?, idGroup: Int?): UnsentMessageEntity = UnsentMessageEntity(
            idDialog = idDialog, idGroup = idGroup, idSender = message.idSender,
            text = message.text, images = message.images, voice = message.voice, file = message.file,
            code = message.code, codeLanguage = message.codeLanguage, isRead = message.isRead,
            isEdited = message.isEdited, timestamp = message.timestamp, isForwarded = message.isForwarded,
            referenceToMessageId = message.referenceToMessageId,
            usernameAuthorOriginal = message.usernameAuthorOriginal, isUnsent = message.isUnsent,
            localFilePaths = message.localFilePaths, isUrl = message.isUrl
        )
    }
}