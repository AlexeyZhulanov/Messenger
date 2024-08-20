package com.example.messenger.model

import com.example.messenger.room.dao.ConversationDao
import com.example.messenger.room.dao.GroupMessageDao
import com.example.messenger.room.dao.MessageDao
import com.example.messenger.room.dao.SettingsDao
import com.example.messenger.room.entities.ConversationDbEntity
import com.example.messenger.room.entities.GroupMessageDbEntity
import com.example.messenger.room.entities.MessageDbEntity
import com.example.messenger.room.entities.SettingsDbEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class MessengerService(
    private val settingsDao: SettingsDao,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val groupMessageDao: GroupMessageDao
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
        return@withContext conversationDao.getConversations().map { it.toConversation() }
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

    override suspend fun replaceMessages(idDialog: Int, messages: List<Message>) = withContext(Dispatchers.IO) {
        val messagesDbEntities = messages.map { MessageDbEntity.fromUserInput(it, idDialog) }
        messageDao.replaceMessages(idDialog, messagesDbEntities)
    }

    override suspend fun getGroupMessages(idGroup: Int): List<GroupMessage> = withContext(Dispatchers.IO) {
        return@withContext groupMessageDao.getGroupMessages(idGroup).map { it.toGroupMessage() }
    }

    override suspend fun replaceGroupMessages(idGroup: Int, groupMessages: List<GroupMessage>) = withContext(Dispatchers.IO) {
        val groupMessageDbEntities = groupMessages.map { GroupMessageDbEntity.fromUserInput(it) }
        groupMessageDao.replaceGroupMessages(idGroup, groupMessageDbEntities)
    }
}