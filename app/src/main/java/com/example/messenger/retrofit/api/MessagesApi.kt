package com.example.messenger.retrofit.api

import com.example.messenger.retrofit.entities.ResponseEntityMessageAnswer
import com.example.messenger.retrofit.entities.messages.AddKeyToDialogRequestEntity
import com.example.messenger.retrofit.entities.messages.DeleteMessagesRequestEntity
import com.example.messenger.retrofit.entities.messages.DialogCreateRequestEntity
import com.example.messenger.retrofit.entities.messages.GetConversationsResponseEntity
import com.example.messenger.retrofit.entities.messages.GetMessagesResponseEntity
import com.example.messenger.retrofit.entities.messages.GetUsersResponseEntity
import com.example.messenger.retrofit.entities.messages.SendMessageRequestEntity
import com.example.messenger.retrofit.entities.messages.UpdateAutoDeleteIntervalRequestEntity
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface MessagesApi {
    @POST("dialogs")
    suspend fun createDialog(
        @Header("Authorization") token: String,
        @Body dialogCreateRequestEntity: DialogCreateRequestEntity) : ResponseEntityMessageAnswer

    @POST("messages")
    suspend fun sendMessage(
        @Header("Authorization") token: String,
        @Body sendMessageRequestEntity: SendMessageRequestEntity) : ResponseEntityMessageAnswer

    @GET("messages")
    suspend fun getMessages(
        @Header("Authorization") token: String,
        @Header("id_dialog") idDialog: Int,
        @Query("start") start: Int,
        @Query("end") end: Int
        ) : GetMessagesResponseEntity

    @PUT("dialogs/{dialog_id}/key")
    suspend fun addKeyToDialog(
        @Path("dialog_id") dialogId: Int,
        @Header("Authorization") token: String,
        @Body addKeyToDialogRequestEntity: AddKeyToDialogRequestEntity
    ) : ResponseEntityMessageAnswer

    @DELETE("dialogs/{dialog_id}/key")
    suspend fun removeKeyFromDialog(
        @Path("dialog_id") dialogId: Int,
        @Header("Authorization") token: String
    ) : ResponseEntityMessageAnswer

    @PUT("messages/{message_id}")
    suspend fun editMessage(
        @Path("message_id") messageId: Int,
        @Header("Authorization") token: String,
        @Body sendMessageRequestEntity: SendMessageRequestEntity
    ) : ResponseEntityMessageAnswer

    @DELETE("messages")
    suspend fun deleteMessages(
        @Header("Authorization") token: String,
        @Body deleteMessagesRequestEntity: DeleteMessagesRequestEntity
    ) : ResponseEntityMessageAnswer

    @DELETE("dialogs/{dialog_id}")
    suspend fun deleteDialog(
        @Path("dialog_id") dialogId: Int,
        @Header("Authorization") token: String
    ) : ResponseEntityMessageAnswer

    @GET("users")
    suspend fun getUsers(
        @Header("Authorization") token: String
    ) : GetUsersResponseEntity

    @PUT("messages/read")
    suspend fun markMessagesAsRead(
        @Header("Authorization") token: String,
        @Body deleteMessagesRequestEntity: DeleteMessagesRequestEntity
    ) : ResponseEntityMessageAnswer

    @GET("dialogs/{dialog_id}/messages/search")
    suspend fun searchMessagesInDialog(
        @Path("dialog_id") dialogId: Int,
        @Header("Authorization") token: String,
        @Query("q") word: String
    ) : GetMessagesResponseEntity

    @GET("conversations")
    suspend fun getConversations(
        @Header("Authorization") token: String
    ) : GetConversationsResponseEntity

    @PUT("dialogs/{dialog_id}/toggle_can_delete")
    suspend fun toggleDialogCanDelete(
        @Path("dialog_id") dialogId: Int,
        @Header("Authorization") token: String
    ) : ResponseEntityMessageAnswer

    @PUT("dialogs/{dialog_id}/update_auto_delete_interval")
    suspend fun updateAutoDeleteInterval(
        @Path("dialog_id") dialogId: Int,
        @Header("Authorization") token: String,
        @Body updateAutoDeleteIntervalRequestEntity: UpdateAutoDeleteIntervalRequestEntity
    ) : ResponseEntityMessageAnswer

    @DELETE("dialogs/{dialog_id}/delete_messages")
    suspend fun deleteDialogMessages(
        @Path("dialog_id") dialogId: Int,
        @Header("Authorization") token: String
    ) : ResponseEntityMessageAnswer
}