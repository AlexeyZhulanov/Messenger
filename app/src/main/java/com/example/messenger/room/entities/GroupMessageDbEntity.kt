package com.example.messenger.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.messenger.model.Message

@Entity(tableName = "group_messages")
data class GroupMessageDbEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "group_id") val groupId: Int,
    @ColumnInfo(name = "id_sender") val idSender: Int,
    val text: String?,
    val images: List<String>?,
    val voice: String?,
    val file: String?,
    val code: String?,
    @ColumnInfo(name = "code_language") val codeLanguage: String?,
    @ColumnInfo(name = "is_read") val isRead: Boolean = false,
    val timestamp: Long,
    @ColumnInfo(name = "is_edited") val isEdited: Boolean = false,
    @ColumnInfo(name = "is_forwarded") val isForwarded: Boolean = false,
    @ColumnInfo(name = "is_url") val isUrl: Boolean? = false,
    @ColumnInfo(name = "reference_to_message_id") val referenceToMessageId: Int?,
    @ColumnInfo(name = "username_author_original") val usernameAuthorOriginal: String?,
    val waveform: List<Int>?
) {
    fun toMessage(): Message = Message(
        id = id, idSender = idSender, text = text, images = images, voice = voice, file = file,
        code = code, codeLanguage = codeLanguage,  timestamp = timestamp, isRead = isRead,
        isEdited = isEdited, referenceToMessageId = referenceToMessageId, isForwarded = isForwarded,
        isUrl = isUrl, usernameAuthorOriginal = usernameAuthorOriginal, waveform = waveform
    )
    companion object {
        fun fromUserInput(message: Message, groupId: Int): GroupMessageDbEntity = message.let {
            GroupMessageDbEntity(
                id = it.id, groupId = groupId, idSender = it.idSender, text = it.text,
                images = it.images, voice = it.voice, file = it.file, code = it.code,
                codeLanguage = it.codeLanguage, isRead = it.isRead, timestamp = it.timestamp,
                isEdited = it.isEdited, isForwarded = it.isForwarded, isUrl = it.isUrl,
                referenceToMessageId = it.referenceToMessageId,
                usernameAuthorOriginal = it.usernameAuthorOriginal, waveform = it.waveform
            )
        }
    }
}