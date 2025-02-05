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
        val resizedFiles = source.map { resizeImageBeforeCompress(context, it) }

        Luban.with(context)
            .load(resizedFiles) // Передаём уже уменьшенные файлы
            .setRenameListener { filePath ->
            val indexOf = filePath.lastIndexOf(".")
            val postfix = if (indexOf != -1) filePath.substring(indexOf) else ".jpg"
            DateUtils.getCreateFileName("CMP_") + postfix
        }.filter(CompressionPredicate { path ->
            if (PictureMimeType.isUrlHasImage(path) && !PictureMimeType.isHasHttp(path)) {
                return@CompressionPredicate true
            }
            !PictureMimeType.isUrlHasGif(path)
        }).setCompressListener(object : OnNewCompressListener {
            override fun onStart() {}

            override fun onSuccess(source: String, compressFile: File) {
                call.onCallback(source, compressFile.absolutePath)
            }

            override fun onError(source: String, e: Throwable) {
                call.onCallback(source, null)
            }
        }).launch()
    }

    private fun resizeImageBeforeCompress(context: Context, uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream) ?: return File(uri.path ?: "")

        // Проверяем, нужно ли уменьшать
        val maxWidth = 1080
        if (bitmap.width <= maxWidth) {
            return File(uri.path ?: "") // Если картинка уже маленькая, просто возвращаем файл
        }

        // Вычисляем новую высоту с сохранением пропорций
        val newHeight = (bitmap.height * (maxWidth.toFloat() / bitmap.width)).toInt()
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true)

        // Создаём временный файл
        val file = File(context.cacheDir, "resized_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }

        return file
    }

}