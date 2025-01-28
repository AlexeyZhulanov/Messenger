package com.example.messenger.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "last_read_message",
    indices = [
        Index("dialog_id", unique = true),
        Index("group_id", unique = true)
    ]
)
data class LastReadMessageEntity(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "dialog_id") val dialogId: Int?,
    @ColumnInfo(name = "group_id") val groupId: Int?,
    @ColumnInfo(name = "last_read_message_id") val lastReadMessageId: Int
) {

    fun toEntity(): Int = lastReadMessageId

    companion object {
        fun fromUserInput(lastReadMessageId: Int, dialogId: Int?, groupId: Int?): LastReadMessageEntity = LastReadMessageEntity(
            lastReadMessageId = lastReadMessageId, dialogId = dialogId, groupId = groupId
        )
    }
}