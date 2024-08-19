package com.example.messenger.retrofit.source.groups

import com.example.messenger.model.ConversationSettings
import com.example.messenger.model.GroupMessage
import com.example.messenger.model.User
import com.example.messenger.model.UserShort
import com.example.messenger.retrofit.api.GroupsApi
import com.example.messenger.retrofit.entities.groups.AddUserToGroupRequestEntity
import com.example.messenger.retrofit.entities.groups.CreateGroupRequestEntity
import com.example.messenger.retrofit.entities.groups.SendGroupMessageRequestEntity
import com.example.messenger.retrofit.entities.groups.UpdateGroupAvatarRequestEntity
import com.example.messenger.retrofit.entities.messages.DeleteMessagesRequestEntity
import com.example.messenger.retrofit.entities.messages.UpdateAutoDeleteIntervalRequestEntity
import com.example.messenger.retrofit.source.base.BaseRetrofitSource
import com.example.messenger.retrofit.source.base.RetrofitConfig

class RetrofitGroupsSource(
    config: RetrofitConfig
) : BaseRetrofitSource(config), GroupsSource {

    private val groupsApi = retrofit.create(GroupsApi::class.java)

    override suspend fun createGroup(name: String): String = wrapRetrofitExceptions {
        val createGroupRequestEntity = CreateGroupRequestEntity(name = name)
        groupsApi.createGroup(createGroupRequestEntity).message
    }

    override suspend fun sendGroupMessage(groupId: Int, text: String?,
        images: List<String>?, voice: String?, file: String?, referenceToMessageId: Int?,
          isForwarded: Boolean, usernameAuthorOriginal: String?): String = wrapRetrofitExceptions {
        val sendGroupMessageRequestEntity = SendGroupMessageRequestEntity(text = text,
            images = images, voice = voice, file = file, referenceToMessageId = referenceToMessageId,
            isForwarded = isForwarded, usernameAuthorOriginal = usernameAuthorOriginal)
        groupsApi.sendGroupMessage(groupId, sendGroupMessageRequestEntity).message
    }

    override suspend fun getGroupMessages(groupId: Int,
        start: Int, end: Int): List<GroupMessage> = wrapRetrofitExceptions {
        val response = groupsApi.getGroupMessages(groupId, start, end)
        response.map { it.toGroupMessage() }
    }

    override suspend fun editGroupMessage(groupMessageId: Int, text: String?,
        images: List<String>?, voice: String?, file: String?): String = wrapRetrofitExceptions {
        val sendGroupMessageRequestEntity = SendGroupMessageRequestEntity(text = text,
            images = images, voice = voice, file = file)
        groupsApi.editGroupMessage(groupMessageId, sendGroupMessageRequestEntity).message
    }

    override suspend fun deleteGroupMessages(ids: List<Int>): String = wrapRetrofitExceptions {
        val deleteMessagesRequestEntity = DeleteMessagesRequestEntity(ids = ids)
        groupsApi.deleteGroupMessages(deleteMessagesRequestEntity).message
    }

    override suspend fun deleteGroup(groupId: Int): String = wrapRetrofitExceptions {
        groupsApi.deleteGroup(groupId).message
    }

    override suspend fun editGroupName(groupId: Int, name: String): String = wrapRetrofitExceptions {
        val createGroupRequestEntity = CreateGroupRequestEntity(name = name)
        groupsApi.editGroupName(groupId, createGroupRequestEntity).message
    }

    override suspend fun addUserToGroup(groupId: Int, userId: Int): String = wrapRetrofitExceptions  {
        val addUserToGroupRequestEntity = AddUserToGroupRequestEntity(userId = userId)
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

    override suspend fun markGroupMessagesAsRead(ids: List<Int>): String = wrapRetrofitExceptions {
        val deleteMessagesRequestEntity = DeleteMessagesRequestEntity(ids = ids)
        groupsApi.markGroupMessagesAsRead(deleteMessagesRequestEntity).message
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

    override suspend fun searchMessagesInGroup(groupId: Int, word: String): List<GroupMessage> = wrapRetrofitExceptions {
        val response = groupsApi.searchMessagesInGroup(groupId, word)
        response.map { it.toGroupMessage() }
    }
}