package com.fffemotes.skintools.ads.policy

import android.content.Context
import android.os.Handler
import android.os.Looper

interface AdClock {
    fun postDelayed(ms: Long, r: Runnable)
    fun cancel(r: Runnable)
}

/**
 * The single "how long may an ad keep the user waiting" rule (RC ads.loader_wait_sec → vault inter_load_timeout_sec).
 * start() when the loader/shimmer appears; arrived() when the ad loads or fails: true = act on it now,
 * false = the wait already ended (the user moved on) — keep a loaded ad cached, do not show it.
 */
class AdWait(private val clock: AdClock, private val waitMs: Long) {
    @Volatile var timedOut: Boolean = false
        private set
    @Volatile private var done = false
    private var timer: Runnable? = null

    fun start(onTimeout: () -> Unit) {
        val r = Runnable {
            if (done) return@Runnable
            done = true
            timedOut = true
            onTimeout()
        }
        timer = r
        clock.postDelayed(waitMs, r)
    }

    fun arrived(): Boolean {
        if (done) return false
        done = true
        timer?.let { clock.cancel(it) }
        return true
    }

    companion object {
        private const val FALLBACK_SEC = 8
        fun sanitize(sec: Int): Long = (if (sec > 0) sec else FALLBACK_SEC) * 1000L
    }
}

object AdWaits {
    private val mainClock = object : AdClock {
        private val h = Handler(Looper.getMainLooper())
        override fun postDelayed(ms: Long, r: Runnable) { h.postDelayed(r, ms) }
        override fun cancel(r: Runnable) { h.removeCallbacks(r) }
    }

    fun waitMs(context: Context): Long =
        AdWait.sanitize(AdSettingsVault.getInstance(context).getInt("inter_load_timeout_sec"))

    fun main(context: Context): AdWait = AdWait(mainClock, waitMs(context))
}

/**
 * Bottom/inline slot guard: an ad view attached to [container] counts as arrived; if none arrives within
 * ads.loader_wait_sec the slot collapses (no empty box). Slots never block input, so nothing else waits.
 * A slot whose ad is still [loading] keeps its shimmer instead: it fills when the ad arrives, with no jump.
 */
internal fun slotCollapsesOnTimeout(showing: Boolean, loading: Boolean): Boolean = !showing && !loading

internal fun guardSlot(
    context: Context,
    container: android.widget.FrameLayout,
    shimmer: android.view.View?,
    loading: () -> Boolean = { false },
): AdWait {
    val wait = AdWaits.main(context)
    container.setOnHierarchyChangeListener(object : android.view.ViewGroup.OnHierarchyChangeListener {
        override fun onChildViewAdded(parent: android.view.View?, child: android.view.View?) {
            if (child !== shimmer) wait.arrived()
        }

        override fun onChildViewRemoved(parent: android.view.View?, child: android.view.View?) = Unit
    })
    wait.start {
        // An ad already on screen (e.g. kept from a previous load) is never collapsed.
        val showing = (0 until container.childCount).any { container.getChildAt(it) !== shimmer }
        if (!slotCollapsesOnTimeout(showing, loading())) return@start
        container.removeAllViews()
        container.minimumHeight = 0
        container.visibility = android.view.View.GONE
        (shimmer as? com.facebook.shimmer.ShimmerFrameLayout)?.stopShimmer()
        shimmer?.visibility = android.view.View.GONE
    }
    return wait
}
