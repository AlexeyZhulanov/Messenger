package com.example.messenger.retrofit.entities.users

data class UpdateProfileRequestEntity(
    val token: String,
    val username: String,
    val avatar: String
)