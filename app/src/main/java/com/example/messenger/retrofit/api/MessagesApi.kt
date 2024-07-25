package com.example.messenger.retrofit.api

import com.example.messenger.retrofit.entities.ResponseEntityMessageAnswer
import com.example.messenger.retrofit.entities.messages.DialogCreateRequestEntity
import com.example.messenger.retrofit.entities.messages.GetMessagesRequestEntity
import com.example.messenger.retrofit.entities.messages.GetMessagesResponseEntity
import com.example.messenger.retrofit.entities.messages.SendMessageRequestEntity
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface MessagesApi {
    @POST("dialogs")
    suspend fun createDialog(@Body dialogCreateRequestEntity: DialogCreateRequestEntity) : ResponseEntityMessageAnswer

    @POST("messages")
    suspend fun sendMessage(@Body sendMessageRequestEntity: SendMessageRequestEntity) : ResponseEntityMessageAnswer

    @GET("messages")
    suspend fun getMessages(@Body getMessagesRequestEntity: GetMessagesRequestEntity) : GetMessagesResponseEntity

    // todo add more methods
}