package com.example.messenger.customview

import android.content.Context
import android.text.Layout
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.example.messenger.R

class MessageLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    private lateinit var messageTextView: TextView
    private lateinit var timeTextView: TextView
    private lateinit var editTextView: TextView
    private lateinit var icCheck: ImageView
    private lateinit var icCheck2: ImageView
    private lateinit var icError: ImageView

    private var isSender: Boolean = false
    private var mesWid: Int = 0

    init {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.MessageLayout, 0, 0)
            isSender = typedArray.getBoolean(R.styleable.MessageLayout_isSender, false)
            typedArray.recycle()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Найдем нужные дочерние элементы
        messageTextView = if(isSender) findViewById(R.id.messageSenderTextView) else findViewById(R.id.messageReceiverTextView)
        timeTextView = findViewById(R.id.timeTextView)
        editTextView = findViewById(R.id.editTextView)
        if(isSender) {
            icCheck = findViewById(R.id.ic_check)
            icCheck2 = findViewById(R.id.ic_check2)
            icError = findViewById(R.id.ic_error)
        }
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight

        // Измеряем все дочерние элементы
        measureChildWithMargins(messageTextView, widthMeasureSpec, 0, heightMeasureSpec, 0)

        val messageLayout: Layout = messageTextView.layout
        val lastLineWidth = messageLayout.getLineWidth(messageLayout.lineCount - 1).toInt()
        val remainingWidth = maxWidth - lastLineWidth

        measureChildWithMargins(editTextView, widthMeasureSpec, 0, heightMeasureSpec, 0)
        measureChildWithMargins(timeTextView, widthMeasureSpec, 0, heightMeasureSpec, 0)
        mesWid = if(isSender) when {
            icCheck.visibility == View.VISIBLE -> {
                measureChildWithMargins(icCheck, widthMeasureSpec, 0, heightMeasureSpec, 0)
                icCheck.measuredWidth
            }
            icCheck2.visibility == View.VISIBLE -> {
                measureChildWithMargins(icCheck2, widthMeasureSpec, 0, heightMeasureSpec, 0)
                icCheck2.measuredWidth
            }
            icError.visibility == View.VISIBLE -> {
                measureChildWithMargins(icError, widthMeasureSpec, 0, heightMeasureSpec, 0)
                icError.measuredWidth
            }
            else -> 0
        } else 0

        val editWidth = if (editTextView.visibility == View.VISIBLE) editTextView.measuredWidth else 0
        val timeTopOffset = if (remainingWidth < timeTextView.measuredWidth + editWidth + mesWid) {
            timeTextView.measuredHeight + paddingTop
        } else 0

        // Общая ширина и высота ViewGroup
        val wid = messageTextView.measuredWidth + timeTextView.measuredWidth + mesWid + editWidth
        val totalWidth = if(isSender) wid.coerceAtMost(maxWidth) + paddingLeft + paddingRight else wid.coerceAtMost(maxWidth) + paddingLeft + paddingRight + 25
        val totalHeight = messageTextView.measuredHeight.coerceAtLeast(timeTextView.measuredHeight) + paddingTop + paddingBottom + timeTopOffset
        setMeasuredDimension(
            resolveSize(totalWidth, widthMeasureSpec),
            resolveSize(totalHeight, heightMeasureSpec)
        )
    }

    override fun onLayout(p0: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val parentWidth = right - left
        val messageLayout: Layout = messageTextView.layout

        // Вычисляем ширину последней строки текста в messageTextView
        val lastLineWidth = messageLayout.getLineWidth(messageLayout.lineCount - 1)
        val remainingWidth = parentWidth - lastLineWidth.toInt()
        // Координаты для messageSenderTextView
        val messageLeft = paddingLeft
        val messageTop = paddingTop
        val messageRight = messageLeft + messageTextView.measuredWidth
        val messageBottom = messageTop + messageTextView.measuredHeight
        messageTextView.layout(messageLeft, messageTop, messageRight, messageBottom)

        // Проверяем видимость editTextView
        val editWidth = if (editTextView.visibility == View.VISIBLE) editTextView.measuredWidth else 0

        // Определяем, достаточно ли места для timeTextView и editTextView
        if (remainingWidth >= timeTextView.measuredWidth + editWidth + mesWid) {
            // Размещаем timeTextView и editTextView в одной строке с сообщением
            val timeLeft = if(isSender) parentWidth - timeTextView.measuredWidth - mesWid - paddingRight + 25 else parentWidth - timeTextView.measuredWidth - mesWid - paddingRight
            val timeTop = messageBottom - timeTextView.measuredHeight
            timeTextView.layout(timeLeft, timeTop, timeLeft + timeTextView.measuredWidth, timeTop + timeTextView.measuredHeight)

            if (editTextView.visibility == View.VISIBLE) {
                val editLeft = timeLeft - editTextView.measuredWidth - 10
                editTextView.layout(editLeft, timeTop, editLeft + editTextView.measuredWidth, timeTop + editTextView.measuredHeight)
            }
            if(isSender) {
                when {
                    icCheck.visibility == View.VISIBLE -> icCheck.layout(timeLeft - 20 + timeTextView.measuredWidth, timeTop, timeLeft + timeTextView.measuredWidth + icCheck.measuredWidth, timeTop + timeTextView.measuredHeight)
                    icCheck2.visibility == View.VISIBLE -> icCheck2.layout(timeLeft - 10 + timeTextView.measuredWidth, timeTop, timeLeft + timeTextView.measuredWidth + icCheck2.measuredWidth, timeTop + timeTextView.measuredHeight)
                    icError.visibility == View.VISIBLE -> icError.layout(timeLeft - 20 + timeTextView.measuredWidth, timeTop, timeLeft + timeTextView.measuredWidth + icError.measuredWidth, timeTop + timeTextView.measuredHeight)
                }
            }
        } else {
            // Размещаем timeTextView и editTextView на следующей строке
            val timeLeft = if(isSender) parentWidth - timeTextView.measuredWidth - mesWid - paddingRight + 25 else parentWidth - timeTextView.measuredWidth - mesWid - paddingRight
            val timeTop = messageBottom + paddingTop
            timeTextView.layout(timeLeft, timeTop, timeLeft + timeTextView.measuredWidth, timeTop + timeTextView.measuredHeight)

            if (editTextView.visibility == View.VISIBLE) {
                val editLeft = timeLeft - editTextView.measuredWidth - 10
                editTextView.layout(editLeft, timeTop, editLeft + editTextView.measuredWidth, timeTop + editTextView.measuredHeight)
            }
            if(isSender) {
                when {
                    icCheck.visibility == View.VISIBLE -> icCheck.layout(timeLeft - 20 + timeTextView.measuredWidth, timeTop, timeLeft + timeTextView.measuredWidth + icCheck.measuredWidth, timeTop + timeTextView.measuredHeight)
                    icCheck2.visibility == View.VISIBLE -> icCheck2.layout(timeLeft - 10 + timeTextView.measuredWidth, timeTop, timeLeft + timeTextView.measuredWidth + icCheck2.measuredWidth, timeTop + timeTextView.measuredHeight)
                    icError.visibility == View.VISIBLE -> icError.layout(timeLeft - 20 + timeTextView.measuredWidth, timeTop, timeLeft + timeTextView.measuredWidth + icError.measuredWidth, timeTop + timeTextView.measuredHeight)
                }
            }
        }
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        return MarginLayoutParams(context, attrs)
    }
}
