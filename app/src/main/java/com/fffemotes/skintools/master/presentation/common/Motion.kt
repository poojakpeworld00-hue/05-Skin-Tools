package com.fffemotes.skintools.master.presentation.common

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import com.fffemotes.skintools.master.R

// Shared Claude Design motion. Animators honour the system "remove animations" setting (the design's reduced-motion rule).

/** Corner glow "glowPulse": alpha .7↔1 and scale 1↔1.08 over 4 s. */
fun View.glowPulse() {
    ObjectAnimator.ofPropertyValuesHolder(
        this,
        PropertyValuesHolder.ofFloat(View.ALPHA, 0.7f, 1f),
        PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f),
        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f),
    ).apply {
        duration = 2000
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
        start()
    }
}

/** "shine": a light bar sweeping left to right across [parent] every [periodMs]. */
fun View.shine(parent: View, periodMs: Long) {
    parent.post {
        ObjectAnimator.ofFloat(this, View.TRANSLATION_X, -width * 1.5f, parent.width + width.toFloat()).apply {
            duration = periodMs
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }
}

/** "cardPop" on selection: 1 → 1.06 → 1. */
fun View.pop() {
    animate().scaleX(1.06f).scaleY(1.06f).setDuration(140).withEndAction {
        animate().scaleX(1f).scaleY(1f).setDuration(210).start()
    }.start()
}

/** Recolours the `<b>` part of a string resource in the accent, as the design's two-tone titles. */
fun CharSequence.accentBold(view: View): CharSequence {
    if (this !is Spanned) return this
    val out = SpannableStringBuilder(this)
    val accent = ContextCompat.getColor(view.context, R.color.sh_primary)
    out.getSpans(0, out.length, StyleSpan::class.java).forEach {
        out.setSpan(ForegroundColorSpan(accent), out.getSpanStart(it), out.getSpanEnd(it), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return out
}
