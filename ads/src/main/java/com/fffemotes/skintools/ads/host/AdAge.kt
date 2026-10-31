package com.fffemotes.skintools.ads.host

import android.os.SystemClock

/** Cached full-screen ads go stale (Google: app open after 4 h, interstitial / rewarded after 1 h); stale ones are dropped. */
internal object AdAge {
    const val APP_OPEN_MS = 4 * 3_600_000L
    const val FULL_SCREEN_MS = 3_600_000L

    fun now(): Long = SystemClock.elapsedRealtime()

    fun stale(loadedAt: Long, maxMs: Long): Boolean = now() - loadedAt > maxMs
}
