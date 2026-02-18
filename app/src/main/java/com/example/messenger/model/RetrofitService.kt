package com.example.messenger.model

import android.content.Context
import android.util.Log
import com.example.messenger.model.appsettings.AppSettings
import com.example.messenger.retrofit.source.gitlab.GitlabSource
import com.example.messenger.retrofit.source.groups.GroupsSource
import com.example.messenger.retrofit.source.messages.MessagesSource
import com.example.messenger.retrofit.source.news.NewsSource
import com.example.messenger.retrofit.source.uploads.UploadsSource
import com.example.messenger.retrofit.source.users.UsersSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File


class RetrofitService(
    private val usersSource: UsersSource,
    private val messagesSource: MessagesSource,
    private val groupsSource: GroupsSource,
    private val uploadSource: UploadsSource,
    private val newsSource: NewsSource,
    private val gitlabSource: GitlabSource,
    private val appSettings: AppSettings,
    private val ioDispatcher: CoroutineDispatcher,
    private val appContext: Context
) : RetrofitRepository {

    override suspend fun register(name: String, username: String, password: String) : Boolean = withContext(ioDispatcher) {
        if (name.isBlank()) throw EmptyFieldException(Field.Name)
        if (username.isBlank()) throw EmptyFieldException(Field.Username)
        if (password.isBlank()) throw EmptyFieldException(Field.Password)
        val message = try {
            usersSource.register(name, username, password)
        } catch (e: BackendException) {
            if(e.code == 400) throw AccountAlreadyExistsException(e)
            else throw e
        }
        Log.d("testRegister", message)
        return@withContext true
    }

    override suspend fun login(name: String, password: String) : Boolean = withContext(ioDispatcher) {
        if (name.isBlank()) throw EmptyFieldException(Field.Name)
        if (password.isBlank()) throw EmptyFieldException(Field.Password)
        val tokens = try {
            usersSource.login(name, password)
        } catch (e: BackendException) {
            when(e.code) {
                401 -> throw InvalidCredentialsException(e)
                404 -> throw UserNotFoundException(e)
                else -> throw e
            }
        }
        appSettings.setCurrentAccessToken(tokens.first)
        appSettings.setCurrentRefreshToken(tokens.second)
        Log.d("testLogin", "OK")
        return@withContext true
    }

    override suspend fun getConversations(): List<Conversation> = withContext(ioDispatcher) {
        val conversations = try {
            messagesSource.getConversations()
        } catch (e: BackendException) { throw e }
        Log.d("testConversations", conversations.toString())
        return@withContext conversations
    }

    override suspend fun updateProfile(username: String?, avatar: String?): Boolean = withContext(ioDispatcher) {
        val message = try {
            usersSource.updateProfile(username, avatar)
        } catch (e: BackendException) {
            if (e.code == 404) {
                throw UserNotFoundException(e)
            } else {
                throw e
            }
        }
        Log.d("testUpdateProfile", message)
        return@withContext true
    }

    override suspend fun updatePassword(oldPassword: String, newPassword: String): Boolean = withContext(ioDispatcher) {
        val message = try {
            usersSource.updatePassword(oldPassword, newPassword)
        } catch (e: BackendException) {
            throw when(e.code) {
                404 -> UserNotFoundException(e)
                400 -> InvalidCredentialsException(e)
                else -> e
            }
        }
        Log.d("testUpdatePassword", message)
        return@withContext true
    }

    override suspend fun updateLastSession(): Boolean = withContext(ioDispatcher) {
        val message = try {
            usersSource.updateLastSession()
        } catch (e: BackendException) {
            if (e.code == 404) {
                throw UserNotFoundException(e)
            } else {
                throw e
            }
        }
        Log.d("testUpdateLastSession", message)
        return@withContext true
    }

    override suspend fun getLastSession(userId: Int): Long = withContext(ioDispatcher) {
        val time = try {
            usersSource.getLastSession(userId)
        } catch (e: BackendException) {
            if (e.code == 404) {
                throw UserNotFoundException(e)
            } else {
                throw e
            }
        }
        Log.d("testGetLastSession", time.toString())
        return@withContext time
    }

    override suspend fun getUser(userId: Int): User = withContext(ioDispatcher) {
        val user = try {
            usersSource.getUser(userId)
        } catch (e: BackendException) {
            if (e.code == 404) {
                throw UserNotFoundException(e)
            } else {
                throw e
            }
        }
        Log.d("testGetUser", user.toString())
        return@withContext user
    }

    override suspend fun createDialog(name: String, keyUser1: String, keyUser2: String): Int = withContext(ioDispatcher) {
        val dialogId = try {
            messagesSource.createDialog(name, keyUser1, keyUser2)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw UserNotFoundException(e)
                409 -> throw DialogAlreadyExistsException(e)
                400 -> throw InvalidKeyException(e)
                else -> throw e
            }
        }
        Log.d("testCreateDialog", "dialogId: $dialogId")
        return@withContext dialogId
    }

    override suspend fun sendMessage(idDialog: Int, text: String?, images: List<String>?,
                         voice: String?, file: String?, code: String?, codeLanguage: String?,
                         referenceToMessageId: Int?, isForwarded: Boolean, isUrl: Boolean?,
                         usernameAuthorOriginal: String?,
                         waveform: List<Int>?): Boolean = withContext(ioDispatcher) {
        return@withContext try {
            messagesSource.sendMessage(idDialog, text, images, voice, file, code, codeLanguage,
                referenceToMessageId, isForwarded, isUrl, usernameAuthorOriginal, waveform)
            Log.d("testSendMessage", "Message sent successfully")
            true
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw DialogNotFoundException(e)
                403 -> throw NoPermissionException(e)
                else -> {
                    Log.e("testSendMessage", "Error sending message: ${e.message}")
                    false
                }
            }
        }
    }

    override suspend fun getMessages(idDialog: Int, pageIndex: Int, pageSize: Int): List<Message> = withContext(ioDispatcher) {
        val messages = try {
            messagesSource.getMessages(idDialog, pageIndex, pageSize)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw DialogNotFoundException(e)
                403 -> throw NoPermissionException(e)
                400 -> throw InvalidStartEndValuesException(e)
                else -> throw e
            }
        }
        val largeMessage = messages.toString()
        val maxLogSize = 1000
        for (i in 0..largeMessage.length / maxLogSize) {
            val start = i * maxLogSize
            val end = ((i + 1) * maxLogSize).coerceAtMost(largeMessage.length)
            Log.d("testGetMessages", largeMessage.substring(start, end))
        }
        return@withContext messages
    }

    override suspend fun findMessage(idMessage: Int, idDialog: Int): Pair<Message, Int> = withContext(ioDispatcher) {
        val message = try {
            messagesSource.findMessage(idMessage, idDialog)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw MessageNotFoundException(e)
                403 -> throw NoPermissionException(e)
                else -> throw e
            }
        }
        Log.d("testFindMessage", message.toString())
        return@withContext message
    }

    override suspend fun editMessage(idDialog: Int, messageId: Int, text: String?, images: List<String>?,
                       voice: String?, file: String?, code: String?, codeLanguage: String?, isUrl: Boolean?): Boolean = withContext(ioDispatcher) {
        val message = try {
            messagesSource.editMessage(idDialog, messageId, text, images, voice, file, code, codeLanguage, isUrl)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw MessageNotFoundException(e)
                403 -> throw NoPermissionException(e)
                400 -> throw NoChangedMadeException(e)
                else -> throw e
            }
        }
        Log.d("testEditMessage", message)
        return@withContext true
    }

    override suspend fun deleteMessages(idDialog: Int, ids: List<Int>): Boolean = withContext(ioDispatcher) {
        val message = try {
            messagesSource.deleteMessages(idDialog, ids)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw MessageNotFoundException(e)
                403 -> throw NoPermissionException(e)
                400 -> throw InvalidIdsException(e)
                else -> throw e
            }
        }
        Log.d("testDeleteMessages", message)
        return@withContext true
    }

    override suspend fun deleteDialog(dialogId: Int): Boolean = withContext(ioDispatcher) {
        val message = try {
            messagesSource.deleteDialog(dialogId)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw DialogNotFoundException(e)
                403 -> throw NoPermissionException(e)
                else -> throw e
            }
        }
        Log.d("testDeleteDialog", message)
        return@withContext true
    }

    override suspend fun getUsers(): List<UserShort> = withContext(ioDispatcher) {
        val users = try {
            messagesSource.getUsers()
        } catch (e: BackendException) {
            if(e.code == 500) throw InvalidCredentialsException(e)
            else throw e
        }
        Log.d("testGetUsers", users.toString())
        return@withContext users
    }

    override suspend fun markMessagesAsRead(idDialog: Int, ids: List<Int>): Boolean = withContext(ioDispatcher) {
        val message = try {
            messagesSource.markMessagesAsRead(idDialog, ids)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw MessageNotFoundException(e)
                403 -> throw NoPermissionException(e)
                400 -> throw InvalidIdsException(e)
                else -> throw e
            }
        }
        Log.d("testMarkMessagesAsRead", message)
        return@withContext true
    }

    override suspend fun searchMessagesInDialog(dialogId: Int): List<Message> = withContext(ioDispatcher) {
        val messagesSearch = try {
            messagesSource.searchMessagesInDialog(dialogId)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw DialogNotFoundException(e)
                403 -> throw NoPermissionException(e)
                else -> throw e
            }
        }
        Log.d("testSearchMessagesInDialog", messagesSearch.toString())
        return@withContext messagesSearch
    }

    override suspend fun toggleDialogCanDelete(dialogId: Int): Boolean = withContext(ioDispatcher) {
        val message = try {
            messagesSource.toggleDialogCanDelete(dialogId)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw DialogNotFoundException(e)
                403 -> throw NoPermissionException(e)
                else -> throw e
            }
        }
        Log.d("testToggleDialogCanDelete", message)
        return@withContext true
    }

    override suspend fun updateAutoDeleteInterval(dialogId: Int, autoDeleteInterval: Int): Boolean = withContext(ioDispatcher) {
        val message = try {
            messagesSource.updateAutoDeleteInterval(dialogId, autoDeleteInterval)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw DialogNotFoundException(e)
                403 -> throw NoPermissionException(e)
                else -> throw e
            }
        }
        Log.d("testUpdateAutoDeleteInterval", message)
        return@withContext true
    }

    override suspend fun deleteDialogMessages(dialogId: Int): Boolean = withContext(ioDispatcher) {
        val message = try {
            messagesSource.deleteDialogMessages(dialogId)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw DialogNotFoundException(e)
                403 -> throw NoPermissionException(e)
                else -> throw e
            }
        }
        Log.d("testDeleteDialogMessages", message)
        return@withContext true
    }

    override suspend fun createGroup(name: String, key: String): Int = withContext(ioDispatcher) {
        val groupId = try {
            groupsSource.createGroup(name, key)
        } catch (e: BackendException) {
            throw if(e.code == 400) InvalidKeyException(e) else e
        }
        Log.d("testCreateGroup", "groupId: $groupId")
        return@withContext groupId
    }

    override suspend fun sendGroupMessage(groupId: Int, text: String?, images: List<String>?,
             voice: String?, file: String?, code: String?, codeLanguage: String?, referenceToMessageId: Int?,
             isForwarded: Boolean, isUrl: Boolean?, usernameAuthorOriginal: String?, waveform: List<Int>?): Boolean = withContext(ioDispatcher) {
        val message = try {
            groupsSource.sendGroupMessage(groupId, text, images, voice, file, code, codeLanguage,
                referenceToMessageId, isForwarded, isUrl, usernameAuthorOriginal, waveform)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw GroupNotFoundException(e)
                403 -> throw NoPermissionException(e)
                else -> throw e
            }
        }
        Log.d("testSendGroupMessage", message)
        return@withContext true
    }

    override suspend fun getGroupMessages(groupId: Int, start: Int, end: Int): List<Message> = withContext(ioDispatcher) {
        val groupMessages = try {
            groupsSource.getGroupMessages(groupId, start, end)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw GroupNotFoundException(e)
                403 -> throw NoPermissionException(e)
                400 -> throw InvalidStartEndValuesException(e)
                else -> throw e
            }
        }
        Log.d("testGetGroupMessages", groupMessages.toString())
        return@withContext groupMessages
    }

    override suspend fun findGroupMessage(idMessage: Int, groupId: Int): Pair<Message, Int> = withContext(ioDispatcher) {
        val message = try {
            groupsSource.findGroupMessage(idMessage, groupId)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw MessageNotFoundException(e)
                403 -> throw NoPermissionException(e)
                else -> throw e
            }
        }
        Log.d("testFindGroupMessage", message.toString())
        return@withContext message
    }

    override suspend fun editGroupMessage(groupId: Int, messageId: Int, text: String?,
                              images: List<String>?, voice: String?, file: String?, code: String?,
                              codeLanguage: String?, isUrl: Boolean?): Boolean = withContext(ioDispatcher) {
        val message = try {
            groupsSource.editGroupMessage(groupId, messageId, text, images, voice, file, code, codeLanguage, isUrl)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw MessageNotFoundException(e)
                403 -> throw NoPermissionException(e)
                else -> throw e
            }
        }
        Log.d("testEditGroupMessage", message)
        return@withContext true
    }

    override suspend fun deleteGroupMessages(groupId: Int, ids: List<Int>): Boolean = withContext(ioDispatcher) {
        val message = try {
            groupsSource.deleteGroupMessages(groupId, ids)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw MessageNotFoundException(e)
                403 -> throw NoPermissionException(e)
                400 -> throw InvalidIdsException(e)
                else -> throw e
            }
        }
        Log.d("testDeleteGroupMessages", message)
        return@withContext true
    }

    override suspend fun deleteGroup(groupId: Int): Boolean = withContext(ioDispatcher) {
        val message = try {
            groupsSource.deleteGroup(groupId)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw GroupNotFoundException(e)
                403 -> throw NoPermissionException(e)
                else -> throw e
            }
        }
        Log.d("testDeleteGroup", message)
        return@withContext true
    }

    override suspend fun editGroupName(groupId: Int, name: String): Boolean = withContext(ioDispatcher) {
        val message = try {
            groupsSource.editGroupName(groupId, name)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw GroupNotFoundException(e)
                403 -> throw NoPermissionException(e)
                else -> throw e
            }
        }
        Log.d("testEditGroupName", message)
        return@withContext true
    }

    override suspend fun addUserToGroup(groupId: Int, name: String, key: String): Boolean = withContext(ioDispatcher) {
        val message = try {
            groupsSource.addUserToGroup(groupId, name, key)
        } catch (e: BackendException) {
            when (e.code) {
                403 -> throw NoPermissionException(e)
                404 -> throw UserNotFoundException(e)
                409 -> throw UserAlreadyInGroupException(e)
                400 -> throw InvalidKeyException(e)
                else -> throw e
            }
        }
        Log.d("testAddUserToGroup", message)
        return@withContext true
    }


    override suspend fun deleteUserFromGroup(groupId: Int, userId: Int): Boolean = withContext(ioDispatcher) {
        val message = try {
            groupsSource.deleteUserFromGroup(groupId, userId)
        } catch (e: BackendException) {
            when (e.code) {
                403 -> throw NoPermissionException(e)
                404 -> throw UserIsNotAMemberOfGroupException(e)
                else -> throw e
            }
        }
        Log.d("testDeleteUserFromGroup", message)
        return@withContext true
    }

    override suspend fun getAvailableUsersForGroup(groupId: Int): List<UserShort> = withContext(ioDispatcher) {
        val users = try {
            groupsSource.getAvailableUsersForGroup(groupId)
            } catch (e: BackendException) {
            if(e.code == 500) throw InvalidCredentialsException(e)
            else throw e
        }
        Log.d("testGetAvailableUsersForGroup", users.toString())
        return@withContext users
    }

    override suspend fun getGroupMembers(groupId: Int): List<User> = withContext(ioDispatcher) {
        val members = try {
            groupsSource.getGroupMembers(groupId)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw GroupNotFoundException(e)
                403 -> throw NoPermissionException(e)
                else -> throw e
            }
        }
        Log.d("testGetGroupMembers", members.toString())
        return@withContext members
    }

    override suspend fun updateGroupAvatar(groupId: Int, avatar: String): Boolean = withContext(ioDispatcher) {
        val message = try {
            groupsSource.updateGroupAvatar(groupId, avatar)
            } catch (e: BackendException) {
            when (e.code) {
                404 -> throw GroupNotFoundException(e)
                403 -> throw NoPermissionException(e)
                else -> throw e
            }
        }
        Log.d("testUpdateGroupAvatar", message)
        return@withContext true
    }

    override suspend fun markGroupMessagesAsRead(groupId: Int, ids: List<Int>): Boolean = withContext(ioDispatcher) {
        val message = try {
            groupsSource.markGroupMessagesAsRead(groupId, ids)
            } catch (e: BackendException) {
            when (e.code) {
                404 -> throw MessageNotFoundException(e)
                403 -> throw NoPermissionException(e)
                400 -> throw InvalidIdsException(e)
                else -> throw e
            }
        }
        Log.d("testMarkGroupMessagesAsRead", message)
        return@withContext true
    }

    override suspend fun toggleGroupCanDelete(groupId: Int): Boolean = withContext(ioDispatcher) {
        val message = try {
            groupsSource.toggleGroupCanDelete(groupId)
            } catch (e: BackendException) {
            when (e.code) {
                404 -> throw GroupNotFoundException(e)
                403 -> throw NoPermissionException(e)
                else -> throw e
            }
        }
        Log.d("testToggleGroupCanDelete", message)
        return@withContext true
    }

    override suspend fun updateGroupAutoDeleteInterval(groupId: Int, autoDeleteInterval: Int): Boolean = withContext(ioDispatcher) {
        val message = try {
            groupsSource.updateGroupAutoDeleteInterval(groupId, autoDeleteInterval)
            } catch (e: BackendException) {
            when (e.code) {
                404 -> throw GroupNotFoundException(e)
                403 -> throw NoPermissionException(e)
                else -> throw e
            }
        }
        Log.d("testUpdateGroupAutoDeleteInterval", message)
        return@withContext true
    }

    override suspend fun deleteGroupMessagesAll(groupId: Int): Boolean = withContext(ioDispatcher) {
        val message = try {
            groupsSource.deleteGroupMessagesAll(groupId)
            } catch (e: BackendException) {
                when (e.code) {
                404 -> throw GroupNotFoundException(e)
                403 -> throw NoPermissionException(e)
                else -> throw e
            }
        }
        Log.d("testDeleteGroupMessagesAll", message)
        return@withContext true
    }

    override suspend fun searchMessagesInGroup(groupId: Int): List<Message> = withContext(ioDispatcher) {
        val messagesGroupSearch = try {
            groupsSource.searchMessagesInGroup(groupId)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw GroupNotFoundException(e)
                403 -> throw NoPermissionException(e)
                else -> throw e
            }
        }
        Log.d("testSearchMessagesInDialog", messagesGroupSearch.toString())
        return@withContext messagesGroupSearch
    }

    override suspend fun uploadPhoto(dialogId: Int, photo: File, isGroup: Int?): String = withContext(ioDispatcher) {
        val file = try {
            uploadSource.uploadPhoto(dialogId, isGroup ?: 0, photo)
        } catch (e: BackendException) {
            if(e.code == 400) throw InvalidCredentialsException(e)
            else throw e
        }
        Log.d("testUploadPhoto", file)
        return@withContext file
    }

    override suspend fun uploadPhotoPreview(dialogId: Int, photoPreview: File, isGroup: Int?): Boolean = withContext(ioDispatcher) {
        val message = try {
            uploadSource.uploadPhotoPreview(dialogId, isGroup ?: 0, photoPreview)
        } catch (e: BackendException) {
            if(e.code == 400) throw InvalidCredentialsException(e)
            else throw e
        }
        Log.d("testUploadPreview", message)
        return@withContext true
    }

    override suspend fun uploadFile(dialogId: Int, file: File, isGroup: Int?): String = withContext(ioDispatcher) {
        val fileUpload = try {
            uploadSource.uploadFile(dialogId, isGroup ?: 0, file)
        } catch (e: BackendException) {
            if(e.code == 400) throw InvalidCredentialsException(e)
            else throw e
        }
        Log.d("testUploadFile", fileUpload)
        return@withContext fileUpload
    }

    override suspend fun uploadAudio(dialogId: Int, audio: File, isGroup: Int?): String = withContext(ioDispatcher) {
        val file = try {
            uploadSource.uploadAudio(dialogId, isGroup ?: 0, audio)
        } catch (e: BackendException) {
            if(e.code == 400) throw InvalidCredentialsException(e)
            else throw e
        }
        Log.d("testUploadAudio", file)
        return@withContext file
    }

    override suspend fun uploadAvatar(avatar: File): String = withContext(ioDispatcher) {
        val file = try {
            uploadSource.uploadAvatar(avatar)
        } catch (e: BackendException) {
            if(e.code == 400) throw InvalidCredentialsException(e)
            else throw e
        }
        Log.d("testUploadAvatar", file)
        return@withContext file
    }

    override suspend fun downloadFile(folder: String, dialogId: Int,
                                      filename: String, isGroup: Int?): String = withContext(ioDispatcher) {
        val filePath = try {
            uploadSource.downloadFile(appContext, folder, dialogId, filename, isGroup ?: 0)
        } catch (e: BackendException) {
            if(e.code == 400) throw InvalidCredentialsException(e)
            else throw e
        }
        Log.d("testDownloadFile", filePath)
        return@withContext filePath
    }

    override suspend fun downloadAvatar(filename: String): String = withContext(ioDispatcher) {
        val filePath = try {
            uploadSource.downloadAvatar(appContext, filename)
        } catch (e: BackendException) {
            if(e.code == 400) throw InvalidCredentialsException(e)
            else throw e
        }
        Log.d("testDownloadAvatar", filePath)
        return@withContext filePath
    }

    override suspend fun getMedias(dialogId: Int, page: Int, isGroup: Int?): List<String>? = withContext(ioDispatcher) {
        val files = try {
            uploadSource.getMediaPreviews(isGroup ?: 0, dialogId, page)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw FileNotFoundException(e)
                400 -> throw InvalidCredentialsException(e)
                else -> throw e
            }
        }
        return@withContext files
    }

    override suspend fun getFiles(dialogId: Int, page: Int, isGroup: Int?): List<String>? = withContext(ioDispatcher) {
        val files = try {
            uploadSource.getFiles(isGroup ?: 0, dialogId, page)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw FileNotFoundException(e)
                400 -> throw InvalidCredentialsException(e)
                else -> throw e
            }
        }
        return@withContext files
    }

    override suspend fun getAudios(dialogId: Int, page: Int, isGroup: Int?): List<String>? = withContext(ioDispatcher) {
        val files = try {
            uploadSource.getAudios(isGroup ?: 0, dialogId, page)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw FileNotFoundException(e)
                400 -> throw InvalidCredentialsException(e)
                else -> throw e
            }
        }
        return@withContext files
    }

    override suspend fun getMediaPreview(dialogId: Int, filename: String,
                                         isGroup: Int?): String = withContext(ioDispatcher) {
        val preview = try {
            uploadSource.getMediaPreview(appContext, dialogId, filename, isGroup ?: 0)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw FileNotFoundException(e)
                400 -> throw InvalidCredentialsException(e)
                else -> throw e
            }
        }
        return@withContext preview
    }

    override suspend fun getPermission(): Int = withContext(ioDispatcher) {
        val permission = try {
            usersSource.getPermission()
        } catch (e: BackendException) {
            when(e.code) {
                404 -> throw UserNotFoundException(e)
                else -> throw e
            }
        }
        return@withContext permission
    }

    override suspend fun getVacation(): Pair<String, String>? = withContext(ioDispatcher) {
        val vacation = try {
            usersSource.getVacation()
        } catch (e: BackendException) {
            when(e.code) {
                404 -> throw UserNotFoundException(e)
                else -> throw e
            }
        }
        return@withContext vacation
    }

    override suspend fun sendNews(headerText: String?, text: String?, images: List<String>?,
                         voices: List<String>?, files: List<String>?): Boolean = withContext(ioDispatcher) {
        return@withContext try {
            newsSource.sendNews(headerText, text, images, voices, files)
            Log.d("testSendNews", "News post sent successfully")
            true
        } catch (e: BackendException) {
            when (e.code) {
                403 -> throw NoPermissionException(e)
                else -> {
                    Log.e("testSendNews", "Error sending news post: ${e.message}")
                    false
                }
            }
        }
    }

    override suspend fun getNews(pageIndex: Int, pageSize: Int): List<News> = withContext(ioDispatcher) {
        val news = try {
            newsSource.getNews(pageIndex, pageSize)
        } catch (e: BackendException) {
            when (e.code) {
                400 -> throw InvalidStartEndValuesException(e)
                else -> throw e
            }
        }
        val largeMessage = news.toString()
        val maxLogSize = 1000
        for (i in 0..largeMessage.length / maxLogSize) {
            val start = i * maxLogSize
            val end = ((i + 1) * maxLogSize).coerceAtMost(largeMessage.length)
            Log.d("testGetNews", largeMessage.substring(start, end))
        }
        return@withContext news
    }

    override suspend fun editNews(newsId: Int, headerText: String?, text: String?, images: List<String>?,
                     voices: List<String>?, files: List<String>?): Boolean = withContext(ioDispatcher) {
        val message = try {
            newsSource.editNews(newsId, headerText, text, images, voices, files)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw MessageNotFoundException(e)
                403 -> throw NoPermissionException(e)
                400 -> throw NoChangedMadeException(e)
                else -> throw e
            }
        }
        Log.d("testEditNews", message)
        return@withContext true
    }

    override suspend fun deleteNews(newsId: Int): Boolean = withContext(ioDispatcher) {
        val message = try {
            newsSource.deleteNews(newsId)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw MessageNotFoundException(e)
                403 -> throw NoPermissionException(e)
                else -> throw e
            }
        }
        Log.d("testDeleteNews", message)
        return@withContext true
    }

    override suspend fun uploadNews(news: File): String = withContext(ioDispatcher) {
        val file = try {
            uploadSource.uploadNews(news)
        } catch (e: BackendException) {
            if(e.code == 400) throw InvalidCredentialsException(e)
            else throw e
        }
        Log.d("testUploadNews", file)
        return@withContext file
    }

    override suspend fun downloadNews(filename: String): String = withContext(ioDispatcher) {
        val filePath = try {
            uploadSource.downloadNews(appContext, filename)
        } catch (e: BackendException) {
            if(e.code == 400) throw InvalidCredentialsException(e)
            else throw e
        }
        Log.d("testDownloadNews", filePath)
        return@withContext filePath
    }

    override suspend fun saveFCMToken(token: String): Boolean = withContext(ioDispatcher) {
        val message = try {
            usersSource.saveFCMToken(token)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw UserNotFoundException(e)
                400 -> throw InvalidCredentialsException(e)
                else -> throw e
            }
        }
        Log.d("testSaveFCMToken", message)
        return@withContext true
    }

    override suspend fun deleteFCMToken(): Boolean = withContext(ioDispatcher) {
        val message = try {
            usersSource.deleteFCMToken()
        } catch (e: BackendException) {
            when(e.code) {
                404 -> throw UserNotFoundException(e)
                else -> throw e
            }
        }
        Log.d("testDeleteFCMToken", message)
        return@withContext true
    }

    override suspend fun refreshToken(token: String): String = withContext(ioDispatcher) {
        val newToken = try {
            usersSource.refreshToken(token)
        } catch (e: BackendException) { throw e }
        Log.d("testRefreshToken", "OK")
        return@withContext newToken
    }

    override suspend fun getUserKey(name: String): String? = withContext(ioDispatcher) {
        val publicKey = try {
            usersSource.getUserKey(name)
        } catch (e: BackendException) {
            throw if(e.code == 404) UserNotFoundException(e) else e
        }
        Log.d("testGetPublicKey", "user($name) token is OK")
        return@withContext publicKey
    }

    override suspend fun getKeys(): Pair<String?, String?> = withContext(ioDispatcher) {
        val pair = try {
            usersSource.getKeys()
        } catch (e: BackendException) {
            throw if(e.code == 404) UserNotFoundException(e) else e
        }
        Log.d("testGetKeys", "OK")
        return@withContext pair
    }

    override suspend fun saveUserKeys(publicKey: String, privateKey: String): Boolean = withContext(ioDispatcher) {
        val message = try {
            usersSource.saveUserKeys(publicKey, privateKey)
        } catch (e: BackendException) {
            when(e.code) {
                404 -> throw UserNotFoundException(e)
                400 -> throw InvalidKeyException(e)
                else -> throw e
            }
        }
        Log.d("testSaveKeys", message)
        return@withContext true
    }

    override suspend fun getNewsKey(): String? = withContext(ioDispatcher) {
        val key = try {
            newsSource.getNewsKey()
        } catch (e: BackendException) {
            if(e.code == 404) throw InvalidKeyException(e)
            else throw e
        }
        Log.d("testNewsKey", key.toString())
        return@withContext key
    }

    override suspend fun getRepos(token: String): List<Repo> = withContext(ioDispatcher) {
        val list = try {
            gitlabSource.getRepos(token)
        } catch (e: BackendException) {
            if(e.code == 404) throw RepositoryNotFoundException(e)
            else throw e
        }
        Log.d("testGetRepos", list.toString())
        return@withContext list
    }

    override suspend fun updateRepo(projectId: Int, hPush: Boolean?, hMerge: Boolean?, hTag: Boolean?,
        hIssue: Boolean?, hNote: Boolean?, hRelease: Boolean?): Boolean = withContext(ioDispatcher) {
        val message = try {
            gitlabSource.updateRepo(projectId, hPush, hMerge, hTag, hIssue, hNote, hRelease)
        } catch (e: BackendException) { throw e }
        Log.d("testUpdateRepo", message)
        return@withContext true
    }
}