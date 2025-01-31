package com.example.messenger.model

import android.util.Log
import com.example.messenger.room.dao.ChatSettingsDao
import com.example.messenger.room.dao.ConversationDao
import com.example.messenger.room.dao.GroupMemberDao
import com.example.messenger.room.dao.GroupMessageDao
import com.example.messenger.room.dao.LastReadMessageDao
import com.example.messenger.room.dao.MessageDao
import com.example.messenger.room.dao.SettingsDao
import com.example.messenger.room.dao.UnsentMessageDao
import com.example.messenger.room.dao.UserDao
import com.example.messenger.room.entities.ChatSettingsDbEntity
import com.example.messenger.room.entities.ConversationDbEntity
import com.example.messenger.room.entities.GroupMemberDbEntity
import com.example.messenger.room.entities.GroupMessageDbEntity
import com.example.messenger.room.entities.LastReadMessageEntity
import com.example.messenger.room.entities.MessageDbEntity
import com.example.messenger.room.entities.SettingsDbEntity
import com.example.messenger.room.entities.UnsentMessageEntity
import com.example.messenger.room.entities.UserDbEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext


class MessengerService(
    private val settingsDao: SettingsDao,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val groupMessageDao: GroupMessageDao,
    private val userDao: UserDao,
    private val lastReadMessageDao: LastReadMessageDao,
    private val chatSettingsDao: ChatSettingsDao,
    private val unsentMessageDao: UnsentMessageDao,
    private val groupMemberDao: GroupMemberDao,
    private val ioDispatcher: CoroutineDispatcher
) : MessengerRepository {
    private var settings = Settings(0)
    override suspend fun getSettings(): Settings = withContext(ioDispatcher) {
        val settingsEntity = settingsDao.getSettings()
            settings = settingsEntity.toSettings()
        return@withContext settings
    }

    override suspend fun updateSettings(settings: Settings) = withContext(ioDispatcher) {
        settingsDao.updateSettings(SettingsDbEntity.fromUserInput(settings))
    }

    override suspend fun getConversations(): List<Conversation> = withContext(ioDispatcher) {
        val conversationEntities = conversationDao.getConversations()

        val userIds = conversationEntities.mapNotNull { it.otherUserId }
        val lastMessageIds = conversationEntities.mapNotNull { it.lastMessageId }

        val users = conversationDao.getUsersByIds(userIds)
        val lastMessages = conversationDao.getLastMessagesByIds(lastMessageIds)

        val conversationDbEntities = conversationDao.mapToConversationDbEntity(conversationEntities, users, lastMessages)

        return@withContext conversationDbEntities.map { it.toConversation() }
    }

    override suspend fun replaceConversations(conversations: List<Conversation>) = withContext(ioDispatcher) {
        val conversationDbEntities = mutableListOf<ConversationDbEntity>()
        for((index, value) in conversations.withIndex()) {
            conversationDbEntities.add(ConversationDbEntity.fromUserInput(value, index))
        }
        conversationDao.replaceConversations(conversationDbEntities)
        conversationDao.cleanUpUsers()
        conversationDao.cleanUpLastMessages()
    }

    override suspend fun getMessages(idDialog: Int): List<Message> = withContext(ioDispatcher) {
        return@withContext messageDao.getMessages(idDialog).map { it.toMessage() }
    }

    override suspend fun replaceMessages(idDialog: Int, messages: List<Message>, fileManager: FileManager) = withContext(ioDispatcher) {
        val messagesDbEntities = messages.map { MessageDbEntity.fromUserInput(it, idDialog) }
        messageDao.replaceMessages(idDialog, messagesDbEntities)
        val usedFiles = messages.flatMap { message ->
            mutableListOf<String>().apply {
                message.images?.let { addAll(it) }
                message.voice?.let { add(it) }
                message.file?.let { add(it) }
            }
        }.toSet()
        fileManager.cleanupUnusedMessageFiles(usedFiles)
    }

    override suspend fun getGroupMessages(idGroup: Int): List<Message> = withContext(ioDispatcher) {
        return@withContext groupMessageDao.getGroupMessages(idGroup).map { it.toMessage() }
    }

    override suspend fun replaceGroupMessages(idGroup: Int, groupMessages: List<Message>, fileManager: FileManager) = withContext(ioDispatcher) {
        val groupMessageDbEntities = groupMessages.map { GroupMessageDbEntity.fromUserInput(it, idGroup) }
        groupMessageDao.replaceGroupMessages(idGroup, groupMessageDbEntities)
        val usedFiles = groupMessages.flatMap { message ->
            mutableListOf<String>().apply {
                message.images?.let { addAll(it) }
                message.voice?.let { add(it) }
                message.file?.let { add(it) }
            }
        }.toSet()
        fileManager.cleanupUnusedMessageFiles(usedFiles)
    }

    override suspend fun getUser(): User? = withContext(ioDispatcher) {
        return@withContext userDao.getUser()?.toUser()
    }

    override suspend fun updateUser(user: User) = withContext(ioDispatcher) {
        val existingUser = userDao.getUser()
        if(existingUser != null) {
            userDao.updateUser(UserDbEntity.fromUserInput(user))
        } else {
            userDao.insertUser(UserDbEntity.fromUserInput(user))
        }
    }

    override suspend fun getPreviousMessage(idDialog: Int, lastMessageId: Int): Message? = withContext(ioDispatcher) {
        return@withContext messageDao.getPreviousMessage(idDialog, lastMessageId)?.toMessage()
    }

    override suspend fun getPreviousMessageGroup(groupId: Int, lastMessageId: Int): Message? = withContext(ioDispatcher) {
        return@withContext groupMessageDao.getPreviousMessage(groupId, lastMessageId)?.toMessage()
    }

    override suspend fun saveLastReadMessage(lastMessageId: Int, idDialog: Int?, idGroup: Int?) = withContext(ioDispatcher) {
        lastReadMessageDao.saveLastReadMessage(LastReadMessageEntity.fromUserInput(lastMessageId, idDialog, idGroup))
    }

    override suspend fun getLastReadMessage(idDialog: Int): Int? = withContext(ioDispatcher) {
        return@withContext lastReadMessageDao.getLastReadMessage(idDialog)?.toEntity()
    }

    override suspend fun getLastReadMessageGroup(idGroup: Int): Int? = withContext(ioDispatcher) {
        return@withContext lastReadMessageDao.getLastReadMessageGroup(idGroup)?.toEntity()
    }

    override suspend fun updateLastReadMessage(lastMessageId: Int, idDialog: Int?, idGroup: Int?) = withContext(ioDispatcher) {
        lastReadMessageDao.updateLastReadMessage(LastReadMessageEntity.fromUserInput(lastMessageId, idDialog, idGroup))
    }

    override suspend fun isNotificationsEnabled(id: Int, type: Boolean): Boolean = withContext(ioDispatcher) {
        val answer = chatSettingsDao.getChatSettings(id, type)?.toChat()
        return@withContext answer == null
    }

    override suspend fun insertChatSettings(chatSettings: ChatSettings) = withContext(ioDispatcher) {
        chatSettingsDao.insertChatSettings(ChatSettingsDbEntity.fromUserInput(chatSettings))
    }

    override suspend fun deleteChatSettings(idDialog: Int, type: Boolean) = withContext(ioDispatcher) {
        chatSettingsDao.deleteChatSettings(idDialog, type)
    }

    override suspend fun insertUnsentMessage(idDialog: Int, message: Message) : Int = withContext(ioDispatcher) {
        val v = unsentMessageDao.insertUnsentMessage(UnsentMessageEntity.fromUserInput(message,
            idDialog = idDialog, idGroup = null))
        return@withContext v.toInt()
    }

    override suspend fun insertUnsentMessageGroup(idGroup: Int, message: Message): Int = withContext(ioDispatcher) {
        val v = unsentMessageDao.insertUnsentMessage(UnsentMessageEntity.fromUserInput(message,
            idDialog = null, idGroup = idGroup))
        return@withContext v.toInt()
    }

    override suspend fun getUnsentMessages(idDialog: Int): List<Message>? = withContext(ioDispatcher) {
        return@withContext unsentMessageDao.getUnsentMessages(idDialog)?.map { it.toMessage() }
    }

    override suspend fun getUnsentMessagesGroup(idGroup: Int): List<Message>? = withContext(ioDispatcher) {
        return@withContext unsentMessageDao.getUnsentMessagesGroup(idGroup)?.map { it.toMessage() }
    }

    override suspend fun deleteUnsentMessage(messageId: Int) = withContext(ioDispatcher) {
        unsentMessageDao.deleteUnsentMessage(messageId)
    }

    override suspend fun getGroupMembers(groupId: Int): List<User> = withContext(ioDispatcher) {
        return@withContext groupMemberDao.getMembers(groupId).map { it.toUser() }
    }

    override suspend fun replaceGroupMembers(groupId: Int, groupMembers: List<User>) {
        val groupMemberDbEntities = groupMembers.map { GroupMemberDbEntity.fromUserInput(groupId, it) }
        groupMemberDao.replaceMembers(groupId, groupMemberDbEntities)
    }
}