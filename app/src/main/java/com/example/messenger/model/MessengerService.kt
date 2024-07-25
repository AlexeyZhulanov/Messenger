package com.example.messenger.model

import com.example.messenger.room.SettingsDao
import com.example.messenger.room.SettingsDbEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class MessengerService(
    private val settingsDao: SettingsDao
) {
    private lateinit var settings : Settings
    suspend fun getSettings(): Settings = withContext(Dispatchers.IO) {
        settings = settingsDao.getSettings().toSettings()
        return@withContext settings
    }

    suspend fun updateSettings(settings: Settings) = withContext(Dispatchers.IO) {
        settingsDao.updateSettings(SettingsDbEntity.fromUserInput(settings))
    }

}