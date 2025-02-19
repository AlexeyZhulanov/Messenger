package com.example.messenger

import android.app.Application
import com.example.messenger.model.WebSocketService
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {
    @Inject
    lateinit var webSocketService: WebSocketService

    override fun onCreate() {
        super.onCreate()
        webSocketService.connect()
    }
}