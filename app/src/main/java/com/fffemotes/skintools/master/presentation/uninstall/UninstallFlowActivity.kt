package com.fffemotes.skintools.master.presentation.uninstall

import android.animation.ObjectAnimator
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.addCallback
import com.fffemotes.skintools.master.presentation.common.enableDarkEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fffemotes.skintools.ads.Ads
import com.fffemotes.skintools.ads.ExitAdRunner
import com.fffemotes.skintools.ads.analytics.Analytics
import com.fffemotes.skintools.ads.analytics.Screens
import com.fffemotes.skintools.master.App
import com.fffemotes.skintools.master.R
import com.fffemotes.skintools.master.data.remote.config.Track
import com.fffemotes.skintools.master.databinding.ActivitySplashBinding
import com.fffemotes.skintools.master.databinding.ActivityUninstallBinding
import com.fffemotes.skintools.master.databinding.ItemToolBinding
import com.fffemotes.skintools.master.databinding.ItemUninstallReasonBinding
import com.fffemotes.skintools.master.launcherhost.OurIcon
import com.fffemotes.skintools.master.presentation.common.glowPulse
import com.fffemotes.skintools.master.presentation.common.padForSystemBars
import com.fffemotes.skintools.master.presentation.home.HomeActivity
import com.fffemotes.skintools.master.presentation.onboarding.OptionAdapter
import com.fffemotes.skintools.master.presentation.onboarding.languageStep
import com.fffemotes.skintools.master.presentation.settings.SettingsActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The reference app's premium uninstall flow (`uninstall_flow.flow` = "premium"), opened by Uninstall in the long-press
 * menu on our icon:
 *  - paid (track_b): splash → language → step1 (quick fixes) → step2 (reasons) → progress → step3 → our icon leaves the
 *    launcher home and drawer and this screen closes; the app stays installed.
 *  - organic (track_a): starts on step1 (no splash, no language) → step2 → progress → step3 → Android's App info page.
 * Each screen but the splash shows `uninstall_flow.<screen>`'s bottom ad and moving forward runs its exit ad. Keep / a quick
 * fix go back to the app.
 */
class UninstallFlowActivity : AppCompatActivity() {
    private enum class Screen { SPLASH, LANGUAGE, STEP1, STEP2, PROGRESS, STEP3 }

    /** Each reason has a quick fix; step 1 shows [TIPS] of them. */
    private enum class Reason(val label: Int, val fix: Int, val fixButton: Int, val icon: Int) {
        BROKEN(R.string.un_r_broken, R.string.un_fix_broken, R.string.un_fix_broken_btn, R.drawable.ic_replay),
        ADS(R.string.un_r_ads, R.string.un_fix_ads, R.string.un_fix_settings_btn, R.drawable.ic_diamond),
        CONTENT(R.string.un_r_content, R.string.un_fix_content, R.string.un_fix_content_btn, R.drawable.ic_search),
        HARD(R.string.un_r_hard, R.string.un_fix_hard, R.string.un_fix_settings_btn, R.drawable.ic_settings),
        NOT_NEEDED(R.string.un_r_not_needed, R.string.un_fix_not_needed, R.string.sh_home, R.drawable.ic_home),
    }

    private lateinit var b: ActivityUninstallBinding
    private val container get() = (application as App).container
    private var screen = Screen.STEP1
    private var picked: Reason? = null
    private var paid = false
    private var splash: ActivitySplashBinding? = null
    private var timer: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = container.config.value
        if (!config.uninstallFlow.premium) return finish() // normal flow: there is no Uninstall entry that opens this
        enableDarkEdgeToEdge()
        b = ActivityUninstallBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.content.padForSystemBars()
        b.glow.glowPulse()
        b.header.title.setText(R.string.un_title)
        b.header.back.setOnClickListener { Analytics.click("back"); goBack() }
        paid = config.track == Track.B
        screen = savedInstanceState?.getString(KEY_SCREEN)?.let(Screen::valueOf) ?: if (paid) Screen.SPLASH else Screen.STEP1
        picked = savedInstanceState?.getString(KEY_REASON)?.let(Reason::valueOf)
        if (savedInstanceState == null) Analytics.step(Analytics.UNINSTALL, "start")
        onBackPressedDispatcher.addCallback(this) { Analytics.keyPress("back"); goBack() }
        show(screen)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SCREEN, screen.name)
        picked?.let { outState.putString(KEY_REASON, it.name) }
    }

    /** The language picked here applied in place (manifest configChanges, as the reference): the page again in it. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        b.header.title.setText(R.string.un_title)
        if (screen != Screen.SPLASH && screen != Screen.PROGRESS) show(screen)
    }

    override fun onResume() {
        super.onResume()
        Screens.show("uninstall_flow")
    }

    /** As the reference: splash and progress move on by themselves; language keeps the current pick; step1 → reasons. */
    private fun goBack() = when (screen) {
        Screen.SPLASH, Screen.PROGRESS -> Unit
        Screen.LANGUAGE -> pickLanguage(container.settingsRepository.currentLanguageIndex())
        Screen.STEP1 -> leave { show(Screen.STEP2) }
        Screen.STEP2 -> show(Screen.STEP1)
        Screen.STEP3 -> show(Screen.STEP2)
    }

    private fun show(next: Screen) {
        screen = next
        timer?.cancel()
        Analytics.step(Analytics.UNINSTALL, next.name.lowercase())
        val flow = container.config.value.uninstallFlow
        splash?.root?.let(b.root::removeView)
        splash = null
        // Every page shows its own parts; the rest is hidden.
        listOf(b.header.root, b.pageTitle, b.sub, b.list, b.rowsScroll, b.center, b.progress, b.percent, b.primary, b.secondary)
            .forEach { it.visibility = View.GONE }
        b.adSlot.removeAllViews()
        if (next != Screen.SPLASH) Ads.bottom(this, b.adSlot, place())
        when (next) {
            Screen.SPLASH -> showSplash(flow.splashMs)
            Screen.LANGUAGE -> {
                show(b.pageTitle, b.list, b.primary)
                b.pageTitle.setText(R.string.un_language_title)
                // A tap marks the row and moves on after 250 ms; Continue moves on with the current pick.
                var pick = container.settingsRepository.currentLanguageIndex()
                b.list.layoutManager = LinearLayoutManager(this)
                b.list.adapter = OptionAdapter(languageStep(container.settingsRepository.languages), setOf(pick)) {
                    pick = it
                    (b.list.adapter as OptionAdapter).submitSelection(setOf(it))
                    b.list.postDelayed({ if (screen == Screen.LANGUAGE) pickLanguage(it) }, 250)
                }
                b.primary.setText(R.string.next)
                b.primary.setOnClickListener { pickLanguage(pick) }
            }
            Screen.STEP1 -> {
                show(b.header.root, b.pageTitle, b.sub, b.rowsScroll, b.primary, b.secondary)
                b.pageTitle.setText(R.string.un_sorry_title)
                b.sub.setText(R.string.un_sorry_body)
                b.rows.removeAllViews()
                TIPS.forEach { r ->
                    ItemToolBinding.inflate(layoutInflater, b.rows, true).apply {
                        icon.setImageResource(r.icon)
                        name.setText(r.label)
                        sub.setText(r.fix)
                        root.setOnClickListener { fix(r) }
                    }
                }
                b.primary.setText(R.string.un_keep)
                b.primary.setOnClickListener { keepApp() }
                b.secondary.setText(R.string.un_still)
                b.secondary.setOnClickListener { Analytics.click("still_uninstall"); leave { show(Screen.STEP2) } }
            }
            Screen.STEP2 -> {
                show(b.header.root, b.pageTitle, b.rowsScroll, b.secondary)
                b.pageTitle.setText(R.string.un_reason_title)
                bindReasons()
                b.secondary.setText(R.string.un_keep)
                b.secondary.setOnClickListener { keepApp() }
            }
            Screen.PROGRESS -> {
                show(b.center, b.progress, b.percent)
                b.centerTitle.text = getString(R.string.un_progress, getString(R.string.app_name))
                b.centerSub.setText(R.string.un_progress_sub)
                runProgress(flow.progressMs.coerceIn(500L, 20_000L))
            }
            Screen.STEP3 -> {
                show(b.header.root, b.center, b.primary, b.secondary)
                b.centerTitle.setText(R.string.un_confirm_title)
                b.centerSub.setText(R.string.un_confirm_body)
                b.primary.setText(R.string.un_keep)
                b.primary.setOnClickListener { keepApp() }
                b.secondary.setText(R.string.un_uninstall)
                b.secondary.setOnClickListener { Analytics.click("uninstall"); leave { end() } }
                // As the reference: no tap within 3 s ends the flow.
                timer = lifecycleScope.launch { delay(3_000); leave { end() } }
            }
        }
    }

    private fun show(vararg views: View) = views.forEach { it.visibility = View.VISIBLE }

    /** The app's own splash (art, logo, name, bar) for `splash_ms`, as the reference shows its splash screen here. */
    private fun showSplash(durationMs: Long) {
        val s = ActivitySplashBinding.inflate(layoutInflater, b.root, false)
        b.root.addView(s.root)
        s.content.padForSystemBars()
        splash = s
        ObjectAnimator.ofInt(s.progress, "progress", 0, 100).setDuration(durationMs).start()
        timer = lifecycleScope.launch { delay(durationMs); leaveWhenFree { show(Screen.LANGUAGE) } }
    }

    /** Step 2: a picked reason opens its fix, the fix button and "Uninstall anyway" (→ progress). */
    private fun bindReasons() {
        b.rows.removeAllViews()
        Reason.entries.forEach { r ->
            val selected = picked == r
            ItemUninstallReasonBinding.inflate(layoutInflater, b.rows, true).apply {
                card.strokeColor = getColor(if (selected) R.color.sh_primary else R.color.sh_border)
                radio.setImageResource(if (selected) R.drawable.ic_radio_checked else R.drawable.ic_radio_unchecked)
                if (selected) radio.setColorFilter(getColor(R.color.sh_primary))
                label.setText(r.label)
                row.setOnClickListener {
                    Analytics.click("reason", r.name.lowercase())
                    Analytics.step(Analytics.UNINSTALL, "reason", r.name.lowercase())
                    picked = r
                    bindReasons()
                }
                fix.visibility = if (selected) View.VISIBLE else View.GONE
                fixText.setText(r.fix)
                fixButton.setText(r.fixButton)
                fixButton.setOnClickListener { fix(r) }
                anyway.setOnClickListener { Analytics.click("uninstall_anyway"); leave { show(Screen.PROGRESS) } }
            }
        }
    }

    /** 0 → 100 % over [durationMs], "Ready to uninstall" for 1.2 s, then step 3. */
    private fun runProgress(durationMs: Long) {
        b.progress.progress = 0
        b.percent.setTextColor(getColor(R.color.sh_muted))
        val start = SystemClock.elapsedRealtime()
        timer = lifecycleScope.launch {
            while (true) {
                val p = ((SystemClock.elapsedRealtime() - start) * 100 / durationMs).toInt().coerceAtMost(100)
                b.progress.progress = p
                b.percent.text = getString(R.string.un_progress_percent, p)
                if (p >= 100) break
                delay(40)
            }
            b.percent.setText(R.string.un_progress_done)
            b.percent.setTextColor(getColor(R.color.sh_primary))
            delay(1_200)
            leaveWhenFree { show(Screen.STEP3) }
        }
    }

    private fun place() = "uninstall_flow.${screen.name.lowercase()}"

    private fun pickLanguage(index: Int) {
        Analytics.click("language", container.settingsRepository.languages[index].tag)
        leave {
            show(Screen.STEP1)
            container.settingsRepository.setLanguage(index) // in place: onConfigurationChanged redraws step 1
        }
    }

    /** Runs the screen's exit ad, then [action]: once, even on double taps. */
    private fun leave(action: () -> Unit) {
        if (ExitAdRunner.busy()) return
        val from = screen
        Ads.exit(this, place()) { if (screen == from && !isFinishing) action() }
    }

    /** Screens that move on by themselves wait out an ad still on screen instead of dropping the step. */
    private fun leaveWhenFree(action: () -> Unit) {
        lifecycleScope.launch {
            while (ExitAdRunner.busy()) delay(300)
            leave(action)
        }
    }

    private fun keepApp() {
        Analytics.click("keep")
        Analytics.step(Analytics.UNINSTALL, "keep")
        startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    /** A quick fix: back into the app at the place that fixes it. */
    private fun fix(r: Reason) {
        Analytics.click("fix", r.name.lowercase())
        Analytics.step(Analytics.UNINSTALL, "fix", r.name.lowercase())
        val home = Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        when (r) {
            Reason.BROKEN -> startActivity(home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK))
            Reason.ADS, Reason.HARD -> startActivities(arrayOf(home, Intent(this, SettingsActivity::class.java)))
            Reason.CONTENT, Reason.NOT_NEEDED -> startActivity(home)
        }
        finish()
    }

    /** As the reference: paid hides our icon (the app stays installed, the user is back on the launcher); organic opens App info. */
    private fun end() {
        timer?.cancel()
        endUninstall(this, paid)
        finish()
    }

    private companion object {
        const val KEY_SCREEN = "screen"
        const val KEY_REASON = "reason"
        val TIPS = listOf(Reason.BROKEN, Reason.CONTENT, Reason.HARD)
    }
}

/** The end of the premium uninstall (flow or dialog), as the reference: paid hides our icon; organic opens App info. */
internal fun endUninstall(activity: android.app.Activity, paid: Boolean) {
    if (paid) {
        Analytics.step(Analytics.UNINSTALL, "hidden")
        OurIcon.hide(activity)
    } else {
        Analytics.step(Analytics.UNINSTALL, "app_info")
        runCatching {
            activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)))
        }
    }
}
