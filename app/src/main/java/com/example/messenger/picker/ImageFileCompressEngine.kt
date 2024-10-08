package com.example.messenger.picker

import android.content.Context
import android.net.Uri
import com.luck.picture.lib.config.PictureMimeType
import com.luck.picture.lib.engine.CompressFileEngine
import com.luck.picture.lib.interfaces.OnKeyValueResultCallbackListener
import top.zibin.luban.CompressionPredicate
import top.zibin.luban.Luban
import top.zibin.luban.OnNewCompressListener
import java.io.File


class ImageFileCompressEngine : CompressFileEngine {
    override fun onStartCompress(
        context: Context,
        source: ArrayList<Uri>,
        call: OnKeyValueResultCallbackListener
    ) {
        Luban.with(context).load<Uri>(source).ignoreBy(100).setRenameListener { filePath ->
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
                if (call != null) {
                    call.onCallback(source, compressFile.absolutePath)
                }
            }

            override fun onError(source: String, e: Throwable) {
                if (call != null) {
                    call.onCallback(source, null)
                }
            }
        }).launch()
    }
}