package com.fffemotes.skintools.ads.events.charging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.fffemotes.skintools.ads.R
import com.fffemotes.skintools.ads.Ads
import com.fffemotes.skintools.ads.analytics.Analytics
import com.fffemotes.skintools.ads.analytics.Screens
import com.fffemotes.skintools.ads.events.SystemDialogHelper

class ChargingStatusActivity : AppCompatActivity() {

    private val placeKey: String
        get() = intent.getStringExtra(EXTRA_PLACE) ?: Ads.CHARGER_CONNECTED

    private var closed = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = render(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(SystemDialogHelper(this) { finishAndRemoveTask() })
        showOverLockAndWake()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_charging_status)

        val content = findViewById<View>(R.id.chargingFunctionalContent)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.chargingRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            content.updatePadding(top = bars.top)
            findViewById<View>(R.id.chargingAdSlot).updatePadding(bottom = bars.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.btnCloseCharging).setOnClickListener {
            Analytics.click("close")
            close()
        }
        onBackPressedDispatcher.addCallback(this) {
            Analytics.keyPress("back")
            close()
        }
        render(registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        fillAd()
    }

    override fun onResume() {
        super.onResume()
        Screens.show("charging_event")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onStop() {
        runCatching { unregisterReceiver(batteryReceiver) }
        super.onStop()
    }

    private fun render(battery: Intent?) {
        val level = battery?.let {
            val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (l >= 0 && scale > 0) l * 100 / scale else 0
        } ?: 0
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val connected = plugged != 0

        findViewById<ChargingEnergyView>(R.id.chargingEnergyView).apply {
            setConnected(connected)
            setProgress(level, animate = true)
        }
        findViewById<TextView>(R.id.chargingPercentage).text =
            getString(R.string.charging_percentage_value, level)

        if (connected) {
            findViewById<TextView>(R.id.chargingModeLabel).setText(R.string.charging_live_energy)
            findViewById<TextView>(R.id.chargingTitle).setText(R.string.charging_connected_title)
            findViewById<TextView>(R.id.chargingSubtitle).text =
                getString(R.string.charging_connected_subtitle, sourceLabel(plugged))
            findViewById<TextView>(R.id.chargingMetricLeftLabel).setText(R.string.charging_until_full)
            findViewById<TextView>(R.id.chargingMetricLeftValue).text = timeToFull(level)
            findViewById<TextView>(R.id.chargingMetricRightLabel).setText(R.string.charging_power_source)
            findViewById<TextView>(R.id.chargingMetricRightValue).text = sourceLabel(plugged)
            findViewById<TextView>(R.id.chargingStatusText).setText(R.string.charging_power_flow_active)
            findViewById<View>(R.id.chargingInfoCard).visibility = View.VISIBLE
        } else {
            findViewById<TextView>(R.id.chargingModeLabel).setText(R.string.charging_session_complete)
            findViewById<TextView>(R.id.chargingTitle).setText(R.string.charging_disconnected_title)
            findViewById<TextView>(R.id.chargingSubtitle).text =
                getString(R.string.charging_ready_to_use, level)
            findViewById<TextView>(R.id.chargingStatusText).setText(R.string.charging_disconnected_safely)
            findViewById<View>(R.id.chargingInfoCard).visibility = View.GONE
        }
    }

    private fun timeToFull(level: Int): String {
        if (level >= 100) return getString(R.string.charging_time_full)
        val remainingMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (getSystemService(BATTERY_SERVICE) as? BatteryManager)?.computeChargeTimeRemaining() ?: -1L
        } else {
            -1L
        }
        if (remainingMs <= 0L) return getString(R.string.charging_time_calculating)
        val minutes = maxOf(1L, (remainingMs + 30_000) / 60_000)
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) {
            getString(R.string.charging_time_hours_minutes, h, m)
        } else {
            getString(R.string.charging_time_minutes, m)
        }
    }

    private fun sourceLabel(plugged: Int): String = when {
        plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 ->
            getString(R.string.charging_source_wireless)
        plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> getString(R.string.charging_source_usb)
        plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> getString(R.string.charging_source_ac)
        else -> getString(R.string.charging_source_power)
    }

    private fun fillAd() {
        Ads.bottom(this, findViewById<FrameLayout>(R.id.chargingAdSlot), placeKey)
    }

    private fun close() {
        if (closed) return
        closed = true
        findViewById<ImageButton>(R.id.btnCloseCharging).isEnabled = false
        Ads.exit(this, placeKey) { finishSafely() }
    }

    private fun finishSafely() {
        if (!isFinishing) finish()
    }

    private fun showOverLockAndWake() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    companion object {
        /** The RC place: [Ads.CHARGER_CONNECTED] or [Ads.CHARGER_DISCONNECTED]. */
        const val EXTRA_PLACE = "ad_place"
    }
}
