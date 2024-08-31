package com.example.messenger.model

data class User(
    val id: Int,
    val name: String,
    val username: String,
    val avatar: String? = null,
    val lastSession: Long? = 0
)

data class UserShort(
    val id: Int,
    val name: String
)