package com.example.messenger.retrofit.api

import com.example.messenger.retrofit.entities.ResponseEntityMessageAnswer
import com.example.messenger.retrofit.entities.uploads.UploadPreviewResponseEntity
import com.example.messenger.retrofit.entities.uploads.UploadResponseEntity
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path

interface UploadsApi {
    @Multipart
    @POST("upload/photo/{dialog_id}/{is_group}")
    suspend fun uploadPhoto(
        @Path("dialog_id") dialogId: Int,
        @Path("is_group") isGroup: Int,
        @Part photo: MultipartBody.Part): UploadResponseEntity

    @Multipart
    @POST("upload/file/{dialog_id}/{is_group}")
    suspend fun uploadFile(
        @Path("dialog_id") dialogId: Int,
        @Path("is_group") isGroup: Int,
        @Part file: MultipartBody.Part): UploadResponseEntity

    @Multipart
    @POST("upload/audio/{dialog_id}/{is_group}")
    suspend fun uploadAudio(
        @Path("dialog_id") dialogId: Int,
        @Path("is_group") isGroup: Int,
        @Part audio: MultipartBody.Part): UploadResponseEntity

    @Multipart
    @POST("upload/avatar")
    suspend fun uploadAvatar(@Part avatar: MultipartBody.Part): UploadResponseEntity

    @GET("files/{folder}/{dialog_id}/{filename}/{is_group}")
    suspend fun downloadFile(
        @Path("folder") folder: String,
        @Path("dialog_id") dialogId: Int,
        @Path("filename") filename: String,
        @Path("is_group") isGroup: Int): ResponseBody

    @GET("avatars/{filename}")
    suspend fun downloadAvatar(@Path("filename") filename: String) : ResponseBody

    @DELETE("files/{folder}/{dialog_id}/{filename}")
    suspend fun deleteFile(
        @Path("folder") folder: String,
        @Path("dialog_id") dialogId: Int,
        @Path("filename") filename: String): ResponseEntityMessageAnswer

    @GET("files/{is_group}/{dialog_id}/media/{page}")
    suspend fun getMediaPreviews(
        @Path("is_group") isGroup: Int,
        @Path("dialog_id") dialogId: Int,
        @Path("page") page: Int) : UploadPreviewResponseEntity?

    @GET("files/{is_group}/{dialog_id}/file/{page}")
    suspend fun getFiles(
        @Path("is_group") isGroup: Int,
        @Path("dialog_id") dialogId: Int,
        @Path("page") page: Int) : UploadPreviewResponseEntity?

    @GET("files/{is_group}/{dialog_id}/audio/{page}")
    suspend fun getAudios(
        @Path("is_group") isGroup: Int,
        @Path("dialog_id") dialogId: Int,
        @Path("page") page: Int) : UploadPreviewResponseEntity?

    @GET("media/preview/{dialog_id}/{filename}/{is_group}")
    suspend fun getMediaPreview(
        @Path("dialog_id") dialogId: Int,
        @Path("filename") filename: String,
        @Path("is_group") isGroup: Int
    ) : ResponseBody
}