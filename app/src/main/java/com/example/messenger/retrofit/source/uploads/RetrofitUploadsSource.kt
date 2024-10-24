package com.example.messenger.retrofit.source.uploads

import android.content.Context
import android.util.Log
import com.example.messenger.retrofit.api.UploadsApi
import com.example.messenger.retrofit.source.base.BaseRetrofitSource
import com.example.messenger.retrofit.source.base.RetrofitConfig
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class RetrofitUploadsSource(
    config: RetrofitConfig
) : BaseRetrofitSource(config), UploadsSource {

    private val uploadsApi = retrofit.create(UploadsApi::class.java)
    override suspend fun uploadPhoto(dialogId: Int, photo: File): String = wrapRetrofitExceptions {
        val requestBody = MultipartBody.Part.createFormData(
            "file",
            photo.name,
            photo.asRequestBody("image/*".toMediaTypeOrNull())
        )
        uploadsApi.uploadPhoto(dialogId, requestBody).filename
    }

    override suspend fun uploadFile(dialogId: Int, file: File): String = wrapRetrofitExceptions {
        val requestBody = MultipartBody.Part.createFormData(
            "file",
            file.name,
            file.asRequestBody("file/*".toMediaTypeOrNull())
        )
        uploadsApi.uploadFile(dialogId, requestBody).filename
    }

    override suspend fun uploadAudio(dialogId: Int, audio: File): String = wrapRetrofitExceptions {
        val requestBody = MultipartBody.Part.createFormData(
            "file",
            audio.name,
            audio.asRequestBody("audio/*".toMediaTypeOrNull())
        )
        uploadsApi.uploadAudio(dialogId, requestBody).filename
    }

    override suspend fun uploadAvatar(avatar: File): String = wrapRetrofitExceptions {
        val requestBody = MultipartBody.Part.createFormData(
            "file",
            avatar.name,
            avatar.asRequestBody("image/*".toMediaTypeOrNull())
        )
        uploadsApi.uploadAvatar(requestBody).filename
    }

    override suspend fun downloadFile(context: Context, folder: String, dialogId: Int, filename: String) : String = wrapRetrofitExceptions {
        try {
            val responseBody = uploadsApi.downloadFile(folder, dialogId, filename)

            val fileTypeDir = when (folder) {
                "photos" -> "photos"
                "audio" -> "audio"
                "files" -> "files"
                else -> ""
            }

            val directory = File(context.filesDir, fileTypeDir)
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val file = File(directory, filename)

            // Запись файла на диск
            file.outputStream().use { outputStream ->
                responseBody.byteStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            Log.d("testDownload","File downloaded to ${file.absolutePath}")
            return@wrapRetrofitExceptions file.absolutePath

        } catch (e: Exception) {
            // Обработка ошибок
            Log.d("testDownload","Error downloading file: ${e.message}")
            return@wrapRetrofitExceptions "Error: ${e.message}"
        }
    }

    override suspend fun downloadAvatar(context: Context, filename: String): String = wrapRetrofitExceptions {
        try {
            val responseBody = uploadsApi.downloadAvatar(filename)
            val directory = File(context.filesDir, "avatars")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val file = File(directory, filename)
            // Запись файла на диск
            file.outputStream().use { outputStream ->
                responseBody.byteStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Log.d("testDownloadAvatar","File downloaded to ${file.absolutePath}")
            return@wrapRetrofitExceptions file.absolutePath
        } catch (e: Exception) {
            Log.d("testDownloadAvatar","Error downloading file: ${e.message}")
            return@wrapRetrofitExceptions "Error: ${e.message}"
        }
    }

    override suspend fun deleteFile(folder: String, dialogId: Int, filename: String): String = wrapRetrofitExceptions {
        uploadsApi.deleteFile(folder, dialogId, filename).message
    }

    override suspend fun getMediaPreviews(dialogId: Int, page: Int): List<String> = wrapRetrofitExceptions {
        val response = uploadsApi.getMediaPreviews(dialogId, page)
        return@wrapRetrofitExceptions response.map { it.filename }
    }

    override suspend fun getMediaPreview(context: Context, dialogId: Int, filename: String): String = wrapRetrofitExceptions {
        try {
            val responseBody = uploadsApi.getMediaPreview(dialogId, filename)
            val directory = File(context.filesDir, "previews")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val file = File(directory, filename)
            // Запись файла на диск
            file.outputStream().use { outputStream ->
                responseBody.byteStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Log.d("testDownloadPreview","File downloaded to ${file.absolutePath}")
            return@wrapRetrofitExceptions file.absolutePath
        } catch (e: Exception) {
            Log.d("testDownloadPreview","Error downloading file: ${e.message}")
            return@wrapRetrofitExceptions "Error: ${e.message}"
        }
    }

    override suspend fun getFiles(dialogId: Int, page: Int): List<String> = wrapRetrofitExceptions {
        val response = uploadsApi.getFiles(dialogId, page)
        return@wrapRetrofitExceptions response.map { it.filename }
    }

    override suspend fun getAudios(dialogId: Int, page: Int): List<String> = wrapRetrofitExceptions {
        val response = uploadsApi.getAudios(dialogId, page)
        return@wrapRetrofitExceptions response.map { it.filename }
    }
}