package com.fffemotes.skintools.ads.analytics

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * The shared screen timer: one `screen` event per visit, sent when the visit ends, with enter / exit time.
 * Every screen calls [show] when it appears; nothing else sends `screen`. Leaving the app ends the visit
 * (`background`, or `home` / `recents` when that key was just pressed) unless [ownLeave] says the app opened it itself
 * (an ad, a link, a permission prompt, the default-home Settings page); coming back starts a new visit.
 */
object Screens {
    /** True while something the app opened is in front: leaving for it doesn't end the visit. */
    var ownLeave: () -> Boolean = { false }

    private val visits = ScreenVisits(clock = { System.currentTimeMillis() }, uptime = { SystemClock.elapsedRealtime() }) { params ->
        Analytics.log(Analytics.SCREEN, *params.toTypedArray())
    }

    val current: String get() = visits.current

    fun show(name: String) = visits.show(name)

    /** How the current visit is about to end: next / back / home / recents (button is the default). */
    fun exitBy(reason: String) = visits.exitBy(reason)

    fun install() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) { if (!ownLeave()) visits.leaveApp() }
            override fun onStart(owner: LifecycleOwner) = visits.returnToApp()
        })
    }
}

/** Pure visit rules of [Screens] (unit-tested). [send] gets the `screen` event's params. */
class ScreenVisits(
    private val clock: () -> Long,
    private val uptime: () -> Long,
    private val send: (List<Pair<String, Any?>>) -> Unit,
) {
    var current = "unknown"
        private set
    private var enterAt = 0L
    private var active = false
    private var away = false
    private var pending: String? = null
    private var pendingAt = 0L

    @Synchronized
    fun show(name: String) {
        if (active && name == current) return
        // Away (another app in front): only remember the screen; its visit starts when the user comes back.
        if (away) { current = name; return }
        end("button")
        start(name)
    }

    @Synchronized
    fun exitBy(reason: String) {
        pending = reason
        pendingAt = uptime()
    }

    @Synchronized
    fun leaveApp() {
        if (!active) return
        end("background")
        away = true
    }

    @Synchronized
    fun returnToApp() {
        if (away && !active) start(current)
        away = false
    }

    private fun start(name: String) {
        current = name
        enterAt = clock()
        active = true
    }

    private fun end(fallback: String) {
        if (!active) return
        val reason = pending?.takeIf { uptime() - pendingAt <= PENDING_MS } ?: fallback
        pending = null
        val now = clock()
        active = false
        send(
            listOf(
                "screen" to current,
                "enter_time" to enterAt,
                "exit_time" to now,
                "duration_sec" to ((now - enterAt) / 1000).coerceAtLeast(0),
                "exit_by" to reason,
            ),
        )
    }

    private companion object {
        /** A key press / Next counts for the visit that ends within this time. */
        const val PENDING_MS = 3_000L
    }
}
