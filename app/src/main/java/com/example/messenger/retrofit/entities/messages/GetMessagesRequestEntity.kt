package com.example.messenger.retrofit.entities.messages

class GetMessagesRequestEntity(
    val token: String,
    val idDialog: Long,
    val start: Int,
    val end: Int
)