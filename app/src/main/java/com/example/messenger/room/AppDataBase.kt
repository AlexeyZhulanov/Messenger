package com.example.messenger.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.messenger.room.dao.ChatSettingsDao
import com.example.messenger.room.dao.ConversationDao
import com.example.messenger.room.dao.GitlabDao
import com.example.messenger.room.dao.GroupMemberDao
import com.example.messenger.room.dao.GroupMessageDao
import com.example.messenger.room.dao.MessageDao
import com.example.messenger.room.dao.NewsDao
import com.example.messenger.room.dao.UnsentMessageDao
import com.example.messenger.room.dao.UserDao
import com.example.messenger.room.entities.ChatSettingsDbEntity
import com.example.messenger.room.entities.ConversationEntity
import com.example.messenger.room.entities.GitlabDbEntity
import com.example.messenger.room.entities.GroupMemberDbEntity
import com.example.messenger.room.entities.GroupMessageDbEntity
import com.example.messenger.room.entities.LastMessageEntity
import com.example.messenger.room.entities.MessageDbEntity
import com.example.messenger.room.entities.NewsDbEntity
import com.example.messenger.room.entities.UnsentMessageEntity
import com.example.messenger.room.entities.UserDbEntity
import com.example.messenger.room.entities.UserEntity

@Database(
    version = 1,
    entities = [
        ConversationEntity::class,
        UserEntity::class,
        LastMessageEntity::class,
        MessageDbEntity::class,
        GroupMessageDbEntity::class,
        UserDbEntity::class,
        ChatSettingsDbEntity::class,
        UnsentMessageEntity::class,
        GroupMemberDbEntity::class,
        NewsDbEntity::class,
        GitlabDbEntity::class
    ]
)

@TypeConverters(Converters::class)
abstract class AppDatabase: RoomDatabase() {

    abstract fun getConversationDao(): ConversationDao

    abstract fun getMessageDao(): MessageDao

    abstract fun getGroupMessageDao(): GroupMessageDao

    abstract fun getUserDao(): UserDao

    abstract fun getChatSettingsDao(): ChatSettingsDao

    abstract fun getUnsentMessageDao(): UnsentMessageDao

    abstract fun getGroupMemberDao(): GroupMemberDao

    abstract fun getNewsDao(): NewsDao

    abstract fun getGitlabDao(): GitlabDao
}
