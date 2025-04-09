package com.example.messenger.codeview.plugins

import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.EditText


class SourcePositionListener(private val editText: EditText) {
    fun interface OnPositionChanged {
        fun onPositionChange(line: Int, column: Int)
    }

    private var onPositionChanged: OnPositionChanged? = null

    fun setOnPositionChanged(listener: OnPositionChanged?) {
        onPositionChanged = listener
    }

    private val viewAccessibility: View.AccessibilityDelegate =
        object : View.AccessibilityDelegate() {
            override fun sendAccessibilityEvent(host: View, eventType: Int) {
                super.sendAccessibilityEvent(host, eventType)
                if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED && onPositionChanged != null) {
                    val selectionStart = editText.selectionStart
                    if(editText.layout == null) return
                    val line = editText.layout.getLineForOffset(selectionStart)
                    val column = selectionStart - editText.layout.getLineStart(line)
                    onPositionChanged!!.onPositionChange(line + 1, column + 1)
                }
            }
        }

    init {
        editText.accessibilityDelegate = viewAccessibility
    }
}