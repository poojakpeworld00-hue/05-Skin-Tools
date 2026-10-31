package com.fffemotes.skintools.master.presentation.onboarding

import com.fffemotes.skintools.launcher.api.LauncherSettings
import com.fffemotes.skintools.master.data.remote.config.AppConfig
import com.fffemotes.skintools.master.data.remote.config.KeyAction
import com.fffemotes.skintools.master.data.remote.config.OnboardingConfig
import com.fffemotes.skintools.master.data.remote.config.OnboardingScreen.DEFAULT_HOME
import com.fffemotes.skintools.master.data.remote.config.OnboardingScreen.LANGUAGE
import com.fffemotes.skintools.master.data.remote.config.OnboardingScreen.DAILY
import com.fffemotes.skintools.master.data.remote.config.SystemKey
import com.fffemotes.skintools.master.data.remote.config.SystemKeys
import com.fffemotes.skintools.master.presentation.onboarding.OnboardingRules.KeyResult
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingRulesTest {
    private val order = listOf(LANGUAGE, DEFAULT_HOME, DAILY)
    private fun config(launcherOn: Boolean) =
        AppConfig(onboarding = OnboardingConfig(order = order), launcher = LauncherSettings(on = launcherOn))

    @Test fun defaultHomeOnlyWhenLauncherOnAndNotDefault() {
        assertEquals(order, OnboardingRules.screens(config(true), isDefaultHome = false))
        assertEquals(listOf(LANGUAGE, DAILY), OnboardingRules.screens(config(true), isDefaultHome = true))
        assertEquals(listOf(LANGUAGE, DAILY), OnboardingRules.screens(config(false), isDefaultHome = false))
    }

    @Test fun keys() {
        val keys = SystemKeys(home = KeyAction.RESET, back = KeyAction.BACK, sheetOn = true, sheetKeys = setOf(SystemKey.BACK))
        assertEquals(KeyResult.ShowSheet, OnboardingRules.onKey(keys, SystemKey.BACK, 2))
        assertEquals(KeyResult.GoTo(0), OnboardingRules.onKey(keys, SystemKey.HOME, 2))
        assertEquals(KeyResult.GoTo(1), OnboardingRules.onKey(keys, SystemKey.BACK, 2, sheetOpen = true))
        assertEquals(KeyResult.LeaveApp, OnboardingRules.onKey(keys, SystemKey.BACK, 0, sheetOpen = true))
        assertEquals(KeyResult.Next, OnboardingRules.onKey(SystemKeys(), SystemKey.BACK, 0))
        val device = SystemKeys(home = KeyAction.DEFAULT, back = KeyAction.DEFAULT, sheetOn = true, sheetKeys = setOf(SystemKey.BACK))
        assertEquals(KeyResult.DeviceDefault, OnboardingRules.onKey(device, SystemKey.HOME, 2))
        assertEquals(KeyResult.ShowSheet, OnboardingRules.onKey(device, SystemKey.BACK, 2)) // a listed key still opens the sheet first
        assertEquals(KeyResult.DeviceDefault, OnboardingRules.onKey(device, SystemKey.BACK, 2, sheetOpen = true))
        // sheet switched off: listed keys do their action
        assertEquals(KeyResult.Next, OnboardingRules.onKey(SystemKeys(sheetKeys = setOf(SystemKey.BACK)), SystemKey.BACK, 0))
    }

    @Test fun recentsKey() {
        assertEquals(KeyResult.Nothing, OnboardingRules.onKey(SystemKeys(), SystemKey.RECENTS, 2)) // missing key: stays
        assertEquals(KeyResult.Next, OnboardingRules.onKey(SystemKeys(recents = KeyAction.NEXT), SystemKey.RECENTS, 2))
        assertEquals(KeyResult.GoTo(0), OnboardingRules.onKey(SystemKeys(recents = KeyAction.RESET), SystemKey.RECENTS, 2))
        assertEquals(KeyResult.DeviceDefault, OnboardingRules.onKey(SystemKeys(recents = KeyAction.DEFAULT), SystemKey.RECENTS, 2))
        val sheet = SystemKeys(recents = KeyAction.NEXT, sheetOn = true, sheetKeys = setOf(SystemKey.RECENTS))
        assertEquals(KeyResult.ShowSheet, OnboardingRules.onKey(sheet, SystemKey.RECENTS, 2))
        assertEquals(KeyResult.Next, OnboardingRules.onKey(sheet, SystemKey.RECENTS, 2, sheetOpen = true))
    }

    @Test fun listedKey_opensSheetFirst_thenDoesItsAction() {
        val keys = SystemKeys(home = KeyAction.NEXT, back = KeyAction.NEXT, sheetOn = true, sheetKeys = setOf(SystemKey.BACK, SystemKey.HOME))
        assertEquals(KeyResult.ShowSheet, OnboardingRules.onKey(keys, SystemKey.BACK, 1))
        assertEquals(KeyResult.Next, OnboardingRules.onKey(keys, SystemKey.BACK, 1, sheetOpen = true))
        assertEquals(KeyResult.Next, OnboardingRules.onKey(keys, SystemKey.HOME, 1, sheetOpen = true)) // another listed key
        assertEquals(KeyResult.Next, OnboardingRules.onKey(keys.copy(sheetKeys = setOf(SystemKey.HOME)), SystemKey.BACK, 1)) // not listed: at once
        // empty list: every key does its own action at once
        assertEquals(KeyResult.Next, OnboardingRules.onKey(keys.copy(sheetKeys = emptySet()), SystemKey.BACK, 1))
        // second press keeps the key's own action (back → previous screen)
        assertEquals(KeyResult.GoTo(0), OnboardingRules.onKey(keys.copy(back = KeyAction.BACK), SystemKey.BACK, 1, sheetOpen = true))
    }
}
