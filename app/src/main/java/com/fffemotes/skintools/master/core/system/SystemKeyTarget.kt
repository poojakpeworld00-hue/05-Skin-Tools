package com.fffemotes.skintools.master.core.system

import com.fffemotes.skintools.master.data.remote.config.SystemKey

/** A screen that reacts to Home / Back / Recents from outside it (the onboarding screen; RecentsPolicy and the launcher call it). */
interface SystemKeyTarget {
    fun systemKey(key: SystemKey)

    companion object {
        /** Intent extra carrying a [SystemKey.key] forwarded to the main screen. */
        const val EXTRA_SYSTEM_KEY = "system_key"
    }
}
