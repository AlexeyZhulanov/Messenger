package com.example.messenger.retrofit.entities.news

import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class SendNewsRequestEntity(
    val text: String? = null,
    val images: List<String>? = null,
    val files: List<String>? = null,
    val voices: List<String>? = null
)
