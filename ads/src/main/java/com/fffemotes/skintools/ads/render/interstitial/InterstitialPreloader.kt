package com.fffemotes.skintools.ads.render.interstitial

import com.fffemotes.skintools.ads.AdKit
import android.app.Activity
import android.app.Dialog
import android.os.Build
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.fffemotes.skintools.ads.R

object InterstitialPreloader {
    private var dialog: Dialog? = null

    /** The full-screen ad loader is up. */
    val isShowing: Boolean get() = dialog?.isShowing == true

    @Suppress("DEPRECATION")
    fun show(activity: Activity, isLoader: Boolean) {
        if (!isLoader) return

        try {
            if (activity.isFinishing || activity.isDestroyed) return

            closeSafely()

            dialog = Dialog(activity).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setCancelable(false)
                setContentView(R.layout.part_fullscreen) // previous version: black screen, spinner, "Please Wait..."
                // Black, and drawn into the camera cutout: with the bars hidden, a transparent window let the screen
                // behind show through the status-bar strip.
                window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) window?.attributes = window?.attributes?.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }

                window?.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

                window?.setFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                )

                window?.let { w ->
                    WindowCompat.setDecorFitsSystemWindows(w, false)
                    WindowCompat.getInsetsController(w, w.decorView).apply {
                        systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        hide(WindowInsetsCompat.Type.systemBars())
                    }
                }

                show()

                window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            }
        } catch (e: Exception) {
            Log.e("InterstitialPreloader", "Error showing loader", e)
            AdKit.onError(e, "interstitial.loader")
        }
    }

    fun hide() {
        closeSafely()
    }

    private fun closeSafely() {
        try {
            dialog?.let {
                if (it.isShowing) {
                    val context = it.context
                    if (context is Activity) {
                        if (!context.isFinishing && !context.isDestroyed) {
                            it.dismiss()
                        }
                    } else {
                        it.dismiss()
                    }
                }
            }
        } catch (e: Exception) {
        } finally {
            dialog = null
        }
    }
}
