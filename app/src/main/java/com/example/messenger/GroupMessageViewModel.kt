package com.example.messenger

import android.util.Log
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.example.messenger.di.IoDispatcher
import com.example.messenger.model.FileManager
import com.example.messenger.model.MessagePagingSource
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.User
import com.example.messenger.model.WebSocketService
import com.example.messenger.model.appsettings.AppSettings
import com.example.messenger.states.AvatarState
import com.example.messenger.states.MessageUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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


        init {
            webSocketService.reconnectIfNeeded()
            viewModelScope.launch {
                initFlow.collect {
                    pagingSource = MessagePagingSource(retrofitService, messengerService, convId, fileManager, tinkAesGcmHelper, false)
                }
            }

            // Пагинация
            @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
            searchBy.asFlow()
                .debounce(500)
                .flatMapLatest { searchQuery ->
                    currentPage.map { page -> searchQuery to page }
                }.onEach { (searchQuery, page) ->
                    if(isLoadingPage) return@onEach

                    isLoadingPage = true
                    try {
                        if(page == 0) pagingSource?.resetCursor()
                        val pageSize = 30
                        val triples = pagingSource?.loadPage(pageSize, searchQuery)
                        if(triples != null) {
                            val flag = page == 0
                            val newUi = triples.map { triple -> toMessageUi(triple, flag) }
                            val endData = if(page == 0) {
                                val firstMessage = newUi.firstOrNull()?.message
                                if(firstMessage != null) updateLastDate(firstMessage.timestamp)
                                val m = getUnsentMessages()
                                if(m != null) {
                                    val mUi = m.map { message ->
                                        toMessageUi(Triple(message, "", ""), false)
                                    }
                                    newUi + mUi
                                } else newUi
                            } else {
                                val processed = processDateDuplicates(_messagesUi.value + newUi)
                                processed
                            }
                            val finalList = applyGroupDisplayInfo(endData)
                            _messagesUi.value = finalList
                            preloadAttachments(finalList)
                        }
                    } finally {
                        isLoadingPage = false
                    }
                }.launchIn(viewModelScope)

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
                } catch (_: Exception) { return@launch }
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

        override fun applyGroupDisplayInfo(list: List<MessageUi>): List<MessageUi> {
            if (list.isEmpty()) return list

            val userMap = currentMemberList.associateBy { it.id }

            return list.mapIndexed { index, ui ->
                val message = ui.message

                if (message.idSender == currentUserId) {
                    ui.copy(
                        username = null,
                        showUsername = false,
                        showAvatar = false,
                        avatarState = null
                    )
                } else {
                    val prev = list.getOrNull(index - 1)
                    val next = list.getOrNull(index + 1)

                    val showUsername = prev == null ||
                                prev.message.idSender != message.idSender ||
                                ui.formattedDate.isNotEmpty()

                    val showAvatar = next == null ||
                                next.message.idSender != message.idSender ||
                                next.formattedDate.isNotEmpty()

                    val user = userMap[message.idSender]

                    ui.copy(
                        username = if (showUsername) user?.username else null,
                        showUsername = showUsername,
                        showAvatar = showAvatar,
                        avatarState =
                            if (showAvatar && !user?.avatar.isNullOrBlank())
                                AvatarState.Loading
                            else null
                    )
                }
            }
        }
            // todo можно убрать, если верхняя функция будет без ошибок работать
//        fun buildGroupDisplayInfo(messages: List<Triple<Message, String, String>>, currentUserId: Int): Map<Int, GroupDisplayInfo> {
//
//            val userMap = currentMemberList.associate { it.id to (it.username to it.avatar) }
//            val groupedMessages = mutableListOf<List<Message>>()
//            val tempList = mutableListOf<Message>()
//
//            for ((message, date) in messages.reversed()) {
//                if (tempList.isNotEmpty()) {
//                    if ((tempList.last().idSender != message.idSender) || (date != "")) {
//                        groupedMessages.add(ArrayList(tempList))
//                        tempList.clear()
//                    }
//                }
//                if (message.idSender != currentUserId) tempList.add(message)
//            }
//
//            if (tempList.isNotEmpty()) groupedMessages.add(tempList)
//
//            val result = mutableMapOf<Int, GroupDisplayInfo>()
//
//            for (group in groupedMessages) {
//                val first = group.last()
//                val last = group.first()
//                val userInfo = userMap[first.idSender]
//
//                if (first == last) {
//                    result[first.id] =
//                        GroupDisplayInfo(
//                            username = userInfo?.first, avatar = userInfo?.second,
//                            showUsername = true, showAvatar = true
//                        )
//                } else {
//                    result[last.id] =
//                        GroupDisplayInfo(
//                            username = userInfo?.first, avatar = null,
//                            showUsername = true, showAvatar = false
//                        )
//                    result[first.id] =
//                        GroupDisplayInfo(
//                            username = null, avatar = userInfo?.second,
//                            showUsername = false, showAvatar = true
//                        )
//                }
//            }
//            return result
//        }

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

        override fun preloadAttachments(list: List<MessageUi>) {
            val userMap = currentMemberList.associateBy { it.id }

            list.forEach { ui ->
                ui.avatarState?.let {
                    val user = userMap[ui.message.idSender]
                    val avatarString = user?.avatar
                    if (!avatarString.isNullOrBlank()) {
                        preloadAvatar(ui.message.id, avatarString)
                    }
                }
                when {
                    ui.voiceState != null -> preloadVoice(ui)
                    ui.imageState != null -> preloadImage(ui)
                    ui.replyState != null -> preloadReply(ui)
                    ui.imagesState != null -> preloadImages(ui)
                    ui.fileState != null -> preloadFile(ui)
                }
            }
        }

        override fun onCleared() {
            leaveGroup()
            super.onCleared()
        }
}