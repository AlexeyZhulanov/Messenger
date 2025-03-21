package com.example.messenger.model

interface MessengerRepository {

    suspend fun getConversations(): List<Conversation>

    suspend fun getConversationByTypeAndId(type: String, chatId: Int): Conversation?

    suspend fun replaceConversations(conversations: List<Conversation>)

    suspend fun getMessages(idDialog: Int): List<Message>

    suspend fun replaceMessages(idDialog: Int, messages: List<Message>, fileManager: FileManager)

    suspend fun getGroupMessages(idGroup: Int): List<Message>

    suspend fun replaceGroupMessages(idGroup: Int, groupMessages: List<Message>, fileManager: FileManager)

    suspend fun getUser(): User?

    suspend fun updateUser(user: User)

    suspend fun deleteCurrentUser()

    suspend fun isNotificationsEnabled(id: Int, type: Boolean) : Boolean

    suspend fun insertChatSettings(chatSettings: ChatSettings)

    suspend fun deleteChatSettings(idDialog: Int, type: Boolean)

    suspend fun insertUnsentMessage(idDialog: Int, message: Message) : Int

    suspend fun insertUnsentMessageGroup(idGroup: Int, message: Message) : Int

    suspend fun getUnsentMessages(idDialog: Int) : List<Message>?

    suspend fun getUnsentMessagesGroup(idGroup: Int) : List<Message>?

    suspend fun deleteUnsentMessage(messageId: Int)

    suspend fun getGroupMembers(groupId: Int) : List<User>

    suspend fun replaceGroupMembers(groupId: Int, groupMembers: List<User>)

    suspend fun getNews() : List<News>

    suspend fun replaceNews(newNews: List<News>, fileManager: FileManager)
}