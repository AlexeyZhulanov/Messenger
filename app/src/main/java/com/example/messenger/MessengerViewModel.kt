package com.example.messenger

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messenger.di.IoDispatcher
import com.example.messenger.model.Conversation
import com.example.messenger.model.DeletedMessagesEvent
import com.example.messenger.model.DialogCreatedEvent
import com.example.messenger.model.DialogDeletedEvent
import com.example.messenger.model.DialogMessagesAllDeleted
import com.example.messenger.model.FileManager
import com.example.messenger.model.Message
import com.example.messenger.model.MessengerService
import com.example.messenger.model.ReadMessagesEvent
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.TypingEvent
import com.example.messenger.model.User
import com.example.messenger.model.UserSessionUpdatedEvent
import com.example.messenger.model.WebSocketListenerInterface
import com.example.messenger.model.WebSocketService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
) : ViewModel(), WebSocketListenerInterface {

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
        //webSocketService.setListener(this) временное отключение
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
                        _currentUser.postValue(initUser!!)
                        initialUser = initUser
                    }
                } catch (e: Exception) {Log.e("Can't take user in db", e.toString())}
                val user = retrofitService.getUser(0)
                _currentUser.postValue(user)
                if(user.username != initialUser?.username || user.avatar != initialUser.avatar ||
                    initialUser == null) {
                    Log.d("testUpdateCurUser", user.toString())
                    messengerService.updateUser(user)
                }
            } catch (e: Exception) {
                // todo Toast error
            }
        }
    }

    private fun fetchConversations() {
        viewModelScope.launch {
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

    override fun onNewMessage(message: Message) {
        Log.d("testSocketsMessenger", "New Message: $message")
    }

    override fun onEditedMessage(message: Message) {
        Log.d("testSocketsMessenger", "Edited Message: $message")
    }

    override fun onMessagesDeleted(deletedMessagesEvent: DeletedMessagesEvent) {
        Log.d("testSocketsMessenger", "Messages deleted")
    }

    override fun onDialogCreated(dialogCreatedEvent: DialogCreatedEvent) {
        Log.d("testSocketsMessenger", "Dialog created")
    }

    override fun onDialogDeleted(dialogDeletedEvent: DialogDeletedEvent) {
        Log.d("testSocketsMessenger", "Dialog deleted")
    }

    override fun onUserSessionUpdated(userSessionUpdatedEvent: UserSessionUpdatedEvent) {
        Log.d("testSocketsMessenger", "Session updated")
    }

    override fun onStartTyping(typingEvent: TypingEvent) {
        Log.d("testSocketsMessenger", "Typing started")
    }

    override fun onStopTyping(typingEvent: TypingEvent) {
        Log.d("testSocketsMessenger", "Typing stopped")
    }

    override fun onMessagesRead(readMessagesEvent: ReadMessagesEvent) {
        Log.d("testSocketsMessenger", "Messages read")
    }

    override fun onAllMessagesDeleted(dialogMessagesAllDeleted: DialogMessagesAllDeleted) {
        Log.d("testSocketsMessenger", "All messages deleted")
    }

    override fun onUserJoinedDialog(dialogId: Int, userId: Int) {
        Log.d("testSocketsMessenger", "User $userId joined Dialog $dialogId")
    }

    override fun onUserLeftDialog(dialogId: Int, userId: Int) {
        Log.d("testSocketsMessenger", "User $userId left Dialog $dialogId")
    }
}