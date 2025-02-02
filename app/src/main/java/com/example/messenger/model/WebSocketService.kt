package com.example.messenger.model

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URISyntaxException
import javax.inject.Inject

class WebSocketService @Inject constructor(
    private val appSettings: AppSettings,
    private val retrofitService: RetrofitService,
    private val messengerService: MessengerService
) {
    private lateinit var socket: Socket
    private var lastEvent: String? = null
    private var lastData: JSONObject? = null
    private val uiScope = CoroutineScope(Dispatchers.Main)

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

    // Пока что не знаю это нужно ли вообще будет
    private val _dialogCreatedFlow = MutableSharedFlow<DialogCreatedEvent>(extraBufferCapacity = 1)
    val dialogCreatedFlow: SharedFlow<DialogCreatedEvent> get() = _dialogCreatedFlow

    private val _dialogDeletedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val dialogDeletedFlow: SharedFlow<Unit> get() = _dialogDeletedFlow

    private val _userSessionFlow = MutableSharedFlow<UserSessionUpdatedEvent>(extraBufferCapacity = 1)
    val userSessionFlow: SharedFlow<UserSessionUpdatedEvent> get() = _userSessionFlow

    private val _typingFlow = MutableSharedFlow<Pair<Int, Boolean>>(extraBufferCapacity = 1)
    val typingFlow: SharedFlow<Pair<Int, Boolean>> get() = _typingFlow

    private val _joinLeaveDialogFlow = MutableSharedFlow<Triple<Int, Int, Boolean>>(extraBufferCapacity = 1)
    val joinLeaveDialogFlow: SharedFlow<Triple<Int, Int, Boolean>> get() = _joinLeaveDialogFlow

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory()) // support serializable kt class
        .build()

    fun connect() {
        val options = IO.Options()
        options.transports = arrayOf(WebSocket.NAME)
        options.extraHeaders = mapOf("Authorization" to listOf(appSettings.getCurrentToken() ?: ""))


        try {
            socket = IO.socket("https://amessenger.ru", options)
            socket.on(Socket.EVENT_CONNECT) {
                Log.d("testSocketIO", "Connected successfully")
            }.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.d("testSocketIO", "Connection error: ${args[0]}")
            }
            // Register additional event listeners here
            registerEventListeners()

            socket.connect()
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
    }

    private fun registerEventListeners() {
        // Registering the "user_joined" event listener
        socket.on("user_joined", onUserJoined)
        socket.on("user_left", onUserLeft)

        // Registering message-related event listeners
        socket.on("new_message", onNewMessage)
        socket.on("message_edited", onEditedMessage)
        socket.on("messages_deleted", onMessagesDeleted)

        // Register more events
        socket.on("dialog_created", onDialogCreated)
        socket.on("dialog_deleted", onDialogDeleted)
        socket.on("user_session_updated", onUserSessionUpdated)
        socket.on("typing", onStartTyping)
        socket.on("stop_typing", onStopTyping)
        socket.on("messages_read", onMessagesRead)
        socket.on("messages_all_deleted", onAllMessagesDeleted)

        // token expired
        socket.on("token_expired", onTokenExpired)
    }

    private val onTokenExpired = Emitter.Listener {
        Log.d("testSocketIO", "Token has expired, refreshing token")
        uiScope.launch {
            val settingsResponse = messengerService.getSettings()
            val success = retrofitService.login(settingsResponse.name!!, settingsResponse.password!!)
            if(success) {
                Log.d("testSocketIO", "Try to reconnect sockets")
                // send last emit
                if(lastData != null && lastEvent != null) {
                    socket.disconnect()
                    delay(200)
                    connect()
                    socket.emit(lastEvent, lastData)
                    lastData = null
                    lastEvent = null
                } else {
                    socket.disconnect()
                    delay(200)
                    connect()
                    lastData = null
                    lastEvent = null
                }
            } else {
                Log.d("testSocketIO", "Error Sockets Token")
            }
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

    fun disconnect() {
        socket.disconnect()
    }

    fun send(event: String, data: JSONObject) {
        lastEvent = event
        lastData = data
        socket.emit(event, data)
    }

    private val onNewMessage = Emitter.Listener { args ->
        val messageAdapter = moshi.adapter(Message::class.java)
        val messageData = args[0] as JSONObject
        val newMessage = messageAdapter.fromJson(messageData.toString())
        _newMessageFlow.tryEmit(newMessage!!)
    }

    private val onEditedMessage = Emitter.Listener { args ->
        val messageAdapter = moshi.adapter(Message::class.java)
        val messageData = args[0] as JSONObject
        val editedMessage = messageAdapter.fromJson(messageData.toString())
        _editMessageFlow.tryEmit(editedMessage!!)
    }

    private val onMessagesDeleted = Emitter.Listener { args ->
        val deletedAdapter = moshi.adapter(DeletedMessagesEvent::class.java)
        val messageData = args[0] as JSONObject
        val deletedEvent = deletedAdapter.fromJson(messageData.toString())
        _deleteMessageFlow.tryEmit(deletedEvent!!)
    }

    private val onDialogCreated = Emitter.Listener { args ->
        val dialogAdapter = moshi.adapter(DialogCreatedEvent::class.java)
        val messageData = args[0] as JSONObject
        val dialogCreatedEvent = dialogAdapter.fromJson(messageData.toString())
        _dialogCreatedFlow.tryEmit(dialogCreatedEvent!!)
    }

    private val onDialogDeleted = Emitter.Listener {
        _dialogDeletedFlow.tryEmit(Unit)
    }

    private val onUserSessionUpdated = Emitter.Listener { args ->
        val userAdapter = moshi.adapter(UserSessionUpdatedEvent::class.java)
        val messageData = args[0] as JSONObject
        val lastSessionUpdatedEvent = userAdapter.fromJson(messageData.toString())
        _userSessionFlow.tryEmit(lastSessionUpdatedEvent!!)
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
        _readMessageFlow.tryEmit(readEvent!!)
    }

    private val onAllMessagesDeleted = Emitter.Listener {
        _deleteAllMessageFlow.tryEmit(Unit)
    }
}
