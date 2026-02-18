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
import com.example.messenger.states.MessageUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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


    init {
        webSocketService.reconnectIfNeeded()
        viewModelScope.launch {
            initFlow.collect {
                pagingSource = MessagePagingSource(retrofitService, messengerService, convId, fileManager, tinkAesGcmHelper, true)
            }
        }

        // Пагинация
        @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
        searchBy.asFlow()
            .debounce(500)
            .flatMapLatest { searchQuery ->
                currentPage.map { page -> searchQuery to page }
            }.onEach { (searchQuery, page) ->
                val pageSize = 30
                val triples = pagingSource?.loadPage(page, pageSize, searchQuery)
                if(triples != null) {
                    val newUi = triples.map { triple -> toMessageUi(triple) }
                    val endData = if(page == 0) {
                        val firstMessage = newUi.firstOrNull()?.message
                        if(firstMessage != null) updateLastDate(firstMessage.timestamp)
                        val m = getUnsentMessages()
                        if(m != null) {
                            val mUi = m.map { toMessageUi(Triple(it, "", "")) }
                            newUi + mUi
                        } else newUi
                    } else {
                        val processed = processDateDuplicates(_messagesUi.value + newUi)
                        processed
                    }
                    _messagesUi.value = endData
                    preloadAttachments(endData)
                }
            }.launchIn(viewModelScope)

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

    override fun applyGroupDisplayInfo(list: List<MessageUi>): List<MessageUi> = list

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

    override fun preloadAttachments(list: List<MessageUi>) {
        list.forEach { ui ->
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
        leaveDialog()
        super.onCleared()
    }
}