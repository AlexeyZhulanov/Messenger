package com.example.messenger.room

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.messenger.room.dao.SettingsDao
import com.example.messenger.room.entities.SettingsDbEntity

@Database(
    version = 1,
    entities = [
        SettingsDbEntity::class
    ]
)
abstract class AppDatabase: RoomDatabase() {
    abstract fun getSettingsDao(): SettingsDao
}