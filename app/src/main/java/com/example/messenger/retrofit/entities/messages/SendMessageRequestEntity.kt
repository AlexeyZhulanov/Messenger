package com.example.messenger.retrofit.entities.messages

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true) //null значения не будут отправлены
data class SendMessageRequestEntity(
    val text: String? = null,
    val images: List<String>? = null,
    val file: String? = null,
    val voice: String? = null,
    val code: String? = null,
    @param:Json(name = "code_language") val codeLanguage: String? = null,
    @param:Json(name = "reference_to_message_id") val referenceToMessageId: Int? = null,
    @param:Json(name = "is_forwarded") val isForwarded: Boolean? = false,
    @param:Json(name = "is_url") var isUrl: Boolean? = false,
    @param:Json(name = "username_author_original") val usernameAuthorOriginal: String? = null,
    val waveform: List<Int>? = null
)