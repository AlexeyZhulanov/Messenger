package com.example.messenger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messenger.model.RetrofitService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val retrofitService: RetrofitService
) : ViewModel() {

    fun register(name: String, username: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = async { retrofitService.register(name, username, password) }
            if (result.await()) {
                onSuccess()
            } else {
                onError("Ошибка: Имя пользователя уже занято")
            }
        }
    }
}