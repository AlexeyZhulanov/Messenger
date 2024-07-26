package com.example.messenger.retrofit.source.messages

import com.example.messenger.model.Conversation
import com.example.messenger.model.ConversationSettings
import com.example.messenger.model.Message
import com.example.messenger.model.UserShort

interface MessagesSource {
    suspend fun createDialog(name: String) : String

    suspend fun sendMessage(idDialog: Int, text: String? = null, images: List<String>? = null,
                            voice: String? = null, file: String? = null) : String

    suspend fun getMessages(idDialog: Int, start: Int, end: Int) : List<Message>

    suspend fun addKeyToDialog(dialogId: Int, key: String) : String

    suspend fun removeKeyFromDialog(dialogId: Int) : String

    suspend fun editMessage(messageId: Int, text: String? = null,
                            images: List<String>? = null, voice: String? = null,
                            file: String? = null) : String

    suspend fun deleteMessages(ids: List<Int>) : String

    suspend fun deleteDialog(dialogId: Int) : String

    suspend fun getUsers() : List<UserShort>

    suspend fun markMessagesAsRead(ids: List<Int>) : String

    suspend fun searchMessagesInDialog(dialogId: Int, word: String) : List<Message>

    suspend fun getConversations() : List<Conversation>

    suspend fun toggleDialogCanDelete(dialogId : Int) : String

    suspend fun updateAutoDeleteInterval(dialogId: Int, autoDeleteInterval: Int) : String

    suspend fun deleteDialogMessages(dialogId: Int) : String

    suspend fun getDialogSettings(dialogId: Int) : ConversationSettings

}