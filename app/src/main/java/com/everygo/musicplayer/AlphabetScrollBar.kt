package com.everygo.musicplayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt

class AlphabetScrollbar(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val letters = (listOf("#") + ('A'..'Z').map { it.toString() })
    private val paint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val highlightPaint = Paint().apply {
        color = "#BB86FC".toColorInt()
        textSize = 36f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    var onLetterSelected: ((String) -> Unit)? = null
    private var selectedIndex = -1

    override fun onDraw(canvas: Canvas) {
        val itemHeight = height.toFloat() / letters.size
        letters.forEachIndexed { i, letter ->
            val y = itemHeight * i + itemHeight / 2 + paint.textSize / 3
            canvas.drawText(letter, width / 2f, y, if (i == selectedIndex) highlightPaint else paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent.requestDisallowInterceptTouchEvent(true)
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val index = ((event.y / height) * letters.size).toInt().coerceIn(0, letters.size - 1)
                if (index != selectedIndex) {
                    selectedIndex = index
                    onLetterSelected?.invoke(letters[index])
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                selectedIndex = -1
                invalidate()
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}