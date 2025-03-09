package com.example.messenger.retrofit.entities.messages

import com.squareup.moshi.Json

data class DialogCreateResponseEntity(
    @Json(name = "id_dialog") val idDialog: Int
)