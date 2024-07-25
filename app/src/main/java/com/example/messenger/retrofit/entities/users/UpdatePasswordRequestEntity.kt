package com.example.messenger.retrofit.entities.users

data class UpdatePasswordRequestEntity(
    val token: String,
    val password: String
)