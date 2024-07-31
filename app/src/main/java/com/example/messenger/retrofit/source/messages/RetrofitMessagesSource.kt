package com.example.messenger.retrofit.source.messages

import com.example.messenger.model.Conversation
import com.example.messenger.model.ConversationSettings
import com.example.messenger.model.Message
import com.example.messenger.model.UserShort
import com.example.messenger.retrofit.api.MessagesApi
import com.example.messenger.retrofit.entities.messages.AddKeyToDialogRequestEntity
import com.example.messenger.retrofit.entities.messages.DeleteMessagesRequestEntity
import com.example.messenger.retrofit.entities.messages.DialogCreateRequestEntity
import com.example.messenger.retrofit.entities.messages.SendMessageRequestEntity
import com.example.messenger.retrofit.entities.messages.UpdateAutoDeleteIntervalRequestEntity
import com.example.messenger.retrofit.source.base.BaseRetrofitSource
import com.example.messenger.retrofit.source.base.RetrofitConfig

class RetrofitMessagesSource(
    config: RetrofitConfig
) : BaseRetrofitSource(config), MessagesSource {

    private val messagesApi = retrofit.create(MessagesApi::class.java)

    override suspend fun createDialog(name: String): String = wrapRetrofitExceptions {
        val dialogCreateRequestEntity = DialogCreateRequestEntity(name = name)
        messagesApi.createDialog(dialogCreateRequestEntity).message
    }

    override suspend fun sendMessage(idDialog: Int, text: String?, images: List<String>?,
        voice: String?, file: String?): String = wrapRetrofitExceptions {
        val sendMessageRequestEntity = SendMessageRequestEntity(text = text, images = images,
            file = file, voice = voice)
        messagesApi.sendMessage(idDialog, sendMessageRequestEntity).message
    }

    override suspend fun getMessages(idDialog: Int, start: Int, end: Int): List<Message> = wrapRetrofitExceptions {
        val response = messagesApi.getMessages(idDialog, start, end)
        response.map { it.toMessage() }
    }

    override suspend fun addKeyToDialog(dialogId: Int, key: String): String = wrapRetrofitExceptions {
        val addKeyToDialogRequestEntity = AddKeyToDialogRequestEntity(key = key)
        messagesApi.addKeyToDialog(dialogId, addKeyToDialogRequestEntity).message
    }

    override suspend fun removeKeyFromDialog(dialogId: Int): String = wrapRetrofitExceptions {
        messagesApi.removeKeyFromDialog(dialogId).message
    }

    override suspend fun editMessage(messageId: Int, text: String?,
                                     images: List<String>?, voice: String?,
                                     file: String?): String = wrapRetrofitExceptions {
        val sendMessageRequestEntity = SendMessageRequestEntity(text = text, images = images,
            file = file, voice = voice)
        messagesApi.editMessage(messageId, sendMessageRequestEntity).message
    }

    override suspend fun deleteMessages(ids: List<Int>): String = wrapRetrofitExceptions {
        val deleteMessagesRequestEntity = DeleteMessagesRequestEntity(ids = ids)
        messagesApi.deleteMessages(deleteMessagesRequestEntity).message
    }

    override suspend fun deleteDialog(dialogId: Int): String = wrapRetrofitExceptions {
        messagesApi.deleteDialog(dialogId).message
    }

    override suspend fun getUsers(): List<UserShort> = wrapRetrofitExceptions {
        messagesApi.getUsers().toUsersShort()
    }

    override suspend fun markMessagesAsRead(ids: List<Int>): String = wrapRetrofitExceptions {
        val deleteMessagesRequestEntity = DeleteMessagesRequestEntity(ids = ids)
        messagesApi.markMessagesAsRead(deleteMessagesRequestEntity).message
    }

    override suspend fun searchMessagesInDialog(dialogId: Int,
                                                word: String): List<Message> = wrapRetrofitExceptions {
        val response = messagesApi.searchMessagesInDialog(dialogId, word)
        response.map { it.toMessage() }
    }

    override suspend fun getConversations(): List<Conversation> = wrapRetrofitExceptions {
        val response = messagesApi.getConversations()
        response.map { it.toConversation() }
    }

    override suspend fun toggleDialogCanDelete(dialogId: Int): String = wrapRetrofitExceptions {
        messagesApi.toggleDialogCanDelete(dialogId).message
    }

    override suspend fun updateAutoDeleteInterval(dialogId: Int,
                                                  autoDeleteInterval: Int): String = wrapRetrofitExceptions {
        val updateAutoDeleteIntervalRequestEntity = UpdateAutoDeleteIntervalRequestEntity(autoDeleteInterval = autoDeleteInterval)
        messagesApi.updateAutoDeleteInterval(dialogId, updateAutoDeleteIntervalRequestEntity).message
    }

    override suspend fun deleteDialogMessages(dialogId: Int): String = wrapRetrofitExceptions {
        messagesApi.deleteDialogMessages(dialogId).message
    }

    override suspend fun getDialogSettings(dialogId: Int): ConversationSettings = wrapRetrofitExceptions {
        messagesApi.getDialogSettings(dialogId).toConversationSettings()
    }
}