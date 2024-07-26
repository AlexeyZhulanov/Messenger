package com.example.messenger.retrofit.source.users

interface UsersSource {
    suspend fun register(name: String, username: String, password: String) : String

    suspend fun login(name: String, password: String) : String

    suspend fun updateProfile(token: String, username: String? = null, avatar: String? = null) : String

    suspend fun updatePassword(token: String, password: String) : String

    suspend fun updateLastSession(token: String) : String

    suspend fun getLastSession(token: String, userId: Int) : Long

}