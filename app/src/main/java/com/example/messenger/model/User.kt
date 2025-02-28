package com.example.messenger.model

import com.squareup.moshi.Json

data class User(
    val id: Int,
    val name: String,
    val username: String,
    val avatar: String? = null,
    val lastSession: Long? = 0,
    @Json(name = "public_key") val publicKey: String? = null
)

data class UserShort(
    val id: Int,
    val name: String
)