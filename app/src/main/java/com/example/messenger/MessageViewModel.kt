package com.example.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.messenger.di.IoDispatcher
import com.example.messenger.model.ChatSettings
import com.example.messenger.model.ConversationSettings
import com.example.messenger.model.DeletedMessagesEvent
import com.example.messenger.model.DialogCreatedEvent
import com.example.messenger.model.DialogDeletedEvent
import com.example.messenger.model.DialogMessagesAllDeleted
import com.example.messenger.model.FileManager
import com.example.messenger.model.Message
import com.example.messenger.model.MessagePagingSource
import com.example.messenger.model.MessengerService
import com.example.messenger.model.ReadMessagesEvent
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.TypingEvent
import com.example.messenger.model.UserSessionUpdatedEvent
import com.example.messenger.model.WebSocketListenerInterface
import com.example.messenger.model.WebSocketService
import com.luck.picture.lib.config.PictureMimeType
import com.luck.picture.lib.entity.LocalMedia
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class MessageViewModel @Inject constructor(
    messengerService: MessengerService,
    retrofitService: RetrofitService,
    fileManager: FileManager,
    webSocketService: WebSocketService,
    @IoDispatcher ioDispatcher: CoroutineDispatcher
) : BaseChatViewModel(messengerService, retrofitService, fileManager, webSocketService, ioDispatcher),
    WebSocketListenerInterface {

    private val _lastSessionString = MutableLiveData<String>()
    val lastSessionString: LiveData<String> get() = _lastSessionString

    private var otherUserId: Int = -1
    private var isOtherUserInChat: Boolean = false

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val pagingDataFlow = searchBy.asFlow()
        .debounce(500)
        .flatMapLatest { searchQuery ->
            currentPage.flatMapLatest { page ->
                flow {
                    val pageSize = 30
                    val messages = MessagePagingSource(retrofitService, messengerService, convId,
                        searchQuery, isFirst, fileManager).loadPage(page, pageSize)
                    emit(messages)
                }
            }
        }

    init {
        webSocketService.setListener(this)
        webSocketService.connect()
    }

    override fun setConvInfo(convId: Int, otherUserId: Int, isGroup: Int) {
        super.setConvInfo(convId, otherUserId, isGroup)
        this.otherUserId = otherUserId
        joinDialog()
    }

    fun fetchLastSession() {
        viewModelScope.launch {
            try {
                val session = retrofitService.getLastSession(otherUserId)
                _lastSessionString.value = formatUserSessionDate(session)
            } catch (e: Exception) {
                Log.d("testSessionExcept", e.message.toString())
                _lastSessionString.value = "Unknown"
            }
        }
    }

    fun setMarkScrollListener(recyclerView: RecyclerView, adapter: MessageAdapter) {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                // Проверяем видимые элементы от последнего к первому
                if (lastVisibleItemPosition != RecyclerView.NO_POSITION && firstVisibleItemPosition != RecyclerView.NO_POSITION) {
                    val visibleMessages = (lastVisibleItemPosition downTo firstVisibleItemPosition).mapNotNull { position ->
                        adapter.getItemNotProtected(position).first.takeIf { it.idSender == otherUserId && !it.isRead }
                    }
                    markMessagesAsRead(visibleMessages)
                }
            }
        })
    }

    fun saveLastMessage(id: Int) {
        viewModelScope.launch {
            val temp = messengerService.getLastReadMessage(convId)
            if(temp != null) messengerService.updateLastReadMessage(convId, id)
            else messengerService.saveLastReadMessage(convId, id)
        }
    }

    suspend fun getLastMessageId(): Int {
        return messengerService.getLastReadMessage(convId)?.second ?: -1
    }

    suspend fun getPreviousMessageId(id: Int): Int {
        return messengerService.getPreviousMessage(convId, id)?.id ?: -1
    }

    private fun joinDialog() {
        val joinData = JSONObject()
        joinData.put("dialog_id", convId)
        webSocketService.send("join_dialog", joinData)
    }

    private fun leaveDialog() {
        val leaveData = JSONObject()
        leaveData.put("dialog_id", convId)
        webSocketService.send("leave_dialog", leaveData)
    }

    fun sendTypingEvent(flag: Boolean) {
        val typingData = JSONObject()
        typingData.put("dialog_id", convId)
        if(flag) {
            webSocketService.send("typing", typingData)
        } else {
            webSocketService.send("stop_typing", typingData)
        }
    }

    override fun onNewMessage(message: Message) {
        Log.d("testSocketsMessage", "New Message: $message")
        val newMessagePair = if(lastMessageDate == "") message to "" else message to formatMessageDate(message.timestamp)
        _newMessageFlow.value = newMessagePair
    }

    override fun onEditedMessage(message: Message) {
        Log.d("testSocketsMessage", "Edited Message: $message")
        _editMessageFlow.value = message
        recyclerView.adapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                recyclerView.scrollToPosition(0)
            }
        })
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onMessagesDeleted(deletedMessagesEvent: DeletedMessagesEvent) {
        Log.d("testSocketsMessage", "Deleted messages")
        val adapter = recyclerView.adapter
        if(adapter is MessageAdapter)
            viewModelScope.launch { adapter.clearPositions() }
        refresh()
        recyclerView.adapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                recyclerView.scrollToPosition(0)
            }
        })
    }

    override fun onDialogCreated(dialogCreatedEvent: DialogCreatedEvent) {
        Log.d("testSocketsMessage", "Dialog created")
    }

    override fun onDialogDeleted(dialogDeletedEvent: DialogDeletedEvent) {
        Log.d("testSocketsMessage", "Dialog deleted")
        _deleteState.value = 2
    }

    override fun onUserSessionUpdated(userSessionUpdatedEvent: UserSessionUpdatedEvent) {
        if(userSessionUpdatedEvent.userId == otherUserId && !isOtherUserInChat) {
            Log.d("testSocketsMessage", "Session updated")
            val sessionString = formatUserSessionDate(userSessionUpdatedEvent.lastSession)
            _lastSessionString.postValue(sessionString)
        }
    }

    override fun onStartTyping(typingEvent: TypingEvent) {
        Log.d("testSocketsMessage", "Typing started")
        _typingState.value = true
    }

    override fun onStopTyping(typingEvent: TypingEvent) {
        Log.d("testSocketsMessage", "Typing stopped")
        _typingState.value = false
    }

    override fun onMessagesRead(readMessagesEvent: ReadMessagesEvent) {
        Log.d("testSocketsMessage", "Messages read")
        _readMessagesFlow.value = readMessagesEvent.messagesReadIds
    }

    override fun onAllMessagesDeleted(dialogMessagesAllDeleted: DialogMessagesAllDeleted) {
        Log.d("testSocketsMessage", "All messages deleted")
        refresh()
    }

    override fun onUserJoinedDialog(dialogId: Int, userId: Int) {
        Log.d("testSocketsMessage", "User $userId joined Dialog $dialogId")
        _lastSessionString.postValue("в этом чате")
        isOtherUserInChat = true
    }

    override fun onUserLeftDialog(dialogId: Int, userId: Int) {
        Log.d("testSocketsMessage", "User $userId left Dialog $dialogId")
        _lastSessionString.postValue("был в сети только что")
        isOtherUserInChat = false
    }

    override fun onCleared() {
        leaveDialog()
        super.onCleared()
    }
}