package com.example.messenger

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.Settings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val retrofitService: RetrofitService,
    private val messengerService: MessengerService
) : ViewModel() {

    fun login(name: String, password: String, remember: Boolean, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = async(Dispatchers.IO) { retrofitService.login(name, password) }
            if (result.await()) {
                val settings = Settings(0, if (remember) 1 else 0, name, password)
                Log.d("LoginViewModel", "Settings updated")
                messengerService.updateSettings(settings)
                onSuccess()
            } else {
                onError("Ошибка: Неверный логин или пароль")
            }
        }
    }
}