package com.example.messenger.picker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.messenger.R
import com.luck.picture.lib.config.PictureMimeType
import com.luck.picture.lib.engine.CropFileEngine
import com.luck.picture.lib.entity.LocalMedia
import com.luck.picture.lib.interfaces.OnMediaEditInterceptListener
import com.luck.picture.lib.style.PictureSelectorStyle
import com.luck.picture.lib.style.SelectMainStyle
import com.luck.picture.lib.style.TitleBarStyle
import com.luck.picture.lib.utils.StyleUtils
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCropImageEngine
import java.io.File
import javax.annotation.Nullable


internal class ImageFileCropEngine(private val selectorStyle: PictureSelectorStyle, private val isCircle: Boolean, private val isFreeStyleCrop : Boolean) : CropFileEngine {
    override fun onStartCrop(
        fragment: Fragment?,
        srcUri: Uri?,
        destinationUri: Uri?,
        dataSource: ArrayList<String>?,
        requestCode: Int
    ) {
        val options: UCrop.Options = buildOptions(selectorStyle, fragment, isCircle, isFreeStyleCrop)
        val uCrop = UCrop.of(srcUri!!, destinationUri!!, dataSource)
        uCrop.withOptions(options)
        uCrop.setImageEngine(object : UCropImageEngine {
            override fun loadImage(context: Context, url: String, imageView: ImageView) {
                if (!ImageLoaderUtils.assertValidRequest(context)) {
                    return
                }
                Glide.with(context).load(url).override(180, 180).into(imageView)
            }

            override fun loadImage(
                context: Context,
                url: Uri,
                maxWidth: Int,
                maxHeight: Int,
                call: UCropImageEngine.OnCallbackListener<Bitmap>
            ) {
                Glide.with(context).asBitmap().load(url).override(maxWidth, maxHeight)
                    .into(object : CustomTarget<Bitmap?>() {
                        override fun onResourceReady(
                            resource: Bitmap,
                            @Nullable transition: Transition<in Bitmap?>?
                        ) {
                            if (call != null) {
                                call.onCall(resource)
                            }
                        }

                        override fun onLoadCleared(@Nullable placeholder: Drawable?) {
                            if (call != null) {
                                call.onCall(null)
                            }
                        }
                    })
            }
        })
        uCrop.start(fragment!!.requireActivity(), fragment, requestCode)
    }
}

private fun buildOptions(selectorStyle: PictureSelectorStyle, fragment: Fragment?, isCircle: Boolean, isFreeStyleCrop : Boolean): UCrop.Options {
    val options = UCrop.Options()
    options.setHideBottomControls(false)
    if(isFreeStyleCrop) options.setFreeStyleCropEnabled(true) // free crop - you can change XtoY
    else options.setFreeStyleCropEnabled(false)
    if(isCircle) {
        options.setCircleDimmedLayer(true) // circle photo for avatar
        options.setShowCropFrame(false)
        options.setShowCropGrid(false)
    }
    else {
        options.setCircleDimmedLayer(false)
        options.setShowCropFrame(true)
        options.setShowCropGrid(true)
    }
    options.setShowCropFrame(true)
    options.setShowCropGrid(true)
    options.setCircleDimmedLayer(false)
    options.withAspectRatio(-1f, -1f) // crop default, 1to1 : 1 1, 3to4 : 3 4 ....
    options.setCropOutputPathDir(getSandboxPath(fragment!!))
    options.isCropDragSmoothToCenter(false)
    options.isForbidSkipMultipleCrop(true)
    options.setMaxScaleMultiplier(100f)
    if (selectorStyle != null && selectorStyle.getSelectMainStyle().getStatusBarColor() !== 0) {
        val mainStyle: SelectMainStyle = selectorStyle.getSelectMainStyle()
        val isDarkStatusBarBlack = mainStyle.isDarkStatusBarBlack
        val statusBarColor = mainStyle.statusBarColor
        options.isDarkStatusBarBlack(isDarkStatusBarBlack)
        if (StyleUtils.checkStyleValidity(statusBarColor)) {
            options.setStatusBarColor(statusBarColor)
            options.setToolbarColor(statusBarColor)
        } else {
            options.setStatusBarColor(ContextCompat.getColor(fragment.requireContext(), R.color.ps_color_grey))
            options.setToolbarColor(ContextCompat.getColor(fragment.requireContext(), R.color.ps_color_grey))
        }
        val titleBarStyle: TitleBarStyle = selectorStyle.titleBarStyle
        if (StyleUtils.checkStyleValidity(titleBarStyle.titleTextColor)) {
            options.setToolbarWidgetColor(titleBarStyle.titleTextColor)
        } else {
            options.setToolbarWidgetColor(
                ContextCompat.getColor(
                    fragment.requireContext(),
                    R.color.ps_color_white
                )
            )
        }
    } else {
        options.setStatusBarColor(ContextCompat.getColor(fragment.requireContext(), R.color.ps_color_grey))
        options.setToolbarColor(ContextCompat.getColor(fragment.requireContext(), R.color.ps_color_grey))
        options.setToolbarWidgetColor(ContextCompat.getColor(fragment.requireContext(), R.color.ps_color_white))
    }
    return options
}

private fun getSandboxPath(fragment: Fragment): String {
    val externalFilesDir: File = fragment.requireContext().getExternalFilesDir("")!!
    val customFile = File(externalFilesDir.absolutePath, "Sandbox")
    if (!customFile.exists()) {
        customFile.mkdirs()
    }
    return customFile.absolutePath + File.separator
}

fun getMediaEditInterceptListener(fragment: Fragment, selectorStyle: PictureSelectorStyle, isCircle: Boolean, isFreeStyleCrop : Boolean): OnMediaEditInterceptListener {
    return MeOnMediaEditInterceptListener(getSandboxPath(fragment), buildOptions(selectorStyle, fragment, isCircle, isFreeStyleCrop))
}

private class MeOnMediaEditInterceptListener(
    private val outputCropPath: String,
    private val options: UCrop.Options
) : OnMediaEditInterceptListener {
    override fun onStartMediaEdit(
        fragment: Fragment,
        currentLocalMedia: LocalMedia,
        requestCode: Int
    ) {
        val currentEditPath = currentLocalMedia.availablePath
        val inputUri = if (PictureMimeType.isContent(currentEditPath)
        ) Uri.parse(currentEditPath) else Uri.fromFile(File(currentEditPath))
        val destinationUri = Uri.fromFile(
            File(outputCropPath, DateUtils.getCreateFileName("CROP_") + ".jpeg")
        )
        val uCrop = UCrop.of<Any>(inputUri, destinationUri)
        options.setHideBottomControls(false)
        uCrop.withOptions(options)
        uCrop.setImageEngine(object : UCropImageEngine {
            override fun loadImage(context: Context, url: String, imageView: ImageView) {
                if (!ImageLoaderUtils.assertValidRequest(context)) {
                    return
                }
                Glide.with(context).load(url).override(180, 180).into(imageView)
            }

            override fun loadImage(
                context: Context,
                url: Uri,
                maxWidth: Int,
                maxHeight: Int,
                call: UCropImageEngine.OnCallbackListener<Bitmap>
            ) {
                Glide.with(context).asBitmap().load(url).override(maxWidth, maxHeight)
                    .into(object : CustomTarget<Bitmap?>() {
                        override fun onResourceReady(
                            resource: Bitmap,
                            @Nullable transition: Transition<in Bitmap?>?
                        ) {
                            if (call != null) {
                                call.onCall(resource)
                            }
                        }

                        override fun onLoadCleared(@Nullable placeholder: Drawable?) {
                            if (call != null) {
                                call.onCall(null)
                            }
                        }
                    })
            }
        })
        uCrop.startEdit(fragment.requireActivity(), fragment, requestCode)
    }
}