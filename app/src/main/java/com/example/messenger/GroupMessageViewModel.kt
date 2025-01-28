package com.example.messenger

import androidx.lifecycle.viewModelScope
import com.example.messenger.di.IoDispatcher
import com.example.messenger.model.FileManager
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.User
import com.example.messenger.model.WebSocketService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupMessageViewModel @Inject constructor(
    messengerService: MessengerService,
    retrofitService: RetrofitService,
    fileManager: FileManager,
    webSocketService: WebSocketService,
    @IoDispatcher ioDispatcher: CoroutineDispatcher
) : BaseChatViewModel(messengerService, retrofitService, fileManager, webSocketService, ioDispatcher)
    {
        var currentMemberList: List<User> = emptyList()

        private val _membersCount = MutableStateFlow(0)
        val membersCount: StateFlow<Int> get() = _membersCount

        override fun setConvInfo(convId: Int, otherUserId: Int, isGroup: Int) {
            super.setConvInfo(convId, otherUserId, isGroup)
        }

        fun fetchMembersList() {
            viewModelScope.launch {
                currentMemberList = messengerService.getGroupMembers(convId)
                val size = currentMemberList.size
                _membersCount.value = size
                try {
                    val actualList = retrofitService.getGroupMembers(convId)
                    if(actualList != currentMemberList) {
                        if(size != actualList.size) _membersCount.value = actualList.size
                        currentMemberList = actualList
                        messengerService.replaceGroupMembers(convId, actualList)
                    }
                } catch (e: Exception) { return@launch }
            }
        }

}