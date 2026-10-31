package com.fffemotes.skintools.ads.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.fffemotes.skintools.ads.BuildConfig

/**
 * The one Firebase Analytics logger ("Launcher App Config & Events", Part 2). Only the 11 fixed events exist; details go
 * in parameters. Every event gets `segment`. Debug builds send to Firebase too (DebugView). While [DebugEventLog.enabled]
 * (debug builds, or a forced-paid test build) the event is also kept there: one notification, tapping it opens the event list.
 */
object Analytics {
    const val SCREEN = "screen"
    const val CLICK = "click"
    const val KEY_PRESS = "key_press"
    const val PERMISSION = "permission"
    const val AD = "ad"
    const val AD_IMPRESSION = FirebaseAnalytics.Event.AD_IMPRESSION
    const val DEFAULT_HOME = "default_home"
    const val APP_UPDATE = "app_update"
    const val RATE_US = "rate_us"
    const val UNINSTALL = "uninstall"
    const val INSTALL_SOURCE = "install_source"
    const val FIRST_HOME = "first_home"

    val EVENTS = setOf(SCREEN, CLICK, KEY_PRESS, PERMISSION, AD, AD_IMPRESSION, DEFAULT_HOME, APP_UPDATE, RATE_US, UNINSTALL, INSTALL_SOURCE, FIRST_HOME)

    private const val TAG = "Analytics"
    private var app: Context? = null
    private var fa: FirebaseAnalytics? = null

    /** "paid" / "organic"; set by the app from its organic / paid verdict. */
    var segment: () -> String = { "organic" }

    fun init(context: Context, segment: () -> String) {
        app = context.applicationContext
        this.segment = segment
        fa = runCatching { FirebaseAnalytics.getInstance(context.applicationContext) }.getOrNull()
    }

    /** Sends [event] (one of [EVENTS]) with [params]; null values are left out. Never throws. */
    fun log(event: String, vararg params: Pair<String, Any?>) {
        runCatching {
            if (event !in EVENTS) {
                Log.e(TAG, "unknown event '$event' dropped: only the 11 fixed events are allowed")
                return
            }
            val bundle = EventRules.clean(params.toList() + ("segment" to segment())).toBundle()
            if (BuildConfig.DEBUG) Log.d(TAG, "$event $bundle")
            app?.let { DebugEventLog.add(it, event, bundle) } // no-op unless DebugEventLog.enabled
            fa?.logEvent(event, bundle)
        }.onFailure { Log.w(TAG, "log $event failed", it) }
    }

    /** Any tap, on the screen the user is on. */
    fun click(button: String, value: Any? = null) =
        log(CLICK, "screen" to Screens.current, "button" to button, "value" to value)

    /** System Back / Home / Recents; also tells the screen timer how the current visit may end. */
    fun keyPress(key: String) {
        Screens.exitBy(key)
        log(KEY_PRESS, "screen" to Screens.current, "key" to key)
    }

    /** [result] = show / allow / deny / already. */
    fun permission(permission: String, result: String, screen: String = Screens.current) =
        log(PERMISSION, "screen" to screen, "permission" to permission, "result" to result)

    /** default_home / app_update / rate_us / uninstall steps. */
    fun step(event: String, step: String, value: Any? = null) = log(event, "step" to step, "value" to value)
}

/** Pure naming rules of [Analytics] (unit-tested): snake_case keys <= 40 chars, text values <= 100 chars, nulls dropped. */
object EventRules {
    private val VALID = Regex("^[a-z][a-z0-9_]{0,39}$")

    fun validName(name: String) = VALID.matches(name)

    fun clean(params: List<Pair<String, Any?>>): List<Pair<String, Any>> = params.mapNotNull { (key, value) ->
        when {
            value == null || !validName(key) -> null
            value is Int -> key to value.toLong()
            value is Float -> key to value.toDouble()
            value is Long || value is Double -> key to value
            else -> key to value.toString().take(100)
        }
    }
}

private fun List<Pair<String, Any>>.toBundle() = Bundle().apply {
    for ((key, value) in this@toBundle) when (value) {
        is Long -> putLong(key, value)
        is Double -> putDouble(key, value)
        else -> putString(key, value.toString())
    }
}
