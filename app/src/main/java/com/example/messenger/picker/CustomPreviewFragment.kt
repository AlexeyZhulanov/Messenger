package com.example.messenger.picker

import android.os.Bundle
import com.example.messenger.BaseInfoViewModel
import com.luck.picture.lib.PictureSelectorPreviewFragment
import com.luck.picture.lib.adapter.PicturePreviewAdapter

class CustomPreviewFragment(
    private val viewModel: BaseInfoViewModel
) : PictureSelectorPreviewFragment() {
    override fun getFragmentTag(): String {
        return CustomPreviewFragment::class.java.simpleName
    }

    override fun createAdapter(): PicturePreviewAdapter {
        return CustomPreviewAdapter(viewModel)
    }

    companion object {
        fun newInstance(viewModel: BaseInfoViewModel): CustomPreviewFragment {
            val fragment = CustomPreviewFragment(viewModel)
            fragment.arguments = Bundle()
            return fragment
        }
    }
}