package com.example.messenger.customview

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class CustomLayoutManager : RecyclerView.LayoutManager() {

    private var columnWidth = 0
    private var rowHeight = 0

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        detachAndScrapAttachedViews(recycler)

        if (itemCount == 0) return

        // Определяем количество колонок
        val columns = when (itemCount) {
            1 -> 1
            in 2..5 -> 2
            else -> 3
        }

        // Определяем количество строк
        val rows = when (itemCount) {
            1 -> 1
            in 2..3 -> 2
            in 4..8 -> 3
            else -> 4
        }

        columnWidth = width / columns
        rowHeight = height / rows

        // Матрица занятых клеток
        val occupiedCells = Array(rows) { BooleanArray(columns) { false } }

        for (i in 0 until itemCount) {
            val view = recycler.getViewForPosition(i)
            var spanSizeW = 1
            var spanSizeH = 1

            // Логика определения размеров элемента
            when (itemCount) {
                2 -> spanSizeH = 2
                3 -> if (i == 0) spanSizeW = 2
                4 -> if (i == 0) spanSizeH = 3
                5 -> if (i == 0) spanSizeH = 2
                6 -> if (i == 0) spanSizeW = 3 else if (i == 1) spanSizeW = 2
                7 -> if (i == 0) spanSizeH = 2 else if (i == 1) spanSizeW = 2
                8 -> if (i == 0) spanSizeW = 2
                9 -> if (i == 0) spanSizeW = 3 else if (i == 1) spanSizeW = 2
                10 -> if (i == 0) spanSizeH = 2 else if (i == 1) spanSizeW = 2
            }

            // Находим первую доступную позицию
            var positionFound = false
            for (row in 0 until rows) {
                for (col in 0 until columns) {
                    if (canPlaceItem(row, col, spanSizeW, spanSizeH, occupiedCells)) {
                        placeItem(view, row, col, spanSizeW, spanSizeH, occupiedCells)
                        positionFound = true
                        break
                    }
                }
                if (positionFound) break
            }
        }
    }

    private fun canPlaceItem(row: Int, col: Int, spanSizeW: Int, spanSizeH: Int, occupiedCells: Array<BooleanArray>): Boolean {
        for (r in row until row + spanSizeH) {
            for (c in col until col + spanSizeW) {
                if (r >= occupiedCells.size || c >= occupiedCells[0].size || occupiedCells[r][c]) {
                    return false
                }
            }
        }
        return true
    }

    private fun placeItem(view: View, row: Int, col: Int, spanSizeW: Int, spanSizeH: Int, occupiedCells: Array<BooleanArray>) {
        val left = col * columnWidth
        val top = row * rowHeight
        val right = left + columnWidth * spanSizeW
        val bottom = top + rowHeight * spanSizeH

        // Отмечаем клетки как занятые
        for (r in row until row + spanSizeH) {
            for (c in col until col + spanSizeW) {
                occupiedCells[r][c] = true
            }
        }

        // Измеряем и размещаем view
        val widthSpec = View.MeasureSpec.makeMeasureSpec(right - left, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(bottom - top, View.MeasureSpec.EXACTLY)
        view.measure(widthSpec, heightSpec)
        addView(view)

        val outRect = Rect()
        calculateItemDecorationsForChild(view, outRect)

        layoutDecorated(
            view,
            left + outRect.left,
            top + outRect.top,
            right - outRect.right,
            bottom - outRect.bottom
        )
    }

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}