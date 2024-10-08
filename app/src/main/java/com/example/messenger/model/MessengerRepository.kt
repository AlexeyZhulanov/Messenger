package com.example.messenger.model

interface MessengerRepository {

    suspend fun getSettings(): Settings

    suspend fun updateSettings(settings: Settings)

    suspend fun getConversations(): List<Conversation>

    suspend fun replaceConversations(conversations: List<Conversation>)

    suspend fun getMessages(idDialog: Int): List<Message>

    suspend fun replaceMessages(idDialog: Int, messages: List<Message>, fileManager: FileManager)

    suspend fun getGroupMessages(idGroup: Int): List<GroupMessage>

    suspend fun replaceGroupMessages(idGroup: Int, groupMessages: List<GroupMessage>, fileManager: FileManager)

    suspend fun getUser(): User?

    suspend fun updateUser(user: User)

    suspend fun getPreviousMessage(idDialog: Int, lastMessageId: Int): Message?

    suspend fun saveLastReadMessage(idDialog: Int, lastMessageId: Int)

    suspend fun getLastReadMessage(idDialog: Int): Pair<Int, Int>?

    suspend fun updateLastReadMessage(idDialog: Int, lastMessageId: Int)

    suspend fun isNotificationsEnabled(id: Int, type: Boolean) : Boolean

    suspend fun insertChatSettings(chatSettings: ChatSettings)

    suspend fun deleteChatSettings(idDialog: Int, type: Boolean)
}