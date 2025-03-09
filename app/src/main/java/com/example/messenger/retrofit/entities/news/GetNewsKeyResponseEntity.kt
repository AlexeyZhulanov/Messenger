package com.example.messenger.retrofit.entities.news

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GetNewsKeyResponseEntity(
    @Json(name = "news_key") val newsKey: String? = null
)