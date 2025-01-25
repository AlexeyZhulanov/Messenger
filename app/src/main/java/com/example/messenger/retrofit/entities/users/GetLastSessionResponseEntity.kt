package com.example.messenger.retrofit.entities.users

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GetLastSessionResponseEntity(
    @Json(name = "last_session") val lastSession: Long? = -1
)