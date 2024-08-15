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
    override suspend fun uploadPhoto(photo: File): String = wrapRetrofitExceptions {
        val requestBody = MultipartBody.Part.createFormData(
            "file",
            photo.name,
            photo.asRequestBody("image/*".toMediaTypeOrNull())
        )
        uploadsApi.uploadPhoto(requestBody).filename
    }

    override suspend fun uploadFile(file: File): String = wrapRetrofitExceptions {
        val requestBody = MultipartBody.Part.createFormData(
            "file",
            file.name,
            file.asRequestBody("file/*".toMediaTypeOrNull())
        )
        uploadsApi.uploadFile(requestBody).filename
    }

    override suspend fun uploadAudio(audio: File): String = wrapRetrofitExceptions {
        val requestBody = MultipartBody.Part.createFormData(
            "file",
            audio.name,
            audio.asRequestBody("audio/*".toMediaTypeOrNull())
        )
        uploadsApi.uploadAudio(requestBody).filename
    }

    override suspend fun downloadFile(context: Context, folder: String, filename: String) : String = wrapRetrofitExceptions {
        try {
            val responseBody = uploadsApi.downloadFile(folder, filename)

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

    override suspend fun deleteFile(folder: String, filename: String): String = wrapRetrofitExceptions {
        uploadsApi.deleteFile(folder, filename).message
    }
}