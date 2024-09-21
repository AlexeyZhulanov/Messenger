package com.example.messenger

import androidx.lifecycle.ViewModel
import com.example.messenger.model.FileManager
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DialogInfoViewModel @Inject constructor(
    private val retrofitService: RetrofitService,
    private val messengerService: MessengerService,
    private val fileManager: FileManager
) : ViewModel() {

}