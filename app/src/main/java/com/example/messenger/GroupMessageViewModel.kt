package com.example.messenger

import android.util.Log
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.di.IoDispatcher
import com.example.messenger.model.FileManager
import com.example.messenger.model.Message
import com.example.messenger.model.MessagePagingSource
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.User
import com.example.messenger.model.WebSocketService
import com.example.messenger.model.appsettings.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class GroupMessageViewModel @Inject constructor(
    messengerService: MessengerService,
    retrofitService: RetrofitService,
    fileManager: FileManager,
    webSocketService: WebSocketService,
    appSettings: AppSettings,
    @IoDispatcher ioDispatcher: CoroutineDispatcher
) : BaseChatViewModel(messengerService, retrofitService, fileManager, webSocketService, appSettings, ioDispatcher)
    {
        var currentMemberList: List<User> = emptyList()

        private val _membersCount = MutableStateFlow(0)
        val membersCount: StateFlow<Int> get() = _membersCount

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
                    pagingSource = MessagePagingSource(retrofitService, messengerService, convId, fileManager, tinkAesGcmHelper, false)
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
                webSocketService.typingFlow.collect { (userId, isStart) ->
                    Log.d("testSocketsMessage", "User#$userId is typing: $isStart")
                    val username = currentMemberList.find { it.id == userId }?.username
                    _typingState.value = Pair(isStart, username)
                }
            }
        }

        // Появляется ANR на 10-15 мс, но там так или иначе нельзя фрагментом пользоваться, поэтому не критично
        fun fetchMembersList(): List<User> {
            return runBlocking {
                val list = messengerService.getGroupMembers(convId)
                currentMemberList = list
                list
            }
        }

        fun fetchMembersList2() {
            viewModelScope.launch {
                val size = currentMemberList.size
                _membersCount.value = size
                try {
                    val actualList = retrofitService.getGroupMembers(convId)
                    if(actualList != currentMemberList) {
                        if(size != actualList.size) _membersCount.value = actualList.size
                        currentMemberList = actualList
                        Log.d("testActualMembers", actualList.toString())
                        messengerService.replaceGroupMembers(convId, actualList)
                    }
                } catch (e: Exception) { return@launch }
            }
        }

        fun sendTypingEvent(flag: Boolean) {
            val typingData = JSONObject()
            typingData.put("group_id", convId)
            if(flag) {
                webSocketService.send("typing_group", typingData)
            } else {
                webSocketService.send("stop_typing_group", typingData)
            }
        }

        fun setMarkScrollListener(recyclerView: RecyclerView, adapter: MessageAdapter, currentUserId: Int) {
            val scrollListener = object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    // Проверяем видимые элементы от последнего к первому
                    if (lastVisibleItemPosition != RecyclerView.NO_POSITION && firstVisibleItemPosition != RecyclerView.NO_POSITION) {
                        val visibleMessages = (lastVisibleItemPosition downTo firstVisibleItemPosition).mapNotNull { position ->
                            adapter.getItemNotProtected(position).first.takeIf { it.idSender != currentUserId && it.isPersonalUnread == true }
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

        fun separateMessages(messages: List<Triple<Message, String, String>>, currentUserId: Int): Map<Int, Pair<String?, String?>?> {
            // Optimizing access to users using the Map
            val userMap = currentMemberList.associate { it.id to (it.username to it.avatar) }
            val groupedMessages = mutableListOf<List<Message>>()
            val tempList = mutableListOf<Message>()

            for ((message, date) in messages.reversed()) {
                if(tempList.isNotEmpty()) {
                    if ((tempList.last().idSender != message.idSender) || (date != "")) {
                        groupedMessages.add(ArrayList(tempList))
                        tempList.clear()
                    }
                }
                if (message.idSender != currentUserId) tempList.add(message)
            }
            if (tempList.isNotEmpty()) {
                groupedMessages.add(tempList)
            }
            // Filling in the final Map with username and avatar
            val messageDisplayMap = mutableMapOf<Int, Pair<String?, String?>?>()

            for (mes in groupedMessages) {
                val first = mes.last() // reversed
                val last = mes.first() // reversed
                val userInfo = userMap[first.idSender]

                // If there is one element in the group, both fields are used.
                if (first == last) {
                    messageDisplayMap[first.id] = userInfo
                } else {
                    // The first element gets a username (the adapter is reversed)
                    messageDisplayMap[last.id] = userInfo?.first to null
                    // The last element gets an avatar (adapter is reversed)
                    val avatar = userInfo?.second ?: "" // If user without avatar
                    messageDisplayMap[first.id] = null to avatar
                }
            }
            return messageDisplayMap
        }

        fun joinGroup() {
            val joinData = JSONObject()
            joinData.put("group_id", convId)
            webSocketService.send("join_group", joinData)
        }

        private fun leaveGroup() {
            val leaveData = JSONObject()
            leaveData.put("group_id", convId)
            webSocketService.send("leave_group", leaveData)
        }

        override fun onCleared() {
            leaveGroup()
            super.onCleared()
        }
}