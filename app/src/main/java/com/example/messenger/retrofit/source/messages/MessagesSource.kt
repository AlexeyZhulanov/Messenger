package com.example.messenger.retrofit.source.messages

import com.example.messenger.model.Conversation
import com.example.messenger.model.Message
import com.example.messenger.model.UserShort

interface MessagesSource {
    suspend fun createDialog(name: String, keyUser1: String, keyUser2: String) : Int

    suspend fun sendMessage(idDialog: Int, text: String? = null, images: List<String>? = null,
              voice: String? = null, file: String? = null, code: String? = null,
              codeLanguage: String? = null, referenceToMessageId: Int? = null, isForwarded: Boolean = false,
              isUrl: Boolean? = null, usernameAuthorOriginal: String? = null, waveform: List<Int>? = null) : String

    suspend fun getMessages(idDialog: Int, pageIndex: Int, pageSize: Int) : List<Message>

    suspend fun findMessage(idMessage: Int, idDialog: Int) : Pair<Message, Int>

    suspend fun editMessage(idDialog: Int, messageId: Int, text: String? = null,
                            images: List<String>? = null, voice: String? = null,
                            file: String? = null, code: String? = null, codeLanguage: String? = null,
                            isUrl: Boolean? = null) : String

    suspend fun deleteMessages(idDialog: Int, ids: List<Int>) : String

    suspend fun deleteDialog(dialogId: Int) : String

    suspend fun getUsers() : List<UserShort>

    suspend fun markMessagesAsRead(idDialog: Int, ids: List<Int>) : String

    suspend fun searchMessagesInDialog(dialogId: Int) : List<Message>

    suspend fun getConversations() : List<Conversation>

    suspend fun toggleDialogCanDelete(dialogId : Int) : String

    suspend fun updateAutoDeleteInterval(dialogId: Int, autoDeleteInterval: Int) : String

    suspend fun deleteDialogMessages(dialogId: Int) : String

}