package com.example.messenger.picker

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.messenger.MessageFragment
import com.example.messenger.R
import com.luck.picture.lib.basic.PictureSelector
import com.luck.picture.lib.config.InjectResourceSource
import com.luck.picture.lib.config.SelectMimeType
import com.luck.picture.lib.entity.LocalMedia
import com.luck.picture.lib.interfaces.OnInjectLayoutResourceListener
import com.luck.picture.lib.interfaces.OnResultCallbackListener
import com.luck.picture.lib.style.BottomNavBarStyle
import com.luck.picture.lib.style.PictureSelectorStyle
import com.luck.picture.lib.style.SelectMainStyle
import com.luck.picture.lib.style.TitleBarStyle
import com.luck.picture.lib.utils.DensityUtil
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.ArrayList
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


class FilePickerManager(private val fragment: MessageFragment) {

    val selectorStyle = PictureSelectorStyle()

    init {
        val numberSelectMainStyle = SelectMainStyle()
        initStyle(numberSelectMainStyle)
    }
    private fun initStyle(numberSelectMainStyle: SelectMainStyle) {
        numberSelectMainStyle.isSelectNumberStyle = true
        numberSelectMainStyle.isPreviewSelectNumberStyle = false
        numberSelectMainStyle.isPreviewDisplaySelectGallery = true
        numberSelectMainStyle.selectBackground = R.drawable.ps_default_num_selector
        numberSelectMainStyle.previewSelectBackground = R.drawable.ps_preview_checkbox_selector
        numberSelectMainStyle.selectNormalBackgroundResources = R.drawable.ps_select_complete_normal_bg
        numberSelectMainStyle.selectNormalTextColor = ContextCompat.getColor(
            fragment.requireContext(),
            R.color.ps_color_53575e
        )
        numberSelectMainStyle.setSelectNormalText(R.string.ps_send)
        numberSelectMainStyle.adapterPreviewGalleryBackgroundResource = R.drawable.ps_preview_gallery_bg
        numberSelectMainStyle.adapterPreviewGalleryItemSize = DensityUtil.dip2px(
            fragment.requireContext(),
            52f
        )
        numberSelectMainStyle.setPreviewSelectText(R.string.ps_select)
        numberSelectMainStyle.previewSelectTextSize = 14
        numberSelectMainStyle.previewSelectTextColor = ContextCompat.getColor(
            fragment.requireContext(),
            R.color.ps_color_white
        )
        numberSelectMainStyle.previewSelectMarginRight = DensityUtil.dip2px(fragment.requireContext(), 6F);
        numberSelectMainStyle.selectBackgroundResources = R.drawable.ps_select_complete_bg
        numberSelectMainStyle.setSelectText(R.string.ps_send_num)
        numberSelectMainStyle.selectTextColor = ContextCompat.getColor(
            fragment.requireContext(),
            R.color.ps_color_white
        )
        numberSelectMainStyle.mainListBackgroundColor = ContextCompat.getColor(
            fragment.requireContext(),
            R.color.ps_color_black
        )
        numberSelectMainStyle.isCompleteSelectRelativeTop = true
        numberSelectMainStyle.isPreviewSelectRelativeBottom = true
        numberSelectMainStyle.isAdapterItemIncludeEdge = false

        // TitleBar
        val numberTitleBarStyle = TitleBarStyle()
        numberTitleBarStyle.isHideCancelButton = true
        numberTitleBarStyle.isAlbumTitleRelativeLeft = true
        numberTitleBarStyle.titleAlbumBackgroundResource = R.drawable.ps_album_bg
        numberTitleBarStyle.titleDrawableRightResource = R.drawable.ps_ic_grey_arrow
        numberTitleBarStyle.previewTitleLeftBackResource = R.drawable.ps_ic_normal_back

        // NavBar
        val numberBottomNavBarStyle = BottomNavBarStyle()
        numberBottomNavBarStyle.bottomPreviewNarBarBackgroundColor = ContextCompat.getColor(
            fragment.requireContext(),
            R.color.ps_color_half_grey
        );
        numberBottomNavBarStyle.setBottomPreviewNormalText(R.string.ps_preview)
        numberBottomNavBarStyle.bottomPreviewNormalTextColor = ContextCompat.getColor(
            fragment.requireContext(),
            R.color.ps_color_9b
        )
        numberBottomNavBarStyle.bottomPreviewNormalTextSize = 16
        numberBottomNavBarStyle.isCompleteCountTips = false
        numberBottomNavBarStyle.setBottomPreviewSelectText(R.string.ps_preview_num)
        numberBottomNavBarStyle.bottomPreviewSelectTextColor = ContextCompat.getColor(
            fragment.requireContext(),
            R.color.ps_color_white
        )


        selectorStyle.titleBarStyle = numberTitleBarStyle
        selectorStyle.bottomBarStyle = numberBottomNavBarStyle
        selectorStyle.selectMainStyle = numberSelectMainStyle
    }

    suspend fun openFilePicker(isCircle: Boolean, isFreeStyleCrop: Boolean, data: ArrayList<LocalMedia>) : ArrayList<LocalMedia> = suspendCancellableCoroutine { continuation ->
        PictureSelector.create(fragment)
            .openGallery(SelectMimeType.ofAll())
            .setImageEngine(GlideEngine.createGlideEngine())
            .setVideoPlayerEngine(ExoPlayerEngine())
            //.setCropEngine(ImageFileCropEngine(selectorStyle, isCircle, isFreeStyleCrop)) // Обязательное редактирование всех фото
            .setCompressEngine(ImageFileCompressEngine())
            .setEditMediaInterceptListener(getMediaEditInterceptListener(fragment, selectorStyle, isCircle, isFreeStyleCrop))
            .isAutoVideoPlay(false)
            .isLoopAutoVideoPlay(false)
            .isVideoPauseResumePlay(true)
            .setSelectorUIStyle(selectorStyle)
            .setSelectionMode(2)
            .setMaxSelectNum(10)
            .setMaxVideoSelectNum(10)
            .isWithSelectVideoImage(true)
            .isSelectZoomAnim(true)
            .setImageSpanCount(4)
            .isPreviewImage(true)
            .isPreviewZoomEffect(true)
            .isPageSyncAlbumCount(true)
            .setRecyclerAnimationMode(1)
            .setInjectLayoutResourceListener(object: OnInjectLayoutResourceListener {
                override fun getLayoutResourceId(context: Context?, resourceSource: Int): Int {
                    @Suppress("DEPRECATED_IDENTITY_EQUALS")
                    return if (resourceSource === InjectResourceSource.PREVIEW_LAYOUT_RESOURCE
                    ) R.layout.ps_custom_fragment_preview
                    else InjectResourceSource.DEFAULT_LAYOUT_RESOURCE
                }
            })
            .setSelectedData(data)
            .forResult(object: OnResultCallbackListener<LocalMedia> {
                override fun onResult(result: ArrayList<LocalMedia>?) {
                    if(result != null) {
                        continuation.resume(result)
                    } else continuation.resumeWithException(CancellationException("Empty array"))
                }

                override fun onCancel() {
                    continuation.resumeWithException(CancellationException("User cancelled the operation"))
                }
            })
        continuation.invokeOnCancellation {
            Log.d("testCancellation", "cancelled")
        }
    }
}