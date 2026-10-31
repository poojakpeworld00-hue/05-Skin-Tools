package com.fffemotes.skintools.master.presentation.onboarding

import com.fffemotes.skintools.master.data.remote.config.AppConfig
import com.fffemotes.skintools.master.data.remote.config.KeyAction
import com.fffemotes.skintools.master.data.remote.config.OnboardingScreen
import com.fffemotes.skintools.master.data.remote.config.SystemKey
import com.fffemotes.skintools.master.data.remote.config.SystemKeys

/** Pure onboarding rules (no Android), unit-tested in OnboardingRulesTest. Same rules as the reference app. */
object OnboardingRules {
    /** `onboarding.order`, minus default_home when the launcher is off or we already are the default home. */
    fun screens(config: AppConfig, isDefaultHome: Boolean): List<OnboardingScreen> =
        config.onboarding.order.filter { it != OnboardingScreen.DEFAULT_HOME || (config.launcher.on && !isDefaultHome) }

    sealed interface KeyResult {
        data object ShowSheet : KeyResult
        data object Next : KeyResult
        data class GoTo(val index: Int) : KeyResult
        data object LeaveApp : KeyResult
        /** `""`: Android's own behaviour (Back sends the app to the background; Home is left to the launcher). */
        data object DeviceDefault : KeyResult
        data object Nothing : KeyResult
    }

    /**
     * What a system key does on screen [index] during onboarding. A key in `bottom_sheet.keys` first opens the bottom sheet;
     * pressed again while it is open ([sheetOpen]) it does its own action, like a key that isn't listed.
     */
    fun onKey(keys: SystemKeys, key: SystemKey, index: Int, sheetOpen: Boolean = false): KeyResult = when {
        keys.opensSheet(key) && !sheetOpen -> KeyResult.ShowSheet
        else -> when (keys.actionFor(key)) {
            null -> KeyResult.Nothing
            KeyAction.NEXT -> KeyResult.Next
            KeyAction.RESET -> KeyResult.GoTo(0)
            KeyAction.DEFAULT -> KeyResult.DeviceDefault
            KeyAction.BACK -> when {
                index > 0 -> KeyResult.GoTo(index - 1)
                key == SystemKey.BACK -> KeyResult.LeaveApp
                else -> KeyResult.Nothing
            }
        }
    }
}

/** The set-as-default-home step is on screen (no resume ad over it; the launcher hands Home to it). */
internal object DefaultHomeStep {
    @Volatile var shown = false
}

/** Where onboarding is, in memory for this app session: read when the app reopens after Recents removed our task. */
internal object OnboardingProgress {
    var keys: List<String> = emptyList()
    var index = 0
    /** The setup sheet was open: it opens again with the step. */
    var sheet = false
}
