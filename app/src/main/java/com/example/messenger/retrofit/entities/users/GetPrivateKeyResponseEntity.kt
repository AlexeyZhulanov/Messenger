package com.example.messenger.retrofit.entities.users

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class GetPrivateKeyResponseEntity(
    @Json(name = "private_key") val privateKey: String? = null
)