package com.example.messenger.retrofit.entities.users

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GetPermissionResponseEntity(
    val permission: Int? = 0
)