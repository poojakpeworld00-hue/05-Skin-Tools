package com.fffemotes.skintools.ads.host

import android.content.Context
import android.util.Log
import com.fffemotes.skintools.ads.BuildConfig

/** Debug-only ad engine log. Never logs config values: RC carries secrets. */
object AdLog {
    private const val TAG = "AdEngine"

    fun d(area: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "[$area] $message")
    }

    @Suppress("UNUSED_PARAMETER")
    fun read(source: String, key: String?, value: Any?) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun dumpVault(context: Context, reason: String) = d("VAULT", reason)
}
