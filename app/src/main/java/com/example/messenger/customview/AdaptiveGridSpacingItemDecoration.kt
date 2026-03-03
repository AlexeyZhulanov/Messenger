package com.example.messenger.customview

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class AdaptiveGridSpacingItemDecoration(
    private val spacing: Int,
    private val includeEdge: Boolean
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = (view.layoutParams as RecyclerView.LayoutParams).bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return

        val itemCount = state.itemCount

        val columns = when (itemCount) {
            in 2..5 -> 2
            else -> 3
        }

        val rows = when (itemCount) {
            in 2..3 -> 2
            in 4..8 -> 3
            else -> 4
        }

        val column = position % columns
        val row = position / columns

        val horizontalSpacing = spacing
        val verticalSpacing = spacing

        if (includeEdge) {
            outRect.left = if (column == 0) horizontalSpacing else horizontalSpacing / 2
            outRect.right = if (column == columns - 1) horizontalSpacing else horizontalSpacing / 2
            outRect.top = if (row == 0) verticalSpacing else verticalSpacing / 2
            outRect.bottom = if (row == rows - 1) verticalSpacing else verticalSpacing / 2
        } else {
            outRect.left = horizontalSpacing / 2
            outRect.right = horizontalSpacing / 2
            outRect.top = if (row > 0) verticalSpacing / 2 else 0
            outRect.bottom = if (row < rows - 1) verticalSpacing / 2 else 0
        }
    }
}