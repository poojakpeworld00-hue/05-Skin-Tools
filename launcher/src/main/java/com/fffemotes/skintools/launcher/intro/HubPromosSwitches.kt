package com.fffemotes.skintools.launcher.intro

import com.fffemotes.skintools.launcher.Launcher

/** Panel / guide switches, read from [com.fffemotes.skintools.launcher.api.LauncherHost.settings] on every call. */
object HubPromosSwitches {
    const val LEFT_SWIPE = "leftSwipe"
    const val RIGHT_SWIPE = "rightSwipe"
    const val DRAWER_OPEN = "drawerOpen"
    const val APP_LAUNCH = "appLaunch"
    const val GAME_LAUNCH = "game_launch"

    const val KEY_PANEL_MIDDLE = "suggested_recent_panel"
    const val KEY_PANEL_BOTTOM = "apps_panel"
    const val KEY_APP_DRAWER = "app_drawer"

    fun guidePhaseEnabled(step: HubGuideHalt): Boolean {
        val guide = Launcher.settings().swipeGuide
        if (!guide.on) return false
        return when (step) {
            HubGuideHalt.SWIPE_RIGHT_MESSAGES -> deckEnabled(RIGHT_SWIPE) && guide.swipeRightHostApp
            HubGuideHalt.SWIPE_LEFT_APPS -> deckEnabled(LEFT_SWIPE) && guide.swipeLeftSuggestedPanel
            HubGuideHalt.SWIPE_UP_DRAWER -> guide.swipeUpDrawer
        }
    }

    fun deckEnabled(gesture: String): Boolean = when (gesture) {
        LEFT_SWIPE -> Launcher.settings().suggestedPanel
        RIGHT_SWIPE -> Launcher.settings().hostAppPanel && Launcher.host() != null
        else -> true
    }

    /** App rows above the drawer ad (ads.launcher.app_drawer.bottom_ad_row). */
    fun rackAdvertRowPoint(): Int = Launcher.settings().drawerAdRow
}
