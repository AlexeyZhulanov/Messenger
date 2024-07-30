package com.example.messenger.model

interface RetrofitRepository {

    fun isSignedIn(): Boolean

    suspend fun register(name: String, username: String, password: String) : Boolean

    suspend fun login(name: String, password: String) : Boolean

    suspend fun getConversations(): List<Conversation>

}