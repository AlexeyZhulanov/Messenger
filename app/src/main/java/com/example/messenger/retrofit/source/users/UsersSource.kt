package com.example.messenger.retrofit.source.users

interface UsersSource {
    suspend fun register(name: String, username: String, password: String) : String

    suspend fun login(name: String, password: String) : String

    suspend fun updateProfile(username: String? = null, avatar: String? = null) : String

    suspend fun updatePassword(password: String) : String

    suspend fun updateLastSession() : String

    suspend fun getLastSession(userId: Int) : Long

}