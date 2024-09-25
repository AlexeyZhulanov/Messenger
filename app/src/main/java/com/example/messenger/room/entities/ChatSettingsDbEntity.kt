package com.example.messenger.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.messenger.model.ChatSettings

@Entity(tableName = "chat_settings")
data class ChatSettingsDbEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "id_chat") val chatId: Int,
    val type: Boolean, // false - dialog, true - group
) {
    fun toChat(): ChatSettings = ChatSettings(chatId = chatId, type = type)

    companion object {
        fun fromUserInput(chatSettings: ChatSettings): ChatSettingsDbEntity = ChatSettingsDbEntity(
            id = 0, chatId = chatSettings.chatId, type = chatSettings.type
        )
    }
}