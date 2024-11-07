package com.example.messenger.picker

import android.os.Bundle
import com.example.messenger.MessageViewModel
import com.luck.picture.lib.PictureSelectorPreviewFragment
import com.luck.picture.lib.adapter.PicturePreviewAdapter

class CustomPreviewFragment(
    private val messageViewModel: MessageViewModel,
    private val filename: String
) : PictureSelectorPreviewFragment() {
    override fun getFragmentTag(): String {
        return CustomPreviewFragment::class.java.simpleName
    }

    override fun createAdapter(): PicturePreviewAdapter {
        return CustomPreviewAdapter(messageViewModel, filename)
    }

    companion object {
        fun newInstance(messageViewModel: MessageViewModel, filename: String): CustomPreviewFragment {
            val fragment = CustomPreviewFragment(messageViewModel, filename)
            fragment.arguments = Bundle()
            return fragment
        }
    }
}