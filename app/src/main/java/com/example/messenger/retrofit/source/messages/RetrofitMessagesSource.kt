package com.example.messenger.retrofit.source.messages

import com.example.messenger.model.Conversation
import com.example.messenger.model.Message
import com.example.messenger.model.UserShort
import com.example.messenger.retrofit.api.MessagesApi
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

    override suspend fun createDialog(name: String, keyUser1: String, keyUser2: String): Int = wrapRetrofitExceptions {
        val dialogCreateRequestEntity = DialogCreateRequestEntity(name = name, keyUser1 = keyUser1, keyUser2 = keyUser2)
        messagesApi.createDialog(dialogCreateRequestEntity).idDialog
    }

    override suspend fun sendMessage(idDialog: Int, text: String?, images: List<String>?,
                    voice: String?, file: String?, referenceToMessageId: Int?, isForwarded: Boolean,
                    isUrl: Boolean?, usernameAuthorOriginal: String?): String = wrapRetrofitExceptions {
        val sendMessageRequestEntity = SendMessageRequestEntity(text = text, images = images,
            file = file, voice = voice, referenceToMessageId = referenceToMessageId,
            isForwarded = isForwarded, isUrl = isUrl, usernameAuthorOriginal = usernameAuthorOriginal)
        messagesApi.sendMessage(idDialog, sendMessageRequestEntity).message
    }

    override suspend fun getMessages(idDialog: Int, pageIndex: Int, pageSize: Int): List<Message> = wrapRetrofitExceptions {
        val response = messagesApi.getMessages(idDialog, pageIndex, pageSize)
        response.map { it.toMessage() }
    }

    override suspend fun findMessage(idMessage: Int, idDialog: Int): Pair<Message, Int> = wrapRetrofitExceptions {
        val response = messagesApi.findMessage(idMessage, idDialog)
        Pair(response.toMessage(), response.position ?: -1)
    }

    override suspend fun editMessage(idDialog: Int, messageId: Int, text: String?,
                                     images: List<String>?, voice: String?,
                                     file: String?, isUrl: Boolean?): String = wrapRetrofitExceptions {
        val sendMessageRequestEntity = SendMessageRequestEntity(text = text, images = images,
            file = file, voice = voice, isUrl = isUrl)
        messagesApi.editMessage(messageId, idDialog, sendMessageRequestEntity).message
    }

    override suspend fun deleteMessages(idDialog: Int, ids: List<Int>): String = wrapRetrofitExceptions {
        val deleteMessagesRequestEntity = DeleteMessagesRequestEntity(ids = ids)
        messagesApi.deleteMessages(idDialog, deleteMessagesRequestEntity).message
    }

    override suspend fun deleteDialog(dialogId: Int): String = wrapRetrofitExceptions {
        messagesApi.deleteDialog(dialogId).message
    }

    override suspend fun getUsers(): List<UserShort> = wrapRetrofitExceptions {
        messagesApi.getUsers().toUsersShort()
    }

    override suspend fun markMessagesAsRead(idDialog: Int, ids: List<Int>): String = wrapRetrofitExceptions {
        val deleteMessagesRequestEntity = DeleteMessagesRequestEntity(ids = ids)
        messagesApi.markMessagesAsRead(idDialog, deleteMessagesRequestEntity).message
    }

    override suspend fun searchMessagesInDialog(dialogId: Int): List<Message> = wrapRetrofitExceptions {
        val response = messagesApi.searchMessagesInDialog(dialogId)
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
}