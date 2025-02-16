package com.example.messenger.model

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.messenger.di.MessengerModule
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MarkAsReadReceiver : BroadcastReceiver() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getIntExtra("chat_id", -1)
        val messageId = intent.getIntExtra("message_id", -1)
        val isGroup = intent.getBooleanExtra("is_group", false)
        if (chatId != -1 && messageId != -1) {
            val entryPoint = EntryPointAccessors.fromApplication(context, MessengerModule.MarkAsReadReceiverEntryPoint::class.java)
            val retrofitService = entryPoint.retrofitService()
            serviceScope.launch {
                try {
                    if(isGroup) {
                        retrofitService.markGroupMessagesAsRead(chatId, listOf(messageId))
                    } else {
                        retrofitService.markMessagesAsRead(chatId, listOf(messageId))
                    }
                } catch (e: Exception) {
                    Log.d("testMarkAsReadReceiver", "Error reading message: ${e.message}")
                }
            }
            Log.d("testMarkAsReadReceiver", "Сообщение $messageId было успешно прочитано")
        }
        // Удаляем уведомление
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(chatId * 10)
    }
}
