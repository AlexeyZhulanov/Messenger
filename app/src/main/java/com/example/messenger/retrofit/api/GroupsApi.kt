package com.example.messenger.retrofit.api

import com.example.messenger.retrofit.entities.ResponseEntityMessageAnswer
import com.example.messenger.retrofit.entities.groups.AddUserToGroupRequestEntity
import com.example.messenger.retrofit.entities.groups.GroupCreateRequestEntity
import com.example.messenger.retrofit.entities.groups.GroupCreateResponseEntity
import com.example.messenger.retrofit.entities.groups.UpdateGroupAvatarRequestEntity
import com.example.messenger.retrofit.entities.messages.DeleteMessagesRequestEntity
import com.example.messenger.retrofit.entities.messages.GetDialogSettingsResponseEntity
import com.example.messenger.retrofit.entities.messages.GetUsersResponseEntity
import com.example.messenger.retrofit.entities.messages.Message
import com.example.messenger.retrofit.entities.messages.SendMessageRequestEntity
import com.example.messenger.retrofit.entities.messages.UpdateAutoDeleteIntervalRequestEntity
import com.example.messenger.retrofit.entities.messages.UserEntity
import com.example.messenger.retrofit.entities.users.KeyRequestEntity
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface GroupsApi {
    @POST("groups")
    suspend fun createGroup(
        @Body createGroupRequestEntity: GroupCreateRequestEntity) : GroupCreateResponseEntity

    @POST("group/{group_id}/messages")
    suspend fun sendGroupMessage(
        @Path("group_id") groupId: Int,
        @Body sendGroupMessageRequestEntity: SendMessageRequestEntity
    ) : ResponseEntityMessageAnswer

    @GET("group/messages/{group_id}")
    suspend fun getGroupMessages(
        @Path("group_id") groupId: Int,
        @Query("page") pageIndex: Int,
        @Query("size") pageSize: Int
    ) : List<Message>

    @GET("group/message/{message_id}")
    suspend fun findMessage(
        @Path("message_id") messageId: Int,
        @Query("group_id") groupId: Int
    ) : Message

    @PUT("group_messages/{message_id}")
    suspend fun editGroupMessage(
        @Path("message_id") messageId: Int,
        @Query("group_id") groupId: Int,
        @Body sendGroupMessageRequestEntity: SendMessageRequestEntity
    ) : ResponseEntityMessageAnswer

    @HTTP(method = "DELETE", path = "group/messages/{group_id}", hasBody = true)
    suspend fun deleteGroupMessages(
        @Path("group_id") groupId: Int,
        @Body deleteMessagesRequestEntity: DeleteMessagesRequestEntity
    ) : ResponseEntityMessageAnswer

    @DELETE("groups/{group_id}")
    suspend fun deleteGroup(
        @Path("group_id") groupId: Int
    ) : ResponseEntityMessageAnswer

    @PUT("groups/{group_id}")
    suspend fun editGroupName(
        @Path("group_id") groupId: Int,
        @Body keyRequestEntity: KeyRequestEntity
    ) : ResponseEntityMessageAnswer

    @POST("groups/{group_id}/members")
    suspend fun addUserToGroup(
        @Path("group_id") groupId: Int,
        @Body addUserToGroupRequestEntity: AddUserToGroupRequestEntity
    ) : ResponseEntityMessageAnswer

    @DELETE("groups/{group_id}/members/{user_id}")
    suspend fun deleteUserFromGroup(
        @Path("group_id") groupId: Int,
        @Path("user_id") userId: Int,
    ) : ResponseEntityMessageAnswer

    @GET("groups/{group_id}/available_users")
    suspend fun getAvailableUsersForGroup(
        @Path("group_id") groupId: Int
    ) : GetUsersResponseEntity

    @GET("groups/{group_id}/members")
    suspend fun getGroupMembers(
        @Path("group_id") groupId: Int
    ) : List<UserEntity>

    @PUT("groups/{group_id}/avatar")
    suspend fun updateGroupAvatar(
        @Path("group_id") groupId: Int,
        @Body updateGroupAvatarRequestEntity: UpdateGroupAvatarRequestEntity
    ) : ResponseEntityMessageAnswer

    @PUT("group_messages/{group_id}/read")
    suspend fun markGroupMessagesAsRead(
        @Path("group_id") groupId: Int,
        @Body deleteMessagesRequestEntity: DeleteMessagesRequestEntity
    ) : ResponseEntityMessageAnswer

    @PUT("groups/{group_id}/toggle_can_delete")
    suspend fun toggleGroupCanDelete(
        @Path("group_id") groupId: Int
    ) : ResponseEntityMessageAnswer

    @PUT("groups/{group_id}/update_auto_delete_interval")
    suspend fun updateGroupAutoDeleteInterval(
        @Path("group_id") groupId: Int,
        @Body updateAutoDeleteIntervalRequestEntity: UpdateAutoDeleteIntervalRequestEntity
    ) : ResponseEntityMessageAnswer

    @DELETE("groups/{group_id}/delete_messages")
    suspend fun deleteGroupMessagesAll(
        @Path("group_id") groupId: Int
    ) : ResponseEntityMessageAnswer

    @GET("group/{group_id}/settings")
    suspend fun getGroupSettings(
        @Path("group_id") groupId: Int
    ) : GetDialogSettingsResponseEntity

    @GET("groups/{group_id}/messages/search")
    suspend fun searchMessagesInGroup(
        @Path("group_id") groupId: Int
    ) : List<Message>

}