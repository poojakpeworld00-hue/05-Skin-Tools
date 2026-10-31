package com.fffemotes.skintools.launcher.exts

import android.provider.Settings
import android.view.View
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

/** Staggered fade-and-rise of [views] (MSG-05 IntroMotion.enter, without the hero part the launcher never used). */
fun enterMotion(vararg views: View) {
    val context = views.firstOrNull()?.context ?: return
    val scale = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    if (scale <= 0f) return
    val rise = 24f * context.resources.displayMetrics.density
    val interpolator = FastOutSlowInInterpolator()
    views.forEachIndexed { index, view ->
        view.animate().cancel()
        view.alpha = 0f
        view.translationY = rise
        view.animate().alpha(1f).translationY(0f)
            .setStartDelay((index + 1) * 60L)
            .setDuration(350L)
            .setInterpolator(interpolator)
            .start()
    }
}
