package com.example.messenger.model

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput

class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence("reply_text")?.toString()
        val chatId = intent.getIntExtra("chat_id", -1)

        if (!replyText.isNullOrEmpty() && chatId != -1) {
            sendReplyMessage(context, chatId, replyText)
        }
    }

    private fun sendReplyMessage(context: Context, chatId: Int, message: String) {
        // TODO: Отправка сообщения через WebSocket или API
        Log.d("testReplyReceiver", "Ответ в чат $chatId: $message")

        // Удаляем уведомление после ответа
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(chatId * 100)
    }
}
