package com.example.messenger

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.di.IoDispatcher
import com.example.messenger.model.FileManager
import com.example.messenger.model.MessagePagingSource
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.WebSocketService
import com.example.messenger.model.appsettings.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class MessageViewModel @Inject constructor(
    messengerService: MessengerService,
    retrofitService: RetrofitService,
    fileManager: FileManager,
    webSocketService: WebSocketService,
    appSettings: AppSettings,
    @IoDispatcher ioDispatcher: CoroutineDispatcher
) : BaseChatViewModel(messengerService, retrofitService, fileManager, webSocketService, appSettings, ioDispatcher) {

    private val _lastSessionString = MutableLiveData<String>()
    val lastSessionString: LiveData<String> get() = _lastSessionString

    private var otherUserId: Int = -1
    private var isOtherUserInChat: Boolean = false

    private var pagingSource: MessagePagingSource? = null

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val pagingDataFlow = searchBy.asFlow()
        .debounce(500)
        .flatMapLatest { searchQuery ->
            currentPage.flatMapLatest { page ->
                flow {
                    val pageSize = 30
                    val messages = pagingSource?.loadPage(page, pageSize, searchQuery)
                    if(messages != null) emit(messages)
                }
            }
        }

    init {
        webSocketService.reconnectIfNeeded()
        viewModelScope.launch {
            initFlow.collect {
                pagingSource = MessagePagingSource(retrofitService, messengerService, convId, fileManager, tinkAesGcmHelper, true)
            }
        }
        viewModelScope.launch {
            webSocketService.newMessageFlow.collect { message ->
                if(!disableRefresh) {
                    message.text = message.text?.let { tinkAesGcmHelper?.decryptText(it) }
                    Log.d("testSocketsMessage", "New Message: $message")
                    val newMessageTriple =
                        if(lastMessageDate == "") Triple(message,"", formatMessageTime(message.timestamp))
                        else Triple(message,formatMessageDate(message.timestamp), formatMessageTime(message.timestamp))
                    _newMessageFlow.tryEmit(newMessageTriple)
                    updateLastDate(message.timestamp)
                } else pendingRefresh = true
            }
        }
        viewModelScope.launch {
            webSocketService.editMessageFlow.collect { message ->
                if(!disableRefresh) {
                    message.text = message.text?.let { tinkAesGcmHelper?.decryptText(it) }
                    Log.d("testSocketsMessage", "Edited Message: $message")
                    _editMessageFlow.value = message
                } else pendingRefresh = true
            }
        }
        viewModelScope.launch {
            webSocketService.deleteMessageFlow.collect {
                if(!disableRefresh) {
                    Log.d("testSocketsMessage", "Deleted messages ids: ${it.deletedMessagesIds}")
                    val adapter = recyclerView.adapter
                    if(adapter is MessageAdapter) adapter.clearPositions()
                    refresh()
                    recyclerView.adapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                            recyclerView.scrollToPosition(0)
                        }
                    })
                } else pendingRefresh = true
            }
        }
        viewModelScope.launch {
            webSocketService.readMessageFlow.collect {
                Log.d("testSocketsMessage", "Messages read: ${it.messagesReadIds}")
                _readMessagesFlow.tryEmit(it.messagesReadIds)
            }
        }
        viewModelScope.launch {
            webSocketService.deleteAllMessageFlow.collect {
                Log.d("testSocketsMessage", "All messages deleted")
                _deleteState.value = 1
                refresh()
            }
        }
        viewModelScope.launch {
            webSocketService.dialogDeletedFlow.collect {
                Log.d("testSocketsMessage", "Dialog deleted")
                _deleteState.value = 2
            }
        }
        viewModelScope.launch {
            webSocketService.userSessionFlow.collect {
                if(it.userId == otherUserId && !isOtherUserInChat) {
                    Log.d("testSocketsMessage", "Session updated")
                    val sessionString = formatUserSessionDate(it.lastSession)
                    _lastSessionString.postValue(sessionString)
                }
            }
        }
        viewModelScope.launch {
            webSocketService.typingFlow.collect { (userId, isStart) ->
                Log.d("testSocketsMessage", "User#$userId is typing: $isStart")
                _typingState.value = Pair(isStart, null)
            }
        }
        viewModelScope.launch {
            webSocketService.joinLeaveDialogFlow.collect { (dialogId, userId, isJoin) ->
                isOtherUserInChat = isJoin
                if(isJoin) {
                    Log.d("testSocketsMessage", "User $userId joined Dialog $dialogId")
                    _lastSessionString.postValue("в этом чате")
                } else {
                    Log.d("testSocketsMessage", "User $userId left Dialog $dialogId")
                    _lastSessionString.postValue("был в сети только что")
                }
            }
        }
    }

    fun setInfo(otherUserId: Int) {
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
        val scrollListener = object : RecyclerView.OnScrollListener() {
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

                    if (firstVisibleItemPosition == 0) {
                        recyclerView.removeOnScrollListener(this)
                    }
                }
            }
        }
        recyclerView.addOnScrollListener(scrollListener)
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

    override fun onCleared() {
        leaveDialog()
        super.onCleared()
    }
}