package com.example.messenger.model

interface MessengerRepository {

    suspend fun getSettings(): Settings

    suspend fun updateSettings(settings: Settings)

    suspend fun getConversations(): List<Conversation>

    suspend fun replaceConversations(conversations: List<Conversation>)

    suspend fun getMessages(idDialog: Int): List<Message>

    suspend fun replaceMessages(idDialog: Int, messages: List<Message>, fileManager: FileManager)

    suspend fun getGroupMessages(idGroup: Int): List<Message>

    suspend fun replaceGroupMessages(idGroup: Int, groupMessages: List<Message>, fileManager: FileManager)

    suspend fun getUser(): User?

    suspend fun updateUser(user: User)

    suspend fun getPreviousMessage(idDialog: Int, lastMessageId: Int): Message?

    suspend fun saveLastReadMessage(lastMessageId: Int, idDialog: Int?, idGroup: Int?)

    suspend fun getLastReadMessage(idDialog: Int): Int?

    suspend fun getLastReadMessageGroup(idGroup: Int): Int?

    suspend fun updateLastReadMessage(lastMessageId: Int, idDialog: Int?, idGroup: Int?)

    suspend fun isNotificationsEnabled(id: Int, type: Boolean) : Boolean

    suspend fun insertChatSettings(chatSettings: ChatSettings)

    suspend fun deleteChatSettings(idDialog: Int, type: Boolean)

    suspend fun insertUnsentMessage(idDialog: Int, message: Message) : Int

    suspend fun insertUnsentMessageGroup(idGroup: Int, message: Message) : Int

    suspend fun getUnsentMessages(idDialog: Int) : List<Message>?

    suspend fun getUnsentMessagesGroup(idGroup: Int) : List<Message>?

    suspend fun deleteUnsentMessage(messageId: Int)

    suspend fun getGroupMembers(groupId: Int) : List<Pair<String, String?>>

    suspend fun replaceGroupMembers(groupId: Int, groupMembers: List<GroupMember>)
}