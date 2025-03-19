package com.example.messenger.picker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.luck.picture.lib.config.PictureMimeType
import com.luck.picture.lib.engine.CompressFileEngine
import com.luck.picture.lib.interfaces.OnKeyValueResultCallbackListener
import top.zibin.luban.CompressionPredicate
import top.zibin.luban.Luban
import top.zibin.luban.OnNewCompressListener
import java.io.File
import java.io.FileOutputStream


class ImageFileCompressEngine : CompressFileEngine {
    override fun onStartCompress(
        context: Context,
        source: ArrayList<Uri>,
        call: OnKeyValueResultCallbackListener
    ) {
        Luban.with(context).load<Uri>(source).setRenameListener { filePath ->
            val indexOf = filePath.lastIndexOf(".")
            val postfix = if (indexOf != -1) filePath.substring(indexOf) else ".jpg"
            DateUtils.getCreateFileName("CMP_") + postfix
        }.filter(CompressionPredicate { path ->
            if (PictureMimeType.isUrlHasImage(path) && !PictureMimeType.isHasHttp(path)) {
                return@CompressionPredicate true
            }
            !PictureMimeType.isUrlHasGif(path)
        }).setCompressListener(object : OnNewCompressListener {
            override fun onStart() {
            }

            override fun onSuccess(source: String, compressFile: File) {
                val cachedFile = copyToAppStorage(context, compressFile)
                reduceQualityAndResize(cachedFile, 85, 1920, 1080)
                if (call != null) {
                    call.onCallback(source, cachedFile.absolutePath)
                }
            }

            override fun onError(source: String, e: Throwable) {
                if (call != null) {
                    call.onCallback(source, null)
                }
            }
        }).launch()
    }

    private fun reduceQualityAndResize(file: File, quality: Int = 85, maxWidth: Int = 1920, maxHeight: Int = 1080) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)

        val (originalWidth, originalHeight) = options.outWidth to options.outHeight

        // Если изображение уже меньше 1920x1080 — просто уменьшаем качество
        if (originalWidth <= maxWidth && originalHeight <= maxHeight) {
            reduceQuality(file, quality)
            return
        }

        // Определяем нужные размеры с сохранением пропорций
        val aspectRatio = originalWidth.toFloat() / originalHeight
        val (newWidth, newHeight) = if (aspectRatio > 1) {
            maxWidth to (maxWidth / aspectRatio).toInt()
        } else {
            (maxHeight * aspectRatio).toInt() to maxHeight
        }

        val resizedBitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?.let { Bitmap.createScaledBitmap(it, newWidth, newHeight, true) }
            ?: return

        FileOutputStream(file).use { out ->
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }

        resizedBitmap.recycle()
    }


    private fun reduceQuality(file: File, quality: Int = 85) {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }

        bitmap.recycle()
    }

    fun copyToAppStorage(context: Context, sourceFile: File): File {
        val destFile = File(context.cacheDir, sourceFile.name)
        sourceFile.inputStream().use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }

}