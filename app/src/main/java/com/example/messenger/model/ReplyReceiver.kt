package com.example.messenger.model

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.messenger.MainActivity
import com.example.messenger.R
import com.example.messenger.di.MessengerModule
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ReplyReceiver : BroadcastReceiver() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val CHANNEL_ID = "chat_notifications"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence("reply_text")?.toString()
        val chatId = intent.getIntExtra("chat_id", -1)
        val messageId = intent.getIntExtra("message_id", -1)
        val isGroup = intent.getBooleanExtra("is_group", false)
        val senderName = intent.getStringExtra("sender_name") ?: "User"

        if (!replyText.isNullOrEmpty() && chatId != -1 && messageId != -1) {
            val entryPoint = EntryPointAccessors.fromApplication(context, MessengerModule.ReplyReceiverEntryPoint::class.java)
            val retrofitService = entryPoint.retrofitService()
            serviceScope.launch {
                try {
                    if (isGroup) {
                        retrofitService.sendGroupMessage(chatId, replyText, null, null,
                            null, messageId, false, senderName)
                    } else {
                        retrofitService.sendMessage(chatId, replyText, null, null,
                            null, messageId, false, senderName)
                    }
                    withContext(Dispatchers.Main) {
                        updateNotification(context, chatId, success = true)
                    }
                } catch (e: Exception) {
                    Log.d("testReplyReceiver", "Error sending reply: ${e.message}")
                    withContext(Dispatchers.Main) {
                        updateNotification(context, chatId, success = false)
                    }
                }
            }
        }
    }

    /**
     * Обновляет уведомление для чата с chatId.
     * Если success = true – уведомление показывает галочку, иначе – крестик.
     */
    @SuppressLint("LaunchActivityFromNotification")
    private fun updateNotification(context: Context, chatId: Int, success: Boolean) {
        val emptyIntent = PendingIntent.getBroadcast(
            context, 0, Intent(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Выбираем иконку в зависимости от статуса
        val smallIcon = if (success) R.drawable.ic_check else R.drawable.ic_clear

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle("Ответ отправлен")
            .setContentText(
                if (success)
                    "Ваш ответ успешно отправлен"
                else
                    "Ошибка при отправке ответа"
            )
            .setAutoCancel(true)
            .setContentIntent(emptyIntent)

        val notificationManager = NotificationManagerCompat.from(context)
        // Обновляем уведомление с тем же id (chatId * 100)
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationManager.notify(chatId * 100, builder.build())
    }
}
