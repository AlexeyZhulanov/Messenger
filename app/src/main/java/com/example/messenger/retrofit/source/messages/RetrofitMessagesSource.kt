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
import com.example.messenger.retrofit.source.BaseRetrofitSource
import com.example.messenger.retrofit.source.RetrofitConfig

class RetrofitMessagesSource(
    config: RetrofitConfig
) : BaseRetrofitSource(config), MessagesSource {

    private val messagesApi = retrofit.create(MessagesApi::class.java)

    override suspend fun createDialog(token: String, name: String): String = wrapRetrofitExceptions {
        val dialogCreateRequestEntity = DialogCreateRequestEntity(name = name)
        messagesApi.createDialog(token, dialogCreateRequestEntity).message
    }

    override suspend fun sendMessage(token: String, text: String?, images: List<String>?,
        voice: String?, file: String?): String = wrapRetrofitExceptions {
        val sendMessageRequestEntity = SendMessageRequestEntity(text = text, images = images,
            file = file, voice = voice)
        messagesApi.sendMessage(token, sendMessageRequestEntity).message
    }

    override suspend fun getMessages(token: String, idDialog: Int, start: Int, end: Int): List<Message> = wrapRetrofitExceptions {
        messagesApi.getMessages(token, idDialog, start, end).toMessages()
    }

    override suspend fun addKeyToDialog(token: String, dialogId: Int, key: String): String = wrapRetrofitExceptions {
        val addKeyToDialogRequestEntity = AddKeyToDialogRequestEntity(key = key)
        messagesApi.addKeyToDialog(dialogId, token, addKeyToDialogRequestEntity).message
    }

    override suspend fun removeKeyFromDialog(token: String, dialogId: Int): String = wrapRetrofitExceptions {
        messagesApi.removeKeyFromDialog(dialogId, token).message
    }

    override suspend fun editMessage(token: String, messageId: Int, text: String?,
                                     images: List<String>?, voice: String?,
                                     file: String?): String = wrapRetrofitExceptions {
        val sendMessageRequestEntity = SendMessageRequestEntity(text = text, images = images,
            file = file, voice = voice)
        messagesApi.editMessage(messageId, token, sendMessageRequestEntity).message
    }

    override suspend fun deleteMessages(token: String, ids: List<Int>): String = wrapRetrofitExceptions {
        val deleteMessagesRequestEntity = DeleteMessagesRequestEntity(ids = ids)
        messagesApi.deleteMessages(token, deleteMessagesRequestEntity).message
    }

    override suspend fun deleteDialog(token: String, dialogId: Int): String = wrapRetrofitExceptions {
        messagesApi.deleteDialog(dialogId, token).message
    }

    override suspend fun getUsers(token: String): List<UserShort> = wrapRetrofitExceptions {
        messagesApi.getUsers(token).toUsersShort()
    }

    override suspend fun markMessagesAsRead(token: String, ids: List<Int>): String = wrapRetrofitExceptions {
        val deleteMessagesRequestEntity = DeleteMessagesRequestEntity(ids = ids)
        messagesApi.markMessagesAsRead(token, deleteMessagesRequestEntity).message
    }

    override suspend fun searchMessagesInDialog(token: String, dialogId: Int,
                                                word: String): List<Message> = wrapRetrofitExceptions {
        messagesApi.searchMessagesInDialog(dialogId, token, word).toMessages()
    }

    override suspend fun getConversations(token: String): List<Conversation> = wrapRetrofitExceptions {
        messagesApi.getConversations(token).toConversations()
    }

    override suspend fun toggleDialogCanDelete(token: String, dialogId: Int): String = wrapRetrofitExceptions {
        messagesApi.toggleDialogCanDelete(dialogId, token).message
    }

    override suspend fun updateAutoDeleteInterval(token: String, dialogId: Int,
                                                  autoDeleteInterval: Int): String = wrapRetrofitExceptions {
        val updateAutoDeleteIntervalRequestEntity = UpdateAutoDeleteIntervalRequestEntity(autoDeleteInterval = autoDeleteInterval)
        messagesApi.updateAutoDeleteInterval(dialogId, token, updateAutoDeleteIntervalRequestEntity).message
    }

    override suspend fun deleteDialogMessages(token: String, dialogId: Int): String = wrapRetrofitExceptions {
        messagesApi.deleteDialogMessages(dialogId, token).message
    }

    override suspend fun getDialogSettings(token: String, dialogId: Int): ConversationSettings = wrapRetrofitExceptions {
        messagesApi.getDialogSettings(dialogId, token).toConversationSettings()
    }
}