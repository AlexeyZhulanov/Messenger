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
import com.example.messenger.retrofit.source.BaseRetrofitSource
import com.example.messenger.retrofit.source.RetrofitConfig

class RetrofitGroupsSource(
    config: RetrofitConfig
) : BaseRetrofitSource(config), GroupsSource {

    private val groupsApi = retrofit.create(GroupsApi::class.java)

    override suspend fun createGroup(token: String, name: String): String = wrapRetrofitExceptions {
        val createGroupRequestEntity = CreateGroupRequestEntity(name = name)
        groupsApi.createGroup(token, createGroupRequestEntity).message
    }

    override suspend fun sendGroupMessage(token: String, groupId: Int, text: String?,
        images: List<String>?, voice: String?,
        file: String?): String = wrapRetrofitExceptions {
        val sendGroupMessageRequestEntity = SendGroupMessageRequestEntity(text = text,
            images = images, voice = voice, file = file)
        groupsApi.sendGroupMessage(groupId, token, sendGroupMessageRequestEntity).message
    }

    override suspend fun getGroupMessages(token: String, groupId: Int,
        start: Int, end: Int): List<GroupMessage> = wrapRetrofitExceptions {
        groupsApi.getGroupMessages(token, groupId, start, end).toGroupMessages()
    }

    override suspend fun editGroupMessage(token: String, groupMessageId: Int, text: String?,
        images: List<String>?, voice: String?, file: String?): String = wrapRetrofitExceptions {
        val sendGroupMessageRequestEntity = SendGroupMessageRequestEntity(text = text,
            images = images, voice = voice, file = file)
        groupsApi.editGroupMessage(groupMessageId, token, sendGroupMessageRequestEntity).message
    }

    override suspend fun deleteGroupMessages(token: String, ids: List<Int>): String = wrapRetrofitExceptions {
        val deleteMessagesRequestEntity = DeleteMessagesRequestEntity(ids = ids)
        groupsApi.deleteGroupMessages(token, deleteMessagesRequestEntity).message
    }

    override suspend fun deleteGroup(token: String, groupId: Int): String = wrapRetrofitExceptions {
        groupsApi.deleteGroup(groupId, token).message
    }

    override suspend fun editGroupName(token: String, groupId: Int, name: String): String = wrapRetrofitExceptions {
        val createGroupRequestEntity = CreateGroupRequestEntity(name = name)
        groupsApi.editGroupName(groupId, token, createGroupRequestEntity).message
    }

    override suspend fun addUserToGroup(token: String, groupId: Int, userId: Int): String = wrapRetrofitExceptions  {
        val addUserToGroupRequestEntity = AddUserToGroupRequestEntity(userId = userId)
        groupsApi.addUserToGroup(groupId, token, addUserToGroupRequestEntity).message
    }

    override suspend fun deleteUserFromGroup(token: String, groupId: Int, userId: Int): String = wrapRetrofitExceptions {
        groupsApi.deleteUserFromGroup(groupId, userId, token).message
    }

    override suspend fun getAvailableUsersForGroup(token: String, groupId: Int): List<UserShort> = wrapRetrofitExceptions {
        groupsApi.getAvailableUsersForGroup(groupId, token).toUsersShort()
    }

    override suspend fun getGroupMembers(token: String, groupId: Int): List<User> = wrapRetrofitExceptions {
        groupsApi.getGroupMembers(groupId, token).toUsers()
    }

    override suspend fun updateGroupAvatar(token: String, groupId: Int, avatar: String): String = wrapRetrofitExceptions {
        val updateGroupAvatarRequestEntity = UpdateGroupAvatarRequestEntity(avatar = avatar)
        groupsApi.updateGroupAvatar(groupId, token, updateGroupAvatarRequestEntity).message
    }

    override suspend fun markGroupMessagesAsRead(token: String, ids: List<Int>): String = wrapRetrofitExceptions {
        val deleteMessagesRequestEntity = DeleteMessagesRequestEntity(ids = ids)
        groupsApi.markGroupMessagesAsRead(token, deleteMessagesRequestEntity).message
    }

    override suspend fun toggleGroupCanDelete(token: String, groupId: Int): String = wrapRetrofitExceptions {
        groupsApi.toggleGroupCanDelete(groupId, token).message
    }

    override suspend fun updateGroupAutoDeleteInterval(token: String, groupId: Int,
                        autoDeleteInterval: Int): String = wrapRetrofitExceptions {
        val updateAutoDeleteIntervalRequestEntity = UpdateAutoDeleteIntervalRequestEntity(autoDeleteInterval = autoDeleteInterval)
        groupsApi.updateGroupAutoDeleteInterval(groupId, token, updateAutoDeleteIntervalRequestEntity).message
    }

    override suspend fun deleteGroupMessagesAll(token: String, groupId: Int): String = wrapRetrofitExceptions {
        groupsApi.deleteGroupMessagesAll(groupId, token).message
    }

    override suspend fun getGroupSettings(token: String, groupId: Int): ConversationSettings = wrapRetrofitExceptions {
        groupsApi.getGroupSettings(groupId, token).toConversationSettings()
    }
}