package com.fffemotes.skintools.master.data.remote.config

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

/** Where the device is, as the IP lookup reports it. Blank = unknown. */
data class Location(
    val country: String = "",
    val countryCode: String = "",
    val state: String = "",
    val stateCode: String = "",
    val city: String = "",
)

/**
 * `base.exclusive_locations`: with `on`, a user whose country, state or city is in the list gets the organic config
 * (track_a), paid or not; off = no lookup and no override. The location comes from an IP lookup once per app start and is saved; a lookup that fails keeps the last
 * saved one. Until the splash ends a new location applies at once; after that it applies from the next launch.
 */
object ExclusiveLocations {
    private const val TAG = "ExclusiveLocations"
    private const val PREFS = "exclusive_locations"
    private const val KEY_LOCATION = "location"
    private const val TIMEOUT_MS = 3000

    private val looked = CompletableDeferred<Unit>()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var live = true

    fun saved(context: Context): Location? =
        prefs(context).getString(KEY_LOCATION, null)?.let { runCatching { parse(it) }.getOrNull() }

    /** Looks the location up when the list has entries; [onLiveChange] runs on the main thread if it changes before [lock]. */
    fun start(context: Context, spec: () -> ExclusiveLocationsSpec, onLiveChange: () -> Unit) {
        val app = context.applicationContext
        thread(name = TAG) {
            // First install: the list comes from the live config, so wait for its first fetch (offline: code defaults after 5s).
            runBlocking { withTimeoutOrNull(5_000) { AppConfigStore.ready.first { it } } }
            val s = spec()
            if (!s.on || s.list.isEmpty() || s.endpoint.isBlank()) return@thread done()
            val location = lookup(s.endpoint) ?: return@thread done() // offline: the last saved location stays
            main.post {
                val changed = saved(app) != location
                prefs(app).edit { putString(KEY_LOCATION, toJson(location)) }
                Log.d(TAG, "location $location")
                if (changed && live) onLiveChange()
                looked.complete(Unit)
            }
        }
    }

    /** Waits up to [timeoutMs] for this launch's lookup (the splash calls it after its own hold). */
    suspend fun await(timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) { looked.await() }
    }

    /** The splash is over: a location landing later must not switch the config mid-flow. */
    fun lock() {
        live = false
    }

    private fun done() {
        main.post { looked.complete(Unit) }
    }

    private fun lookup(endpoint: String): Location? = runCatching {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        try {
            parse(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    // The lookup's fields (ipconfig.io shape); the saved copy uses the same keys.
    private fun parse(json: String): Location = JSONObject(json).let {
        Location(it.optString("country"), it.optString("country_iso"), it.optString("region_name"), it.optString("region_code"), it.optString("city"))
    }

    private fun toJson(l: Location) = JSONObject()
        .put("country", l.country).put("country_iso", l.countryCode)
        .put("region_name", l.state).put("region_code", l.stateCode).put("city", l.city).toString()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Pure rules of [ExclusiveLocations]. */
internal object LocationRules {
    /** An entry equals the country, country code, state, state code or city; case and surrounding spaces don't matter. */
    fun matches(list: List<String>, location: Location?): Boolean {
        location ?: return false
        val mine = with(location) { listOf(country, countryCode, state, stateCode, city) }.map(::norm).filter { it.isNotEmpty() }.toSet()
        return list.any { norm(it) in mine }
    }

    private fun norm(s: String) = s.trim().lowercase(Locale.ROOT)
}
