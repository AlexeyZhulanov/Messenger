package com.example.messenger.retrofit.api

import com.example.messenger.retrofit.entities.ResponseEntityMessageAnswer
import com.example.messenger.retrofit.entities.messages.ConversationEntity
import com.example.messenger.retrofit.entities.messages.DeleteMessagesRequestEntity
import com.example.messenger.retrofit.entities.messages.DialogCreateRequestEntity
import com.example.messenger.retrofit.entities.messages.DialogCreateResponseEntity
import com.example.messenger.retrofit.entities.messages.GetUsersResponseEntity
import com.example.messenger.retrofit.entities.messages.Message
import com.example.messenger.retrofit.entities.messages.SendMessageRequestEntity
import com.example.messenger.retrofit.entities.messages.UpdateAutoDeleteIntervalRequestEntity
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface MessagesApi {
    @POST("dialogs")
    suspend fun createDialog(
        @Body dialogCreateRequestEntity: DialogCreateRequestEntity) : DialogCreateResponseEntity

    @POST("messages/{id_dialog}")
    suspend fun sendMessage(
        @Path("id_dialog") idDialog: Int,
        @Body sendMessageRequestEntity: SendMessageRequestEntity) : ResponseEntityMessageAnswer

    @GET("messages/{id_dialog}")
    suspend fun getMessages(
        @Path("id_dialog") idDialog: Int,
        @Query("page") pageIndex: Int,
        @Query("size") pageSize: Int
        ) : List<Message>

    @GET("message/{message_id}")
    suspend fun findMessage(
        @Path("message_id") messageId: Int,
        @Query("id_dialog") idDialog: Int
    ) : Message

    @PUT("messages/{message_id}")
    suspend fun editMessage(
        @Path("message_id") messageId: Int,
        @Query("id_dialog") idDialog: Int,
        @Body sendMessageRequestEntity: SendMessageRequestEntity
    ) : ResponseEntityMessageAnswer

    @HTTP(method = "DELETE", path = "messages/{id_dialog}", hasBody = true)
    suspend fun deleteMessages(
        @Path("id_dialog") idDialog: Int,
        @Body deleteMessagesRequestEntity: DeleteMessagesRequestEntity
    ) : ResponseEntityMessageAnswer

    @DELETE("dialogs/{dialog_id}")
    suspend fun deleteDialog(
        @Path("dialog_id") dialogId: Int
    ) : ResponseEntityMessageAnswer

    @GET("users")
    suspend fun getUsers() : GetUsersResponseEntity

    @PUT("messages/{id_dialog}/read")
    suspend fun markMessagesAsRead(
        @Path("id_dialog") idDialog: Int,
        @Body deleteMessagesRequestEntity: DeleteMessagesRequestEntity
    ) : ResponseEntityMessageAnswer

    @GET("dialogs/{dialog_id}/messages/search")
    suspend fun searchMessagesInDialog(
        @Path("dialog_id") dialogId: Int
    ) : List<Message>

    @GET("conversations")
    suspend fun getConversations() : List<ConversationEntity>

    @PUT("dialogs/{dialog_id}/toggle_can_delete")
    suspend fun toggleDialogCanDelete(
        @Path("dialog_id") dialogId: Int ) : ResponseEntityMessageAnswer

    @PUT("dialogs/{dialog_id}/update_auto_delete_interval")
    suspend fun updateAutoDeleteInterval(
        @Path("dialog_id") dialogId: Int,
        @Body updateAutoDeleteIntervalRequestEntity: UpdateAutoDeleteIntervalRequestEntity
    ) : ResponseEntityMessageAnswer

    @DELETE("dialogs/{dialog_id}/delete_messages")
    suspend fun deleteDialogMessages(
        @Path("dialog_id") dialogId: Int,
    ) : ResponseEntityMessageAnswer

}