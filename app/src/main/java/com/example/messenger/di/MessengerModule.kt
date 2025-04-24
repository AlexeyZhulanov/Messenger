package com.example.messenger.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.example.messenger.model.FileManager
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.appsettings.AppSettings
import com.example.messenger.model.appsettings.SharedPreferencesAppSettings
import com.example.messenger.retrofit.source.SourceProviderHolder
import com.example.messenger.model.WebSocketService
import com.example.messenger.model.appsettings.SharedPreferencesAppSettings.Companion.APP_PREFERENCES
import com.example.messenger.retrofit.source.base.SourcesProvider
import com.example.messenger.room.AppDatabase
import com.example.messenger.room.dao.ChatSettingsDao
import com.example.messenger.room.dao.ConversationDao
import com.example.messenger.room.dao.GitlabDao
import com.example.messenger.room.dao.GroupMemberDao
import com.example.messenger.room.dao.GroupMessageDao
import com.example.messenger.room.dao.MessageDao
import com.example.messenger.room.dao.NewsDao
import com.example.messenger.room.dao.UnsentMessageDao
import com.example.messenger.room.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Provider
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
        ).build()
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
    fun provideUserDao(appDatabase: AppDatabase): UserDao {
        return appDatabase.getUserDao()
    }

    @Provides
    @Singleton
    fun provideChatSettingsDao(appDatabase: AppDatabase): ChatSettingsDao {
        return appDatabase.getChatSettingsDao()
    }

    @Provides
    @Singleton
    fun provideUnsentMessageDao(appDatabase: AppDatabase): UnsentMessageDao {
        return appDatabase.getUnsentMessageDao()
    }

    @Provides
    @Singleton
    fun provideGroupMemberDao(appDatabase: AppDatabase): GroupMemberDao {
        return appDatabase.getGroupMemberDao()
    }

    @Provides
    @Singleton
    fun provideNewsDao(appDatabase: AppDatabase): NewsDao {
        return appDatabase.getNewsDao()
    }

    @Provides
    @Singleton
    fun provideGitlabDao(appDatabase: AppDatabase): GitlabDao {
        return appDatabase.getGitlabDao()
    }

    @Provides
    @Singleton
    fun provideMessengerService(
        conversationDao: ConversationDao,
        messageDao: MessageDao,
        groupMessageDao: GroupMessageDao,
        userDao: UserDao,
        chatSettingsDao: ChatSettingsDao,
        unsentMessageDao: UnsentMessageDao,
        groupMemberDao: GroupMemberDao,
        newsDao: NewsDao,
        gitlabDao: GitlabDao,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher
    ): MessengerService {
        return MessengerService(conversationDao, messageDao, groupMessageDao, userDao,
            chatSettingsDao, unsentMessageDao, groupMemberDao, newsDao, gitlabDao, ioDispatcher, defaultDispatcher)
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
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): RetrofitService {
        val usersSource = sourcesProvider.getUsersSource()
        val messagesSource = sourcesProvider.getMessagesSource()
        val groupsSource = sourcesProvider.getGroupsSource()
        val uploadsSource = sourcesProvider.getUploadsSource()
        val newsSource = sourcesProvider.getNewsSource()
        val gitlabSource = sourcesProvider.getGitlabSource()

        return RetrofitService(
            usersSource = usersSource,
            messagesSource = messagesSource,
            groupsSource = groupsSource,
            uploadSource = uploadsSource,
            newsSource = newsSource,
            gitlabSource = gitlabSource,
            appSettings = appSettings,
            ioDispatcher = ioDispatcher
        )
    }

    @Provides
    @Singleton
    fun provideSourceProviderHolder(
        appSettings: AppSettings,
        retrofitServiceProvider: Provider<RetrofitService>, // если передать просто retrofitService то будет цикл
        @ApplicationContext context: Context
        ): SourceProviderHolder {
        return SourceProviderHolder(appSettings, retrofitServiceProvider, context)
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

    @Provides
    @Singleton
    fun provideWebSocketService(
        appSettings: AppSettings,
        retrofitService: RetrofitService,
        messengerService: MessengerService
    ): WebSocketService {
        return WebSocketService(appSettings, retrofitService, messengerService)
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE)
    }

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MarkAsReadReceiverEntryPoint {
        fun retrofitService(): RetrofitService
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReplyReceiverEntryPoint {
        fun retrofitService(): RetrofitService
    }
}