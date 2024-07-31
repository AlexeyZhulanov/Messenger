package com.example.messenger.model

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.messenger.Singletons
import com.example.messenger.model.appsettings.AppSettings
import com.example.messenger.retrofit.source.groups.GroupsSource
import com.example.messenger.retrofit.source.messages.MessagesSource
import com.example.messenger.retrofit.source.users.UsersSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


typealias ConversationsListener = (conversations: List<Conversation>) -> Unit
class RetrofitService(
    private val usersSource: UsersSource,
    private val messagesSource: MessagesSource,
    private val groupsSource: GroupsSource,
    private val appSettings: AppSettings,
    private val messengerRepository: MessengerRepository
) : RetrofitRepository {
    private var conversations = listOf<Conversation>()
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

    override suspend fun register(name: String, username: String, password: String) : Boolean {
        Log.d("testStartRegister", name)
        if (name.isBlank()) throw EmptyFieldException(Field.Name)
        if (username.isBlank()) throw EmptyFieldException(Field.Username)
        if (password.isBlank()) throw EmptyFieldException(Field.Password)
        val message = try {
            usersSource.register(name, username, password)
        } catch (e: BackendException) {
            Log.d("testRegisterThrow", "...")
            if(e.code == 400) throw AccountAlreadyExistsException(e)
            else throw e
        }
        Log.d("testRegister", message)
        return true
    }

    override suspend fun login(name: String, password: String) : Boolean {
        if (name.isBlank()) throw EmptyFieldException(Field.Name)
        if (password.isBlank()) throw EmptyFieldException(Field.Password)
        Log.d("testBeforeSaveToken", "OK")
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
        Log.d("testBeforeSetToken", token)
        appSettings.setCurrentToken(token)
        Log.d("testLoginToken", token)
        return true
    }

    override suspend fun getConversations(): List<Conversation> {
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
        return conversations
    }

    override suspend fun updateProfile(username: String?, avatar: String?): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun updatePassword(password: String): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun updateLastSession(): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun getLastSession(userId: Int): Long {
        TODO("Not yet implemented")
    }

    override suspend fun createDialog(name: String): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun sendMessage(idDialog: Int, text: String?, images: List<String>?,
        voice: String?, file: String?): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun getMessages(idDialog: Int, start: Int, end: Int): List<Message> {
        TODO("Not yet implemented")
    }

    override suspend fun addKeyToDialog(dialogId: Int, key: String): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun removeKeyFromDialog(dialogId: Int): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun editMessage(messageId: Int, text: String?, images: List<String>?,
        voice: String?, file: String?): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun deleteMessages(ids: List<Int>): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun deleteDialog(dialogId: Int): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun getUsers(): List<UserShort> {
        TODO("Not yet implemented")
    }

    override suspend fun markMessagesAsRead(ids: List<Int>): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun searchMessagesInDialog(dialogId: Int, word: String): List<Message> {
        TODO("Not yet implemented")
    }

    override suspend fun toggleDialogCanDelete(dialogId: Int): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun updateAutoDeleteInterval(dialogId: Int, autoDeleteInterval: Int): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun deleteDialogMessages(dialogId: Int): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun getDialogSettings(dialogId: Int): ConversationSettings {
        TODO("Not yet implemented")
    }

    override suspend fun createGroup(name: String): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun sendGroupMessage(groupId: Int, text: String?, images: List<String>?,
        voice: String?, file: String?): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun getGroupMessages(groupId: Int, start: Int, end: Int): List<GroupMessage> {
        TODO("Not yet implemented")
    }

    override suspend fun editGroupMessage(groupMessageId: Int, text: String?, images: List<String>?,
        voice: String?, file: String?): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun deleteGroupMessages(ids: List<Int>): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun deleteGroup(groupId: Int): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun editGroupName(groupId: Int, name: String): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun addUserToGroup(groupId: Int, userId: Int): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun deleteUserFromGroup(groupId: Int, userId: Int): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun getAvailableUsersForGroup(groupId: Int): List<UserShort> {
        TODO("Not yet implemented")
    }

    override suspend fun getGroupMembers(groupId: Int): List<User> {
        TODO("Not yet implemented")
    }

    override suspend fun updateGroupAvatar(groupId: Int, avatar: String): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun markGroupMessagesAsRead(ids: List<Int>): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun toggleGroupCanDelete(groupId: Int): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun updateGroupAutoDeleteInterval(groupId: Int, autoDeleteInterval: Int): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun deleteGroupMessagesAll(groupId: Int): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun getGroupSettings(groupId: Int): ConversationSettings {
        TODO("Not yet implemented")
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