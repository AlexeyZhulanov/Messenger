package com.example.messenger.retrofit.source.groups

import com.example.messenger.model.ConversationSettings
import com.example.messenger.model.Message
import com.example.messenger.model.User
import com.example.messenger.model.UserShort
import com.example.messenger.retrofit.api.GroupsApi
import com.example.messenger.retrofit.entities.groups.AddUserToGroupRequestEntity
import com.example.messenger.retrofit.entities.groups.UpdateGroupAvatarRequestEntity
import com.example.messenger.retrofit.entities.messages.AddKeyToDialogRequestEntity
import com.example.messenger.retrofit.entities.messages.DeleteMessagesRequestEntity
import com.example.messenger.retrofit.entities.messages.DialogCreateRequestEntity
import com.example.messenger.retrofit.entities.messages.SendMessageRequestEntity
import com.example.messenger.retrofit.entities.messages.UpdateAutoDeleteIntervalRequestEntity
import com.example.messenger.retrofit.source.base.BaseRetrofitSource
import com.example.messenger.retrofit.source.base.RetrofitConfig

class RetrofitGroupsSource(
    config: RetrofitConfig
) : BaseRetrofitSource(config), GroupsSource {

    private val groupsApi = retrofit.create(GroupsApi::class.java)

    override suspend fun createGroup(name: String): String = wrapRetrofitExceptions {
        val createGroupRequestEntity = DialogCreateRequestEntity(name = name)
        groupsApi.createGroup(createGroupRequestEntity).message
    }

    override suspend fun sendGroupMessage(groupId: Int, text: String?,
        images: List<String>?, voice: String?, file: String?, referenceToMessageId: Int?,
          isForwarded: Boolean, usernameAuthorOriginal: String?): String = wrapRetrofitExceptions {
        val sendGroupMessageRequestEntity = SendMessageRequestEntity(text = text,
            images = images, voice = voice, file = file, referenceToMessageId = referenceToMessageId,
            isForwarded = isForwarded, usernameAuthorOriginal = usernameAuthorOriginal)
        groupsApi.sendGroupMessage(groupId, sendGroupMessageRequestEntity).message
    }

    override suspend fun getGroupMessages(groupId: Int, pageIndex: Int, pageSize: Int): List<Message> = wrapRetrofitExceptions {
        val response = groupsApi.getGroupMessages(groupId, pageIndex, pageSize)
        response.map { it.toMessage() }
    }

    override suspend fun findGroupMessage(idMessage: Int, groupId: Int): Pair<Message, Int> = wrapRetrofitExceptions {
        val response = groupsApi.findMessage(idMessage, groupId)
        Pair(response.toMessage(), response.position ?: -1)
    }

    override suspend fun addKeyToGroup(groupId: Int, key: String): String = wrapRetrofitExceptions {
        val addKeyToGroupRequestEntity = AddKeyToDialogRequestEntity(key = key)
        groupsApi.addKeyToGroup(groupId, addKeyToGroupRequestEntity).message
    }

    override suspend fun removeKeyFromGroup(groupId: Int): String = wrapRetrofitExceptions {
        groupsApi.removeKeyFromGroup(groupId).message
    }

    override suspend fun editGroupMessage(groupId: Int, messageId: Int, text: String?,
                                          images: List<String>?, voice: String?, file: String?): String = wrapRetrofitExceptions {
        val sendGroupMessageRequestEntity = SendMessageRequestEntity(text = text,
            images = images, voice = voice, file = file)
        groupsApi.editGroupMessage(messageId, groupId, sendGroupMessageRequestEntity).message
    }

    override suspend fun deleteGroupMessages(groupId: Int, ids: List<Int>): String = wrapRetrofitExceptions {
        val deleteMessagesRequestEntity = DeleteMessagesRequestEntity(ids = ids)
        groupsApi.deleteGroupMessages(groupId, deleteMessagesRequestEntity).message
    }

    override suspend fun deleteGroup(groupId: Int): String = wrapRetrofitExceptions {
        groupsApi.deleteGroup(groupId).message
    }

    override suspend fun editGroupName(groupId: Int, name: String): String = wrapRetrofitExceptions {
        val createGroupRequestEntity = DialogCreateRequestEntity(name = name)
        groupsApi.editGroupName(groupId, createGroupRequestEntity).message
    }

    override suspend fun addUserToGroup(groupId: Int, name: String): String = wrapRetrofitExceptions  {
        val addUserToGroupRequestEntity = AddUserToGroupRequestEntity(name = name)
        groupsApi.addUserToGroup(groupId, addUserToGroupRequestEntity).message
    }

    override suspend fun deleteUserFromGroup(groupId: Int, userId: Int): String = wrapRetrofitExceptions {
        groupsApi.deleteUserFromGroup(groupId, userId).message
    }

    override suspend fun getAvailableUsersForGroup(groupId: Int): List<UserShort> = wrapRetrofitExceptions {
        groupsApi.getAvailableUsersForGroup(groupId).toUsersShort()
    }

    override suspend fun getGroupMembers(groupId: Int): List<User> = wrapRetrofitExceptions {
        val response = groupsApi.getGroupMembers(groupId)
        response.map { it.toUser() }
    }

    override suspend fun updateGroupAvatar(groupId: Int, avatar: String): String = wrapRetrofitExceptions {
        val updateGroupAvatarRequestEntity = UpdateGroupAvatarRequestEntity(avatar = avatar)
        groupsApi.updateGroupAvatar(groupId, updateGroupAvatarRequestEntity).message
    }

    override suspend fun markGroupMessagesAsRead(groupId: Int, ids: List<Int>): String = wrapRetrofitExceptions {
        val deleteMessagesRequestEntity = DeleteMessagesRequestEntity(ids = ids)
        groupsApi.markGroupMessagesAsRead(groupId, deleteMessagesRequestEntity).message
    }

    override suspend fun toggleGroupCanDelete(groupId: Int): String = wrapRetrofitExceptions {
        groupsApi.toggleGroupCanDelete(groupId).message
    }

    override suspend fun updateGroupAutoDeleteInterval(groupId: Int,
                        autoDeleteInterval: Int): String = wrapRetrofitExceptions {
        val updateAutoDeleteIntervalRequestEntity = UpdateAutoDeleteIntervalRequestEntity(autoDeleteInterval = autoDeleteInterval)
        groupsApi.updateGroupAutoDeleteInterval(groupId, updateAutoDeleteIntervalRequestEntity).message
    }

    override suspend fun deleteGroupMessagesAll(groupId: Int): String = wrapRetrofitExceptions {
        groupsApi.deleteGroupMessagesAll(groupId).message
    }

    override suspend fun getGroupSettings(groupId: Int): ConversationSettings = wrapRetrofitExceptions {
        groupsApi.getGroupSettings(groupId).toConversationSettings()
    }

    override suspend fun searchMessagesInGroup(groupId: Int, word: String): List<Message> = wrapRetrofitExceptions {
        val response = groupsApi.searchMessagesInGroup(groupId, word)
        response.map { it.toMessage() }
    }
}