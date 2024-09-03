package com.example.messenger

import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messenger.model.Conversation
import com.example.messenger.model.Message
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.UnknownHostException
import javax.inject.Inject

@HiltViewModel
class MessengerViewModel @Inject constructor(
    private val messengerService: MessengerService,
    private val retrofitService: RetrofitService
) : ViewModel() {

    private val _conversations = MutableLiveData<List<Conversation>>()
    val conversations: LiveData<List<Conversation>> = _conversations
    private val _currentUser = MutableLiveData<User>()
    val currentUser: LiveData<User> = _currentUser

    init {
        fetchCurrentUser()
        fetchConversations()
    }

    private fun fetchCurrentUser() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var initialUser: User? = null
                try {
                    val initUser = messengerService.getUser()
                    _currentUser.postValue(initUser)
                    initialUser = initUser
                } catch (e: Exception) {Log.e("Can't take user in db", e.toString())}
                val user = retrofitService.getUser(0)
                _currentUser.postValue(user)
                if(user.username != initialUser?.username || user.avatar != initialUser.avatar) {
                    messengerService.updateUser(user)
                }
            } catch (e: Exception) {
                // todo Toast error
            }
        }
    }

    private fun fetchConversations() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val initialConversations = messengerService.getConversations()
                _conversations.postValue(initialConversations)
                while (true) {
                    try {
                        val updatedConversations = retrofitService.getConversations()
                        _conversations.postValue(updatedConversations)
                        messengerService.replaceConversations(updatedConversations)
                    } catch (e: UnknownHostException) {
                        // Handle network issues
                    } catch (e: Exception) {
                        // Handle other exceptions
                    }
                    kotlinx.coroutines.delay(30000)
                }
            } catch (e: Exception) {
                // Handle exceptions
            }
        }
    }

    fun createDialog(input: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (retrofitService.createDialog(input)) {
                _conversations.postValue(retrofitService.getConversations())
            }
        }
    }

    fun createGroup(input: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (retrofitService.createGroup(input)) {
                _conversations.postValue(retrofitService.getConversations())
            }
        }
    }

    fun forwardMessages(list: List<Message>?, usernames: List<String>?, id: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                list?.forEachIndexed { index, message ->
                    if(message.usernameAuthorOriginal == null) {
                        forwardMessage(id, message.text, message.images, message.voice, message.file,
                            message.referenceToMessageId, usernames?.get(index))
                    } else {
                        forwardMessage(id, message.text, message.images, message.voice, message.file,
                            message.referenceToMessageId, message.usernameAuthorOriginal)
                    }
                }
            }
        }
    }

    private suspend fun forwardMessage(idDialog: Int, text: String?, images: List<String>?,
                            voice: String?, file: String?, referenceToMessageId: Int?,
                            usernameAuthorOriginal: String?) = withContext(Dispatchers.IO) {
        retrofitService.sendMessage(idDialog, text, images, voice, file, referenceToMessageId, true, usernameAuthorOriginal)
    }
}