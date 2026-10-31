package com.fffemotes.skintools.master.presentation.onboarding

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.fffemotes.skintools.master.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** The setup sheet's voice waveform: [BARS] bars, filled up to [progress]; bars near the play head bounce while [playing]. */
class WaveformView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val played = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.sh_primary) }
    private val rest = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x47FF3B5C }
    private var heights = FloatArray(BARS)

    var progress = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    var playing = false
        set(value) { field = value; invalidate() }

    /** Same step, same shape: heights are a fraction of the view height, seeded by [seed]. */
    fun seed(seed: Int) {
        var s = (seed + 1) * 7919L
        heights = FloatArray(BARS) { i ->
            s = s * 16807 % 2147483647
            val r = s / 2147483647f
            ((5 + r * 19) * (0.45f + 0.55f * sin(PI * (i + 0.5) / BARS).toFloat()) + 3) / 27f
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val gap = 2 * resources.displayMetrics.density
        val w = (width - gap * (BARS - 1)) / BARS
        val head = progress * BARS
        val now = SystemClock.uptimeMillis()
        for (i in 0 until BARS) {
            var h = heights[i] * height
            if (playing && abs(i + 0.5f - head) < 2.5f) h = maxOf(4f, h * (0.5f + 0.5f * abs(sin(now / 95.0 + i * 1.9).toFloat())))
            val x = i * (w + gap)
            val top = (height - h) / 2
            canvas.drawRoundRect(x, top, x + w, top + h, w / 2, w / 2, if (i + 0.5f < head) played else rest)
        }
        if (playing) postInvalidateOnAnimation()
    }

    private companion object { const val BARS = 32 }
}
