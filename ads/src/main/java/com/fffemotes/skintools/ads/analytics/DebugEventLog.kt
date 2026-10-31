package com.fffemotes.skintools.ads.analytics

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fffemotes.skintools.ads.BuildConfig
import com.fffemotes.skintools.ads.R
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * As Notepad-L-01, while [enabled]: every event sent to Firebase is also kept here, newest first, and ONE notification
 * shows the count and the last event; tapping it opens the host's event list ([ACTION_OPEN]).
 */
object DebugEventLog {
    data class Entry(val time: Long, val name: String, val params: List<Pair<String, String>>)

    private const val MAX = 1000
    private const val CHANNEL = "debug_analytics_events"
    private const val NOTIFICATION_ID = 72_000

    /**
     * When the log, its notification and the event list are on. Default: debug builds only; the host app widens it
     * (this app: debug, or a forced-paid test build). A normal release build never shows them.
     */
    @Volatile var enabled: () -> Boolean = { BuildConfig.DEBUG }

    /** The host app's event list declares this action. */
    const val ACTION_OPEN = "com.fffemotes.skintools.ads.analytics.OPEN_DEBUG_EVENTS"

    /** Bumped on every add / clear, so an open event list updates live. */
    val revision = MutableStateFlow(0)

    // shortcut: memory only, gone when the process dies; persist to a file if testers need it across restarts
    private val entries = ArrayDeque<Entry>()

    fun all(): List<Entry> = synchronized(entries) { entries.toList() }

    fun clear(context: Context) {
        synchronized(entries) { entries.clear() }
        revision.value++
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun add(context: Context, name: String, params: Bundle) {
        if (!enabled()) return
        @Suppress("DEPRECATION")
        val list = params.keySet().sorted().map { it to params.get(it).toString() }
        val count = synchronized(entries) {
            entries.addFirst(Entry(System.currentTimeMillis(), name, list))
            if (entries.size > MAX) entries.removeLast()
            entries.size
        }
        revision.value++
        val brief = list.filter { it.first in BRIEF }.joinToString(" · ") { it.second }
        notify(context.applicationContext, count, if (brief.isEmpty()) name else "$name · $brief")
    }

    /** Params shown in the notification and collapsed list rows. */
    val BRIEF = setOf("screen", "button", "key", "permission", "result", "format", "step", "ad_place", "duration_sec", "exit_by", "ad_format", "value")

    private fun notify(app: Context, count: Int, last: String) {
        runCatching {
            val manager = NotificationManagerCompat.from(app)
            if (!manager.areNotificationsEnabled()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Analytics events (debug)", NotificationManager.IMPORTANCE_LOW),
                )
            }
            val open = PendingIntent.getActivity(
                app, 0,
                Intent(ACTION_OPEN).setPackage(app.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_ad)
                .setContentTitle("Analytics events: $count")
                .setContentText("Last: $last")
                .setContentIntent(open)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }
    }
}
