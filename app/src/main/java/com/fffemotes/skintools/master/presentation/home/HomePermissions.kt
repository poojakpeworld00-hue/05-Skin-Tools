package com.fffemotes.skintools.master.presentation.home

import android.content.Context
import androidx.activity.ComponentActivity
import com.fffemotes.skintools.master.presentation.permissions.Permissions

/** `permissions.home` over Home and over the launcher's app panel: the system prompts directly, on the first visit only. */
object HomePermissions {
    private const val KEY_ASKED = "home_permissions_asked"

    fun askOnFirstVisit(activity: ComponentActivity) {
        val prefs = activity.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED, false) || Permissions.pending(activity, "home").isEmpty()) return
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        Permissions.askFor(activity, "home")
    }
}
