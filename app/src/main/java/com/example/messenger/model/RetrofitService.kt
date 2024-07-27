package com.example.messenger.model

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.messenger.model.appsettings.AppSettings
import com.example.messenger.retrofit.source.groups.GroupsSource
import com.example.messenger.retrofit.source.messages.MessagesSource
import com.example.messenger.retrofit.source.users.UsersSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


typealias ConversationsListener = (conversations: List<Conversation>) -> Unit
class RetrofitService(
    private val usersSource: UsersSource,
    private val messagesSource: MessagesSource,
    private val groupsSource: GroupsSource,
    private val appSettings: AppSettings,
    //private val messengerService: MessengerService
) {
    private var conversations = listOf<Conversation>()
    private val listeners = mutableSetOf<ConversationsListener>()
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.IO + job)

    private val _initCompleted = MutableLiveData<Boolean>()
    val initCompleted: LiveData<Boolean> get() = _initCompleted

    init {
        uiScope.launch {
            conversations = getConversations()
            _initCompleted.postValue(true)
        }
    }

    fun isSignedIn(): Boolean {
        // user is signed-in if auth token exists
        return appSettings.getCurrentToken() != null
    }

    suspend fun register(name: String, username: String, password: String) {
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
    }

    suspend fun login(name: String, password: String) : List<Conversation> {
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
        // load account data
        conversations = try {
            messagesSource.getConversations()
        } catch (e: BackendException) {
            if (e.code == 500) {
                throw InvalidCredentialsException(e)
            } else {
                throw e
            }
        }
        return conversations
    }

    suspend fun getConversations(): List<Conversation> {
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
//        else {
//            val settings = messengerService.getSettings()
//            conversations = login(settings.name, settings.password)
//        }
        return conversations
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