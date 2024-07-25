package com.example.messenger.retrofit.entities.groups

import com.squareup.moshi.Json

data class AddUserToGroupRequestEntity(
    @Json(name = "user_id") val userId: Int
)