package com.example.messenger.model

import com.example.messenger.room.dao.SettingsDao
import com.example.messenger.room.entities.SettingsDbEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class MessengerService(
    private val settingsDao: SettingsDao
) : MessengerRepository {
    private var settings = Settings(0)
    override suspend fun getSettings(): Settings = withContext(Dispatchers.IO) {
        val settingsEntity = settingsDao.getSettings()
            settings = settingsEntity.toSettings()
        return@withContext settings
    }

    override suspend fun updateSettings(settings: Settings) = withContext(Dispatchers.IO) {
        settingsDao.updateSettings(SettingsDbEntity.fromUserInput(settings))
    }

}