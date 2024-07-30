package com.example.messenger

import android.content.Context
import androidx.room.Room
import com.example.messenger.model.MessengerRepository
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitRepository
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.appsettings.AppSettings
import com.example.messenger.model.appsettings.SharedPreferencesAppSettings
import com.example.messenger.retrofit.source.SourceProviderHolder
import com.example.messenger.retrofit.source.base.SourcesProvider
import com.example.messenger.room.AppDatabase

object Singletons {

    private lateinit var appContext: Context

    private val sourcesProvider: SourcesProvider by lazy {
        SourceProviderHolder.sourcesProvider
    }

    val appSettings: AppSettings by lazy {
        SharedPreferencesAppSettings(appContext)
    }

    private val usersSource by lazy {
        sourcesProvider.getUsersSource()
    }

    private val messagesSource by lazy {
        sourcesProvider.getMessagesSource()
    }

    private val groupsSource by lazy {
        sourcesProvider.getGroupsSource()
    }

    private val database: AppDatabase by lazy<AppDatabase> {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "database.db")
            .createFromAsset("init_db.db")
            .build()
    }

    val messengerRepository: MessengerRepository by lazy {
        MessengerService(database.getSettingsDao())
    }

    val retrofitRepository: RetrofitRepository by lazy {
        RetrofitService(usersSource, messagesSource, groupsSource, appSettings, messengerRepository)
    }

    fun init(context: Context) {
        appContext = context
    }
}