package com.example.messenger.model

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.messenger.MainActivity
import com.example.messenger.R
import com.example.messenger.security.ChatKeyManager
import com.example.messenger.security.TinkAesGcmHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class  WebSocketNotificationsService : LifecycleService() {

    @Inject lateinit var webSocketService: WebSocketService
    @Inject lateinit var fileManager: FileManager

    private val chatKeyManager = ChatKeyManager()

    companion object {
        private const val CHANNEL_ID = "chat_notifications"
        private const val GROUP_KEY_CHAT = "group_key_chat"
    }

    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private val chatNotifications = mutableMapOf<Int, MutableList<ChatMessageEvent>>()

    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        startForegroundService()
        createNotificationChannel()
        observeNotifications()
    }

    private fun observeNotifications() {
        lifecycleScope.launch {
            launch {
                webSocketService.notificationMessageFlow
                    .filter { webSocketService.isNotificationsEnabled(it.chatId, it.isGroup) }
                    .collect {
                        sendNotification(it)
                    }
            }
            launch {
                webSocketService.notificationNewsFlow.collect {
                    sendNewsNotification(it)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock.release()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WebSocketService::Wakelock")
        wakeLock.acquire(10_000L)
    }

    private fun startForegroundService() {
        val channelId = "websocket_channel"
        val channel = NotificationChannel(
            channelId, "WebSocket Service",
            NotificationManager.IMPORTANCE_MIN
        )
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("WebSocket работает")
            .setSmallIcon(R.drawable.ic_eye)
            .build()

        startForeground(1, notification)
    }

    private fun createNotificationChannel() {
        val channelId = "chat_notifications"
        val channel = NotificationChannel(
            channelId,
            "Сообщения",
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.setShowBadge(true) // Показывать значок уведомления
        channel.enableLights(true)
        channel.enableVibration(true)

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun sendNotification(event: ChatMessageEvent) {

        val typeStr = if(event.isGroup) "group" else "dialog"
        val aead = chatKeyManager.getAead(event.chatId, typeStr)
        val tinkAesGcmHelper = if(aead != null) TinkAesGcmHelper(aead) else return

        val chatId = event.chatId
        val sender = event.senderName
        val text = event.text?.let { tinkAesGcmHelper.decryptText(it) } ?: "[Вложение]"
        val messageId = event.messageId
        val isGroup = event.isGroup
        val senderName = event.senderName

        val messages = chatNotifications.getOrPut(chatId) { mutableListOf() }
        messages.add(event)

        val summaryId = chatId * 10
        val singleMessageId = chatId * 100

        val pendingIntent = PendingIntent.getActivity(
            this, chatId,
            Intent(this, MainActivity::class.java)
                .putExtra("chat_id", chatId)
                .putExtra("is_group", isGroup)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Кнопка "Ответить"
        val replyIntent = Intent(this, ReplyReceiver::class.java).apply {
            putExtra("chat_id", chatId)
            putExtra("message_id", messageId)
            putExtra("is_group", isGroup)
            putExtra("sender_name", senderName)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            this, chatId, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val remoteInput = RemoteInput.Builder("reply_text").setLabel("Ваш ответ...").build()
        val replyAction = NotificationCompat.Action.Builder(R.drawable.ic_answer, "Ответить", replyPendingIntent)
            .addRemoteInput(remoteInput)
            .build()

        // Кнопка "Прочитать"
        val readIntent = Intent(this, MarkAsReadReceiver::class.java).apply {
            putExtra("chat_id", chatId)
            putExtra("message_id", messageId)
            putExtra("is_group", isGroup)
        }
        val readPendingIntent = PendingIntent.getBroadcast(
            this, chatId, readIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val readAction = NotificationCompat.Action.Builder(R.drawable.ic_check, "Прочитать", readPendingIntent)
            .build()

        val context = applicationContext
        lifecycleScope.launch {
            val avatarBitmap = loadAvatar(event.avatar)

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(avatarBitmap)
                .setContentTitle(sender)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(replyAction)
                .addAction(readAction)
                .setGroup(GROUP_KEY_CHAT + chatId)

            notificationManager.notify(singleMessageId, builder.build())

            if (messages.size > 1) {
                val inboxStyle = NotificationCompat.InboxStyle()
                    .setBigContentTitle("Чат с $sender")
                messages.forEach { msg ->
                    inboxStyle.addLine("${msg.senderName}: ${msg.text ?: "[Вложение]"}")
                }

                val groupNotification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setLargeIcon(avatarBitmap)
                    .setContentTitle("Сообщения в чате")
                    .setContentText("У вас ${messages.size} новых сообщений")
                    .setStyle(inboxStyle)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setGroup(GROUP_KEY_CHAT + chatId)
                    .setGroupSummary(true)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)

                notificationManager.notify(summaryId, groupNotification.build())
            }
        }
    }

    private suspend fun loadAvatar(eventAvatar: String?): Bitmap = withContext(Dispatchers.IO) {
        val context = applicationContext
        val avatar = eventAvatar ?: ""
        if (avatar != "") {
            val filePathTemp = async {
                if (fileManager.isExistAvatar(avatar)) {
                    return@async Pair(fileManager.getAvatarFilePath(avatar), true)
                } else {
                    try {
                        return@async Pair(webSocketService.downloadAvatar(context, avatar), false)
                    } catch (e: Exception) {
                        return@async Pair(null, true)
                    }
                }
            }
            val (first, second) = filePathTemp.await()
            if (first != null) {
                val file = File(first)
                if (file.exists()) {
                    if (!second) fileManager.saveAvatarFile(avatar, file.readBytes())
                    val uri = Uri.fromFile(file)
                    return@withContext try {
                        Glide.with(context)
                            .asBitmap()
                            .load(uri)
                            .apply(RequestOptions.circleCropTransform())
                            .submit()
                            .get()
                    } catch (e: Exception) {
                        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                    }
                } else return@withContext Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            } else return@withContext Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        } else return@withContext Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    private fun sendNewsNotification(event: NewsEvent) {

        val aead = chatKeyManager.getAead(0, "news")
        val tinkAesGcmHelper = if(aead != null) TinkAesGcmHelper(aead) else return

        val text = event.text?.let { tinkAesGcmHelper.decryptText(it) } ?: "Новая новость!"
        val headerText = tinkAesGcmHelper.decryptText(event.headerText)

        val newsId = event.hashCode() // Уникальный ID для новости

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("open_news", true)
        }
        val pendingIntent = PendingIntent.getActivity(this, newsId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(headerText)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(newsId, builder.build())
    }
}
