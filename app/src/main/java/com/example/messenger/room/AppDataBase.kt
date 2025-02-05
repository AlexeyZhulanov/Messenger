package com.example.messenger.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.messenger.room.dao.ChatSettingsDao
import com.example.messenger.room.dao.ConversationDao
import com.example.messenger.room.dao.GroupMemberDao
import com.example.messenger.room.dao.GroupMessageDao
import com.example.messenger.room.dao.LastReadMessageDao
import com.example.messenger.room.dao.MessageDao
import com.example.messenger.room.dao.NewsDao
import com.example.messenger.room.dao.SettingsDao
import com.example.messenger.room.dao.UnsentMessageDao
import com.example.messenger.room.dao.UserDao
import com.example.messenger.room.entities.ChatSettingsDbEntity
import com.example.messenger.room.entities.ConversationEntity
import com.example.messenger.room.entities.GroupMemberDbEntity
import com.example.messenger.room.entities.GroupMessageDbEntity
import com.example.messenger.room.entities.LastMessageEntity
import com.example.messenger.room.entities.LastReadMessageEntity
import com.example.messenger.room.entities.MessageDbEntity
import com.example.messenger.room.entities.NewsDbEntity
import com.example.messenger.room.entities.SettingsDbEntity
import com.example.messenger.room.entities.UnsentMessageEntity
import com.example.messenger.room.entities.UserDbEntity
import com.example.messenger.room.entities.UserEntity

@Database(
    version = 1,
    entities = [
        SettingsDbEntity::class,
        ConversationEntity::class,
        UserEntity::class,
        LastMessageEntity::class,
        MessageDbEntity::class,
        GroupMessageDbEntity::class,
        UserDbEntity::class,
        LastReadMessageEntity::class,
        ChatSettingsDbEntity::class,
        UnsentMessageEntity::class,
        GroupMemberDbEntity::class,
        NewsDbEntity::class
    ]
)

@TypeConverters(Converters::class)
abstract class AppDatabase: RoomDatabase() {
    abstract fun getSettingsDao(): SettingsDao

    abstract fun getConversationDao(): ConversationDao

    abstract fun getMessageDao(): MessageDao

    abstract fun getGroupMessageDao(): GroupMessageDao

    abstract fun getUserDao(): UserDao

    abstract fun getLastReadMessageDao(): LastReadMessageDao

    abstract fun getChatSettingsDao(): ChatSettingsDao

    abstract fun getUnsentMessageDao(): UnsentMessageDao

    abstract fun getGroupMemberDao(): GroupMemberDao

    abstract fun getNewsDao(): NewsDao
}
