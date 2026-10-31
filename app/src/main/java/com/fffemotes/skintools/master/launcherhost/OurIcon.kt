package com.fffemotes.skintools.master.launcherhost

import android.content.Context
import androidx.core.content.edit
import com.fffemotes.skintools.launcher.Launcher

/** Our own icon on the launcher home and drawer, hidden by Hide on our icon (long-press menu). */
object OurIcon {
    private const val PREFS = "our_icon"
    private const val KEY_HIDDEN = "hidden"

    fun hidden(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_HIDDEN, false)

    /** Hides the icon on home and in the drawer at once (the launcher re-reads LauncherHost.settings()). */
    fun hide(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_HIDDEN, true) }
        Launcher.refresh(context.applicationContext)
    }
}
