package com.fffemotes.skintools.master.core.system

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.animation.CycleInterpolator
import com.fffemotes.skintools.ads.Ads
import com.fffemotes.skintools.ads.analytics.Analytics
import com.fffemotes.skintools.ads.analytics.Screens
import com.fffemotes.skintools.ads.render.ResumeAdDirector
import com.fffemotes.skintools.ads.render.hasNetwork
import com.fffemotes.skintools.master.databinding.SheetNoInternetBinding
import com.fffemotes.skintools.master.presentation.uninstall.UninstallDialogActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * RC `base.no_internet_dialog`: while offline, the app's own screens show a "No internet" sheet that can't be closed
 * (Retry / Open settings). It closes by itself once the connection is back, and that screen's empty ad slots load again.
 * Never over the launcher home, ad screens or the module's event screens: those aren't app screens (other packages).
 */
object NoInternet {
    private val onlineState = MutableStateFlow(true)
    val online: StateFlow<Boolean> = onlineState

    private lateinit var app: Application
    private lateinit var enabled: () -> Boolean
    private val main = Handler(Looper.getMainLooper())
    private var resumed: Activity? = null
    private var sheet: BottomSheetDialog? = null
    private var sheetOwner: Activity? = null
    private var coveredScreen: String? = null

    fun install(app: Application, enabled: () -> Boolean) {
        this.app = app
        this.enabled = enabled
        onlineState.value = hasNetwork(app)
        val cm = app.getSystemService(ConnectivityManager::class.java)
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                    post(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                override fun onLost(network: Network) = post(false)
            })
        }
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) {
                resumed = a
                update()
            }
            override fun onActivityPaused(a: Activity) {
                if (resumed === a) resumed = null
                if (sheetOwner === a) close() // Settings / another screen in front: shown again on the next resume
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
            override fun onActivityStarted(a: Activity) = Unit
            override fun onActivityStopped(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
            override fun onActivityDestroyed(a: Activity) = Unit
        })
    }

    private fun post(isOnline: Boolean) {
        main.post {
            val back = isOnline && !onlineState.value
            onlineState.value = isOnline
            update()
            if (back) resumed?.let(Ads::refillEmptySlots)
        }
    }

    private fun update() {
        val a = resumed
        val show = !onlineState.value && a != null && covers(a) && enabled()
        if (!show) {
            val restore = sheet != null && onlineState.value
            close()
            if (restore) coveredScreen?.let(Screens::show) // the covered screen's visit starts again
        } else if (sheetOwner !== a) {
            close()
            show(a!!)
        }
    }

    private fun covers(a: Activity) =
        a.javaClass.name.startsWith(APP_PACKAGE) && a !is UninstallDialogActivity && !a.isFinishing

    private fun show(activity: Activity) {
        val v = SheetNoInternetBinding.inflate(activity.layoutInflater)
        v.netRetry.setOnClickListener {
            Analytics.click("retry", "no_internet")
            if (hasNetwork(app)) post(true)
            else v.netIcon.animate().translationX(12f).setInterpolator(CycleInterpolator(3f)).setDuration(400).start()
        }
        v.netSettings.setOnClickListener {
            Analytics.click("open_settings", "no_internet")
            val panel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            else Intent(Settings.ACTION_WIRELESS_SETTINGS)
            ResumeAdDirector.skipNextAppOpenAd = true // our own trip to Settings: no app-open ad on the way back
            runCatching { activity.startActivity(panel) }.onFailure { runCatching { activity.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
        }
        coveredScreen = Screens.current
        sheet = BottomSheetDialog(activity).apply {
            setContentView(v.root)
            setCancelable(false) // no Back, no outside tap: only the connection closes it
            behavior.isDraggable = false
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            show()
        }
        sheetOwner = activity
        Screens.show("no_internet")
    }

    private fun close() {
        sheet?.let { runCatching { it.dismiss() } }
        sheet = null
        sheetOwner = null
    }

    private const val APP_PACKAGE = "com.fffemotes.skintools.master."
}
