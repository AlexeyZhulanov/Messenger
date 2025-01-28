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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
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
    @IoDispatcher ioDispatcher: CoroutineDispatcher
) : BaseChatViewModel(messengerService, retrofitService, fileManager, webSocketService, ioDispatcher) {

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
        webSocketService.connect()
        viewModelScope.launch {
            webSocketService.newMessageFlow.collect { message ->
                Log.d("testSocketsMessage", "New Message: $message")
                val newMessagePair = if(lastMessageDate == "") message to "" else message to formatMessageDate(message.timestamp)
                _newMessageFlow.value = newMessagePair
            }
        }
        viewModelScope.launch {
            webSocketService.editMessageFlow.collect { message ->
                Log.d("testSocketsMessage", "Edited Message: $message")
                _editMessageFlow.value = message
                recyclerView.adapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                        recyclerView.scrollToPosition(0)
                    }
                })
            }
        }
        viewModelScope.launch {
            webSocketService.deleteMessageFlow.collect {
                Log.d("testSocketsMessage", "Deleted messages ids: ${it.deletedMessagesIds}")
                val adapter = recyclerView.adapter
                if(adapter is MessageAdapter) adapter.clearPositions()
                refresh()
                recyclerView.adapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                        recyclerView.scrollToPosition(0)
                    }
                })
            }
        }
        viewModelScope.launch {
            webSocketService.readMessageFlow.collect {
                Log.d("testSocketsMessage", "Messages read: ${it.messagesReadIds}")
                _readMessagesFlow.value = it.messagesReadIds
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
                _typingState.value = isStart
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
            if(temp != null) messengerService.updateLastReadMessage(id, convId, null)
            else messengerService.saveLastReadMessage(id, convId, null)
        }
    }

    suspend fun getLastMessageId(): Int {
        return messengerService.getLastReadMessage(convId) ?: -1
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

    override fun onCleared() {
        leaveDialog()
        super.onCleared()
    }
}