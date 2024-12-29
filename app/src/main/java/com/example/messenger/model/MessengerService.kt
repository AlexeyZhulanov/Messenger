package com.example.messenger.model

import com.example.messenger.room.dao.ChatSettingsDao
import com.example.messenger.room.dao.ConversationDao
import com.example.messenger.room.dao.GroupMessageDao
import com.example.messenger.room.dao.LastReadMessageDao
import com.example.messenger.room.dao.MessageDao
import com.example.messenger.room.dao.SettingsDao
import com.example.messenger.room.dao.UnsentMessageDao
import com.example.messenger.room.dao.UserDao
import com.example.messenger.room.entities.ChatSettingsDbEntity
import com.example.messenger.room.entities.ConversationDbEntity
import com.example.messenger.room.entities.GroupMessageDbEntity
import com.example.messenger.room.entities.LastReadMessageEntity
import com.example.messenger.room.entities.MessageDbEntity
import com.example.messenger.room.entities.SettingsDbEntity
import com.example.messenger.room.entities.UnsentMessageEntity
import com.example.messenger.room.entities.UserDbEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class MessengerService(
    private val settingsDao: SettingsDao,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val groupMessageDao: GroupMessageDao,
    private val userDao: UserDao,
    private val lastReadMessageDao: LastReadMessageDao,
    private val chatSettingsDao: ChatSettingsDao,
    private val unsentMessageDao: UnsentMessageDao
) : MessengerRepository {
    private var settings = Settings(0)
    override suspend fun getSettings(): Settings = withContext(Dispatchers.IO) {
        val settingsEntity = settingsDao.getSettings()
            settings = settingsEntity.toSettings()
        return@withContext settings
    }

    override suspend fun updateSettings(settings: Settings) = withContext(Dispatchers.IO) {
        settingsDao.updateSettings(SettingsDbEntity.fromUserInput(settings))
    }

    override suspend fun getConversations(): List<Conversation> = withContext(Dispatchers.IO) {
        val conversationEntities = conversationDao.getConversations()

        val userIds = conversationEntities.mapNotNull { it.otherUserId }
        val lastMessageIds = conversationEntities.mapNotNull { it.lastMessageId }

        val users = conversationDao.getUsersByIds(userIds)
        val lastMessages = conversationDao.getLastMessagesByIds(lastMessageIds)

        val conversationDbEntities = conversationDao.mapToConversationDbEntity(conversationEntities, users, lastMessages)

        return@withContext conversationDbEntities.map { it.toConversation() }
    }

    override suspend fun replaceConversations(conversations: List<Conversation>) = withContext(Dispatchers.IO) {
        val conversationDbEntities = mutableListOf<ConversationDbEntity>()
        for((index, value) in conversations.withIndex()) {
            conversationDbEntities.add(ConversationDbEntity.fromUserInput(value, index))
        }
        conversationDao.replaceConversations(conversationDbEntities)
        conversationDao.cleanUpUsers()
        conversationDao.cleanUpLastMessages()
    }

    override suspend fun getMessages(idDialog: Int): List<Message> = withContext(Dispatchers.IO) {
        return@withContext messageDao.getMessages(idDialog).map { it.toMessage() }
    }

    override suspend fun replaceMessages(idDialog: Int, messages: List<Message>, fileManager: FileManager) = withContext(Dispatchers.IO) {
        val messagesDbEntities = messages.map { MessageDbEntity.fromUserInput(it, idDialog) }
        messageDao.replaceMessages(idDialog, messagesDbEntities)
        val usedFiles = messages.flatMap { message ->
            mutableListOf<String>().apply {
                message.images?.let { addAll(it) }
                message.voice?.let { add(it) }
                message.file?.let { add(it) }
            }
        }.toSet()
        fileManager.cleanupUnusedFiles(usedFiles)
    }

    override suspend fun getGroupMessages(idGroup: Int): List<GroupMessage> = withContext(Dispatchers.IO) {
        return@withContext groupMessageDao.getGroupMessages(idGroup).map { it.toGroupMessage() }
    }

    override suspend fun replaceGroupMessages(idGroup: Int, groupMessages: List<GroupMessage>, fileManager: FileManager) = withContext(Dispatchers.IO) {
        val groupMessageDbEntities = groupMessages.map { GroupMessageDbEntity.fromUserInput(it) }
        groupMessageDao.replaceGroupMessages(idGroup, groupMessageDbEntities)
        val usedFiles = groupMessages.flatMap { message ->
            mutableListOf<String>().apply {
                message.images?.let { addAll(it) }
                message.voice?.let { add(it) }
                message.file?.let { add(it) }
            }
        }.toSet()
        fileManager.cleanupUnusedFiles(usedFiles)
    }

    override suspend fun getUser(): User? = withContext(Dispatchers.IO) {
        return@withContext userDao.getUser()?.toUser()
    }

    override suspend fun updateUser(user: User) = withContext(Dispatchers.IO) {
        val existingUser = userDao.getUser()
        if(existingUser != null) {
            userDao.updateUser(UserDbEntity.fromUserInput(user))
        } else {
            userDao.insertUser(UserDbEntity.fromUserInput(user))
        }
    }

    override suspend fun getPreviousMessage(idDialog: Int, lastMessageId: Int): Message? = withContext(Dispatchers.IO) {
        return@withContext messageDao.getPreviousMessage(idDialog, lastMessageId)?.toMessage()
    }

    override suspend fun saveLastReadMessage(idDialog: Int, lastMessageId: Int) = withContext(Dispatchers.IO) {
        lastReadMessageDao.saveLastReadMessage(LastReadMessageEntity.fromUserInput(idDialog, lastMessageId))
    }

    override suspend fun getLastReadMessage(idDialog: Int): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        return@withContext lastReadMessageDao.getLastReadMessage(idDialog)?.toPair()
    }

    override suspend fun updateLastReadMessage(idDialog: Int, lastMessageId: Int) = withContext(Dispatchers.IO) {
        lastReadMessageDao.updateLastReadMessage(LastReadMessageEntity.fromUserInput(idDialog, lastMessageId))
    }

    override suspend fun isNotificationsEnabled(id: Int, type: Boolean): Boolean = withContext(Dispatchers.IO) {
        val answer = chatSettingsDao.getChatSettings(id, type)?.toChat()
        return@withContext answer == null
    }

    override suspend fun insertChatSettings(chatSettings: ChatSettings) = withContext(Dispatchers.IO) {
        chatSettingsDao.insertChatSettings(ChatSettingsDbEntity.fromUserInput(chatSettings))
    }

    override suspend fun deleteChatSettings(idDialog: Int, type: Boolean) = withContext(Dispatchers.IO) {
        chatSettingsDao.deleteChatSettings(idDialog, type)
    }

    override suspend fun insertUnsentMessage(idDialog: Int, message: Message) : Int = withContext(Dispatchers.IO) {
        val v = unsentMessageDao.insertUnsentMessage(UnsentMessageEntity.fromUserInput(idDialog, message))
        return@withContext v.toInt()
    }

    override suspend fun getUnsentMessages(idDialog: Int): List<Message>? = withContext(Dispatchers.IO) {
        return@withContext unsentMessageDao.getUnsentMessages(idDialog)?.map { it.toMessage() }
    }

    override suspend fun deleteUnsentMessage(messageId: Int) = withContext(Dispatchers.IO) {
        unsentMessageDao.deleteUnsentMessage(messageId)
    }
}