package com.example.messenger.retrofit.entities.messages

import com.squareup.moshi.Json

data class UpdateAutoDeleteIntervalRequestEntity(
    @Json(name = "auto_delete_interval") val autoDeleteInterval: Int
)