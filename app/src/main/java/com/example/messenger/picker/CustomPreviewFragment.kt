package com.example.messenger.picker

import android.os.Bundle
import com.example.messenger.MessageViewModel
import com.luck.picture.lib.PictureSelectorPreviewFragment
import com.luck.picture.lib.adapter.PicturePreviewAdapter

/**
 * @author：luck
 * @date：2022/2/21 4:15 下午
 * @describe：CustomPreviewFragment
 */
class CustomPreviewFragment(
    private val messageViewModel: MessageViewModel
) : PictureSelectorPreviewFragment() {
    override fun getFragmentTag(): String {
        return CustomPreviewFragment::class.java.simpleName
    }

    override fun createAdapter(): PicturePreviewAdapter {
        return CustomPreviewAdapter(messageViewModel)
    }

    companion object {
        fun newInstance(messageViewModel: MessageViewModel): CustomPreviewFragment {
            val fragment = CustomPreviewFragment(messageViewModel)
            fragment.arguments = Bundle()
            return fragment
        }
    }
}