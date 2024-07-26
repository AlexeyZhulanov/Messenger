package com.example.messenger.retrofit.entities.users

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GetLastSessionResponseEntity(
    val timestamp: Long? = -1
)