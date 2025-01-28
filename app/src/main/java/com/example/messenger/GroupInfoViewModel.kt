package com.example.messenger

import com.example.messenger.di.IoDispatcher
import com.example.messenger.model.FileManager
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.WebSocketService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

@HiltViewModel
class GroupInfoViewModel @Inject constructor(
    messengerService: MessengerService,
    retrofitService: RetrofitService,
    fileManager: FileManager,
    webSocketService: WebSocketService,
    @IoDispatcher ioDispatcher: CoroutineDispatcher
) : BaseInfoViewModel(messengerService, retrofitService, fileManager, webSocketService, ioDispatcher) {

    override fun setConvInfo(convId: Int) {
        this.convId = convId
        this.isGroup = 1
    }

    suspend fun addMember(name: String) {
        //retrofitService.addUserToGroup(convId, )
    }
}