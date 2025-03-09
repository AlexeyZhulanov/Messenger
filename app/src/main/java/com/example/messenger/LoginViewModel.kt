package com.example.messenger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.appsettings.AppSettings
import com.example.messenger.security.ChatKeyManager
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

    fun login(name: String, password: String, remember: Boolean, onSuccess: (Triple<Boolean, Boolean, Int>) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            supervisorScope {
            try {
                val result = retrofitService.login(name, password)
                if (result) {
                    appSettings.setRemember(remember)
                    val user = retrofitService.getUser(0) // currentUser
                    if(ChatKeyManager.containsPrivateKey(user.id)) {
                        if(user.publicKey.isNullOrEmpty()) {
                            onSuccess(Triple(false, true, user.id))
                        } else {
                            onSuccess(Triple(true, true, -1))
                        }
                    } else {
                        if(user.publicKey.isNullOrEmpty()) onSuccess(Triple(false, true, user.id))
                        else onSuccess(Triple(false, false, user.id))
                    }
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