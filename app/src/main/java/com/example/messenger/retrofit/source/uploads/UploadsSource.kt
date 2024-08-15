package com.example.messenger.retrofit.source.uploads

import android.content.Context
import java.io.File

interface UploadsSource {

    suspend fun uploadPhoto(photo: File) : String

    suspend fun uploadFile(file: File) : String

    suspend fun uploadAudio(audio: File) : String

    suspend fun downloadFile(context: Context, folder: String, filename: String) : String

    suspend fun deleteFile(folder: String, filename: String) : String

}