package com.fffemotes.skintools.launcher.contracts

import com.fffemotes.skintools.launcher.entities.GadgetWidget

interface WidgetsBlockGuardian {
    fun onWidgetLongPressed(appWidget: GadgetWidget)
}
