package com.example.messenger.retrofit.api

import com.example.messenger.retrofit.entities.ResponseEntityMessageAnswer
import com.example.messenger.retrofit.entities.groups.AddUserToGroupRequestEntity
import com.example.messenger.retrofit.entities.groups.CreateGroupRequestEntity
import com.example.messenger.retrofit.entities.groups.GetGroupMembersResponseEntity
import com.example.messenger.retrofit.entities.groups.GetGroupMessagesResponseEntity
import com.example.messenger.retrofit.entities.groups.SendGroupMessageRequestEntity
import com.example.messenger.retrofit.entities.groups.UpdateGroupAvatarRequestEntity
import com.example.messenger.retrofit.entities.messages.DeleteMessagesRequestEntity
import com.example.messenger.retrofit.entities.messages.GetDialogSettingsResponseEntity
import com.example.messenger.retrofit.entities.messages.GetUsersResponseEntity
import com.example.messenger.retrofit.entities.messages.UpdateAutoDeleteIntervalRequestEntity
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface GroupsApi {
    @POST("groups")
    suspend fun createGroup(
        @Body createGroupRequestEntity: CreateGroupRequestEntity
    ) : ResponseEntityMessageAnswer

    @POST("group/{group_id}/messages")
    suspend fun sendGroupMessage(
        @Path("group_id") groupId: Int,
        @Body sendGroupMessageRequestEntity: SendGroupMessageRequestEntity
    ) : ResponseEntityMessageAnswer

    @GET("group/messages")
    suspend fun getGroupMessages(
        @Header("group_id") groupId: Int,
        @Query("start") start: Int,
        @Query("end") end: Int
    ) : GetGroupMessagesResponseEntity

    @PUT("group_messages/{group_message_id}")
    suspend fun editGroupMessage(
        @Path("group_message_id") groupMessageId: Int,
        @Body sendGroupMessageRequestEntity: SendGroupMessageRequestEntity
    ) : ResponseEntityMessageAnswer

    @DELETE("group/messages")
    suspend fun deleteGroupMessages(
        @Body deleteMessagesRequestEntity: DeleteMessagesRequestEntity
    ) : ResponseEntityMessageAnswer

    @DELETE("groups/{group_id}")
    suspend fun deleteGroup(
        @Path("group_id") groupId: Int
    ) : ResponseEntityMessageAnswer

    @PUT("groups/{group_id}")
    suspend fun editGroupName(
        @Path("group_id") groupId: Int,
        @Body createGroupRequestEntity: CreateGroupRequestEntity
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
    ) : GetGroupMembersResponseEntity

    @PUT("groups/{group_id}/avatar")
    suspend fun updateGroupAvatar(
        @Path("group_id") groupId: Int,
        @Body updateGroupAvatarRequestEntity: UpdateGroupAvatarRequestEntity
    ) : ResponseEntityMessageAnswer

    @PUT("group_messages/read")
    suspend fun markGroupMessagesAsRead(
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
}