package com.example.messenger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.appsettings.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val retrofitService: RetrofitService,
    private val appSettings: AppSettings
) : ViewModel() {

    fun login(name: String, password: String, remember: Boolean, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            supervisorScope {
            try {
                val result = async { retrofitService.login(name, password) }
                if (result.await()) {
                    appSettings.setRemember(remember)
                    onSuccess()
                } else {
                    onError("Ошибка: Неверный логин или пароль")
                }
            } catch (e: Exception) {
                onError("Ошибка: Неверный логин или пароль")
            }
            }
        }
    }
}