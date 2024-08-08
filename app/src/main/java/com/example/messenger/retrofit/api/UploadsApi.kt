package com.example.messenger.retrofit.api

import com.example.messenger.retrofit.entities.uploads.UploadResponseEntity
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path

interface UploadsApi {
    @Multipart
    @POST("upload/photo")
    suspend fun uploadPhoto(@Part photo: MultipartBody.Part): UploadResponseEntity

    @Multipart
    @POST("upload/file")
    suspend fun uploadFile(@Part file: MultipartBody.Part): UploadResponseEntity

    @Multipart
    @POST("upload/audio")
    suspend fun uploadAudio(@Part audio: MultipartBody.Part): UploadResponseEntity

    @GET("files/{folder}/{filename}")
    suspend fun downloadFile(
        @Path("folder") folder: String,
        @Path("filename") filename: String): ResponseBody

}