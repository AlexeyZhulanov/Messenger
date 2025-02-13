package com.example.messenger

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.messenger.databinding.ActivityMainBinding
import com.example.messenger.model.MessengerService
import com.example.messenger.model.WebSocketNotificationsService
import com.example.messenger.model.WebSocketService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

const val APP_PREFERENCES = "APP_PREFERENCES"
const val PREF_WALLPAPER = "PREF_WALLPAPER"
const val PREF_THEME = "PREF_THEME"

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    @Inject
    lateinit var messengerService: MessengerService

    override fun onCreate(savedInstanceState: Bundle?) {
        val serviceIntent = Intent(this, WebSocketNotificationsService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        val preferences = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE)
        val themeNumber = preferences.getInt(PREF_THEME, 0)
        when(themeNumber) {
            0 -> setTheme(R.style.Theme_Messenger)
            1 -> setTheme(R.style.Theme1)
            2 -> setTheme(R.style.Theme2)
            3 -> setTheme(R.style.Theme3)
            4 -> setTheme(R.style.Theme4)
            5 -> setTheme(R.style.Theme5)
            6 -> setTheme(R.style.Theme6)
            7 -> setTheme(R.style.Theme7)
            8 -> setTheme(R.style.Theme8)
            else -> setTheme(R.style.Theme_Messenger)
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val chatId = intent?.getIntExtra("chat_id", -1) ?: -1
        if (chatId != -1) {
            Toast.makeText(this, "Уведомление было нажато", Toast.LENGTH_SHORT).show()
            // todo openChat(chatId)
        }
        binding = ActivityMainBinding.inflate(layoutInflater).also { setContentView(it.root) }
        setContentView(binding.root)
        lifecycleScope.launch {
            val s = async { messengerService.getSettings() }
            val settings = s.await()
            if (savedInstanceState == null) {
                if (settings.remember == 1) {
                    if (settings.name != "" && settings.password != "" && settings.name != "empty" && settings.password != "empty") {
                        supportFragmentManager
                            .beginTransaction()
                            .add(R.id.fragmentContainer, MessengerFragment(), "MESSENGER_FRAGMENT_TAG")
                            .commit()
                    }
                }
                else {
                    supportFragmentManager
                        .beginTransaction()
                        .add(R.id.fragmentContainer, LoginFragment(), "LOGIN_FRAGMENT_TAG")
                        .commit()
                }
            }
        }
    }
}