package com.fffemotes.skintools.master.core.system

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.fffemotes.skintools.ads.analytics.Analytics

/** `key_press` for Home / Recents on any of our screens (app, launcher, event screens), while the app is in front. */
object SystemKeys {
    fun listen(app: Application) {
        @Suppress("DEPRECATION") // still delivered to apps for Home/Recents
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
                when (intent.getStringExtra("reason")) {
                    "homekey" -> Analytics.keyPress("home")
                    "recentapps", "recent_apps" -> Analytics.keyPress("recents")
                }
            }
        }, IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS), ContextCompat.RECEIVER_EXPORTED)
    }
}
