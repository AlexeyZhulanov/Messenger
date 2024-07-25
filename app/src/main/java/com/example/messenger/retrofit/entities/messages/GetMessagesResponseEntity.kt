package com.example.messenger.retrofit.entities.messages

import com.example.messenger.model.Message

data class GetMessagesResponseEntity(
    val messages: List<Message>
)