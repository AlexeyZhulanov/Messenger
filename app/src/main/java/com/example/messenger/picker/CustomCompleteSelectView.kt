package com.example.messenger.picker

import com.example.messenger.R
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import com.luck.picture.lib.widget.CompleteSelectView

class CustomCompleteSelectView : CompleteSelectView {
    constructor(context: Context?) : super(context)

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun inflateLayout() {
        LayoutInflater.from(context).inflate(R.layout.ps_custom_complete_selected_layout, this)
    }

    override fun setCompleteSelectViewStyle() {
        super.setCompleteSelectViewStyle()
    }
}