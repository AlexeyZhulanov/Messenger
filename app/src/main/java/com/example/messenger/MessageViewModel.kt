package com.example.messenger

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import com.example.messenger.model.ConversationSettings
import com.example.messenger.model.FileManager
import com.example.messenger.model.Message
import com.example.messenger.model.MessagePagingSource
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MessageViewModel @Inject constructor(
    private val messengerService: MessengerService,
    private val retrofitService: RetrofitService,
    private val fileManager: FileManager
) : ViewModel() {

    private val _lastSessionString = MutableLiveData<String>()
    val lastSessionString: LiveData<String> get() = _lastSessionString

    private var updateJob: Job? = null

    private var dialogId: Int = -1
    private var otherUserId: Int = -1
    private var isFirst = true

    private var currentQuery: String? = null

    private var _refreshTrigger = MutableStateFlow(Unit)
    private val refreshTrigger: StateFlow<Unit> = _refreshTrigger.asStateFlow()

    val mes = Pager(PagingConfig(pageSize = 30, initialLoadSize = 30)) {
        MessagePagingSource(retrofitService, messengerService, dialogId, currentQuery, isFirst)
    }.flow
        .cachedIn(viewModelScope)
        .combine(refreshTrigger) { pagingData, _ -> pagingData } // PagingSource&refreshTrigger

    fun doTrigger() {
        _refreshTrigger.value = Unit
        isFirst = false
    }

    init {
        viewModelScope.launch {
            while (true) {
                delay(30000) // Каждые 30 секунд
                _refreshTrigger.value = Unit // Триггер обновления
                isFirst = false
            }
        }
    }

    fun setDialogInfo(dialogId: Int, otherUserId: Int) {
        this.dialogId = dialogId
        this.otherUserId = otherUserId
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
        currentQuery = query
        _refreshTrigger.value = Unit
        currentQuery = null //todo возможно нужно добавить delay
    }

    suspend fun replaceMessages(messages: List<Message>) = withContext(Dispatchers.IO) {
        messengerService.replaceMessages(dialogId, messages.takeLast(30), fileManager)
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

    fun stopMessagePolling() {
        updateJob?.cancel()
        updateJob = null
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
}