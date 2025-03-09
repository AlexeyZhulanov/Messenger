package com.example.messenger.model

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.messenger.model.appsettings.AppSettings
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var retrofitService: RetrofitService

    @Inject
    lateinit var appSettings: AppSettings

    private val firebaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("testFCM", "New token: $token")
        if(appSettings.getRemember()) sendTokenToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("testFCM", "Message received: ${remoteMessage.data}")

        remoteMessage.data["type"]?.let { type ->
            when (type) {
                "wakeup" -> startWebSocketService()
            }
        }
    }

    private fun startWebSocketService() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WebSocket:FCM")
        wakeLock.acquire(5000L)

        val serviceIntent = Intent(this, WebSocketService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun sendTokenToServer(token: String) {
        firebaseScope.launch {
            try {
                retrofitService.saveFCMToken(token)
            } catch (e: Exception) {
                Log.d("testFCM", "Не удалось отправить токен на сервер: ${e.message}")
            }
        }
    }
}
