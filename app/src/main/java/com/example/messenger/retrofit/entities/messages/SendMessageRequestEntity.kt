package com.example.messenger.retrofit.entities.messages

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true) //null значения не будут отправлены
data class SendMessageRequestEntity(
    val text: String? = null,
    val images: List<String>? = null,
    val file: String? = null,
    val voice: String? = null
)