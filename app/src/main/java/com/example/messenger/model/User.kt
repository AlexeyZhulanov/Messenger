package com.example.messenger.model

import android.os.Parcelable
import com.squareup.moshi.Json
import kotlinx.parcelize.Parcelize

@Parcelize
data class User(
    val id: Int,
    val name: String,
    val username: String,
    val avatar: String? = null,
    val lastSession: Long? = 0,
    @param:Json(name = "public_key") val publicKey: String? = null
) : Parcelable

data class UserShort(
    val id: Int,
    val name: String
)