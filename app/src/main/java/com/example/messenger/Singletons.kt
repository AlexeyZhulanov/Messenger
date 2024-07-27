package com.example.messenger

import android.content.Context
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.appsettings.AppSettings
import com.example.messenger.model.appsettings.SharedPreferencesAppSettings
import com.example.messenger.retrofit.source.SourceProviderHolder
import com.example.messenger.retrofit.source.base.SourcesProvider

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

    val retrofitService: RetrofitService by lazy {
        RetrofitService(usersSource, messagesSource, groupsSource, appSettings)
    }

    fun init(appContext: Context) {
        Singletons.appContext = appContext
    }
}