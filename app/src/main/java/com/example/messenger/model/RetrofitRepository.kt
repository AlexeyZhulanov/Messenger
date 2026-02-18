package com.example.messenger.model

import java.io.File

interface RetrofitRepository {

    suspend fun register(name: String, username: String, password: String) : Boolean

    suspend fun login(name: String, password: String) : Boolean

    suspend fun getConversations(): List<Conversation>

    suspend fun updateProfile(username: String?, avatar: String?) : Boolean

    suspend fun updatePassword(oldPassword: String, newPassword: String) : Boolean

    suspend fun updateLastSession() : Boolean

    suspend fun getUser(userId: Int) : User

    suspend fun getLastSession(userId: Int): Long

    suspend fun createDialog(name: String, keyUser1: String, keyUser2: String) : Int

    suspend fun sendMessage(idDialog: Int, text: String?, images: List<String>?, voice: String?, file: String?,
                            code: String?, codeLanguage: String?, referenceToMessageId: Int?, isForwarded: Boolean,
                            isUrl: Boolean?, usernameAuthorOriginal: String?, waveform: List<Int>?) : Boolean

    suspend fun getMessages(idDialog: Int, pageIndex: Int, pageSize: Int) : List<Message>

    suspend fun findMessage(idMessage: Int, idDialog: Int) : Pair<Message, Int>

    suspend fun editMessage(idDialog: Int, messageId: Int, text: String?, images: List<String>?,
           voice: String?, file: String?, code: String?, codeLanguage: String?, isUrl: Boolean?) : Boolean

    suspend fun deleteMessages(idDialog: Int, ids: List<Int>) : Boolean

    suspend fun deleteDialog(dialogId: Int) : Boolean

    suspend fun getUsers(): List<UserShort>

    suspend fun markMessagesAsRead(idDialog: Int, ids: List<Int>) : Boolean

    suspend fun searchMessagesInDialog(dialogId: Int): List<Message>

    suspend fun toggleDialogCanDelete(dialogId: Int) : Boolean

    suspend fun updateAutoDeleteInterval(dialogId: Int, autoDeleteInterval: Int) : Boolean

    suspend fun deleteDialogMessages(dialogId: Int) : Boolean

    suspend fun createGroup(name: String, key: String) : Int

    suspend fun sendGroupMessage(groupId: Int, text: String?, images: List<String>?, voice: String?,
                   file: String?, code: String?, codeLanguage: String?, referenceToMessageId: Int?,
                   isForwarded: Boolean, isUrl: Boolean?, usernameAuthorOriginal: String?,
                                 waveform: List<Int>?) : Boolean

    suspend fun getGroupMessages(groupId: Int, start: Int, end: Int): List<Message>

    suspend fun findGroupMessage(idMessage: Int, groupId: Int) : Pair<Message, Int>

    suspend fun editGroupMessage(groupId: Int, messageId: Int, text: String?, images: List<String>?,
       voice: String?, file: String?, code: String?, codeLanguage: String?, isUrl: Boolean?) : Boolean

    suspend fun deleteGroupMessages(groupId: Int, ids: List<Int>) : Boolean

    suspend fun deleteGroup(groupId: Int) : Boolean

    suspend fun editGroupName(groupId: Int, name: String) : Boolean

    suspend fun addUserToGroup(groupId: Int, name: String, key: String) : Boolean

    suspend fun deleteUserFromGroup(groupId: Int, userId: Int) : Boolean

    suspend fun getAvailableUsersForGroup(groupId: Int): List<UserShort>

    suspend fun getGroupMembers(groupId: Int): List<User>

    suspend fun updateGroupAvatar(groupId: Int, avatar: String) : Boolean

    suspend fun markGroupMessagesAsRead(groupId: Int, ids: List<Int>) : Boolean

    suspend fun toggleGroupCanDelete(groupId: Int) : Boolean

    suspend fun updateGroupAutoDeleteInterval(groupId: Int, autoDeleteInterval: Int) : Boolean

    suspend fun deleteGroupMessagesAll(groupId: Int) : Boolean

    suspend fun searchMessagesInGroup(groupId: Int) : List<Message>

    suspend fun uploadPhoto(dialogId: Int, photo: File, isGroup: Int?) : String

    suspend fun uploadPhotoPreview(dialogId: Int, photoPreview: File, isGroup: Int?) : Boolean

    suspend fun uploadFile(dialogId: Int, file: File, isGroup: Int?) : String

    suspend fun uploadAudio(dialogId: Int, audio: File, isGroup: Int?) : String

    suspend fun uploadAvatar(avatar: File) : String

    suspend fun uploadNews(news: File) : String

    suspend fun downloadFile(folder: String, dialogId: Int, filename: String, isGroup: Int?) : String

    suspend fun downloadAvatar(filename: String) : String

    suspend fun downloadNews(filename: String) : String

    suspend fun getMedias(dialogId: Int, page: Int, isGroup: Int?) : List<String>?

    suspend fun getFiles(dialogId: Int, page: Int, isGroup: Int?) : List<String>?

    suspend fun getAudios(dialogId: Int, page: Int, isGroup: Int?) : List<String>?

    suspend fun getMediaPreview(dialogId: Int, filename: String, isGroup: Int?) : String

    suspend fun getVacation() : Pair<String, String>?

    suspend fun getPermission() : Int

    suspend fun sendNews(headerText: String?, text: String?, images: List<String>?, voices: List<String>?,
                         files: List<String>?) : Boolean

    suspend fun getNews(pageIndex: Int, pageSize: Int) : List<News>

    suspend fun editNews(newsId: Int, headerText: String?, text: String?, images: List<String>?,
                         voices: List<String>?, files: List<String>?) : Boolean

    suspend fun deleteNews(newsId: Int) : Boolean

    suspend fun saveFCMToken(token: String) : Boolean

    suspend fun deleteFCMToken() : Boolean

    suspend fun refreshToken(token: String) : String

    suspend fun getUserKey(name: String) : String?

    suspend fun getKeys() : Pair<String?, String?>

    suspend fun saveUserKeys(publicKey: String, privateKey: String) : Boolean

    suspend fun getNewsKey() : String?

    suspend fun getRepos(token: String): List<Repo>

    suspend fun updateRepo(projectId: Int, hPush: Boolean?, hMerge: Boolean?, hTag: Boolean?,
                           hIssue: Boolean?, hNote: Boolean?, hRelease: Boolean?) : Boolean
}