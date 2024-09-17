package com.example.messenger.retrofit.source.users

import com.example.messenger.model.User

interface UsersSource {
    suspend fun register(name: String, username: String, password: String) : String

    suspend fun login(name: String, password: String) : String

    suspend fun updateProfile(username: String? = null, avatar: String? = null) : String

    suspend fun updatePassword(password: String) : String

    suspend fun updateLastSession(idDialog: Int) : String

    suspend fun getLastSession(userId: Int) : Long

    suspend fun getUser(userId: Int) : User
}