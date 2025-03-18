package com.example.messenger.model

import android.content.Context
import android.util.Log
import com.example.messenger.model.appsettings.AppSettings
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import io.socket.engineio.client.transports.WebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URISyntaxException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketService @Inject constructor(
    private val appSettings: AppSettings,
    private val retrofitService: RetrofitService,
    private val messengerService: MessengerService
) {
    private var socket: Socket? = null
    private var lastEvent: String? = null
    private var lastData: JSONObject? = null
    private val notificationEnabledCache = mutableMapOf<Pair<Int, Boolean>, Boolean>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _newMessageFlow = MutableSharedFlow<Message>(extraBufferCapacity = 1)
    val newMessageFlow: SharedFlow<Message> get() = _newMessageFlow

    private val _editMessageFlow = MutableSharedFlow<Message>(extraBufferCapacity = 1)
    val editMessageFlow: SharedFlow<Message> get() = _editMessageFlow

    private val _deleteMessageFlow = MutableSharedFlow<DeletedMessagesEvent>(extraBufferCapacity = 1)
    val deleteMessageFlow: SharedFlow<DeletedMessagesEvent> get() = _deleteMessageFlow

    private val _readMessageFlow = MutableSharedFlow<ReadMessagesEvent>(extraBufferCapacity = 1)
    val readMessageFlow: SharedFlow<ReadMessagesEvent> get() = _readMessageFlow

    private val _deleteAllMessageFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deleteAllMessageFlow: SharedFlow<Unit> get() = _deleteAllMessageFlow

    private val _dialogDeletedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val dialogDeletedFlow: SharedFlow<Unit> get() = _dialogDeletedFlow

    private val _userSessionFlow = MutableSharedFlow<UserSessionUpdatedEvent>(extraBufferCapacity = 1)
    val userSessionFlow: SharedFlow<UserSessionUpdatedEvent> get() = _userSessionFlow

    private val _typingFlow = MutableSharedFlow<Pair<Int, Boolean>>(extraBufferCapacity = 1)
    val typingFlow: SharedFlow<Pair<Int, Boolean>> get() = _typingFlow

    private val _joinLeaveDialogFlow = MutableSharedFlow<Triple<Int, Int, Boolean>>(extraBufferCapacity = 1)
    val joinLeaveDialogFlow: SharedFlow<Triple<Int, Int, Boolean>> get() = _joinLeaveDialogFlow

    private val _notificationMessageFlow = MutableSharedFlow<ChatMessageEvent>(extraBufferCapacity = 1)
    val notificationMessageFlow: SharedFlow<ChatMessageEvent> get() = _notificationMessageFlow

    private val _notificationNewsFlow = MutableSharedFlow<NewsEvent>(extraBufferCapacity = 1)
    val notificationNewsFlow: SharedFlow<NewsEvent> get() = _notificationNewsFlow

    // todo Нужно тщательно протестировать, возможно придется менять/полностью удалить
    private val _isViewModelActive = MutableStateFlow(false)
    val isViewModelActive: StateFlow<Boolean> get() = _isViewModelActive

    fun setViewModelActive(active: Boolean) {
        _isViewModelActive.value = active
    }

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory()) // support serializable kt class
        .build()

    fun connect() {
        val token = appSettings.getCurrentAccessToken()

        val flagExpiredInit = if(token != null) isTokenExpired(token) else false

        val options = IO.Options().apply {
            transports = arrayOf(WebSocket.NAME)
            extraHeaders = mapOf("Authorization" to listOf(token ?: ""))
            reconnection = true
            reconnectionAttempts = Int.MAX_VALUE
            reconnectionDelay = 5000
            reconnectionDelayMax = 10000
        }

        try {
            socket = IO.socket("https://amessenger.ru", options)
            socket?.on(Socket.EVENT_CONNECT) {
                Log.d("testSocketIO", "Connected successfully")
            }?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.d("testSocketIO", "Connection error: ${args[0]}")
            }?.on(Socket.EVENT_DISCONNECT) {
                Log.d("testSocketIO", "Disconnected")
                if(flagExpiredInit) {
                    refresh()
                }
            }
            // Register additional event listeners here
            registerEventListeners()

            socket?.connect()
        } catch (e: URISyntaxException) {
            Log.e("WebSocket", "URI Syntax Error", e)
        }
    }

    fun disconnect() {
        socket?.disconnect()
    }

    fun reconnectIfNeeded() {
        if (!isConnected()) {
            connect()
        }
    }

    private fun isConnected(): Boolean {
        return socket?.connected() ?: false
    }

    private fun registerEventListeners() {
        // Registering the "user_joined" event listener
        socket?.on("user_joined", onUserJoined)
        socket?.on("user_left", onUserLeft)

        // Notifications
        socket?.on("new_message_notification", onMessageNotification)
        socket?.on("news_notification", onNewsNotification)

        // Registering message-related event listeners
        socket?.on("new_message", onNewMessage)
        socket?.on("message_edited", onEditedMessage)
        socket?.on("messages_deleted", onMessagesDeleted)

        // Register more events
        socket?.on("dialog_deleted", onDialogDeleted)
        socket?.on("user_session_updated", onUserSessionUpdated)
        socket?.on("typing", onStartTyping)
        socket?.on("stop_typing", onStopTyping)
        socket?.on("messages_read", onMessagesRead)
        socket?.on("messages_all_deleted", onAllMessagesDeleted)

        // token expired
        socket?.on("token_expired", onTokenExpired)
    }

    private val onTokenExpired = Emitter.Listener {
        refresh()
    }

    private fun refresh() {
        Log.d("testSocketIO", "Token has expired, refreshing token")
        serviceScope.launch {

            if (appSettings.isTokenRefreshing() || appSettings.isTokenRecentlyRefreshed()) {
                Log.d("testSocketIO", "Token is already being refreshed or was recently refreshed")
                delay(1000) // Небольшая задержка перед реконнектом
                reconnect()
                return@launch
            }

            appSettings.setTokenRefreshing(true)

            val refreshToken = appSettings.getCurrentRefreshToken()
            if(refreshToken.isNullOrEmpty()) {
                Log.d("testSocketIO", "Error: Empty refresh token")
                appSettings.setTokenRefreshing(false)
                return@launch
            }

            appSettings.setCurrentAccessToken(null)

            val newAccessToken = try {
                retrofitService.refreshToken(refreshToken)
            } catch (e: Exception) {
                Log.d("testSocketIO", "Error: Couldn't update the token")
                appSettings.setTokenRefreshing(false)
                return@launch
            }
            appSettings.setCurrentAccessToken(newAccessToken)
            appSettings.setTokenRefreshing(false)

            Log.d("testSocketIO", "Try to reconnect sockets")
            reconnect()
        }
    }

    private suspend fun reconnect() {
        // send last emit
        if(lastData != null && lastEvent != null) {
            socket?.disconnect()
            delay(200)
            connect()
            socket?.emit(lastEvent, lastData)
            lastData = null
            lastEvent = null
        } else {
            socket?.disconnect()
            delay(200)
            connect()
            lastData = null
            lastEvent = null
        }
    }

    private val onUserJoined = Emitter.Listener { args ->
        val data = args[0] as JSONObject
        val dialogId = data.getInt("dialog_id")
        val userId = data.getInt("user_id")
        _joinLeaveDialogFlow.tryEmit(Triple(dialogId, userId, true))
    }

    private val onUserLeft = Emitter.Listener { args ->
        val data = args[0] as JSONObject
        val dialogId = data.getInt("dialog_id")
        val userId = data.getInt("user_id")
        _joinLeaveDialogFlow.tryEmit(Triple(dialogId, userId, false))
    }

    fun send(event: String, data: JSONObject) {
        lastEvent = event
        lastData = data
        socket?.emit(event, data)
    }

    private val onNewMessage = Emitter.Listener { args ->
        val messageAdapter = moshi.adapter(Message::class.java)
        val messageData = args[0] as JSONObject
        val newMessage = messageAdapter.fromJson(messageData.toString())
        newMessage?.let {
            _newMessageFlow.tryEmit(it)
        }
    }

    private val onEditedMessage = Emitter.Listener { args ->
        val messageAdapter = moshi.adapter(Message::class.java)
        val messageData = args[0] as JSONObject
        val editedMessage = messageAdapter.fromJson(messageData.toString())
        editedMessage?.let {
            _editMessageFlow.tryEmit(it)
        }
    }

    private val onMessagesDeleted = Emitter.Listener { args ->
        val deletedAdapter = moshi.adapter(DeletedMessagesEvent::class.java)
        val messageData = args[0] as JSONObject
        val deletedEvent = deletedAdapter.fromJson(messageData.toString())
        deletedEvent?.let {
            _deleteMessageFlow.tryEmit(it)
        }
    }

    private val onDialogDeleted = Emitter.Listener {
        _dialogDeletedFlow.tryEmit(Unit)
    }

    private val onUserSessionUpdated = Emitter.Listener { args ->
        val userAdapter = moshi.adapter(UserSessionUpdatedEvent::class.java)
        val messageData = args[0] as JSONObject
        val lastSessionUpdatedEvent = userAdapter.fromJson(messageData.toString())
        lastSessionUpdatedEvent?.let {
            _userSessionFlow.tryEmit(it)
        }
    }

    private val onStartTyping = Emitter.Listener { args ->
        val data = args[0] as JSONObject
        val userId = data.getInt("user_id")
        _typingFlow.tryEmit(Pair(userId, true))
    }

    private val onStopTyping = Emitter.Listener { args ->
        val data = args[0] as JSONObject
        val userId = data.getInt("user_id")
        _typingFlow.tryEmit(Pair(userId, false))
    }

    private val onMessagesRead = Emitter.Listener { args ->
        val readAdapter = moshi.adapter(ReadMessagesEvent::class.java)
        val messageData = args[0] as JSONObject
        val readEvent = readAdapter.fromJson(messageData.toString())
        readEvent?.let {
            _readMessageFlow.tryEmit(it)
        }
    }

    private val onAllMessagesDeleted = Emitter.Listener {
        _deleteAllMessageFlow.tryEmit(Unit)
    }

    private val onMessageNotification = Emitter.Listener { args ->
        val messageAdapter = moshi.adapter(ChatMessageEvent::class.java)
        val messageData = args[0] as JSONObject
        val newMessage = messageAdapter.fromJson(messageData.toString())
        newMessage?.let {
            Log.d("testNotificationMes", it.toString())
            _notificationMessageFlow.tryEmit(it)
        }
    }

    private val onNewsNotification = Emitter.Listener { args ->
        Log.d("testNotificationNews", "OK")
        val messageAdapter = moshi.adapter(NewsEvent::class.java)
        val messageData = args[0] as JSONObject
        val newNews = messageAdapter.fromJson(messageData.toString())
        newNews?.let {
            _notificationNewsFlow.tryEmit(it)
        }
    }

    suspend fun downloadAvatar(context: Context, filename: String): String {
        return retrofitService.downloadAvatar(context, filename)
    }

    suspend fun isNotificationsEnabled(chatId: Int, type: Boolean): Boolean {
        return notificationEnabledCache.getOrPut(Pair(chatId, type)) {
            messengerService.isNotificationsEnabled(chatId, type)
        }
    }

    private fun isTokenExpired(token: String): Boolean {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) {
                throw IllegalArgumentException("Invalid JWT token")
            }

            val payload = String(Base64.getUrlDecoder().decode(parts[1]))
            val jsonObject = JSONObject(payload)

            val exp = jsonObject.getLong("exp")
            val currentTime = System.currentTimeMillis() / 1000

            currentTime >= exp
        } catch (e: Exception) {
            Log.e("WebSocketService", "Error decoding token", e)
            true // В случае ошибки считаем токен невалидным
        }
    }
}