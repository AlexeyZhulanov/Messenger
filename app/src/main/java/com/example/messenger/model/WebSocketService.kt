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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URISyntaxException
import javax.inject.Inject

interface WebSocketListenerInterface {
    fun onNewMessage(message: Message)
    fun onEditedMessage(message: Message)
    fun onMessagesDeleted(deletedMessagesEvent: DeletedMessagesEvent)
    fun onMessagesRead(readMessagesEvent: ReadMessagesEvent)
    fun onAllMessagesDeleted(dialogMessagesAllDeleted: DialogMessagesAllDeleted)
    fun onDialogCreated(dialogCreatedEvent: DialogCreatedEvent)
    fun onDialogDeleted(dialogDeletedEvent: DialogDeletedEvent)
    fun onUserSessionUpdated(userSessionUpdatedEvent: UserSessionUpdatedEvent)
    fun onStartTyping(typingEvent: TypingEvent)
    fun onStopTyping(typingEvent: TypingEvent)
    fun onUserJoinedDialog(dialogId: Int, userId: Int)
    fun onUserLeftDialog(dialogId: Int, userId: Int)
}

class WebSocketService @Inject constructor(
    private val appSettings: AppSettings,
    private val retrofitService: RetrofitService,
    private val messengerService: MessengerService
) {
    private lateinit var socket: Socket
    private var listener: WebSocketListenerInterface? = null
    private var lastEvent: String? = null
    private var lastData: JSONObject? = null
    private val job = Job()
    private val uiScopeIO = CoroutineScope(Dispatchers.IO + job)

    fun setListener(listener: WebSocketListenerInterface) {
        this.listener = listener
    }

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
        socket.on("dialog_messages_all_deleted", onAllMessagesDeleted)

        // token expired
        socket.on("token_expired", onTokenExpired)
    }

    private val onTokenExpired = Emitter.Listener {
        Log.d("testSocketIO", "Token has expired, refreshing token")
        uiScopeIO.launch {
            val settingsResponse = messengerService.getSettings()
            val success = retrofitService.login(settingsResponse.name!!, settingsResponse.password!!)
            if(success) {
                // send last emit
                if(lastData != null && lastEvent != null) {
                    Log.d("testSocketIO", "Try to reconnect sockets")
                    socket.connect()
                    socket.emit(lastEvent, lastData)
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
        listener?.onUserJoinedDialog(dialogId, userId)
    }

    private val onUserLeft = Emitter.Listener { args ->
        val data = args[0] as JSONObject
        val dialogId = data.getInt("dialog_id")
        val userId = data.getInt("user_id")
        listener?.onUserLeftDialog(dialogId, userId)
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
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory()) // support serializable kt class
            .build()
        val messageAdapter = moshi.adapter(Message::class.java)
        val messageData = args[0] as JSONObject
        val newMessage = messageAdapter.fromJson(messageData.toString())
        listener?.onNewMessage(newMessage!!)
    }

    private val onEditedMessage = Emitter.Listener { args ->
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val messageAdapter = moshi.adapter(Message::class.java)
        val messageData = args[0] as JSONObject
        val editedMessage = messageAdapter.fromJson(messageData.toString())
        listener?.onEditedMessage(editedMessage!!)
    }

    private val onMessagesDeleted = Emitter.Listener { args ->
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val deletedAdapter = moshi.adapter(DeletedMessagesEvent::class.java)
        val messageData = args[0] as JSONObject
        val deletedEvent = deletedAdapter.fromJson(messageData.toString())
        listener?.onMessagesDeleted(deletedEvent!!)
    }

    private val onDialogCreated = Emitter.Listener { args ->
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val dialogAdapter = moshi.adapter(DialogCreatedEvent::class.java)
        val messageData = args[0] as JSONObject
        val dialogCreatedEvent = dialogAdapter.fromJson(messageData.toString())
        listener?.onDialogCreated(dialogCreatedEvent!!)
    }

    private val onDialogDeleted = Emitter.Listener { args ->
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val dialogAdapter = moshi.adapter(DialogDeletedEvent::class.java)
        val messageData = args[0] as JSONObject
        val dialogDeletedEvent = dialogAdapter.fromJson(messageData.toString())
        listener?.onDialogDeleted(dialogDeletedEvent!!)

    }

    private val onUserSessionUpdated = Emitter.Listener { args ->
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val userAdapter = moshi.adapter(UserSessionUpdatedEvent::class.java)
        val messageData = args[0] as JSONObject
        val lastSessionUpdatedEvent = userAdapter.fromJson(messageData.toString())
        listener?.onUserSessionUpdated(lastSessionUpdatedEvent!!)
    }

    private val onStartTyping = Emitter.Listener { args ->
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val typingAdapter = moshi.adapter(TypingEvent::class.java)
        val messageData = args[0] as JSONObject
        val typingEvent = typingAdapter.fromJson(messageData.toString())
        listener?.onStartTyping(typingEvent!!)
    }

    private val onStopTyping = Emitter.Listener { args ->
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val typingAdapter = moshi.adapter(TypingEvent::class.java)
        val messageData = args[0] as JSONObject
        val typingEvent = typingAdapter.fromJson(messageData.toString())
        listener?.onStopTyping(typingEvent!!)
    }

    private val onMessagesRead = Emitter.Listener { args ->
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val readAdapter = moshi.adapter(ReadMessagesEvent::class.java)
        val messageData = args[0] as JSONObject
        val readEvent = readAdapter.fromJson(messageData.toString())
        listener?.onMessagesRead(readEvent!!)
    }

    private val onAllMessagesDeleted = Emitter.Listener { args ->
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val deleteAdapter = moshi.adapter(DialogMessagesAllDeleted::class.java)
        val messageData = args[0] as JSONObject
        val deleteEvent = deleteAdapter.fromJson(messageData.toString())
        listener?.onAllMessagesDeleted(deleteEvent!!)
    }
}
