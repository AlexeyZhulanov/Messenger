package com.example.messenger.retrofit.entities.users

import com.example.messenger.model.User
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GetUserResponseEntity(
    val id: Int,
    val name: String,
    val username: String,
    val avatar: String? = null,
    @Json(name = "public_key") val publicKey: String? = null

) {
    fun toUser() : User = User(id = id, name = name, username = username, avatar = avatar, publicKey = publicKey)
}