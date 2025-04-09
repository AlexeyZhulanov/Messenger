package com.example.messenger.codeview.plugins

import android.text.Editable
import android.widget.EditText


class CommentManager(
    private val editText: EditText,
    commentStartInitial: String = "",
    commentEndInitial: String = ""
) {
    private var editable: Editable = editText.text
    private var commentStart = commentStartInitial
    private var commentStartLength = commentStart.length
    private var commentEnd = commentEndInitial
    private var commentEndLength = commentEnd.length

    fun setCommentStart(comment: String) {
        this.commentStart = comment
        this.commentStartLength = comment.length
    }

    fun setCommentEnd(comment: String) {
        this.commentEnd = comment
        this.commentEndLength = comment.length
    }

    fun commentSelected() {
        val start: Int = editText.selectionStart
        val end: Int = editText.selectionEnd
        if (start != end) {
            val lines = editable.subSequence(start, end).toString().split("\n".toRegex())
                .dropLastWhile { it.isEmpty() }.toTypedArray()
            val builder = StringBuilder()
            val len = lines.size
            for (i in 0 until len) {
                val line = lines[i]
                if (!line.startsWith(commentStart)) builder.append(commentStart)
                builder.append(line)
                if (!line.endsWith(commentEnd)) builder.append(commentEnd)
                if (i != len - 1) builder.append("\n")
            }
            editable.replace(start, end, builder)
        }
    }

    fun unCommentSelected() {
        val start: Int = editText.selectionStart
        val end: Int = editText.selectionEnd
        if (start != end) {
            val lines = editable.subSequence(start, end).toString().split("\n".toRegex())
                .dropLastWhile { it.isEmpty() }.toTypedArray()
            val builder = StringBuilder()
            val len = lines.size
            for (i in 0 until len) {
                val line = lines[i]
                if (line.startsWith(commentStart) && line.endsWith(commentEnd)) builder.append(
                    line.substring(
                        commentStartLength,
                        line.length - commentEndLength
                    )
                )
                else builder.append(line)
                if (i != len - 1) builder.append("\n")
            }
            editable.replace(start, end, builder)
        }
    }
}