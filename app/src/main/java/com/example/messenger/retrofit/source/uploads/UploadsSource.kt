package com.example.messenger.retrofit.source.uploads

import android.content.Context
import java.io.File

interface UploadsSource {

    suspend fun uploadPhoto(dialogId: Int, isGroup: Int, photo: File) : String

    suspend fun uploadFile(dialogId: Int, isGroup: Int, file: File) : String

    suspend fun uploadAudio(dialogId: Int, isGroup: Int, audio: File) : String

    suspend fun uploadAvatar(avatar: File) : String

    suspend fun downloadFile(context: Context, folder: String, dialogId: Int, filename: String,
                             isGroup: Int) : String

    suspend fun downloadAvatar(context: Context, filename: String) : String

    suspend fun deleteFile(folder: String, dialogId: Int, filename: String) : String

    suspend fun getMediaPreviews(isGroup: Int, dialogId: Int, page: Int) : List<String>?

    suspend fun getMediaPreview(context: Context, dialogId: Int, filename: String, isGroup: Int) : String

    suspend fun getFiles(isGroup: Int, dialogId: Int, page: Int) : List<String>?

    suspend fun getAudios(isGroup: Int, dialogId: Int, page: Int) : List<String>?
}