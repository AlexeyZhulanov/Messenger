package com.example.messenger.di

import android.content.Context
import androidx.room.Room
import com.example.messenger.model.FileManager
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.appsettings.AppSettings
import com.example.messenger.model.appsettings.SharedPreferencesAppSettings
import com.example.messenger.retrofit.source.SourceProviderHolder
import com.example.messenger.retrofit.source.base.SourcesProvider
import com.example.messenger.room.AppDatabase
import com.example.messenger.room.dao.ConversationDao
import com.example.messenger.room.dao.GroupMessageDao
import com.example.messenger.room.dao.MessageDao
import com.example.messenger.room.dao.SettingsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MessengerModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext appContext: Context): AppDatabase {
        return Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            "database.db"
        ).createFromAsset("init_db.db").build()
    }

    @Provides
    @Singleton
    fun provideSettingsDao(appDatabase: AppDatabase): SettingsDao {
        return appDatabase.getSettingsDao()
    }

    @Provides
    @Singleton
    fun provideConversationDao(appDatabase: AppDatabase): ConversationDao {
        return appDatabase.getConversationDao()
    }

    @Provides
    @Singleton
    fun provideMessageDao(appDatabase: AppDatabase): MessageDao {
        return appDatabase.getMessageDao()
    }

    @Provides
    @Singleton
    fun provideGroupMessageDao(appDatabase: AppDatabase): GroupMessageDao {
        return appDatabase.getGroupMessageDao()
    }

    @Provides
    @Singleton
    fun provideMessengerService(
        settingsDao: SettingsDao,
        conversationDao: ConversationDao,
        messageDao: MessageDao,
        groupMessageDao: GroupMessageDao
    ): MessengerService {
        return MessengerService(settingsDao, conversationDao, messageDao, groupMessageDao)
    }

    @Provides
    @Singleton
    fun provideAppSettings(@ApplicationContext context: Context): AppSettings {
        return SharedPreferencesAppSettings(context)
    }

    @Provides
    @Singleton
    fun provideRetrofitService(
        sourcesProvider: SourcesProvider,
        appSettings: AppSettings,
        messengerService: MessengerService
    ): RetrofitService {
        val usersSource = sourcesProvider.getUsersSource()
        val messagesSource = sourcesProvider.getMessagesSource()
        val groupsSource = sourcesProvider.getGroupsSource()
        val uploadsSource = sourcesProvider.getUploadsSource()

        return RetrofitService(
            usersSource = usersSource,
            messagesSource = messagesSource,
            groupsSource = groupsSource,
            uploadSource = uploadsSource,
            appSettings = appSettings,
            messengerRepository = messengerService
        )
    }

    @Provides
    @Singleton
    fun provideSourceProviderHolder(
        appSettings: AppSettings,
        messengerService: MessengerService,
        retrofitService: RetrofitService
    ): SourceProviderHolder {
        return SourceProviderHolder(appSettings, messengerService, retrofitService)
    }

    @Provides
    @Singleton
    fun provideSourcesProvider(holder: SourceProviderHolder): SourcesProvider {
        return holder.sourcesProvider
    }

    @Provides
    @Singleton
    fun provideFileManager(@ApplicationContext context: Context): FileManager {
        return FileManager(context)
    }
}