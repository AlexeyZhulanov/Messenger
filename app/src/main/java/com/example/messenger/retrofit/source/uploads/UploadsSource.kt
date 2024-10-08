package com.example.messenger.retrofit.source.uploads

import android.content.Context
import java.io.File

interface UploadsSource {

    suspend fun uploadPhoto(dialogId: Int, photo: File) : String

    suspend fun uploadFile(dialogId: Int, file: File) : String

    suspend fun uploadAudio(dialogId: Int, audio: File) : String

    suspend fun uploadAvatar(avatar: File) : String

    suspend fun downloadFile(context: Context, folder: String, dialogId: Int, filename: String) : String

    suspend fun downloadAvatar(context: Context, filename: String) : String

    suspend fun deleteFile(folder: String, dialogId: Int, filename: String) : String

}