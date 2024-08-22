package com.example.messenger

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messenger.model.FileManager
import com.example.messenger.model.Message
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class MessageViewModel(
    private val dialogId: Int,
    private val otherUserId: Int,
    private val messengerService: MessengerService,
    private val retrofitService: RetrofitService,
    private val fileManager: FileManager
) : ViewModel() {

    private val _messages = MutableLiveData<Map<Message, String>>()
    val messages: LiveData<Map<Message, String>> get() = _messages

    private val _countMsg = MutableLiveData<Int>()
    val countMsg: LiveData<Int> get() = _countMsg

    private val _lastSessionString = MutableLiveData<String>()
    val lastSessionString: LiveData<String> get() = _lastSessionString

    private var updateJob: Job? = null

    init {
        loadMessages()
        fetchLastSession()
    }

    private fun fetchLastSession() {
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

    fun loadMessages() {
        updateJob = viewModelScope.launch {
            var mes = messengerService.getMessages(dialogId).associateWith { "" }
            _messages.value = mes
            while (isActive) {
                val temp = withContext(Dispatchers.IO) {
                    try {
                        retrofitService.getMessages(dialogId, 0, _countMsg.value ?: 0).associateWith { "" }
                    } catch (e: Exception) {
                        null
                    }
                }
                temp?.let {
                    mes = it
                    _messages.value = mes
                    messengerService.replaceMessages(dialogId, mes.keys.toList().takeLast(30), fileManager)
                }
                delay(30000)
            }
        }
    }

    fun stopMessagePolling() {
        updateJob?.cancel()
        updateJob = null
    }

    // Остальная логика, связанная с отправкой сообщений, аудиозаписью, выбором файлов и т.д.
}