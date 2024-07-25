package com.example.messenger.retrofit.entities.messages

data class GetUsersResponseEntity(
    val users: List<User>
)

data class User(
    val id: Int,
    val name: String
)