package com.example.messenger.retrofit.entities.groups

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SendGroupMessageRequestEntity(
    val text: String? = null,
    val images: List<String>? = null,
    val file: String? = null,
    val voice: String? = null
)