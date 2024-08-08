package com.example.messenger.picker

import android.content.Context
import android.util.AttributeSet
import com.luck.picture.lib.widget.PreviewBottomNavBar

class CustomPreviewBottomNavBar : PreviewBottomNavBar {
    constructor(context: Context?) : super(context)

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )
}