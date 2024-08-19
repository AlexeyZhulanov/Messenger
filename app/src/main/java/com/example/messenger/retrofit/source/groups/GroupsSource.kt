package com.example.messenger.retrofit.source.groups

import com.example.messenger.model.ConversationSettings
import com.example.messenger.model.GroupMessage
import com.example.messenger.model.User
import com.example.messenger.model.UserShort

interface GroupsSource {

    suspend fun createGroup(name: String) : String

    suspend fun sendGroupMessage(groupId: Int, text: String? = null,
                           images: List<String>? = null, voice: String? = null,
                           file: String? = null, referenceToMessageId: Int? = null,
                           isForwarded: Boolean = false, usernameAuthorOriginal: String? = null) : String

    suspend fun getGroupMessages(groupId: Int, start: Int, end: Int) : List<GroupMessage>

    suspend fun editGroupMessage(groupMessageId: Int, text: String? = null,
                                 images: List<String>? = null, voice: String? = null,
                                 file: String? = null) : String

    suspend fun deleteGroupMessages(ids: List<Int>) : String

    suspend fun deleteGroup(groupId: Int) : String

    suspend fun editGroupName(groupId: Int, name: String) : String

    suspend fun addUserToGroup(groupId: Int, userId: Int) : String

    suspend fun deleteUserFromGroup(groupId: Int, userId: Int) : String

    suspend fun getAvailableUsersForGroup(groupId: Int) : List<UserShort>

    suspend fun getGroupMembers(groupId: Int) : List<User>

    suspend fun updateGroupAvatar(groupId: Int, avatar: String) : String

    suspend fun markGroupMessagesAsRead(ids: List<Int>) : String

    suspend fun toggleGroupCanDelete(groupId: Int) : String

    suspend fun updateGroupAutoDeleteInterval(groupId: Int, autoDeleteInterval: Int) : String

    suspend fun deleteGroupMessagesAll(groupId: Int) : String

    suspend fun getGroupSettings(groupId: Int) : ConversationSettings

    suspend fun searchMessagesInGroup(groupId: Int, word: String) : List<GroupMessage>

}