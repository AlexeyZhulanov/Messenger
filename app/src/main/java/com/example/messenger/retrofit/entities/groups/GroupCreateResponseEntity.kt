package com.example.messenger.retrofit.entities.groups

import com.squareup.moshi.Json

data class GroupCreateResponseEntity(
    @Json(name = "id_group") val idGroup: Int
)