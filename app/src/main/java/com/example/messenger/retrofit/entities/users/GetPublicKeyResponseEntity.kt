package com.example.messenger.retrofit.entities.users

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class GetPublicKeyResponseEntity(
    @Json(name = "public_key") val publicKey: String? = null
)