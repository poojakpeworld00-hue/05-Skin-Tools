package com.fffemotes.skintools.launcher.contracts

import com.fffemotes.skintools.launcher.entities.GadgetHub

interface AllGadgetsGuardian {
    fun onAppLauncherLongPressed(x: Float, y: Float, appLauncher: GadgetHub)
}
