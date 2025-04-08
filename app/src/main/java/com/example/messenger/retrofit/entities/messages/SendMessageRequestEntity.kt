package com.example.messenger.retrofit.entities.messages

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true) //null значения не будут отправлены
data class SendMessageRequestEntity(
    val text: String? = null,
    val images: List<String>? = null,
    val file: String? = null,
    val voice: String? = null,
    @Json(name = "reference_to_message_id") val referenceToMessageId: Int? = null,
    @Json(name = "is_forwarded") val isForwarded: Boolean? = false,
    @Json(name = "is_url") var isUrl: Boolean? = false,
    @Json(name = "username_author_original") val usernameAuthorOriginal: String? = null
)