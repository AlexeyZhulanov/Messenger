package com.example.messenger.room

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.messenger.room.dao.ConversationDao
import com.example.messenger.room.dao.GroupMessageDao
import com.example.messenger.room.dao.MessageDao
import com.example.messenger.room.dao.SettingsDao
import com.example.messenger.room.entities.ConversationDbEntity
import com.example.messenger.room.entities.GroupMessageDbEntity
import com.example.messenger.room.entities.MessageDbEntity
import com.example.messenger.room.entities.SettingsDbEntity

@Database(
    version = 1,
    entities = [
        SettingsDbEntity::class,
        ConversationDbEntity::class,
        MessageDbEntity::class,
        GroupMessageDbEntity::class
    ]
)
abstract class AppDatabase: RoomDatabase() {
    abstract fun getSettingsDao(): SettingsDao

    abstract fun getConversationDao(): ConversationDao

    abstract fun getMessageDao(): MessageDao

    abstract fun getGroupMessageDao(): GroupMessageDao
}