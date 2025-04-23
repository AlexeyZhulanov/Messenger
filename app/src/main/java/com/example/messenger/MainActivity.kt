package com.example.messenger

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.Manifest
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.messenger.databinding.ActivityMainBinding
import com.example.messenger.model.FileManager
import com.example.messenger.model.MessengerService
import com.example.messenger.model.WebSocketNotificationsService
import com.example.messenger.model.appsettings.AppSettings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.inject.Inject

const val APP_PREFERENCES = "APP_PREFERENCES"
const val PREF_WALLPAPER = "PREF_WALLPAPER"
const val PREF_THEME = "PREF_THEME"

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @Inject lateinit var messengerService: MessengerService
    @Inject lateinit var fileManager: FileManager
    @Inject lateinit var appSettings: AppSettings

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1
    }

    private val logoutReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("testLogoutWithReceiver", "OK")
            goToAuthScreen()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val serviceIntent = Intent(this, WebSocketNotificationsService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        val preferences = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE)
        val themeNumber = preferences.getInt(PREF_THEME, 0)
        when (themeNumber) {
            2 -> setTheme(R.style.Theme2)
            3 -> setTheme(R.style.Theme3)
            else -> setTheme(R.style.Theme_Messenger)
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater).also { setContentView(it.root) }
        setContentView(binding.root)

        ContextCompat.registerReceiver(
            this,
            logoutReceiver,
            IntentFilter("com.example.messenger.LOGOUT"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
            }
        }
        val chatId = intent?.getIntExtra("chat_id", -1) ?: -1
        val openNews = intent?.getBooleanExtra("open_news", false) ?: false
        when {
            chatId != -1 -> {
                val isGroup = intent?.getBooleanExtra("is_group", false) ?: false
                if(isGroup) {
                    val (conv, currentUser) = runBlocking { // ANR, but it's necessary
                        val groupDeferred = async { messengerService.getConversationByTypeAndId("group", chatId) }
                        val userDeferred = async { messengerService.getUser() }
                        Pair(groupDeferred.await(), userDeferred.await())
                    }
                    if(conv != null && currentUser != null) {
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, GroupMessageFragment(conv.toGroup(), currentUser, true), "GROUP_FRAGMENT_TAG")
                            .commit()
                    } else Toast.makeText(this, "Не удалось открыть групповой чат", Toast.LENGTH_SHORT).show()
                } else {
                    val (conv, currentUser) = runBlocking { // ANR
                        val dialogDeferred = async { messengerService.getConversationByTypeAndId("dialog", chatId) }
                        val userDeferred = async { messengerService.getUser() }
                        Pair(dialogDeferred.await(), userDeferred.await())
                    }
                    Log.d("testClickNotify", "$conv and $currentUser")
                    if(conv != null && currentUser != null) {
                        Log.d("testClickNotify2", "Trying to open fragment message")
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, MessageFragment(conv.toDialog(), currentUser, true), "MESSAGE_FRAGMENT_TAG")
                            .commit()
                    } else Toast.makeText(this, "Не удалось открыть диалог", Toast.LENGTH_SHORT).show()
                }
            }
            openNews -> {
                val currentUser = runBlocking { messengerService.getUser() }
                val avatar = currentUser?.avatar
                val uri = if(avatar != null) {
                    if(fileManager.isExistAvatar(avatar)) {
                        val path = fileManager.getAvatarFilePath(avatar)
                        val file = File(path)
                        Uri.fromFile(file)
                    } else null
                } else null
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, NewsFragment(uri, null), "NEWS_FRAGMENT_TAG")
                    .commit()
            }
            else -> {
                lifecycleScope.launch {
                    val isRemember = appSettings.getRemember()
                    if (savedInstanceState == null) {
                        if (isRemember) {
                            supportFragmentManager
                                .beginTransaction()
                                .add(R.id.fragmentContainer, MessengerFragment(), "MESSENGER_FRAGMENT_TAG")
                                .commit()
                        } else {
                            goToAuthScreen()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(logoutReceiver)
    }

    fun goToAuthScreen() {
        supportFragmentManager
            .beginTransaction()
            .add(R.id.fragmentContainer, LoginFragment(), "LOGIN_FRAGMENT_TAG")
            .commit()
    }
}