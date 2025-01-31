package com.example.messenger

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messenger.di.IoDispatcher
import com.example.messenger.model.Conversation
import com.example.messenger.model.FileManager
import com.example.messenger.model.Message
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.User
import com.example.messenger.model.WebSocketService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.UnknownHostException
import javax.inject.Inject

@HiltViewModel
class MessengerViewModel @Inject constructor(
    private val messengerService: MessengerService,
    private val retrofitService: RetrofitService,
    private val fileManager: FileManager,
    private val webSocketService: WebSocketService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _conversations = MutableLiveData<List<Conversation>>()
    val conversations: LiveData<List<Conversation>> = _conversations
    private val _currentUser = MutableLiveData<User>()
    val currentUser: LiveData<User> = _currentUser
    private val _vacation = MutableLiveData<Pair<String, String>?>()
    val vacation: LiveData<Pair<String, String>?> = _vacation

    init {
        fetchVacation()
        fetchCurrentUser()
        fetchConversations()
        //webSocketService.connect() временное отключение
        // todo подписаться на нужные flow WebSocketService
    }

    override fun onCleared() {
        super.onCleared()
        //webSocketService.disconnect() // todo надо тестировать, не факт что здесь
    }

    private fun fetchVacation() {
        viewModelScope.launch{
            try {
                val pair = retrofitService.getVacation()
                _vacation.postValue(pair)
            } catch (e: Exception) {
                Log.e("Connection Error", e.toString())
            }
        }
    }

    private fun fetchCurrentUser() {
        viewModelScope.launch {
            try {
                var initialUser: User? = null
                try {
                    val initUser = messengerService.getUser()
                    Log.d("testInitUser", initUser.toString())
                    if(initUser != null) {
                        _currentUser.postValue(initUser ?: null)
                        initialUser = initUser
                    }
                } catch (e: Exception) {Log.e("Can't take user in db", e.toString())}
                val user = retrofitService.getUser(0)
                _currentUser.postValue(user)
                if(user.id != initialUser?.id || user.username != initialUser.username ||
                    user.avatar != initialUser.avatar) {
                    Log.d("testUpdateCurUser", user.toString())
                    messengerService.updateUser(user)
                }
            } catch (e: Exception) { return@launch }
        }
    }

    private fun fetchConversations() {
        viewModelScope.launch {
            try {
                val initialConversations = messengerService.getConversations()
                _conversations.postValue(initialConversations)

                val updatedConversations = retrofitService.getConversations()
                _conversations.postValue(updatedConversations)
                messengerService.replaceConversations(updatedConversations)

            } catch (e: Exception) { return@launch }
        }
    }

    suspend fun getPermission() : Int {
        return retrofitService.getPermission()
    }

    fun createDialog(input: String) {
        viewModelScope.launch {
            if (retrofitService.createDialog(input)) {
                _conversations.postValue(retrofitService.getConversations())
            }
        }
    }

    fun createGroup(input: String) {
        viewModelScope.launch {
            if (retrofitService.createGroup(input)) {
                _conversations.postValue(retrofitService.getConversations())
            }
        }
    }

    fun forwardMessages(list: List<Message>?, usernames: List<String>?, id: Int) {
        viewModelScope.launch {
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

    private suspend fun forwardMessage(idDialog: Int, text: String?, images: List<String>?,
                            voice: String?, file: String?, referenceToMessageId: Int?,
                            usernameAuthorOriginal: String?) {
        retrofitService.sendMessage(idDialog, text, images, voice, file, referenceToMessageId, true, usernameAuthorOriginal)
    }

    fun fManagerIsExistAvatar(fileName: String): Boolean {
        return fileManager.isExistAvatar(fileName)
    }

    fun fManagerGetAvatarPath(fileName: String): String {
        return fileManager.getAvatarFilePath(fileName)
    }

    suspend fun fManagerSaveAvatar(fileName: String, fileData: ByteArray) = withContext(ioDispatcher) {
        fileManager.saveAvatarFile(fileName, fileData)
    }

    suspend fun downloadAvatar(context: Context, filename: String): String {
        return retrofitService.downloadAvatar(context, filename)
    }
}