package com.example.messenger.room

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    version = 1,
    entities = [
        SettingsDbEntity::class
    ]
)
abstract class AppDatabase: RoomDatabase() {
    abstract fun getSettingsDao(): SettingsDao
}