package com.example.messenger.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings")
    suspend fun getSettings(): SettingsDbEntity

    @Update
    suspend fun updateSettings(settingsDbEntity: SettingsDbEntity)
}