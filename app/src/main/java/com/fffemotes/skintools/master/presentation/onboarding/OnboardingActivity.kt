package com.fffemotes.skintools.master.presentation.onboarding

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import java.lang.ref.WeakReference
import java.util.Locale
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.os.Bundle
import android.content.res.Configuration
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.activity.addCallback
import com.fffemotes.skintools.master.presentation.common.enableDarkEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import androidx.recyclerview.widget.GridLayoutManager
import com.fffemotes.skintools.ads.AdKit
import com.fffemotes.skintools.ads.Ads
import com.fffemotes.skintools.ads.ExitAdRunner
import com.fffemotes.skintools.ads.analytics.Analytics
import com.fffemotes.skintools.ads.analytics.Screens
import com.fffemotes.skintools.launcher.Launcher
import com.fffemotes.skintools.master.App
import com.fffemotes.skintools.master.R
import com.fffemotes.skintools.master.core.system.MainScreens
import com.fffemotes.skintools.master.core.system.RecentsPolicy
import com.fffemotes.skintools.master.core.system.SystemKeyTarget
import com.fffemotes.skintools.master.data.remote.config.SystemKey
import com.fffemotes.skintools.master.databinding.ActivityOnboardingBinding
import com.fffemotes.skintools.master.databinding.SheetOnboardingBinding
import com.fffemotes.skintools.master.presentation.common.accentBold
import com.fffemotes.skintools.master.presentation.common.appViewModel
import com.fffemotes.skintools.master.presentation.common.collectWhenStarted
import com.fffemotes.skintools.master.presentation.common.glowPulse
import com.fffemotes.skintools.master.presentation.common.hideNavigationBar
import com.fffemotes.skintools.master.presentation.common.padForSystemBars
import com.fffemotes.skintools.master.presentation.common.shine
import com.fffemotes.skintools.master.presentation.home.HomeActivity
import com.fffemotes.skintools.master.presentation.permissions.Permissions
import com.fffemotes.skintools.master.presentation.onboarding.OnboardingRules.KeyResult
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.intuit.sdp.R as SdpR
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Onboarding, Claude Design "Skin Hub Onboarding v2", with the reference app's onboarding behaviour: Remote Config order,
 * an exit ad on every way forward, system keys (`launcher.system_keys`), the bottom sheet and the set-as-default-home step.
 */
class OnboardingActivity : AppCompatActivity(), SystemKeyTarget {
    private val viewModel by appViewModel { handle ->
        OnboardingViewModel(settingsRepository, config.value, Launcher.isDefaultHome(this@OnboardingActivity), handle)
    }
    private val config get() = (application as App).container.config.value
    private lateinit var b: ActivityOnboardingBinding
    /** Where Continue is pinned, and its layout there, to put it back after a step showed it in the list. */
    private lateinit var ctaHome: ViewGroup
    private lateinit var ctaParams: ViewGroup.LayoutParams
    private var adapter: OptionAdapter? = null
    private var shownStep = -1

    /** Home / Back / Recents for the flow; acted on only while this screen is resumed and no exit ad runs. */
    private val systemKeys = Channel<SystemKey>(Channel.CONFLATED)
    private var keyHandledAt = 0L
    private var sheet: BottomSheetDialog? = null

    // default_home loop: Settings list with the hint → back → role dialog; not set → stay, and Back / Home / the button
    // start it again. Only setting us as home moves on.
    private var askingHome = false
    private var homeGrantedAt = 0L
    private var leftWithHome = false
    private val homeDemo by lazy { HomeSetupDemo(b.homeDemo) }
    private var waitedForLocale = false
    private val finishAnyway = Runnable { finishOnboarding() }
    private val reAsk = Runnable { if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && currentKey() == DEFAULT_HOME_KEY) askDefaultHome() }

    /** Home while we are not the default home yet: the launcher can't see it, so the flow hears it here. */
    private val keyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getStringExtra("reason") == "homekey" && !Launcher.isDefaultHome(context)) systemKey(SystemKey.HOME)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()
        b = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.content.padForSystemBars()
        b.glow.glowPulse()
        b.ctaShine.shine(b.cta, 3200)
        b.cta.clipToOutline = true
        ctaHome = b.cta.parent as ViewGroup
        ctaParams = b.cta.layoutParams
        // A key forwarded to a fresh screen was the press that reopened it, not one on our step.
        if (savedInstanceState == null) intent.removeExtra(SystemKeyTarget.EXTRA_SYSTEM_KEY)

        b.back.setOnClickListener { Analytics.click("back"); viewModel.back() }
        b.skip.setOnClickListener { Analytics.click("skip"); advance() }
        b.next.setOnClickListener {
            Analytics.click("continue")
            primaryAction()
        }
        b.favAll.setOnClickListener { viewModel.toggleAllFavourites() }
        onBackPressedDispatcher.addCallback(this) { Analytics.keyPress("back"); onSystemKey(SystemKey.BACK) }

        collectWhenStarted(viewModel.state, ::render)
        collectWhenStarted(viewModel.finished) { finishOnboarding() }
        lifecycleScope.launch { for (key in systemKeys) lifecycle.withResumed { onSystemKey(key) } }
        // Back after Recents removed our task with the setup sheet open: as the reference, it opens again.
        if (savedInstanceState == null && viewModel.resumed && OnboardingProgress.sheet) b.root.post { showSheet() }
        // The language picked on the last step relaunches this screen on some phones (despite configChanges): the new
        // one, already in that language, goes straight on to Home before it is ever drawn.
        if (viewModel.done) finishOnboarding()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        SystemKey.entries.firstOrNull { it.key == intent.getStringExtra(SystemKeyTarget.EXTRA_SYSTEM_KEY) }?.let(::systemKey)
        intent.removeExtra(SystemKeyTarget.EXTRA_SYSTEM_KEY)
    }

    override fun systemKey(key: SystemKey) {
        // Pressed while an exit ad (ad or links) is running: dropped, so it can't act once the ad ends.
        if (!ExitAdRunner.busy()) systemKeys.trySend(key)
    }

    override fun onStart() {
        super.onStart()
        if (currentKey() == DEFAULT_HOME_KEY) {
            if (Launcher.isDefaultHome(this)) homeGranted() else homeDemo.start()
        }
    }

    override fun onStop() {
        // Away in Settings for the request: the step stays blank until its result, so after picking us the next step is
        // the first thing shown (some phones report the role late, after our first frame).
        if (askingHome) b.content.visibility = View.INVISIBLE
        homeDemo.stop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Our launcher handed HOME back to us and we are on screen: its loop guard starts over, so fast HOME taps
        // during onboarding never make it give up and show the launcher home (the user stays in onboarding).
        Launcher.hostShown()
        live = WeakReference(this)
        if (grantPending) grantAdOver()
        // The sheet opens on Home / Recents, which pause us for a moment: the guide carries on where it was.
        if (voicePausedByLeaving) {
            voicePausedByLeaving = false
            voice?.start()
        }
        @Suppress("DEPRECATION") // still delivered to apps for Home/Recents
        ContextCompat.registerReceiver(this, keyReceiver, IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS), ContextCompat.RECEIVER_EXPORTED)
        if (leftWithHome) {
            leftWithHome = false
            askDefaultHome()
        }
    }

    override fun onPause() {
        voice?.takeIf { it.isPlaying }?.let { it.pause(); voicePausedByLeaving = true }
        runCatching { unregisterReceiver(keyReceiver) }
        super.onPause()
    }

    override fun onDestroy() {
        releaseVoice()
        window.decorView.removeCallbacks(reAsk)
        window.decorView.removeCallbacks(finishAnyway) // relaunched: the new screen takes over
        if (isFinishing) DefaultHomeStep.shown = false
        super.onDestroy()
    }

    /** The step's main button: default_home asks to be home, permission_hub asks its permissions first, others go on. */
    private fun primaryAction() = when (currentKey()) {
        DEFAULT_HOME_KEY -> askDefaultHome()
        PERMISSION_HUB_KEY -> { Analytics.click("allow"); Permissions.askFor(this, PERMISSION_HUB_KEY) { advance() } }
        else -> advance()
    }

    /** Continue back in its pinned spot above the bottom ad (it may be in the list from the previous step). */
    private fun pinCta() {
        if (b.cta.parent === ctaHome) return
        (b.cta.parent as? ViewGroup)?.removeView(b.cta)
        ctaHome.addView(b.cta, ctaHome.indexOfChild(b.adSlot), ctaParams)
    }

    private fun place(step: Step) = "onboarding.${step.key}"

    private fun currentKey() = viewModel.steps.getOrNull(viewModel.state.value.stepIndex)?.key

    /**
     * The ONE way forward: Next, Continue, Skip, bottom sheet Continue, and a system key's `next`. Runs the step's exit
     * ad first; a [key] whose button ad is on during onboarding runs that ad instead (one ad per press).
     */
    private fun advance(key: SystemKey? = null) {
        if (ExitAdRunner.busy()) return
        closeSheet()
        val step = currentKey() ?: return viewModel.skip()
        val place = key?.takeIf { Ads.keyAdDuringOnboarding(it.key) }?.let { "launcher.${it.key}_button" } ?: "onboarding.$step"
        Ads.exit(this, place) {
            if (key == null) Screens.exitBy("next") // a system key already said how the visit ends
            viewModel.next()
        }
    }

    private fun onSystemKey(key: SystemKey) {
        // One press at a time: while its action (and the exit ad it runs) is going, other presses are ignored.
        val now = SystemClock.elapsedRealtime()
        if (ExitAdRunner.busy() || now - keyHandledAt < KEY_REPEAT_MS) return
        keyHandledAt = now
        if (key == SystemKey.HOME && now - homeGrantedAt < 5_000) return
        val notHome = !Launcher.isDefaultHome(this)
        // Only the grant moves on from default_home.
        if (currentKey() == DEFAULT_HOME_KEY) {
            when (key) {
                SystemKey.HOME -> if (notHome) leftWithHome = true
                SystemKey.BACK -> askDefaultHome()
                SystemKey.RECENTS -> Unit
            }
            return
        }
        if (notHome && key == SystemKey.HOME) return // Home keys elsewhere work only while we are the default home
        fun keyAd(then: () -> Unit) {
            if (!Ads.keyAdDuringOnboarding(key.key)) return then()
            if (!ExitAdRunner.busy()) Ads.exit(this, "launcher.${key.key}_button", then)
        }
        when (val r = OnboardingRules.onKey(config.systemKeys, key, viewModel.state.value.stepIndex, sheetOpen = sheet?.isShowing == true)) {
            KeyResult.ShowSheet -> showSheet()
            KeyResult.Next -> advance(key)
            is KeyResult.GoTo -> keyAd { closeSheet(); viewModel.goTo(r.index) }
            KeyResult.LeaveApp -> keyAd { finish() }
            // Back like Android's own: the app goes to the background and onboarding waits where it is.
            KeyResult.DeviceDefault -> if (key == SystemKey.BACK && !moveTaskToBack(true)) finish()
            KeyResult.Nothing -> keyAd {}
        }
    }

    /**
     * `system_keys.bottom_sheet`: the reference app's "Finish setup" sheet: progress, the step up next, and (from the sheet's
     * second opening) the voice guide, auto-played in the app's language. Continue setup and Go to Home go forward; the close button closes it.
     */
    private fun closeSheet() {
        OnboardingProgress.sheet = false
        sheet?.dismiss()
    }

    private fun showSheet() {
        if (sheet?.isShowing == true) return
        Screens.show("onboarding_bottom_sheet")
        val prefs = getSharedPreferences("onboarding_sheet", MODE_PRIVATE)
        val opens = prefs.getInt("opened", 0) + 1
        prefs.edit().putInt("opened", opens).apply()

        // Counts only the OB_ screens, as the step tracker: welcome / permission_hub / default_home sit outside it.
        val all = viewModel.steps
        val at = viewModel.state.value.stepIndex
        val current = all[at]
        val steps = all.filter { it.key !in INFO_STEPS }.ifEmpty { all }
        val index = steps.indexOf(current).takeIf { it >= 0 } ?: steps.count { all.indexOf(it) < at }.coerceAtMost(steps.lastIndex)
        val v = SheetOnboardingBinding.inflate(layoutInflater)
        val number = String.format(Locale.getDefault(), "%02d", index + 1)
        v.sheetBadge.text = number
        v.sheetEyebrow.text = getString(R.string.sh_mission, number, steps.size.toString())
        v.sheetNext.setText(current.name)
        bindSegments(v.sheetSegments, index, steps.size)
        v.sheetSegments.contentDescription = getString(R.string.sh_done, index, steps.size)
        v.sheetDone.text = getString(R.string.sh_done, index, steps.size)
        v.sheetXp.text = getString(R.string.sh_xp, index * 100)
        v.sheetTitle.text = getText(R.string.sh_title).accentBold(v.sheetTitle)
        val fraction = index.toFloat() / steps.size
        val mood = getString(if (fraction < 1f / 3) R.string.sh_mood_start else if (fraction < 2f / 3) R.string.sh_mood_half else R.string.sh_mood_almost)
        val left = steps.size - index
        v.sheetMood.text = SpannableStringBuilder(mood).apply {
            setSpan(ForegroundColorSpan(ContextCompat.getColor(this@OnboardingActivity, R.color.sh_primary)), 0, length, 0)
            setSpan(StyleSpan(Typeface.BOLD), 0, length, 0)
            append(" · ").append(resources.getQuantityString(R.plurals.sh_to_go, left, left))
        }
        v.sheetSaved.text = SpannableStringBuilder(getString(R.string.sh_saved)).apply {
            setSpan(ForegroundColorSpan(0xFFC4CEE6.toInt()), 0, length, 0)
            setSpan(StyleSpan(Typeface.BOLD), 0, length, 0)
            append(" ").append(getString(R.string.sh_saved_text))
        }
        v.sheetGlow.glowPulse()
        ObjectAnimator.ofFloat(v.sheetRing, View.ROTATION, 0f, 360f).apply {
            duration = 9000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        v.sheetShine.shine(v.sheetCta, 2600)
        if (opens >= 2) bindVoiceGuide(v, index)
        v.sheetContinue.setOnClickListener { Analytics.click("continue"); advance() }
        // As the reference: "Go to Home" moves on like Continue; only the close button (or Back) closes the sheet.
        v.sheetClose.setOnClickListener { Analytics.click("go_home"); advance() }
        v.sheetX.setOnClickListener { Analytics.click("dismiss"); closeSheet() }
        OnboardingProgress.sheet = true
        sheet = BottomSheetDialog(this).apply {
            setContentView(v.root)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            // Modal, as the reference: a tap outside or a swipe down doesn't close it.
            setCanceledOnTouchOutside(false)
            behavior.isDraggable = false
            // As the reference: Back over the sheet is the system key's own action (system_keys), not a plain close.
            onBackPressedDispatcher.addCallback { Analytics.keyPress("back"); onSystemKey(SystemKey.BACK) }
            // Dismiss arrives posted: a sheet reopened meanwhile owns the voice now, so an old sheet leaves it alone.
            setOnDismissListener { if (sheet === this) releaseVoice() }
            show()
            window?.hideNavigationBar()
            (v.root.parent as? View)?.setBackgroundColor(Color.TRANSPARENT) // our sheet draws its own rounded surface
        }
    }

    /** The design's segmented progress: done steps lit, the current one wider and pulsing, the rest dim. */
    private fun bindSegments(row: LinearLayout, index: Int, count: Int) {
        val gap = resources.getDimensionPixelSize(SdpR.dimen._3sdp)
        val radius = 2 * resources.displayMetrics.density
        val accent = ContextCompat.getColor(this, R.color.sh_primary)
        for (k in 0 until count) {
            val seg = View(this).apply {
                background = GradientDrawable().apply {
                    cornerRadius = radius
                    setColor(if (k <= index) accent else 0x1AFFFFFF)
                }
                if (k == index) ObjectAnimator.ofFloat(this, View.ALPHA, 0.35f, 1f).apply {
                    duration = 800
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    start()
                }
            }
            row.addView(seg, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, if (k == index) 2.4f else 1f).apply {
                if (k > 0) marginStart = gap
            })
        }
    }

    private var voice: MediaPlayer? = null
    private var voicePausedByLeaving = false

    /** The voice guide card: play / pause / replay the clip in the app's language. */
    private fun bindVoiceGuide(v: SheetOnboardingBinding, index: Int) {
        val settings = (application as App).container.settingsRepository
        releaseVoice() // an earlier sheet's player, if its dismiss hasn't run yet
        val player = MediaPlayer.create(this, voiceClip(settings.languages[settings.currentLanguageIndex()].tag)) ?: return
        voice = player
        v.voiceCard.visibility = View.VISIBLE
        v.voiceWave.seed(index)
        val ripple = ObjectAnimator.ofPropertyValuesHolder(
            v.voiceRipple,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.7f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.7f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.6f, 0f),
        ).apply { duration = 1300; repeatCount = ValueAnimator.INFINITE }
        fun time(ms: Int) = (ms / 1000).let { "%d:%02d".format(Locale.getDefault(), it / 60, it % 60) }
        // Play head, time and waveform follow the clip while the sheet is open.
        val tick = object : Runnable {
            override fun run() {
                val p = voice ?: return
                v.voiceWave.progress = p.currentPosition.toFloat() / p.duration.coerceAtLeast(1)
                v.voiceTime.text = time(if (p.isPlaying) p.currentPosition else p.duration)
                if (p.isPlaying) v.voiceWave.postDelayed(this, 80)
            }
        }
        fun show(playing: Boolean) {
            v.voicePlay.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
            v.voicePlay.contentDescription = getString(if (playing) R.string.sh_pause else R.string.sh_play)
            v.voiceWave.playing = playing
            if (playing) { ripple.start(); v.voiceWave.post(tick) } else { ripple.cancel(); v.voiceRipple.alpha = 0f }
        }
        v.voiceTime.text = time(player.duration)
        player.setOnCompletionListener {
            show(false)
            v.voiceWave.progress = 1f
            v.voicePlay.setImageResource(R.drawable.ic_replay)
            v.voicePlay.contentDescription = getString(R.string.sh_replay)
            v.voiceText.setText(R.string.sh_voice_done)
        }
        v.voicePlay.setOnClickListener {
            Analytics.click("voice_guide")
            if (player.isPlaying) player.pause() else {
                v.voiceText.setText(R.string.sh_voice_text)
                player.start()
            }
            show(player.isPlaying)
        }
        player.start() // auto-plays, as the reference
        show(true)
    }

    private fun releaseVoice() {
        voice?.release()
        voice = null
        voicePausedByLeaving = false
    }

    private fun voiceClip(tag: String) = when (tag) {
        "hi" -> R.raw.ob_voice_hi
        "id" -> R.raw.ob_voice_in
        "tr" -> R.raw.ob_voice_tr
        "vi" -> R.raw.ob_voice_vi
        "ar" -> R.raw.ob_voice_ar
        "fr" -> R.raw.ob_voice_fr
        "de" -> R.raw.ob_voice_de
        "ja" -> R.raw.ob_voice_ja
        else -> R.raw.ob_voice_en
    }

    private fun onDefaultHomeStep(onIt: Boolean) {
        DefaultHomeStep.shown = onIt
        // The reference's animated demo replaces the option list on this step; its button carries the home icon.
        b.list.visibility = if (onIt) View.GONE else View.VISIBLE
        b.homeDemo.root.visibility = if (onIt) View.VISIBLE else View.GONE
        b.next.icon = if (onIt) ContextCompat.getDrawable(this, R.drawable.ic_home) else null
        b.next.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
        if (onIt) homeDemo.start() else homeDemo.stop()
        if (onIt) askDefaultHome() // Settings opens straight away
    }

    private fun askDefaultHome() {
        if (askingHome) return
        askingHome = true
        Launcher.requestDefaultHome(this) { granted ->
            askingHome = false
            b.content.visibility = View.VISIBLE
            if (granted) homeGranted() else declined()
        }
    }

    /** Cancelled: the step shows for `default_home.auto_open_settings_sec`, then Settings opens again (only while on screen). */
    private fun declined() {
        val sec = config.onboarding.defaultHomeAutoOpenSettingsSec
        window.decorView.removeCallbacks(reAsk)
        if (sec > 0) window.decorView.postDelayed(reAsk, sec * 1_000L)
    }

    /** We're the default home now: on to the next step, default_home's exit ad over it; then onboarding moves to the home task. */
    private fun homeGranted() {
        // Picking us as default home closes this screen and opens a new one, which (as we are default home now) starts
        // past default_home. The grant then lands on the old, destroyed screen, where no ad can show: the live screen
        // takes it over and runs default_home's exit ad itself.
        if (isDestroyed || isFinishing) {
            live?.get()?.takeIf { !it.isDestroyed && !it.isFinishing && it !== this }?.grantAdOver() ?: run { grantPending = true }
            return
        }
        grantPending = false
        if (currentKey() != DEFAULT_HOME_KEY) return
        homeGrantedAt = SystemClock.elapsedRealtime()
        window.decorView.removeCallbacks(reAsk)
        DefaultHomeStep.shown = false
        if (viewModel.state.value.stepIndex < viewModel.steps.lastIndex && !ExitAdRunner.busy()) {
            Screens.exitBy("next")
            viewModel.next()
            Ads.exit(this, "onboarding.$DEFAULT_HOME_KEY") { RecentsPolicy.moveToHomeTask(this) }
        } else advance()
    }

    /** A grant this screen didn't see (it opened past default_home): default_home's exit ad runs over the current step. */
    private fun grantAdOver() {
        grantPending = false
        if (currentKey() == DEFAULT_HOME_KEY) return homeGranted()
        if (ExitAdRunner.busy()) return
        homeGrantedAt = SystemClock.elapsedRealtime()
        Ads.exit(this, "onboarding.$DEFAULT_HOME_KEY") { RecentsPolicy.moveToHomeTask(this) }
    }

    /**
     * Done: always the app home first. With our launcher as the default home, it is started in Android's home stack behind
     * the app, so Back from the app home lands on it.
     */
    private fun finishOnboarding() {
        if (isFinishing) return
        // A language picked on the last step reaches us a moment later (onConfigurationChanged): Home opened before then
        // stays in the old language. Wait for it, with a cap so a missed change never strands the user here.
        val wanted = AppCompatDelegate.getApplicationLocales()[0]
        if (!waitedForLocale && wanted != null && wanted.language != resources.configuration.locales[0].language) {
            waitedForLocale = true
            window.decorView.postDelayed(finishAnyway, LOCALE_WAIT_MS)
            return
        }
        window.decorView.removeCallbacks(finishAnyway)
        val home = Intent(this, HomeActivity::class.java)
        if (config.launcher.on && Launcher.isDefaultHome(this)) {
            runCatching {
                startActivities(arrayOf(
                    MainScreens.launcherHomeIntent(this).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION),
                    home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
                ))
            }.onFailure { startActivity(home) }
        } else startActivity(home)
        finish()
    }

    /**
     * The step's words. As the reference, a tap on the language step previews that language on this screen (name,
     * title, subtitle, buttons); Continue then applies it app-wide in place (manifest configChanges: no recreate).
     */
    private fun bindTexts(step: Step, state: OnboardingUiState) {
        val preview = state.selection.firstOrNull()?.takeIf { step.key == LANG_KEY }
            ?.let { viewModel.languageTag(it) }?.let(::inLanguage) ?: this
        b.stepName.text = preview.getText(step.name)
        b.title.text = preview.getText(step.title).accentBold(b.title)
        b.sub.text = preview.getText(step.sub)
        b.skip.text = preview.getText(R.string.skip)
        b.back.contentDescription = preview.getText(R.string.back)
        b.next.text = preview.getText(
            when {
                step.key == DEFAULT_HOME_KEY -> R.string.dh_button
                step.key == PERMISSION_HUB_KEY -> R.string.perm_allow
                step.key == WELCOME_KEY -> R.string.wl_button
                state.stepIndex == viewModel.steps.lastIndex -> R.string.get_started
                else -> R.string.next
            }
        )
    }

    /** This screen's resources in [tag]'s language, without changing the app's language. */
    private fun inLanguage(tag: String): Context =
        createConfigurationContext(Configuration(resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })

    /** The language applied app-wide in place (no recreate): every word on screen again in the new language. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        viewModel.steps.getOrNull(viewModel.state.value.stepIndex)?.let { bindTexts(it, viewModel.state.value) }
        (b.list.adapter as? OptionAdapter)?.let { it.notifyItemRangeChanged(0, it.itemCount) }
        if (waitedForLocale) finishOnboarding()
    }

    private fun render(state: OnboardingUiState) {
        val steps = viewModel.steps
        if (steps.isEmpty()) return // finishing: Remote Config turned every step off
        val step = steps[state.stepIndex]
        bindTexts(step, state)
        if (state.stepIndex != shownStep) {
            val dir = if (state.stepIndex >= shownStep) 1 else -1
            val first = shownStep == -1
            shownStep = state.stepIndex
            val hasBanner = step.banner != 0
            b.bannerBox.visibility = if (hasBanner) View.VISIBLE else View.GONE
            if (hasBanner) {
                b.banner.setImageResource(step.banner)
                b.banner.scaleX = 1.12f; b.banner.scaleY = 1.12f
                b.banner.animate().scaleX(1f).scaleY(1f).setDuration(900).start()
            }
            b.back.visibility = if (state.stepIndex == 0) View.INVISIBLE else View.VISIBLE
            // As the reference: the tracker counts only the OB_ screens; welcome / permission_hub / default_home sit outside it.
            val obSteps = steps.filter { it.key !in INFO_STEPS }
            val obNumber = obSteps.indexOf(step) + 1
            b.tracker.visibility = if (obNumber > 0) View.VISIBLE else View.INVISIBLE
            b.progressFrame.visibility = b.tracker.visibility
            b.progress.max = obSteps.size.coerceAtLeast(1)
            b.stepNum.text = "%02d".format(obNumber)
            b.stepTotal.text = getString(R.string.step_of_total, obSteps.size)
            if (obNumber > 0) b.progress.setProgressCompat(obNumber, !first)
            b.title.updatePadding(top = resources.getDimensionPixelSize(if (hasBanner) SdpR.dimen._30sdp else SdpR.dimen._10sdp))
            b.skip.visibility = if (if (step.key == DEFAULT_HOME_KEY) viewModel.defaultHomeSkipButton else viewModel.skipButton) View.VISIBLE else View.GONE
            onDefaultHomeStep(step.key == DEFAULT_HOME_KEY)
            // As the reference: every step asks its `permissions.<step>` on open; permission_hub asks on its Allow button.
            if (step.key != PERMISSION_HUB_KEY) Permissions.askFor(this, step.key)
            b.favRow.visibility = if (state.stepIndex == viewModel.favIndex) View.VISIBLE else View.GONE
            val place = place(step)
            // RC decides both slots: `list_ad` sits between the options (big native), `bottom_ad` is pinned at the bottom.
            // With the in-list ad showing, Continue moves into the list right under it; otherwise it stays pinned.
            val inline = Ads.listOn(place)
            if (!inline) pinCta()
            b.adSlot.removeAllViews()
            Ads.bottom(this, b.adSlot, place)
            val adapter = OptionAdapter(
                step, state.selection,
                inlineAd = if (inline) { slot -> Ads.list(this, slot, place) } else null,
                cta = if (inline) b.cta else null,
            ) {
                Analytics.click("option", step.key)
                // Information rows (launcher, welcome, permissions): a tap does what the button does.
                if (step.key in INFO_STEPS) primaryAction() else viewModel.select(it)
            }
            this.adapter = adapter
            b.list.layoutManager = GridLayoutManager(this, step.columns).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int) = if (adapter.isAd(position)) step.columns else 1
                }
            }
            b.list.adapter = adapter
            Screens.show(step.key) // as the reference: the step's own key
            b.body.alpha = 0f
            b.body.translationX = resources.getDimension(SdpR.dimen._23sdp) * dir
            b.body.animate().alpha(1f).translationX(0f).setDuration(250).setInterpolator(DecelerateInterpolator(2f)).start()
        } else {
            adapter?.submitSelection(state.selection)
        }

        if (viewModel.favIndex < 0) return
        val favs = state.selections[viewModel.favIndex]
        val favTotal = steps[viewModel.favIndex].opts.size
        b.favCount.text = getString(R.string.fav_count, favs.size, favTotal)
        b.favAll.setText(if (favs.size == favTotal) R.string.clear_all else R.string.select_all)
    }

    private companion object {
        /** Xiaomi sends each key twice ~20 ms apart: a copy this soon is the same press. */
        const val KEY_REPEAT_MS = 300L
        const val LOCALE_WAIT_MS = 1_500L
        /** A default-home grant that reached a destroyed screen, for the next live one to handle. */
        private var grantPending = false
        /** The onboarding screen last resumed: a grant on a destroyed screen goes to it. */
        private var live: WeakReference<OnboardingActivity>? = null
    }
}
