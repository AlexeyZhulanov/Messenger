package com.example.messenger.retrofit.source.groups

import com.example.messenger.model.ConversationSettings
import com.example.messenger.model.GroupMessage
import com.example.messenger.model.User
import com.example.messenger.model.UserShort

interface GroupsSource {

    suspend fun createGroup(token: String, name: String) : String

    suspend fun sendGroupMessage(token: String, groupId: Int, text: String? = null,
                                 images: List<String>? = null, voice: String? = null,
                                 file: String? = null) : String

    suspend fun getGroupMessages(token: String, groupId: Int, start: Int, end: Int) : List<GroupMessage>

    suspend fun editGroupMessage(token: String, groupMessageId: Int, text: String? = null,
                                 images: List<String>? = null, voice: String? = null,
                                 file: String? = null) : String

    suspend fun deleteGroupMessages(token: String, ids: List<Int>) : String

    suspend fun deleteGroup(token: String, groupId: Int) : String

    suspend fun editGroupName(token: String, groupId: Int, name: String) : String

    suspend fun addUserToGroup(token: String, groupId: Int, userId: Int) : String

    suspend fun deleteUserFromGroup(token: String, groupId: Int, userId: Int) : String

    suspend fun getAvailableUsersForGroup(token: String, groupId: Int) : List<UserShort>

    suspend fun getGroupMembers(token: String, groupId: Int) : List<User>

    suspend fun updateGroupAvatar(token: String, groupId: Int, avatar: String) : String

    suspend fun markGroupMessagesAsRead(token: String, ids: List<Int>) : String

    suspend fun toggleGroupCanDelete(token: String, groupId: Int) : String

    suspend fun updateGroupAutoDeleteInterval(token: String, groupId: Int, autoDeleteInterval: Int) : String

    suspend fun deleteGroupMessagesAll(token: String, groupId: Int) : String

    suspend fun getGroupSettings(token: String, groupId: Int) : ConversationSettings

}