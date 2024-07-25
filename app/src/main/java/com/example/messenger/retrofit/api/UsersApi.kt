package com.example.messenger.retrofit.api

import com.example.messenger.retrofit.entities.users.LoginRequestEntity
import com.example.messenger.retrofit.entities.users.LoginResponseEntity
import com.example.messenger.retrofit.entities.users.RegisterRequestEntity
import com.example.messenger.retrofit.entities.ResponseEntityMessageAnswer
import com.example.messenger.retrofit.entities.users.UpdatePasswordRequestEntity
import com.example.messenger.retrofit.entities.users.UpdateProfileRequestEntity
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT

interface UsersApi {
    @POST("register")
    suspend fun register(@Body registerRequestEntity: RegisterRequestEntity): ResponseEntityMessageAnswer

    @POST("login")
    suspend fun login(@Body loginRequestEntity: LoginRequestEntity) : LoginResponseEntity

    @PUT("update_profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body updateProfileRequestEntity: UpdateProfileRequestEntity) : ResponseEntityMessageAnswer

    @PUT("update_password")
    suspend fun updatePassword(
        @Header("Authorization") token: String,
        @Body updatePasswordRequestEntity: UpdatePasswordRequestEntity) : ResponseEntityMessageAnswer

}