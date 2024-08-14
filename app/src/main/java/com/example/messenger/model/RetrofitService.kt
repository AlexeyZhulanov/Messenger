package com.example.messenger.model

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.messenger.Singletons
import com.example.messenger.model.appsettings.AppSettings
import com.example.messenger.retrofit.source.groups.GroupsSource
import com.example.messenger.retrofit.source.messages.MessagesSource
import com.example.messenger.retrofit.source.uploads.UploadsSource
import com.example.messenger.retrofit.source.users.UsersSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


typealias ConversationsListener = (conversations: List<Conversation>) -> Unit
class RetrofitService(
    private val usersSource: UsersSource,
    private val messagesSource: MessagesSource,
    private val groupsSource: GroupsSource,
    private val uploadSource: UploadsSource,
    private val appSettings: AppSettings,
    private val messengerRepository: MessengerRepository
) : RetrofitRepository {
    private var conversations = listOf<Conversation>()
    private var messages = listOf<Message>()
    private var groupMessages = listOf<GroupMessage>()
    private val listeners = mutableSetOf<ConversationsListener>()
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.IO + job)

    private val _initCompleted = MutableLiveData<Boolean>()
    val initCompleted: LiveData<Boolean> get() = _initCompleted

    private val messengerService: MessengerService
        get() = messengerRepository as MessengerService

    init {
        uiScope.launch {
            _initCompleted.postValue(true)
        }
    }

    override fun isSignedIn(): Boolean {
        // user is signed-in if auth token exists
        return appSettings.getCurrentToken() != null
    }

    override suspend fun register(name: String, username: String, password: String) : Boolean = withContext(Dispatchers.IO) {
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

    override suspend fun login(name: String, password: String) : Boolean = withContext(Dispatchers.IO) {
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

    override suspend fun getConversations(): List<Conversation> = withContext(Dispatchers.IO) {
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
                uiScope.launch {
                    val correct = async {
                        login(name, password)
                    }
                    if(correct.await())
                        conversations = messagesSource.getConversations()
                }
            }
        }
        Log.d("testConversations", conversations.toString())
        return@withContext conversations
    }

    override suspend fun updateProfile(username: String?, avatar: String?): Boolean = withContext(Dispatchers.IO) {
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

    override suspend fun updatePassword(password: String): Boolean = withContext(Dispatchers.IO) {
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

    override suspend fun updateLastSession(): Boolean = withContext(Dispatchers.IO) {
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

    override suspend fun getLastSession(userId: Int): Long = withContext(Dispatchers.IO) {
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

    override suspend fun createDialog(name: String): Boolean = withContext(Dispatchers.IO) {
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
        voice: String?, file: String?): Boolean = withContext(Dispatchers.IO) {
        val message = try {
            messagesSource.sendMessage(idDialog, text, images, voice, file)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw DialogNotFoundException(e)
                403 -> throw NoPermissionException(e)
                else -> throw e
            }
        }
        Log.d("testSendMessage", message)
        return@withContext true
    }

    override suspend fun getMessages(idDialog: Int, start: Int, end: Int): List<Message> = withContext(Dispatchers.IO) {
        messages = try {
            messagesSource.getMessages(idDialog, start, end)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw DialogNotFoundException(e)
                403 -> throw NoPermissionException(e)
                400 -> throw InvalidStartEndValuesException(e)
                else -> throw e

            }
        }
        Log.d("testGetMessages", messages.toString())
        return@withContext messages
    }

    override suspend fun addKeyToDialog(dialogId: Int, key: String): Boolean = withContext(Dispatchers.IO) {
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

    override suspend fun removeKeyFromDialog(dialogId: Int): Boolean = withContext(Dispatchers.IO) {
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

    override suspend fun editMessage(messageId: Int, text: String?, images: List<String>?,
        voice: String?, file: String?): Boolean = withContext(Dispatchers.IO) {
        val message = try {
            messagesSource.editMessage(messageId, text, images, voice, file)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw MessageNotFoundException(e)
                403 -> throw NoPermissionException(e)
                else -> throw e
            }
        }
        Log.d("testEditMessage", message)
        return@withContext true
    }

    override suspend fun deleteMessages(ids: List<Int>): Boolean = withContext(Dispatchers.IO) {
        val message = try {
            messagesSource.deleteMessages(ids)
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

    override suspend fun deleteDialog(dialogId: Int): Boolean = withContext(Dispatchers.IO) {
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

    override suspend fun getUsers(): List<UserShort> = withContext(Dispatchers.IO) {
        val users = try {
            messagesSource.getUsers()
        } catch (e: BackendException) {
            if(e.code == 500) throw InvalidCredentialsException(e)
            else throw e
        }
        Log.d("testGetUsers", users.toString())
        return@withContext users
    }

    override suspend fun markMessagesAsRead(ids: List<Int>): Boolean = withContext(Dispatchers.IO) {
        val message = try {
            messagesSource.markMessagesAsRead(ids)
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

    override suspend fun searchMessagesInDialog(dialogId: Int, word: String): List<Message> = withContext(Dispatchers.IO) {
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

    override suspend fun toggleDialogCanDelete(dialogId: Int): Boolean = withContext(Dispatchers.IO) {
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

    override suspend fun updateAutoDeleteInterval(dialogId: Int, autoDeleteInterval: Int): Boolean = withContext(Dispatchers.IO) {
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

    override suspend fun deleteDialogMessages(dialogId: Int): Boolean = withContext(Dispatchers.IO) {
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

    override suspend fun getDialogSettings(dialogId: Int): ConversationSettings = withContext(Dispatchers.IO) {
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

    override suspend fun createGroup(name: String): Boolean = withContext(Dispatchers.IO) {
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
        voice: String?, file: String?): Boolean = withContext(Dispatchers.IO) {
        val message = try {
            groupsSource.sendGroupMessage(groupId, text, images, voice, file)
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

    override suspend fun getGroupMessages(groupId: Int, start: Int, end: Int): List<GroupMessage> = withContext(Dispatchers.IO) {
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

    override suspend fun editGroupMessage(groupMessageId: Int, text: String?, images: List<String>?,
        voice: String?, file: String?): Boolean = withContext(Dispatchers.IO) {
        val message = try {
            groupsSource.editGroupMessage(groupMessageId, text, images, voice, file)
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

    override suspend fun deleteGroupMessages(ids: List<Int>): Boolean = withContext(Dispatchers.IO) {
        val message = try {
            groupsSource.deleteGroupMessages(ids)
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

    override suspend fun deleteGroup(groupId: Int): Boolean = withContext(Dispatchers.IO) {
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

    override suspend fun editGroupName(groupId: Int, name: String): Boolean = withContext(Dispatchers.IO) {
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

    override suspend fun addUserToGroup(groupId: Int, userId: Int): Boolean = withContext(Dispatchers.IO) {
        val message = try {
            groupsSource.addUserToGroup(groupId, userId)
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

    override suspend fun deleteUserFromGroup(groupId: Int, userId: Int): Boolean = withContext(Dispatchers.IO) {
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

    override suspend fun getAvailableUsersForGroup(groupId: Int): List<UserShort> = withContext(Dispatchers.IO) {
        val users = try {
            groupsSource.getAvailableUsersForGroup(groupId)
            } catch (e: BackendException) {
            if(e.code == 500) throw InvalidCredentialsException(e)
            else throw e
        }
        Log.d("testGetAvailableUsersForGroup", users.toString())
        return@withContext users
    }

    override suspend fun getGroupMembers(groupId: Int): List<User> = withContext(Dispatchers.IO) {
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

    override suspend fun updateGroupAvatar(groupId: Int, avatar: String): Boolean = withContext(Dispatchers.IO) {
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

    override suspend fun markGroupMessagesAsRead(ids: List<Int>): Boolean = withContext(Dispatchers.IO) {
        val message = try {
            groupsSource.markGroupMessagesAsRead(ids)
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

    override suspend fun toggleGroupCanDelete(groupId: Int): Boolean = withContext(Dispatchers.IO) {
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

    override suspend fun updateGroupAutoDeleteInterval(groupId: Int, autoDeleteInterval: Int): Boolean = withContext(Dispatchers.IO) {
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

    override suspend fun deleteGroupMessagesAll(groupId: Int): Boolean = withContext(Dispatchers.IO) {
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

    override suspend fun getGroupSettings(groupId: Int): ConversationSettings = withContext(Dispatchers.IO) {
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

    override suspend fun searchMessagesInGroup(groupId: Int, word: String): List<GroupMessage> = withContext(Dispatchers.IO) {
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

    override suspend fun uploadPhoto(photo: File): String = withContext(Dispatchers.IO) {
        val file = try {
            uploadSource.uploadPhoto(photo)
        } catch (e: BackendException) {
            if(e.code == 400) throw InvalidCredentialsException(e)
            else throw e
        }
        Log.d("testUploadPhoto", file)
        return@withContext file
    }

    override suspend fun uploadFile(file: File): String = withContext(Dispatchers.IO) {
        val fileUpload = try {
            uploadSource.uploadFile(file)
        } catch (e: BackendException) {
            if(e.code == 400) throw InvalidCredentialsException(e)
            else throw e
        }
        Log.d("testUploadFile", fileUpload)
        return@withContext fileUpload
    }

    override suspend fun uploadAudio(audio: File): String = withContext(Dispatchers.IO) {
        val file = try {
            uploadSource.uploadAudio(audio)
        } catch (e: BackendException) {
            if(e.code == 400) throw InvalidCredentialsException(e)
            else throw e
        }
        Log.d("testUploadAudio", file)
        return@withContext file
    }

    override suspend fun downloadFile(context: Context, folder: String, filename: String): String = withContext(Dispatchers.IO) {
        val filePath = try {
            uploadSource.downloadFile(context, folder, filename)
        } catch (e: BackendException) {
            if(e.code == 400) throw InvalidCredentialsException(e)
            else throw e
        }
        Log.d("testDownloadFile", filePath)
        return@withContext filePath
    }

    override suspend fun deleteFile(folder: String, filename: String): Boolean = withContext(Dispatchers.IO) {
        val message = try {
            uploadSource.deleteFile(folder, filename)
        } catch (e: BackendException) {
            when (e.code) {
                404 -> throw FileNotFoundException(e)
                400 -> throw InvalidCredentialsException(e)
                else -> throw e
            }
        }
        Log.d("testDeleteFile", message)
        return@withContext true
    }

    fun addListener(listener: ConversationsListener) {
        listeners.add(listener)
        listener.invoke(conversations)
    }
    fun removeListener(listener: ConversationsListener) = listeners.remove(listener)
    suspend fun notifyChanges() = withContext(Dispatchers.Main + job) {
        listeners.forEach {
            it.invoke(conversations)
        }
    }

}