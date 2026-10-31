package com.fffemotes.skintools.ads.host

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.fffemotes.skintools.ads.Ads

/**
 * Background → foreground detector (port of MSG-06 MsgApplication's started-activity counter): the first
 * activity started after the app went to the background gets [Ads.appResume], posted so its onResume
 * (and link-return callbacks) run first.
 */
internal object AppResumeWatcher : Application.ActivityLifecycleCallbacks {
    private var registered = false
    private var started = 0
    private var wentToBackground = false
    private val main = Handler(Looper.getMainLooper())

    fun register(app: Application) {
        if (registered) return
        registered = true
        app.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        started++
        if (started == 1 && wentToBackground) {
            wentToBackground = false
            main.post { if (!activity.isFinishing && !activity.isDestroyed) Ads.appResume(activity) }
        }
    }

    override fun onActivityStopped(activity: Activity) {
        started--
        if (started <= 0) {
            started = 0
            // A configuration change restarts the activity: not a trip to the background.
            if (!activity.isChangingConfigurations) wentToBackground = true
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
