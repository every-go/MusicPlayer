package com.everygo.musicplayer

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.LinearLayout

class GestureLinearLayout(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    private var gestureDetector: GestureDetector? = null
    private var startX = 0f
    private var startY = 0f

    fun setGestureDetector(detector: GestureDetector) {
        gestureDetector = detector
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val diffX = kotlin.math.abs(event.x - startX)
                val diffY = kotlin.math.abs(event.y - startY)
                // Se è uno swipe orizzontale, intercetta l'evento
                if (diffX > diffY && diffX > 30) {
                    gestureDetector?.onTouchEvent(event)
                    return true
                }
            }
        }
        gestureDetector?.onTouchEvent(event)
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector?.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}