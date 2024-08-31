package com.example.messenger.retrofit.api

import com.example.messenger.retrofit.entities.ResponseEntityMessageAnswer
import com.example.messenger.retrofit.entities.messages.AddKeyToDialogRequestEntity
import com.example.messenger.retrofit.entities.messages.ConversationEntity
import com.example.messenger.retrofit.entities.messages.DeleteMessagesRequestEntity
import com.example.messenger.retrofit.entities.messages.DialogCreateRequestEntity
import com.example.messenger.retrofit.entities.messages.GetDialogSettingsResponseEntity
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
        @Body dialogCreateRequestEntity: DialogCreateRequestEntity) : ResponseEntityMessageAnswer

    @POST("messages")
    suspend fun sendMessage(
        @Query("id_dialog") idDialog: Int,
        @Body sendMessageRequestEntity: SendMessageRequestEntity) : ResponseEntityMessageAnswer

    @GET("messages")
    suspend fun getMessages(
        @Query("id_dialog") idDialog: Int,
        @Query("page") pageIndex: Int,
        @Query("size") pageSize: Int
        ) : List<Message>

    @GET("message/{message_id}")
    suspend fun findMessage(
        @Path("message_id") messageId: Int
    ) : Message

    @PUT("dialogs/{dialog_id}/key")
    suspend fun addKeyToDialog(
        @Path("dialog_id") dialogId: Int,
        @Body addKeyToDialogRequestEntity: AddKeyToDialogRequestEntity
    ) : ResponseEntityMessageAnswer

    @DELETE("dialogs/{dialog_id}/key")
    suspend fun removeKeyFromDialog(
        @Path("dialog_id") dialogId: Int
    ) : ResponseEntityMessageAnswer

    @PUT("messages/{message_id}")
    suspend fun editMessage(
        @Path("message_id") messageId: Int,
        @Body sendMessageRequestEntity: SendMessageRequestEntity
    ) : ResponseEntityMessageAnswer

    @HTTP(method = "DELETE", path = "messages", hasBody = true)
    suspend fun deleteMessages(
        @Body deleteMessagesRequestEntity: DeleteMessagesRequestEntity
    ) : ResponseEntityMessageAnswer

    @DELETE("dialogs/{dialog_id}")
    suspend fun deleteDialog(
        @Path("dialog_id") dialogId: Int,
    ) : ResponseEntityMessageAnswer

    @GET("users")
    suspend fun getUsers() : GetUsersResponseEntity

    @PUT("messages/read")
    suspend fun markMessagesAsRead(
        @Body deleteMessagesRequestEntity: DeleteMessagesRequestEntity
    ) : ResponseEntityMessageAnswer

    @GET("dialogs/{dialog_id}/messages/search")
    suspend fun searchMessagesInDialog(
        @Path("dialog_id") dialogId: Int,
        @Query("q") word: String
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

    @GET("dialog/{dialog_id}/settings")
    suspend fun getDialogSettings(
        @Path("dialog_id") dialogId: Int,
    ) : GetDialogSettingsResponseEntity
}