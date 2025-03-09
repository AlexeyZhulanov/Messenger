package com.example.messenger.retrofit.entities.users

import com.squareup.moshi.Json

data class TokenResponseEntity(
    @Json(name = "access_token") val accessToken: String
)