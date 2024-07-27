package com.example.messenger

import android.content.Context
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.messenger.databinding.ActivityMainBinding

const val APP_PREFERENCES = "APP_PREFERENCES"
const val PREF_WALLPAPER = "PREF_WALLPAPER"
const val PREF_THEME = "PREF_THEME"

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        Singletons.init(applicationContext)
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
        binding = ActivityMainBinding.inflate(layoutInflater).also { setContentView(it.root) }
        setContentView(binding.root)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .add(R.id.fragmentContainer, MessengerFragment(), "MESSENGER_FRAGMENT_TAG")
                .commit()
        }
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.messenger_menu, menu)
        val typedValue = TypedValue()
        theme.resolveAttribute(androidx.appcompat.R.attr.colorAccent, typedValue, true)
        val color = typedValue.data
        applyMenuTextColor(menu, color)
        return true
    }

    private fun applyMenuTextColor(menu: Menu, color: Int) {
        for (i in 0 until menu.size()) {
            val menuItem = menu.getItem(i)
            val spannableTitle = SpannableString(menuItem.title)
            spannableTitle.setSpan(ForegroundColorSpan(color), 0, spannableTitle.length, 0)
            menuItem.title = spannableTitle
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.open_settings -> {
                supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, SettingsFragment())
                    .addToBackStack("settings")
                    .commit()
                true
            }
            R.id.off_alarms -> {
                // todo
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}