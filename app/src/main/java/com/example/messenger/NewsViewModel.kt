package com.example.messenger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.example.messenger.di.IoDispatcher
import com.example.messenger.model.FileManager
import com.example.messenger.model.MessengerService
import com.example.messenger.model.NewsPagingSource
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.WebSocketService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val messengerService: MessengerService,
    private val retrofitService: RetrofitService,
    private val fileManager: FileManager,
    private val webSocketService: WebSocketService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private var isFirst = true


    val pagingFlow = Pager(PagingConfig(pageSize = 10, initialLoadSize = 10, prefetchDistance = 3)) {
        NewsPagingSource(retrofitService, messengerService, isFirst)
    }.flow.cachedIn(viewModelScope)


    fun refresh() {
        isFirst = false
    }

    suspend fun getPermission() : Int {
        return try {
            retrofitService.getPermission()
        } catch (e: Exception) {
            0
        }
    }
}