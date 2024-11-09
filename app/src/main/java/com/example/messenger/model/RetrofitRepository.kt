package com.example.messenger.model

import android.content.Context
import java.io.File

interface RetrofitRepository {

    fun isSignedIn(): Boolean

    suspend fun register(name: String, username: String, password: String) : Boolean

    suspend fun login(name: String, password: String) : Boolean

    suspend fun getConversations(): List<Conversation>

    suspend fun updateProfile(username: String?, avatar: String?) : Boolean

    suspend fun updatePassword(password: String) : Boolean

    suspend fun updateLastSession(idDialog: Int) : Boolean

    suspend fun getUser(userId: Int) : User

    suspend fun getLastSession(userId: Int): Long

    suspend fun createDialog(name: String) : Boolean

    suspend fun sendMessage(idDialog: Int, text: String?, images: List<String>?, voice: String?,
     file: String?, referenceToMessageId: Int?, isForwarded: Boolean, usernameAuthorOriginal: String?) : Boolean

    suspend fun getMessages(idDialog: Int, pageIndex: Int, pageSize: Int) : List<Message>

    suspend fun findMessage(idMessage: Int, idDialog: Int) : Pair<Message, Int>

    suspend fun addKeyToDialog(dialogId: Int, key: String) : Boolean

    suspend fun removeKeyFromDialog(dialogId: Int) : Boolean

    suspend fun editMessage(idDialog: Int, messageId: Int, text: String?,
                            images: List<String>?, voice: String?, file: String?) : Boolean

    suspend fun deleteMessages(idDialog: Int, ids: List<Int>) : Boolean

    suspend fun deleteDialog(dialogId: Int) : Boolean

    suspend fun getUsers(): List<UserShort>

    suspend fun markMessagesAsRead(idDialog: Int, ids: List<Int>) : Boolean

    suspend fun searchMessagesInDialog(dialogId: Int, word: String): List<Message>

    suspend fun toggleDialogCanDelete(dialogId: Int) : Boolean

    suspend fun updateAutoDeleteInterval(dialogId: Int, autoDeleteInterval: Int) : Boolean

    suspend fun deleteDialogMessages(dialogId: Int) : Boolean

    suspend fun getDialogSettings(dialogId: Int): ConversationSettings

    suspend fun createGroup(name: String) : Boolean

    suspend fun sendGroupMessage(groupId: Int, text: String?, images: List<String>?, voice: String?,
     file: String?, referenceToMessageId: Int?, isForwarded: Boolean, usernameAuthorOriginal: String?) : Boolean

    suspend fun getGroupMessages(groupId: Int, start: Int, end: Int): List<GroupMessage>

    suspend fun editGroupMessage(groupMessageId: Int, text: String?,
                                 images: List<String>?, voice: String?, file: String?) : Boolean

    suspend fun deleteGroupMessages(ids: List<Int>) : Boolean

    suspend fun deleteGroup(groupId: Int) : Boolean

    suspend fun editGroupName(groupId: Int, name: String) : Boolean

    suspend fun addUserToGroup(groupId: Int, userId: Int) : Boolean

    suspend fun deleteUserFromGroup(groupId: Int, userId: Int) : Boolean

    suspend fun getAvailableUsersForGroup(groupId: Int): List<UserShort>

    suspend fun getGroupMembers(groupId: Int): List<User>

    suspend fun updateGroupAvatar(groupId: Int, avatar: String) : Boolean

    suspend fun markGroupMessagesAsRead(ids: List<Int>) : Boolean

    suspend fun toggleGroupCanDelete(groupId: Int) : Boolean

    suspend fun updateGroupAutoDeleteInterval(groupId: Int, autoDeleteInterval: Int) : Boolean

    suspend fun deleteGroupMessagesAll(groupId: Int) : Boolean

    suspend fun getGroupSettings(groupId: Int): ConversationSettings

    suspend fun searchMessagesInGroup(groupId: Int, word: String) : List<GroupMessage>

    suspend fun uploadPhoto(dialogId: Int, photo: File) : String

    suspend fun uploadFile(dialogId: Int, file: File) : String

    suspend fun uploadAudio(dialogId: Int, audio: File) : String

    suspend fun uploadAvatar(avatar: File) : String

    suspend fun downloadFile(context: Context, folder: String, dialogId: Int, filename: String) : String

    suspend fun downloadAvatar(context: Context, filename: String) : String

    suspend fun deleteFile(folder: String, dialogId: Int, filename: String) : Boolean

    suspend fun getMedias(dialogId: Int, page: Int) : List<String>?

    suspend fun getFiles(dialogId: Int, page: Int) : List<String>?

    suspend fun getAudios(dialogId: Int, page: Int) : List<String>?

    suspend fun getMediaPreview(context: Context, dialogId: Int, filename: String) : String
}