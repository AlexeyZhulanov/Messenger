package com.example.messenger.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "last_read_message",
    indices = [
        Index("dialog_id", unique = true)
    ]
)
data class LastReadMessageEntity(
    @PrimaryKey @ColumnInfo(name = "dialog_id") val dialogId: Int,
    @ColumnInfo(name = "last_read_message_id") val lastReadMessageId: Int
) {

    fun toPair(): Pair<Int, Int> = Pair(dialogId, lastReadMessageId)

    companion object {
        fun fromUserInput(dialogId: Int, lastReadMessageId: Int): LastReadMessageEntity = LastReadMessageEntity(
            dialogId = dialogId, lastReadMessageId = lastReadMessageId
        )
    }
}