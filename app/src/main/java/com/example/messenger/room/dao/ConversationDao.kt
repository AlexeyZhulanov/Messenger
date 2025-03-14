package com.example.messenger.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.messenger.room.entities.ConversationDbEntity
import com.example.messenger.room.entities.ConversationEntity
import com.example.messenger.room.entities.LastMessageEntity
import com.example.messenger.room.entities.UserEntity

@Dao
interface ConversationDao {
    @Transaction
    @Query("SELECT * FROM conversations ORDER BY order_index ASC")
    suspend fun getConversations(): List<ConversationEntity>

    fun mapToConversationDbEntity(
        conversationEntities: List<ConversationEntity>,
        users: List<UserEntity>,
        lastMessages: List<LastMessageEntity>
    ): List<ConversationDbEntity> {
        val usersById = users.associateBy { it.id }
        val messagesByChatIdAndType = lastMessages.associateBy { Pair(it.chatId, it.type) }

        return conversationEntities.map { conversationEntity ->
            val otherUser = usersById[conversationEntity.otherUserId]
            val lastMessage = messagesByChatIdAndType[Pair(conversationEntity.chatId, conversationEntity.type)]
            ConversationDbEntity(
                conversation = conversationEntity,
                otherUser = otherUser,
                lastMessage = lastMessage ?: LastMessageEntity(
                    chatId = conversationEntity.chatId,
                    type = conversationEntity.type,
                    text = null,
                    timestamp = null,
                    isRead = null
                )
            )
        }
    }

    @Transaction
    @Query("SELECT * FROM conversations WHERE type = :type AND chat_id = :chatId LIMIT 1")
    suspend fun getConversationByTypeAndId(type: String, chatId: Int): ConversationEntity?

    @Transaction
    suspend fun replaceConversations(conversations: List<ConversationDbEntity>) {
        deleteAllConversations()
        val conversationEntities = conversations.map { it.conversation }
        insertConversations(conversationEntities)
        val usersEntities = conversations.mapNotNull { it.otherUser }
        insertUsers(usersEntities)
        val lastMessageEntities = conversations.map { it.lastMessage }
        insertLastMessages(lastMessageEntities)
    }

    @Query("SELECT * FROM users WHERE id IN (:userIds)")
    suspend fun getUsersByIds(userIds: List<Int>): List<UserEntity>

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: Int): UserEntity


    @Query("SELECT * FROM last_messages WHERE chat_id = :chatId AND type = :type LIMIT 1")
    suspend fun getLastMessageByChatIdAndType(chatId: Int, type: String): LastMessageEntity?

    suspend fun getLastMessagesByPairs(pairs: List<Pair<Int, String>>): List<LastMessageEntity> {
        return pairs.mapNotNull { (chatId, type) ->
            getLastMessageByChatIdAndType(chatId, type)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(conversations: List<ConversationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLastMessages(messages: List<LastMessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLastMessage(message: LastMessageEntity): Long

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()

    @Query("DELETE FROM users WHERE id NOT IN (SELECT DISTINCT other_user_id FROM conversations WHERE other_user_id IS NOT NULL)")
    suspend fun cleanUpUsers()

    @Query("DELETE FROM last_messages WHERE (chat_id, type) NOT IN (SELECT DISTINCT chat_id, type FROM conversations)")
    suspend fun cleanUpLastMessages()
}