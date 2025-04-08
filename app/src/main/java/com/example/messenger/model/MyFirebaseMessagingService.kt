package com.example.messenger.model

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.messenger.model.appsettings.AppSettings
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var retrofitService: RetrofitService

    @Inject
    lateinit var appSettings: AppSettings

    private val firebaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("testFCM", "New token: $token")
        if(appSettings.getRemember()) sendTokenToServer(token) else appSettings.setFCMToken(token)
    }

    override fun onCreate() {
        super.onCreate()
        GitlabNotificationHelper.createNotificationChannel(this)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("testFCM", "Message received: ${remoteMessage.data}")

        remoteMessage.data["type"]?.let { type ->
            when (type) {
                "wakeup" -> startWebSocketService()
                "Push Hook" -> handlePushEvent(remoteMessage.data)
                "Merge Request Hook" -> handleMergeRequestEvent(remoteMessage.data)
                "Tag Push Hook" -> handleTagEvent(remoteMessage.data)
                "Issue Hook" -> handleIssueEvent(remoteMessage.data)
                "Note Hook" -> handleNoteEvent(remoteMessage.data)
                "Release Hook" -> handleReleaseEvent(remoteMessage.data)
                else -> showDefaultNotification(remoteMessage)
            }
        }
    }

    private fun startWebSocketService() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WebSocket:FCM")
        wakeLock.acquire(5000L)

        val serviceIntent = Intent(this, WebSocketService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun sendTokenToServer(token: String) {
        firebaseScope.launch {
            try {
                retrofitService.saveFCMToken(token)
            } catch (e: Exception) {
                Log.d("testFCM", "Не удалось отправить токен на сервер: ${e.message}")
            }
        }
    }

    private fun handlePushEvent(data: Map<String, String>) {
        val repo = data["repository"] ?: "Неизвестный репозиторий"
        val branch = data["branch"] ?: "Неизвестная ветка"
        val user = data["user"] ?: "Неизвестный пользователь"

        val title = "Новый push в $repo"
        val message = "$user отправил изменения в ветку $branch"

        GitlabNotificationHelper.showNotification(
            this,
            title,
            message,
            generateNotificationId(repo, branch)
        )
    }

    private fun handleMergeRequestEvent(data: Map<String, String>) {
        val repo = data["repository"] ?: "Неизвестный репозиторий"
        val user = data["user"] ?: "Неизвестный пользователь"

        val title = "Новый Merge Request в $repo"
        val message = "$user создал merge request"

        GitlabNotificationHelper.showNotification(
            this,
            title,
            message,
            generateNotificationId(repo, "mr")
        )
    }

    private fun handleTagEvent(data: Map<String, String>) {
        val repo = data["repository"] ?: "Неизвестный репозиторий"
        val tag = data["tag"] ?: "Новый тег"
        val user = data["user"] ?: "Неизвестный пользователь"

        val title = "Новый тег в $repo"
        val message = "$user создал тег $tag"

        GitlabNotificationHelper.showNotification(
            this,
            title,
            message,
            generateNotificationId(repo, "tag-$tag")
        )
    }

    private fun handleIssueEvent(data: Map<String, String>) {
        val repo = data["repository"] ?: "Неизвестный репозиторий"
        val issueId = data["issue_id"] ?: "Новый issue"
        val user = data["user"] ?: "Неизвестный пользователь"
        val action = when (data["action"]) {
            "open" -> "открыл"
            "close" -> "закрыл"
            "reopen" -> "переоткрыл"
            else -> "изменил"
        }

        val title = "Issue $action в $repo"
        val message = "$user $action issue #$issueId"

        GitlabNotificationHelper.showNotification(
            this,
            title,
            message,
            generateNotificationId(repo, "issue-$issueId")
        )
    }

    private fun handleNoteEvent(data: Map<String, String>) {
        val repo = data["repository"] ?: "Неизвестный репозиторий"
        val noteType = when (data["note_type"]) {
            "MergeRequest" -> "в merge request"
            "Issue" -> "в issue"
            "Commit" -> "в коммите"
            else -> ""
        }
        val user = data["user"] ?: "Неизвестный пользователь"

        val title = "Новый комментарий $noteType"
        val message = "$user оставил комментарий в $repo"

        GitlabNotificationHelper.showNotification(
            this,
            title,
            message,
            generateNotificationId(repo, "note-${System.currentTimeMillis()}")
        )
    }

    private fun handleReleaseEvent(data: Map<String, String>) {
        val repo = data["repository"] ?: "Неизвестный репозиторий"
        val version = data["version"] ?: "Новый релиз"
        val user = data["user"] ?: "Неизвестный пользователь"

        val title = "Новый релиз в $repo"
        val message = "$user выпустил версию $version"

        GitlabNotificationHelper.showNotification(
            this,
            title,
            message,
            generateNotificationId(repo, "release-$version")
        )
    }

    private fun showDefaultNotification(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: "GitLab Event"
        val message = remoteMessage.notification?.body
            ?: remoteMessage.data.entries.joinToString { "${it.key}=${it.value}" }

        GitlabNotificationHelper.showNotification(
            this,
            title,
            message,
            title.hashCode()
        )
    }

    private fun generateNotificationId(repo: String, branch: String): Int {
        return (repo + branch).hashCode()
    }
}
