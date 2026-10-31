package com.fffemotes.skintools.master.presentation.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.addCallback
import com.fffemotes.skintools.master.presentation.common.enableDarkEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.fffemotes.skintools.ads.Ads
import com.fffemotes.skintools.ads.analytics.Analytics
import com.fffemotes.skintools.ads.analytics.Screens
import com.fffemotes.skintools.launcher.Launcher
import com.fffemotes.skintools.master.App
import com.fffemotes.skintools.master.R
import com.fffemotes.skintools.master.databinding.ActivityHomeBinding
import com.fffemotes.skintools.master.presentation.common.padForSystemBars
import com.fffemotes.skintools.master.presentation.prompts.RatePrompt

/** Home, as in Claude Design "Skin Hub Onboarding v2"; content in [HomeContent]. */
class HomeActivity : AppCompatActivity() {
    private val config get() = (application as App).container.config.value
    private val askRate = Runnable { RatePrompt.maybeAsk(this, config.prompts) }

    /** Home pressed on our screen while we are the default home: `launcher.home_button`. Recents is RecentsPolicy's. */
    private val keyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getStringExtra("reason") == "homekey" && Launcher.isDefaultHome(context)) Ads.systemKey(this@HomeActivity, "home") {}
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()
        val b = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.screen.padForSystemBars()
        HomeContent(this, b).bind()
        if (savedInstanceState == null) {
            // first_home: once, on the Home visit right after onboarding.
            if ((application as App).container.settingsRepository.takeFirstHome()) Analytics.log(Analytics.FIRST_HOME)
            HomePermissions.askOnFirstVisit(this)
        }

        // Back: the `launcher.back_button` ad, then leave.
        // As the reference: when we are the default home, leaving lands on our launcher home.
        onBackPressedDispatcher.addCallback(this) {
            Analytics.keyPress("back")
            Ads.backButton(this@HomeActivity) { leave() }
        }
    }

    private fun leave() {
        if (Launcher.isDefaultHome(this)) runCatching { Launcher.openLauncherHome(this) }
        finish()
    }

    override fun onResume() {
        super.onResume()
        Screens.show("home")
        // A system permission prompt pauses us, which cancels this; the rate prompt then comes once it has gone.
        window.decorView.postDelayed(askRate, RATE_DELAY_MS)
        @Suppress("DEPRECATION") // still delivered to apps for Home/Recents
        ContextCompat.registerReceiver(this, keyReceiver, IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS), ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onPause() {
        window.decorView.removeCallbacks(askRate)
        runCatching { unregisterReceiver(keyReceiver) }
        super.onPause()
    }

    private companion object {
        const val RATE_DELAY_MS = 1500L
    }
}
