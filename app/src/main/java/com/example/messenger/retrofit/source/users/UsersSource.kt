package com.example.messenger.retrofit.source.users

import com.example.messenger.model.User

interface UsersSource {
    suspend fun register(name: String, username: String, password: String) : String

    suspend fun login(name: String, password: String) : String

    suspend fun updateProfile(username: String? = null, avatar: String? = null) : String

    suspend fun updatePassword(password: String) : String

    suspend fun updateLastSession() : String

    suspend fun getLastSession(userId: Int) : Long

    suspend fun getUser(userId: Int) : User

    suspend fun getVacation() : Pair<String, String>?

    suspend fun getPermission() : Int
}