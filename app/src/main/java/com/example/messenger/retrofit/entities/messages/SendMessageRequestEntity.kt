package com.example.messenger.retrofit.entities.messages

data class SendMessageRequestEntity(
    val token: String,
    val idDialog: Long,
    val text: String,
    val images: List<String>,
    val file: String,
    val voice: String
)