package com.example.messenger.model

import android.util.Log
import com.example.messenger.model.appsettings.AppSettings
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import javax.inject.Inject

interface WebSocketListenerInterface {
    fun onNewMessage(message: Message)
    fun onEditedMessage(message: Message)
    fun onMessagesDeleted(deletedMessagesEvent: DeletedMessagesEvent)
    fun onDialogCreated(dialogCreatedEvent: DialogCreatedEvent)
    fun onDialogDeleted(dialogDeletedEvent: DialogDeletedEvent)
    fun onUserSessionUpdated(userSessionUpdatedEvent: UserSessionUpdatedEvent)
    fun onStartTyping(typingEvent: TypingEvent)
    fun onStopTyping(typingEvent: TypingEvent)
}

class WebSocketService @Inject constructor(
    private val appSettings: AppSettings
) {
    private lateinit var webSocket: WebSocket
    private val BASE_URL = "https://amessenger.ru"
    private var listener: WebSocketListenerInterface? = null

    fun setListener(listener: WebSocketListenerInterface) {
        this.listener = listener
    }

    fun connect() {
        val client = OkHttpClient()

        val request = Request.Builder()
            .url(BASE_URL)
            .addHeader("Authorization", appSettings.getCurrentToken() ?: "")
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                super.onOpen(webSocket, response)
                // здесь можно вызвать join_dialog на сервере
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                handleIncomingMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                super.onMessage(webSocket, bytes)
                // Обработка полученных сообщений
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                Log.e("SocketFailure", t.message ?: "Error")
            }
        }

        webSocket = client.newWebSocket(request, listener)
    }

    fun disconnect() {
        webSocket.close(1000, "Closing connection")
        // здесь можно вызвать leave_dialog на сервере
    }

    private fun handleIncomingMessage(message: String) {
        val moshi = Moshi.Builder().build()

        val json = JSONObject(message)

        when (json.getString("event")) {
            "new_message" -> {
                val messageAdapter = moshi.adapter(Message::class.java)
                val messageData = json.getJSONObject("data")
                val newMessageEvent = messageAdapter.fromJson(messageData.toString())
                listener?.onNewMessage(newMessageEvent!!)
            }
            "message_edited" -> {
                val messageAdapter = moshi.adapter(Message::class.java)
                val messageData = json.getJSONObject("data")
                val editedMessageEvent = messageAdapter.fromJson(messageData.toString())
                listener?.onEditedMessage(editedMessageEvent!!)
            }
            "messages_deleted" -> {
                val messageAdapter = moshi.adapter(DeletedMessagesEvent::class.java)
                val messageData = json.getJSONObject("data")
                val deletesMessagesEvent = messageAdapter.fromJson(messageData.toString())
                listener?.onMessagesDeleted(deletesMessagesEvent!!)
            }
            "dialog_created" -> {
                val dialogAdapter = moshi.adapter(DialogCreatedEvent::class.java)
                val messageData = json.getJSONObject("data")
                val dialogCreatedEvent = dialogAdapter.fromJson(messageData.toString())
                listener?.onDialogCreated(dialogCreatedEvent!!)
            }
            "dialog_deleted" -> {
                val dialogAdapter = moshi.adapter(DialogDeletedEvent::class.java)
                val messageData = json.getJSONObject("data")
                val dialogDeletedEvent = dialogAdapter.fromJson(messageData.toString())
                listener?.onDialogDeleted(dialogDeletedEvent!!)
            }
            "user_session_updated" -> {
                val userAdapter = moshi.adapter(UserSessionUpdatedEvent::class.java)
                val messageData = json.getJSONObject("data")
                val lastSessionUpdatedEvent = userAdapter.fromJson(messageData.toString())
                listener?.onUserSessionUpdated(lastSessionUpdatedEvent!!)
            }
            "typing" -> {
                val typingAdapter = moshi.adapter(TypingEvent::class.java)
                val messageData = json.getJSONObject("data")
                val typingEvent = typingAdapter.fromJson(messageData.toString())
                listener?.onStartTyping(typingEvent!!)
            }
            "stop_typing" -> {
                val typingAdapter = moshi.adapter(TypingEvent::class.java)
                val messageData = json.getJSONObject("data")
                val typingEvent = typingAdapter.fromJson(messageData.toString())
                listener?.onStopTyping(typingEvent!!)
            }
        }
    }
}