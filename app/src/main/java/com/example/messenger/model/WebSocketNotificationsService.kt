package com.example.messenger.model

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.lifecycle.LifecycleService
import com.example.messenger.MainActivity
import com.example.messenger.R
import com.example.messenger.model.appsettings.AppSettings
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.AndroidEntryPoint
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import io.socket.engineio.client.transports.WebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URISyntaxException
import javax.inject.Inject

@AndroidEntryPoint
class WebSocketNotificationsService : LifecycleService() {

    @Inject lateinit var appSettings: AppSettings
    @Inject lateinit var retrofitService: RetrofitService
    @Inject lateinit var messengerService: MessengerService

    private lateinit var socket: Socket

    companion object {
        private const val CHANNEL_ID = "chat_notifications"
        private const val GROUP_KEY_CHAT = "group_key_chat"
    }

    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private val chatNotifications = mutableMapOf<Int, MutableList<ChatMessageEvent>>()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var wakeLock: PowerManager.WakeLock

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory()) // support serializable kt class
        .build()

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        startForegroundService()
        createNotificationChannel()
        connect()
    }

    override fun onDestroy() {
        super.onDestroy()
        socket.disconnect()
        wakeLock.release()
        serviceScope.cancel()
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
            NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("WebSocket работает")
            .setSmallIcon(R.drawable.ic_eye)
            .build()

        startForeground(1, notification)
    }

    private fun connect() {
        val options = IO.Options().apply {
            transports = arrayOf(WebSocket.NAME)
            extraHeaders = mapOf("Authorization" to listOf(appSettings.getCurrentToken() ?: ""))
            reconnection = true
            reconnectionAttempts = Int.MAX_VALUE
            reconnectionDelay = 5000
            reconnectionDelayMax = 10000
        }

        try {
            socket = IO.socket("https://amessenger.ru", options)
            socket.on(Socket.EVENT_CONNECT) {
                Log.d("testSocketIO", "Connected successfully")
            }.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.d("testSocketIO", "Connection error: ${args[0]}")
            }.on(Socket.EVENT_DISCONNECT) {
                Log.d("testSocketIO", "Disconnected")
            }
            // Register additional event listeners here
            registerEventListeners()

            socket.connect()
        } catch (e: URISyntaxException) {
            Log.e("WebSocket", "URI Syntax Error", e)
        }
    }

    private fun registerEventListeners() {
        // Notifications
        socket.on("new_message_notification", onMessageNotification)
        socket.on("news_notification", onNewsNotification)

        // token expired
        socket.on("token_expired", onTokenExpired)
    }

    private val onTokenExpired = Emitter.Listener {
        Log.d("testSocketIO", "Token has expired, refreshing token")
        serviceScope.launch {
            val settingsResponse = messengerService.getSettings()
            val success = retrofitService.login(settingsResponse.name!!, settingsResponse.password!!)
            if(success) {
                Log.d("testSocketIO", "Try to reconnect sockets")
                socket.disconnect()
                delay(200)
                connect()
            } else {
                Log.d("testSocketIO", "Error Sockets Token")
            }
        }
    }

    private val onMessageNotification = Emitter.Listener { args ->
        Log.d("testNotificationMes", "OK")
        val messageAdapter = moshi.adapter(ChatMessageEvent::class.java)
        val messageData = args[0] as JSONObject
        val newMessage = messageAdapter.fromJson(messageData.toString())
        newMessage?.let {
            sendNotification(it)
        }
    }

    private val onNewsNotification = Emitter.Listener { args ->
        Log.d("testNotificationNews", "OK")
        val messageAdapter = moshi.adapter(NewsEvent::class.java)
        val messageData = args[0] as JSONObject
        val newNews = messageAdapter.fromJson(messageData.toString())
        newNews?.let {
            sendNewsNotification(it)
        }
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
        val chatId = event.chatId
        val sender = event.senderName
        val text = event.text ?: "[Вложение]"

        val messages = chatNotifications.getOrPut(chatId) { mutableListOf() }
        messages.add(event)

        val summaryId = chatId * 10
        val singleMessageId = chatId * 100 + messages.size

        val pendingIntent = PendingIntent.getActivity(
            this, chatId,
            Intent(this, MainActivity::class.java)
                .putExtra("chat_id", chatId)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Кнопка "Ответить"
        val replyIntent = Intent(this, ReplyReceiver::class.java).apply {
            putExtra("chat_id", chatId)
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
        }
        val readPendingIntent = PendingIntent.getBroadcast(
            this, chatId, readIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val readAction = NotificationCompat.Action.Builder(R.drawable.ic_check, "Прочитать", readPendingIntent)
            .build()

        val avatarBitmap = loadAvatar(event)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
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

            val groupNotification = NotificationCompat.Builder(this, CHANNEL_ID)
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

    private fun loadAvatar(event: ChatMessageEvent): Bitmap {
        // todo
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    private fun sendNewsNotification(event: NewsEvent) {
        val newsId = event.hashCode() // Уникальный ID для новости

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("open_news", true)
        }
        val pendingIntent = PendingIntent.getActivity(this, newsId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(event.headerText)
            .setContentText(event.text ?: "Новая новость!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(newsId, builder.build())
    }
}
