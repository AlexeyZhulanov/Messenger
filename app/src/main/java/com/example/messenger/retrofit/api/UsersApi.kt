package com.example.messenger.retrofit.api

import com.example.messenger.retrofit.entities.users.LoginRequestEntity
import com.example.messenger.retrofit.entities.users.LoginResponseEntity
import com.example.messenger.retrofit.entities.users.RegisterRequestEntity
import com.example.messenger.retrofit.entities.ResponseEntityMessageAnswer
import com.example.messenger.retrofit.entities.users.GetLastSessionResponseEntity
import com.example.messenger.retrofit.entities.users.GetPermissionResponseEntity
import com.example.messenger.retrofit.entities.users.GetUserResponseEntity
import com.example.messenger.retrofit.entities.users.GetVacationResponseEntity
import com.example.messenger.retrofit.entities.users.UpdatePasswordRequestEntity
import com.example.messenger.retrofit.entities.users.UpdateProfileRequestEntity
import com.example.messenger.retrofit.entities.users.UpdateTokenRequestEntity
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface UsersApi {
    @POST("register")
    suspend fun register(@Body registerRequestEntity: RegisterRequestEntity): ResponseEntityMessageAnswer

    @POST("login")
    suspend fun login(@Body loginRequestEntity: LoginRequestEntity) : LoginResponseEntity

    @PUT("update_profile")
    suspend fun updateProfile(
        @Body updateProfileRequestEntity: UpdateProfileRequestEntity) : ResponseEntityMessageAnswer

    @PUT("update_password")
    suspend fun updatePassword(
        @Body updatePasswordRequestEntity: UpdatePasswordRequestEntity) : ResponseEntityMessageAnswer

    @PUT("update_last_session")
    suspend fun updateLastSession() : ResponseEntityMessageAnswer

    @GET("last_session/{user_id}")
    suspend fun getLastSession(
        @Path("user_id") userId: Int
    ) : GetLastSessionResponseEntity

    @GET("user/{user_id}")
    suspend fun getUser(
        @Path("user_id") userId: Int
    ) : GetUserResponseEntity

    @GET("get_vacation")
    suspend fun getVacation() : GetVacationResponseEntity

    @GET("get_permission")
    suspend fun getPermission() : GetPermissionResponseEntity

    @POST("save_fcm_token")
    suspend fun saveFCMToken(@Body updateTokenRequestEntity: UpdateTokenRequestEntity) : ResponseEntityMessageAnswer

    @DELETE("fcm_token")
    suspend fun deleteFCMToken() : ResponseEntityMessageAnswer
}