package com.example.messenger.retrofit.source.messages

import com.example.messenger.model.Conversation
import com.example.messenger.model.ConversationSettings
import com.example.messenger.model.Message
import com.example.messenger.model.UserShort

interface MessagesSource {
    suspend fun createDialog(token: String, name: String) : String

    suspend fun sendMessage(token: String, text: String? = null, images: List<String>? = null,
                            voice: String? = null, file: String? = null) : String

    suspend fun getMessages(token: String, idDialog: Int, start: Int, end: Int) : List<Message>

    suspend fun addKeyToDialog(token: String, dialogId: Int, key: String) : String

    suspend fun removeKeyFromDialog(token: String, dialogId: Int) : String

    suspend fun editMessage(token: String, messageId: Int, text: String? = null,
                            images: List<String>? = null, voice: String? = null,
                            file: String? = null) : String

    suspend fun deleteMessages(token: String, ids: List<Int>) : String

    suspend fun deleteDialog(token: String, dialogId: Int) : String

    suspend fun getUsers(token: String) : List<UserShort>

    suspend fun markMessagesAsRead(token: String, ids: List<Int>) : String

    suspend fun searchMessagesInDialog(token: String, dialogId: Int, word: String) : List<Message>

    suspend fun getConversations(token: String) : List<Conversation>

    suspend fun toggleDialogCanDelete(token: String, dialogId : Int) : String

    suspend fun updateAutoDeleteInterval(token: String, dialogId: Int, autoDeleteInterval: Int) : String

    suspend fun deleteDialogMessages(token: String, dialogId: Int) : String

    suspend fun getDialogSettings(token: String, dialogId: Int) : ConversationSettings

}