package com.fffemotes.skintools.launcher.intro

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.os.Bundle

/** Our tasks are never listed in Recents: neither the app nor any of its screens shows up there. */
object HubRecentsCharter : Application.ActivityLifecycleCallbacks {
    fun install(app: Application) = app.registerActivityLifecycleCallbacks(this)

    private fun apply(activity: Activity) {
        val hidden = true
        val tasks = runCatching { activity.getSystemService(ActivityManager::class.java)?.appTasks.orEmpty() }.getOrDefault(emptyList())
        tasks.forEach { task ->
            runCatching { task.setExcludeFromRecents(hidden) }
                .onFailure { android.util.Log.w("RecentsCharter", "setExcludeFromRecents failed", it) }
        }
    }

    override fun onActivityResumed(activity: Activity) = apply(activity)

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
