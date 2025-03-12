package com.example.messenger.retrofit.entities.users

import com.squareup.moshi.Json

data class UpdatePasswordRequestEntity(
    @Json(name = "old_password") val oldPassword: String,
    @Json(name = "new_password") val newPassword: String
)