package com.fffemotes.skintools.launcher.ui

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.PointF
import android.os.Handler
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.fffemotes.skintools.launcher.R
import kotlin.math.abs

class CustomGadgetWidgetHolderPane(context: Context) : AppWidgetHostView(context) {
    private var longPressHandler = Handler()
    private var actionDownCoords = PointF()
    private var currentCoords = PointF()
    private var actionDownMS = 0L
    private val moveGestureThreshold = resources.getDimension(R.dimen.launcher_move_gesture_threshold).toInt() / 4
    var hasLongPressed = false
    var ignoreTouches = false
    var longPressListener: ((x: Float, y: Float) -> Unit)? = null
    var onIgnoreInterceptedListener: (() -> Unit)? = null
    /** A short tap that was neither a long press nor a drag (analytics only; the widget still gets it). */
    var tapListener: (() -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return if (ignoreTouches) {
            onIgnoreInterceptedListener?.invoke()
            true
        } else {
            super.onTouchEvent(event)
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        if (ignoreTouches || event == null) {
            return true
        }

        if (hasLongPressed) {
            hasLongPressed = false
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressHandler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                actionDownCoords.x = event.rawX
                actionDownCoords.y = event.rawY
                currentCoords.x = event.rawX
                currentCoords.y = event.rawY
                actionDownMS = System.currentTimeMillis()
            }

            MotionEvent.ACTION_MOVE -> {
                currentCoords.x = event.rawX
                currentCoords.y = event.rawY
                if (abs(actionDownCoords.x - currentCoords.x) > moveGestureThreshold) {
                    reinitTouches()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                reinitTouches()
                if (event.actionMasked == MotionEvent.ACTION_UP && !hasFingerMoved(event.rawX, event.rawY)) tapListener?.invoke()
            }
        }

        return false
    }

    private val longPressRunnable = Runnable {
        if (abs(actionDownCoords.x - currentCoords.x) < moveGestureThreshold && abs(actionDownCoords.y - currentCoords.y) < moveGestureThreshold) {
            longPressHandler.removeCallbacksAndMessages(null)
            hasLongPressed = true
            longPressListener?.invoke(actionDownCoords.x, actionDownCoords.y)
        }
    }

    fun reinitTouches() {
        longPressHandler.removeCallbacksAndMessages(null)
    }

    private fun hasFingerMoved(x: Float, y: Float) =
        ((abs(actionDownCoords.x - x) > moveGestureThreshold) || (abs(actionDownCoords.y - y) > moveGestureThreshold))
}
