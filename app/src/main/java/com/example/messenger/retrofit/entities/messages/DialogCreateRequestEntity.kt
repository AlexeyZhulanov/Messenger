package com.example.messenger.retrofit.entities.messages

import com.squareup.moshi.Json

data class DialogCreateRequestEntity(
    val name: String,
    @Json(name = "key_user1") val keyUser1: String,
    @Json(name = "key_user2") val keyUser2: String
)