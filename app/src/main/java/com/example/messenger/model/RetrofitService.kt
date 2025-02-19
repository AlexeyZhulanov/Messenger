package com.example.messenger.model

import android.content.Context
import android.util.Log
import com.example.messenger.model.appsettings.AppSettings
import com.example.messenger.retrofit.source.groups.GroupsSource
import com.example.messenger.retrofit.source.messages.MessagesSource
import com.example.messenger.retrofit.source.news.NewsSource
import com.example.messenger.retrofit.source.uploads.UploadsSource
import com.example.messenger.retrofit.source.users.UsersSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class RetrofitService(
    private val usersSource: UsersSource,
    private val messagesSource: MessagesSource,
    private val groupsSource: GroupsSource,
    private val uploadSource: UploadsSource,
    private val newsSource: NewsSource,
    private val appSettings: AppSettings,
    private val messengerRepository: MessengerRepository,
    private val ioDispatcher: CoroutineDispatcher
) : RetrofitRepository {
    private var conversations = listOf<Conversation>()
    private var messages = listOf<Message>()
    private var groupMessages = listOf<Message>()
    private var news = listOf<News>()

    private val messengerService: MessengerService
        get() = messengerRepository as MessengerService


    override fun isSignedIn(): Boolean {
        // user is signed-in if auth token exists
        return appSettings.getCurrentToken() != null
    }

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
        val token = try {
            usersSource.login(name, password)
        } catch (e: Exception) {
            if (e is BackendException && e.code == 401) {
                // map 401 error for sign-in to InvalidCredentialsException
                throw InvalidCredentialsException(e)
            } else {
                throw e
            }
        }
        appSettings.setCurrentToken(token)
        Log.d("testLoginToken", token)
        return@withContext true
    }

    override suspend fun getConversations(): List<Conversation> = withContext(ioDispatcher) {
        if(isSignedIn()) {
            conversations = try {
                messagesSource.getConversations()
            } catch (e: BackendException) {
                if (e.code == 500) {
                    throw InvalidCredentialsException(e)
                } else {
                    throw e
                }
        }
    }
        else {
            val settings = messengerService.getSettings()
            if(settings.name != "" && settings.password != "") {
                val name = settings.name ?: ""
                val password = settings.password ?: ""
                CoroutineScope(Dispatchers.Main).launch {
                    val correct = async {
                        login(name, password)
                    }
                    if(correct.await()) conversations = messagesSource.getConversations()
                }
            }
        }
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

    override suspend fun updatePassword(password: String): Boolean = withContext(ioDispatcher) {
        val message = try {
            usersSource.updatePassword(password)
        } catch (e: BackendException) {
            if (e.code == 404) {
                throw UserNotFoundException(e)
            } else {
                throw e
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

    override suspend fun createDialog(name: String): Boolean = withContext(ioDispatcher) {
        val message = try {
            messagesSource.createDialog(name)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw UserNotFoundException(e)
                409 -> throw DialogAlreadyExistsException(e)
                else -> throw e
            }
        }
        Log.d("testCreateDialog", message)
        return@withContext true
    }

    override suspend fun sendMessage(idDialog: Int, text: String?, images: List<String>?,
        voice: String?, file: String?, referenceToMessageId: Int?, isForwarded: Boolean,
         usernameAuthorOriginal: String?): Boolean = withContext(ioDispatcher) {
        return@withContext try {
            messagesSource.sendMessage(idDialog, text, images, voice, file, referenceToMessageId,
                isForwarded, usernameAuthorOriginal)
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
        messages = try {
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

    override suspend fun addKeyToDialog(dialogId: Int, key: String): Boolean = withContext(ioDispatcher) {
        val message = try {
            messagesSource.addKeyToDialog(dialogId, key)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw DialogNotFoundException(e)
                403 -> throw NoPermissionException(e)
                else -> throw e
            }
        }
        Log.d("testAddKeyToDialog", message)
        return@withContext true
    }

    override suspend fun removeKeyFromDialog(dialogId: Int): Boolean = withContext(ioDispatcher) {
        val message = try {
            messagesSource.removeKeyFromDialog(dialogId)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw DialogNotFoundException(e)
                403 -> throw NoPermissionException(e)
                else -> throw e
            }
        }
        Log.d("testRemoveKeyFromDialog", message)
        return@withContext true
    }

    override suspend fun editMessage(idDialog: Int, messageId: Int, text: String?, images: List<String>?,
        voice: String?, file: String?): Boolean = withContext(ioDispatcher) {
        val message = try {
            messagesSource.editMessage(idDialog, messageId, text, images, voice, file)
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

    override suspend fun searchMessagesInDialog(dialogId: Int, word: String): List<Message> = withContext(ioDispatcher) {
        val messagesSearch = try {
            messagesSource.searchMessagesInDialog(dialogId, word)
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

    override suspend fun getDialogSettings(dialogId: Int): ConversationSettings = withContext(ioDispatcher) {
        val settings = try {
            messagesSource.getDialogSettings(dialogId)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw DialogNotFoundException(e)
                else -> throw e
            }
        }
        Log.d("testGetDialogSettings", settings.toString())
        return@withContext settings
    }

    override suspend fun createGroup(name: String): Boolean = withContext(ioDispatcher) {
        val message = try {
            groupsSource.createGroup(name)
        } catch (e: BackendException) {
            if(e.code == 500) throw InvalidCredentialsException(e)
            else throw e
        }
        Log.d("testCreateGroup", message)
        return@withContext true
    }

    override suspend fun sendGroupMessage(groupId: Int, text: String?, images: List<String>?,
        voice: String?, file: String?, referenceToMessageId: Int?, isForwarded: Boolean,
        usernameAuthorOriginal: String?): Boolean = withContext(ioDispatcher) {
        val message = try {
            groupsSource.sendGroupMessage(groupId, text, images, voice, file, referenceToMessageId,
                isForwarded, usernameAuthorOriginal)
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
        groupMessages = try {
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

    override suspend fun addKeyToGroup(groupId: Int, key: String): Boolean = withContext(ioDispatcher) {
        val message = try {
            groupsSource.addKeyToGroup(groupId, key)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw GroupNotFoundException(e)
                403 -> throw NoPermissionException(e)
                else -> throw e
            }
        }
        Log.d("testAddKeyToGroup", message)
        return@withContext true
    }

    override suspend fun removeKeyFromGroup(groupId: Int): Boolean = withContext(ioDispatcher) {
        val message = try {
            groupsSource.removeKeyFromGroup(groupId)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw GroupNotFoundException(e)
                403 -> throw NoPermissionException(e)
                else -> throw e
            }
        }
        Log.d("testRemoveKeyFromGroup", message)
        return@withContext true
    }

    override suspend fun editGroupMessage(groupId: Int, messageId: Int, text: String?,
        images: List<String>?, voice: String?, file: String?): Boolean = withContext(ioDispatcher) {
        val message = try {
            groupsSource.editGroupMessage(groupId, messageId, text, images, voice, file)
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

    override suspend fun addUserToGroup(groupId: Int, name: String): Boolean = withContext(ioDispatcher) {
        val message = try {
            groupsSource.addUserToGroup(groupId, name)
        } catch (e: BackendException) {
            when (e.code) {
                403 -> throw NoPermissionException(e)
                400 -> throw UserAlreadyInGroupException(e)
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

    override suspend fun getGroupSettings(groupId: Int): ConversationSettings = withContext(ioDispatcher) {
        val settings = try {
            groupsSource.getGroupSettings(groupId)
            } catch (e: BackendException) {
            when (e.code) {
                404 -> throw GroupNotFoundException(e)
                else -> throw e
            }
        }
        Log.d("testGetGroupSettings", settings.toString())
        return@withContext settings
    }

    override suspend fun searchMessagesInGroup(groupId: Int, word: String): List<Message> = withContext(ioDispatcher) {
        val messagesGroupSearch = try {
            groupsSource.searchMessagesInGroup(groupId, word)
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

    override suspend fun downloadFile(context: Context, folder: String, dialogId: Int,
                                      filename: String, isGroup: Int?): String = withContext(ioDispatcher) {
        val filePath = try {
            uploadSource.downloadFile(context, folder, dialogId, filename, isGroup ?: 0)
        } catch (e: BackendException) {
            if(e.code == 400) throw InvalidCredentialsException(e)
            else throw e
        }
        Log.d("testDownloadFile", filePath)
        return@withContext filePath
    }

    override suspend fun downloadAvatar(context: Context, filename: String): String = withContext(ioDispatcher) {
        val filePath = try {
            uploadSource.downloadAvatar(context, filename)
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

    override suspend fun getMediaPreview(context: Context, dialogId: Int, filename: String,
                                         isGroup: Int?): String = withContext(ioDispatcher) {
        val preview = try {
            uploadSource.getMediaPreview(context, dialogId, filename, isGroup ?: 0)
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

    override suspend fun sendNews(headerText: String, text: String?, images: List<String>?,
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
        news = try {
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

    override suspend fun editNews(newsId: Int, headerText: String, text: String?, images: List<String>?,
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

    override suspend fun downloadNews(context: Context, filename: String): String = withContext(ioDispatcher) {
        val filePath = try {
            uploadSource.downloadNews(context, filename)
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
}