package com.example.messenger.model

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ImageUtils {

    suspend fun createImagePreview(context: Context, file: File, outputFileName: String, maxWidth: Int, maxHeight: Int): File {
        return withContext(Dispatchers.IO) {

            val concatFileName = "preview_$outputFileName.jpg"
            val previewFile = File(context.cacheDir, concatFileName)

            val bitmap = Glide.with(context)
                .asBitmap()
                .load(file)
                .apply(RequestOptions().override(maxWidth, maxHeight))
                .submit()
                .get() // Блокирующий вызов, поэтому выполняем в IO-диспетчере

            previewFile.outputStream().use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            }

            previewFile
        }
    }

    fun createVideoPreview(context: Context, file: File, outputFileName: String, maxWidth: Int, maxHeight: Int): File {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(file.absolutePath)

        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val durationInSeconds = duration?.toLongOrNull()?.div(1000) ?: 0

        val fileExtension = file.extension

        val bitmap = retriever.frameAtTime

        val previewFileName = "preview_${outputFileName}_${durationInSeconds}s:$fileExtension.jpg"
        val previewFile = File(context.cacheDir, previewFileName)

        if (bitmap != null) {
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, maxWidth, maxHeight, true)

            previewFile.outputStream().use { outputStream ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            }
        }

        retriever.release()
        return previewFile
    }
}