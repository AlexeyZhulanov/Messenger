package com.example.messenger.model

import com.example.messenger.room.dao.ChatSettingsDao
import com.example.messenger.room.dao.ConversationDao
import com.example.messenger.room.dao.GitlabDao
import com.example.messenger.room.dao.GroupMemberDao
import com.example.messenger.room.dao.GroupMessageDao
import com.example.messenger.room.dao.MessageDao
import com.example.messenger.room.dao.NewsDao
import com.example.messenger.room.dao.UnsentMessageDao
import com.example.messenger.room.dao.UserDao
import com.example.messenger.room.entities.ChatSettingsDbEntity
import com.example.messenger.room.entities.ConversationDbEntity
import com.example.messenger.room.entities.GitlabDbEntity
import com.example.messenger.room.entities.GroupMemberDbEntity
import com.example.messenger.room.entities.GroupMessageDbEntity
import com.example.messenger.room.entities.LastMessageEntity
import com.example.messenger.room.entities.MessageDbEntity
import com.example.messenger.room.entities.NewsDbEntity
import com.example.messenger.room.entities.UnsentMessageEntity
import com.example.messenger.room.entities.UserDbEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext


class MessengerService(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val groupMessageDao: GroupMessageDao,
    private val userDao: UserDao,
    private val chatSettingsDao: ChatSettingsDao,
    private val unsentMessageDao: UnsentMessageDao,
    private val groupMemberDao: GroupMemberDao,
    private val newsDao: NewsDao,
    private val gitlabDao: GitlabDao,
    private val ioDispatcher: CoroutineDispatcher
) : MessengerRepository {

    override suspend fun getConversations(): List<Conversation> = withContext(ioDispatcher) {
        val conversationEntities = conversationDao.getConversations()
        val userIds = conversationEntities.mapNotNull { it.otherUserId }
        val lastMessagePairs = conversationEntities.map { it.chatId to it.type }

        val users = conversationDao.getUsersByIds(userIds)
        val lastMessages = conversationDao.getLastMessagesByPairs(lastMessagePairs)

        val conversationDbEntities = conversationDao.mapToConversationDbEntity(conversationEntities, users, lastMessages)
        return@withContext conversationDbEntities.map { it.toConversation() }
    }

    override suspend fun getConversationByTypeAndId(type: String, chatId: Int): Conversation? = withContext(ioDispatcher) {
        val conversationEntity = conversationDao.getConversationByTypeAndId(type, chatId) ?: return@withContext null
        val conversationDbEntity = if(conversationEntity.type == "dialog") {
            val userId = conversationEntity.otherUserId ?: return@withContext null
            val userEntity = conversationDao.getUserById(userId)
            ConversationDbEntity(conversationEntity, userEntity, LastMessageEntity(-1, ""))
        } else ConversationDbEntity(conversationEntity, null, LastMessageEntity(-1, ""))

        return@withContext conversationDbEntity.toConversation()
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

    override suspend fun deleteCurrentUser() = withContext(ioDispatcher) {
        userDao.deleteAllUsers()
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
        return@withContext v.toInt()+100000
    }

    override suspend fun insertUnsentMessageGroup(idGroup: Int, message: Message): Int = withContext(ioDispatcher) {
        val v = unsentMessageDao.insertUnsentMessage(UnsentMessageEntity.fromUserInput(message,
            idDialog = null, idGroup = idGroup))
        return@withContext v.toInt()+100000
    }

    override suspend fun getUnsentMessages(idDialog: Int): List<Message>? = withContext(ioDispatcher) {
        return@withContext unsentMessageDao.getUnsentMessages(idDialog)?.map { it.toMessage() }
    }

    override suspend fun getUnsentMessagesGroup(idGroup: Int): List<Message>? = withContext(ioDispatcher) {
        return@withContext unsentMessageDao.getUnsentMessagesGroup(idGroup)?.map { it.toMessage() }
    }

    override suspend fun deleteUnsentMessage(messageId: Int) = withContext(ioDispatcher) {
        unsentMessageDao.deleteUnsentMessage(messageId-100000)
    }

    override suspend fun getGroupMembers(groupId: Int): List<User> = withContext(ioDispatcher) {
        return@withContext groupMemberDao.getMembers(groupId).map { it.toUser() }
    }

    override suspend fun replaceGroupMembers(groupId: Int, groupMembers: List<User>) = withContext(ioDispatcher) {
        val groupMemberDbEntities = groupMembers.map { GroupMemberDbEntity.fromUserInput(groupId, it) }
        groupMemberDao.replaceMembers(groupId, groupMemberDbEntities)
    }

    override suspend fun getNews(): List<News> = withContext(ioDispatcher) {
        return@withContext newsDao.getNews().map { it.toNews() }
    }

    override suspend fun replaceNews(newNews: List<News>, fileManager: FileManager) = withContext(ioDispatcher) {
        val newsDbEntity = newNews.map { NewsDbEntity.fromUserInput(it) }
        newsDao.replaceNews(newsDbEntity)
        val usedFiles = newNews.flatMap { news ->
            mutableListOf<String>().apply {
                news.images?.let { addAll(it) }
                news.voices?.let { addAll(it) }
                news.files?.let { addAll(it) }
            }
        }.toSet()
        fileManager.cleanupUnusedMessageFiles(usedFiles)
    }

    override suspend fun getRepos(): List<Repo> = withContext(ioDispatcher) {
        return@withContext gitlabDao.getRepos().map { it.toRepo() }
    }

    override suspend fun replaceRepos(newRepos: List<Repo>) = withContext(ioDispatcher) {
        val gitlabDbEntity = newRepos.map { GitlabDbEntity.fromUserInput(it) }
        gitlabDao.replaceRepos(gitlabDbEntity)
    }
}