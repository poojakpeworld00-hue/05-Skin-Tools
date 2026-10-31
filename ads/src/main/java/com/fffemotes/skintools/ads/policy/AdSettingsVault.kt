package com.fffemotes.skintools.ads.policy

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.fffemotes.skintools.ads.host.AdLog

class AdSettingsVault private constructor(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    companion object {
        /** Sponsor links (string or JSON list). Same as the reference: not gated by house ads. */
        fun parseSponsorLinks(raw: String): List<String> {
            val v = raw.trim()
            if (v.isEmpty()) return emptyList()
            if (!v.startsWith("[")) return listOf(v)
            return runCatching {
                val arr = org.json.JSONArray(v)
                (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotEmpty() }
            }.getOrDefault(emptyList())
        }

        private const val PREFERENCE_NAME = "qr_ads_config"
        private const val KEY_LAST_RESULT_SHAPE = "last_result_ad_shape"

        @Volatile
        private var instance: AdSettingsVault? = null

        fun getInstance(context: Context): AdSettingsVault =
            instance ?: synchronized(this) {
                instance ?: AdSettingsVault(context.applicationContext).also { instance = it }
            }

        fun getOrNull(): AdSettingsVault? = instance
    }

    var lastResultAdShape: String
        get() = preferences.getString(KEY_LAST_RESULT_SHAPE, "b") ?: "b"
        set(value) = preferences.edit { putString(KEY_LAST_RESULT_SHAPE, value) }

    fun update(block: SharedPreferences.Editor.() -> Unit) {
        preferences.edit().apply(block).apply()
    }

    fun putString(key: String?, value: String?) = preferences.edit().putString(key, value).apply()

    fun getString(key: String?): String? = getString(key, "")

    fun sponsorLinks(placement: String? = null): List<String> =
        parseSponsorLinks(AdUnitIds.resolve(this, placement, "DirectLink"))

    fun getString(key: String?, defaultValue: String?): String? =
        preferences.getString(key, defaultValue).also { AdLog.read("vault", key, it) }

    fun putBoolean(key: String?, value: Boolean) = preferences.edit().putBoolean(key, value).apply()

    fun getBoolean(key: String?, defaultValue: Boolean): Boolean =
        preferences.getBoolean(key, defaultValue).also { AdLog.read("vault", key, it) }

    fun getBoolean(key: String?): Boolean = getBoolean(key, false)

    fun putLong(key: String?, value: Long) = preferences.edit().putLong(key, value).apply()

    fun getLong(key: String?, defaultValue: Long): Long =
        preferences.getLong(key, defaultValue).also { AdLog.read("vault", key, it) }

    fun putInt(key: String?, value: Int) = preferences.edit().putInt(key, value).apply()

    /** Missing counter = 0 (show every time); QR used -1, which made a missing InterCounter never show. */
    fun getInt(key: String?): Int = getInt(key, 0)

    fun getInt(key: String?, defaultValue: Int): Int =
        preferences.getInt(key, defaultValue).also { AdLog.read("vault", key, it) }

    fun clear() = preferences.edit().clear().apply()

    /** Removes every key the parser writes, so a key dropped from RC does not survive in prefs. */
    fun clearManaged() {
        val editor = preferences.edit()
        clearManagedIn(editor)
        editor.apply()
    }

    /** Same as [clearManaged] inside a caller's editor, so clear + rewrite land in one atomic apply(). */
    fun clearManagedIn(editor: SharedPreferences.Editor) {
        val keep = setOf("OnMaketing", "__cfg_audience_split", "geo_country_code", "GET_DATA_RAW")
        // "__" keys are engine state (unlock counters, audience split), not config: never cleared.
        preferences.all.keys.filter { it !in keep && !it.startsWith("__") }.forEach { editor.remove(it) }
    }
}
