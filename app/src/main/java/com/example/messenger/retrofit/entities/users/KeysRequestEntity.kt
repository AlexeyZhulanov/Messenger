package com.example.messenger.retrofit.entities.users

import com.squareup.moshi.Json

data class KeysRequestEntity(
    @Json(name = "public_key") val publicKey: String,
    @Json(name = "private_key") val privateKey: String
)