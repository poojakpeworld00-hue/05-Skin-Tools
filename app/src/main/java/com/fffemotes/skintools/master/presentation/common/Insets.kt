package com.fffemotes.skintools.master.presentation.common

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.enableEdgeToEdge
import androidx.core.view.updatePadding

/** Pads this view by the system bars, for edge-to-edge activities. */
fun View.padForSystemBars() {
    val start = paddingStart; val top = paddingTop; val end = paddingEnd; val bottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.updatePadding(start + b.left, top + b.top, end + b.right, bottom + b.bottom)
        insets
    }
}

/**
 * Edge-to-edge with light status / navigation bar icons always: the app is dark only, so following the phone's own light
 * theme would draw dark icons on our dark background.
 */
fun androidx.activity.ComponentActivity.enableDarkEdgeToEdge() {
    val bars = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    window.hideNavigationBar()
}

/** App-wide: the navigation bar stays hidden; a swipe from the edge shows it for a moment, then it hides again. */
fun android.view.Window.hideNavigationBar() {
    androidx.core.view.WindowCompat.getInsetsController(this, decorView).apply {
        systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.navigationBars())
    }
}
