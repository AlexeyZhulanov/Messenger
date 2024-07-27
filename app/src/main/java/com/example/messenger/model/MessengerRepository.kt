package com.example.messenger.model

interface MessengerRepository {

    suspend fun getSettings(): Settings

    suspend fun updateSettings(settings: Settings)
}