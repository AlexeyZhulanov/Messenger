package com.example.messenger.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import com.example.messenger.room.entities.SettingsDbEntity

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings")
    suspend fun getSettings(): SettingsDbEntity

    @Update
    suspend fun updateSettings(settingsDbEntity: SettingsDbEntity)
}