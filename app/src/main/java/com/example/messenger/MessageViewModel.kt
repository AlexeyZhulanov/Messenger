package com.example.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class MessageViewModel @Inject constructor(
    private val messengerService: MessengerService,
    private val retrofitService: RetrofitService,
    private val fileManager: FileManager,
    private val webSocketService: WebSocketService
) : ViewModel(), WebSocketListenerInterface {

    private val _lastSessionString = MutableLiveData<String>()
    val lastSessionString: LiveData<String> get() = _lastSessionString

    private var dialogId: Int = -1
    private var otherUserId: Int = -1
    private var isFirst = true
    private var disableRefresh: Boolean = false
    @SuppressLint("StaticFieldLeak")
    private lateinit var recyclerView: RecyclerView

    private val searchBy = MutableLiveData("")

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val mes = searchBy.asFlow()
        .debounce(500)
        .flatMapLatest { Pager(PagingConfig(pageSize = 30, initialLoadSize = 30, prefetchDistance = 5)) {
        MessagePagingSource(retrofitService, messengerService, dialogId, it, isFirst, fileManager)
        }.flow }
        .cachedIn(viewModelScope)

    fun refresh() {
        isFirst = false
        this.searchBy.postValue(this.searchBy.value)
    }

    fun stopRefresh() {
        disableRefresh = true
    }

    fun startRefresh() {
        disableRefresh = false
    }

    init {
        webSocketService.connect()
        webSocketService.setListener(this)
        viewModelScope.launch {
            while (true) {
                delay(30000)
                if(!disableRefresh) refresh()
            }
        }
    }

    fun setDialogInfo(dialogId: Int, otherUserId: Int) {
        this.dialogId = dialogId
        this.otherUserId = otherUserId
        joinDialog()
    }
    fun setRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
    }

    private fun highlightItem(position: Int) {
        val adapterWithLoadStates = recyclerView.adapter
        if (adapterWithLoadStates is ConcatAdapter) {
            // Ищем оригинальный MessageAdapter внутри ConcatAdapter(без load states)
            adapterWithLoadStates.adapters.forEach { adapter ->
                if (adapter is MessageAdapter) {
                    adapter.highlightPosition(position)
                }
            }
        } else {
            Log.e("highlightItem", "Adapter is not of type ConcatAdapter")
        }
    }

    fun smartScrollToPosition(targetPosition: Int) {
        recyclerView.clearOnScrollListeners()
        val currentPos = (recyclerView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()

        if (currentPos >= targetPosition) {
            // Целевая позиция уже на экране
            recyclerView.smoothScrollToPosition(targetPosition)
            highlightItem(targetPosition)
            return
        }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val lastVisiblePosition = (recyclerView.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()

                if (lastVisiblePosition >= targetPosition) {
                    // Достигли целевой позиции, остановим скролл
                    recyclerView.removeOnScrollListener(this)
                    highlightItem(targetPosition)
                } else {
                    // Если не достигли целевой позиции, продолжаем скролл
                    recyclerView.smoothScrollToPosition(lastVisiblePosition + 1)
                }
            }
        })

        // Начинаем скролл
        if(abs(targetPosition - currentPos) > 10)
            recyclerView.smoothScrollToPosition(currentPos + 10)
        else {
            recyclerView.smoothScrollToPosition(targetPosition)
            highlightItem(targetPosition)
        }
    }

    fun fetchLastSession() {
        viewModelScope.launch {
            try {
                val session = withContext(Dispatchers.IO) {
                    retrofitService.getLastSession(otherUserId)
                }
                _lastSessionString.value = formatUserSessionDate(session)
            } catch (e: Exception) {
                _lastSessionString.value = "Unknown"
            }
        }
    }

    fun searchMessagesInDialog(query: String) {
        if(this.searchBy.value == query) return
        this.searchBy.value = query
    }

    suspend fun getDialogSettings(idDialog: Int): ConversationSettings = withContext(Dispatchers.IO) {
        return@withContext retrofitService.getDialogSettings(idDialog)
    }

    suspend fun deleteMessages(ids: List<Int>): Boolean = withContext(Dispatchers.IO) {
        return@withContext retrofitService.deleteMessages(ids)
    }

    suspend fun deleteFile(folder: String, filename: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext retrofitService.deleteFile(folder, filename)
    }

    suspend fun uploadPhoto(photo: File): String = withContext(Dispatchers.IO) {
        return@withContext retrofitService.uploadPhoto(photo)
    }

    suspend fun uploadAudio(audio: File): String = withContext(Dispatchers.IO) {
        return@withContext retrofitService.uploadAudio(audio)
    }

    suspend fun uploadFile(file: File): String = withContext(Dispatchers.IO) {
        return@withContext retrofitService.uploadFile(file)
    }

    suspend fun sendMessage(idDialog: Int, text: String?, images: List<String>?,
                            voice: String?, file: String?, referenceToMessageId: Int?, isForwarded: Boolean,
                            usernameAuthorOriginal: String?) = withContext(Dispatchers.IO) {
     retrofitService.sendMessage(idDialog, text, images, voice, file, referenceToMessageId, isForwarded, usernameAuthorOriginal)
    }

    suspend fun editMessage(messageId: Int, text: String?, images: List<String>?,
                            voice: String?, file: String?) = withContext(Dispatchers.IO) {
        retrofitService.editMessage(messageId, text, images, voice, file)
    }

    suspend fun downloadFile(context: Context, folder: String, filename: String): String = withContext(Dispatchers.IO) {
        return@withContext retrofitService.downloadFile(context, folder, filename)
    }

    suspend fun fManagerIsExist(fileName: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext fileManager.isExist(fileName)
    }

    suspend fun fManagerGetFilePath(fileName: String): String = withContext(Dispatchers.IO) {
        return@withContext fileManager.getFilePath(fileName)
    }

    suspend fun fManagerSaveFile(fileName: String, fileData: ByteArray) = withContext(Dispatchers.IO) {
        fileManager.saveFile(fileName, fileData)
    }

    suspend fun findMessage(idMessage: Int): Pair<Message, Int> = withContext(Dispatchers.IO) {
        return@withContext retrofitService.findMessage(idMessage)
    }

    fun formatMessageTime(timestamp: Long?): String {
        if (timestamp == null) return "-"

        // Приведение серверного времени (МСК GMT+3) к GMT
        val greenwichMessageDate = Calendar.getInstance().apply {
            timeInMillis = timestamp - 10800000
        }
        val dateFormatToday = SimpleDateFormat("HH:mm", Locale.getDefault())
        return dateFormatToday.format(greenwichMessageDate.time)
    }

    private fun formatUserSessionDate(timestamp: Long?): String {
        if (timestamp == null) return "Никогда не был в сети"

        // Приведение серверного времени (МСК GMT+3) к GMT
        val greenwichSessionDate = Calendar.getInstance().apply {
            timeInMillis = timestamp - 10800000
        }
        val now = Calendar.getInstance()

        val diffInMillis = now.timeInMillis - greenwichSessionDate.timeInMillis
        val diffInMinutes = (diffInMillis / 60000).toInt()

        val dateFormatTime = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormatDayMonth = SimpleDateFormat("d MMM", Locale.getDefault())
        val dateFormatYear = SimpleDateFormat("d.MM.yyyy", Locale.getDefault())

        return when {
            diffInMinutes < 2 -> "в сети"
            diffInMinutes < 5 -> "был в сети только что"
            diffInMinutes < 60 -> "был в сети $diffInMinutes минут назад"
            diffInMinutes < 120 -> "был в сети час назад"
            diffInMinutes < 180 -> "был в сети два часа назад"
            diffInMinutes < 240 -> "был в сети три часа назад"
            diffInMinutes < 1440 -> "был в сети в ${dateFormatTime.format(greenwichSessionDate.time)}"
            else -> {
                // Проверка года
                val currentYear = now.get(Calendar.YEAR)
                val sessionYear = greenwichSessionDate.get(Calendar.YEAR)
                if (currentYear == sessionYear) {
                    "был в сети ${dateFormatDayMonth.format(greenwichSessionDate.time)}"
                } else {
                    "был в сети ${dateFormatYear.format(greenwichSessionDate.time)}"
                }
            }
        }
    }

    suspend fun imageSet(image: String, imageView: ImageView, context: Context) = withContext(Dispatchers.IO) {
        val filePathTemp = async(Dispatchers.IO) {
            if (fManagerIsExist(image)) {
                return@async fManagerGetFilePath(image)
            } else {
                try {
                    return@async downloadFile(context, "photos", image)
                } catch (e: Exception) {
                    return@async null
                }
            }
        }
        val first = filePathTemp.await()
        if (first != null) {
            val file = File(first)
            if (file.exists()) {
                withContext(Dispatchers.Main) {
                    val uri = Uri.fromFile(file)
                    imageView.visibility = View.VISIBLE
                    Glide.with(context)
                        .load(uri)
                        .centerCrop()
                        .placeholder(R.color.app_color_f6)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(imageView)
                }
            }
        }
    }

    private fun joinDialog() {
        val joinData = JSONObject()
        joinData.put("dialog_id", dialogId)
        Log.d("testJoinSocket", dialogId.toString())
        webSocketService.send("join_dialog", joinData)
    }

    private fun leaveDialog() {
        val leaveData = JSONObject()
        leaveData.put("dialog_id", dialogId)
        webSocketService.send("leave_dialog", leaveData)
    }

    override fun onNewMessage(message: Message) {
        Log.d("testSocketsMessage", "New Message: $message")
    }

    override fun onEditedMessage(message: Message) {
        Log.d("testSocketsMessage", "Edited Message: $message")
    }

    override fun onMessagesDeleted(deletedMessagesEvent: DeletedMessagesEvent) {
        Log.d("testSocketsMessage", "Deleted messages")
    }

    override fun onDialogCreated(dialogCreatedEvent: DialogCreatedEvent) {
        Log.d("testSocketsMessage", "Dialog created")
    }

    override fun onDialogDeleted(dialogDeletedEvent: DialogDeletedEvent) {
        Log.d("testSocketsMessage", "Dialog deleted")
    }

    override fun onUserSessionUpdated(userSessionUpdatedEvent: UserSessionUpdatedEvent) {
        Log.d("testSocketsMessage", "Session updated")
    }

    override fun onStartTyping(typingEvent: TypingEvent) {
        Log.d("testSocketsMessage", "Typing started")
    }

    override fun onStopTyping(typingEvent: TypingEvent) {
        Log.d("testSocketsMessage", "Typing stopped")
    }

    override fun onMessagesRead(readMessagesEvent: ReadMessagesEvent) {
        Log.d("testSocketsMessage", "Messages read")
    }

    override fun onAllMessagesDeleted(dialogMessagesAllDeleted: DialogMessagesAllDeleted) {
        Log.d("testSocketsMessage", "All messages deleted")
    }

    override fun onCleared() {
        super.onCleared()
        leaveDialog()
    }
}