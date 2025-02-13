package com.example.messenger.model

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat

class MarkAsReadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getIntExtra("chat_id", -1)
        if (chatId != -1) {
            // TODO: Отправить на сервер, что сообщения прочитаны
            Log.d("MarkAsReadReceiver", "Чат $chatId помечен как прочитанный")

            // Удаляем уведомление
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(chatId * 10)
        }
    }
}
