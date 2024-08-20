package com.example.messenger.model

interface MessengerRepository {

    suspend fun getSettings(): Settings

    suspend fun updateSettings(settings: Settings)

    suspend fun getConversations(): List<Conversation>

    suspend fun replaceConversations(conversations: List<Conversation>)

    suspend fun getMessages(idDialog: Int): List<Message>

    suspend fun replaceMessages(idDialog: Int, messages: List<Message>)

    suspend fun getGroupMessages(idGroup: Int): List<GroupMessage>

    suspend fun replaceGroupMessages(idGroup: Int, groupMessages: List<GroupMessage>)
}