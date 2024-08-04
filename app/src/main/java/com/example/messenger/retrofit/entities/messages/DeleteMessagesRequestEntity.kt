package com.example.messenger.retrofit.entities.messages

import com.squareup.moshi.Json

data class DeleteMessagesRequestEntity(
    @Json(name = "message_ids") val ids: List<Int>
)