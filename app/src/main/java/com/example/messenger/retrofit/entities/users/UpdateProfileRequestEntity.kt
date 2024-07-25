package com.example.messenger.retrofit.entities.users

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UpdateProfileRequestEntity(
    val username: String? = null,
    val avatar: String? = null
)