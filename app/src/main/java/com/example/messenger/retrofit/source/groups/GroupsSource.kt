package com.example.messenger.retrofit.source.groups

import com.example.messenger.model.Message
import com.example.messenger.model.User
import com.example.messenger.model.UserShort

interface GroupsSource {

    suspend fun createGroup(name: String, key: String) : Int

    suspend fun sendGroupMessage(groupId: Int, text: String? = null,
                    images: List<String>? = null, voice: String? = null,
                    file: String? = null, code: String? = null, codeLanguage: String? = null,
                    referenceToMessageId: Int? = null, isForwarded: Boolean = false,
                    isUrl: Boolean? = null, usernameAuthorOriginal: String? = null,
                                 waveform: List<Int>? = null) : String

    suspend fun getGroupMessages(groupId: Int, pageSize: Int, lastCursor: Long? = null) : List<Message>

    suspend fun findGroupMessage(idMessage: Int, groupId: Int) : Pair<Message, Int>

    suspend fun editGroupMessage(groupId: Int, messageId: Int, text: String? = null,
                                 images: List<String>? = null, voice: String? = null,
                                 file: String? = null, code: String? = null,
                                 codeLanguage: String? = null, isUrl: Boolean? = null) : String

    suspend fun deleteGroupMessages(groupId: Int, ids: List<Int>) : String

    suspend fun deleteGroup(groupId: Int) : String

    suspend fun editGroupName(groupId: Int, name: String) : String

    suspend fun addUserToGroup(groupId: Int, name: String, key: String) : String

    suspend fun deleteUserFromGroup(groupId: Int, userId: Int) : String

    suspend fun getAvailableUsersForGroup(groupId: Int) : List<UserShort>

    suspend fun getGroupMembers(groupId: Int) : List<User>

    suspend fun updateGroupAvatar(groupId: Int, avatar: String) : String

    suspend fun markGroupMessagesAsRead(groupId: Int, ids: List<Int>) : String

    suspend fun toggleGroupCanDelete(groupId: Int) : String

    suspend fun updateGroupAutoDeleteInterval(groupId: Int, autoDeleteInterval: Int) : String

    suspend fun deleteGroupMessagesAll(groupId: Int) : String

    suspend fun searchMessagesInGroup(groupId: Int) : List<Message>

}