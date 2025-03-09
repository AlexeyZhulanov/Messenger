package com.example.messenger.room.entities

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.messenger.model.Conversation
import com.example.messenger.model.LastMessage
import com.example.messenger.model.User

data class ConversationDbEntity(
    @Embedded val conversation: ConversationEntity,
    @Relation(
        parentColumn = "other_user_id",
        entityColumn = "id"
    )
    val otherUser: UserEntity?,
    @Relation(
        parentColumn = "last_message_id",
        entityColumn = "id"
    )
    val lastMessage: LastMessageEntity
) {
    fun toConversation(): Conversation = Conversation(
        id = conversation.id, type = conversation.type, key = conversation.key,
        otherUser = otherUser?.toUser(), name = conversation.name,
        createdBy = conversation.createdBy, avatar = conversation.avatar,
        lastMessage = lastMessage.toLastMessage(), countMsg = conversation.countMsg,
        canDelete = conversation.canDelete, autoDeleteInterval = conversation.autoDeleteInterval
    )
    companion object {
        fun fromUserInput(conversation: Conversation, orderIndex: Int): ConversationDbEntity {
            val otherUserEntity = conversation.otherUser?.let {
                UserEntity(
                    id = it.id,
                    name = it.name,
                    username = it.username,
                    avatar = it.avatar,
                    lastSession = it.lastSession
                )
            }

            val lastMessageEntity = conversation.lastMessage.let {
                LastMessageEntity(
                    id = conversation.id,
                    text = it.text,
                    timestamp = it.timestamp,
                    isRead = it.isRead
                )
            }

            val conversationEntity = ConversationEntity(
                id = conversation.id,
                type = conversation.type,
                key = conversation.key,
                otherUserId = otherUserEntity?.id,
                name = conversation.name,
                createdBy = conversation.createdBy,
                avatar = conversation.avatar,
                lastMessageId = lastMessageEntity.id,
                countMsg = conversation.countMsg,
                canDelete = conversation.canDelete,
                autoDeleteInterval = conversation.autoDeleteInterval,
                orderIndex = orderIndex
            )

            return ConversationDbEntity(
                conversation = conversationEntity,
                otherUser = otherUserEntity,
                lastMessage = lastMessageEntity
            )
        }
    }
}

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: Int,
    var type: String,
    var key: String? = null,
    @ColumnInfo(name = "other_user_id") var otherUserId: Int? = null,
    var name: String? = null,
    @ColumnInfo(name = "created_by") var createdBy: Int? = null,
    var avatar: String? = null,
    @ColumnInfo(name = "last_message_id") var lastMessageId: Int? = null,
    @ColumnInfo(name = "count_msg") var countMsg: Int,
    @ColumnInfo(name = "can_delete") var canDelete: Boolean,
    @ColumnInfo(name = "auto_delete_interval") var autoDeleteInterval: Int,
    @ColumnInfo(name = "order_index") var orderIndex: Int
)

@Entity(
    tableName = "users",
    indices = [
        Index("name", unique = true)
    ]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val name: String,
    var username: String,
    var avatar: String? = "default",
    @ColumnInfo(name = "last_session") var lastSession: Long? = 0
) {
    fun toUser(): User = User(
        id = id, name = name, username = username, avatar = avatar, lastSession = lastSession
    )
}

@Entity(tableName = "last_messages")
data class LastMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int,
    var text: String? = null,
    var timestamp: Long? = null,
    @ColumnInfo(name = "is_read") var isRead: Boolean? = null
) {
    fun toLastMessage(): LastMessage = LastMessage(
        text = text, timestamp = timestamp, isRead = isRead
    )
}