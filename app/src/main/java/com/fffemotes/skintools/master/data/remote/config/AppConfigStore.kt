package com.fffemotes.skintools.master.data.remote.config

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.fffemotes.skintools.master.BuildConfig
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of the resolved config. [init] publishes the last live JSON saved on this device at once, then the
 * fresh value when it activates. Live values only, as the reference: first launch has nothing saved, so [current]
 * holds the code defaults ([AppConfig]: ads and launcher off) until the fetch lands, and [ready] says when it has.
 */
object AppConfigStore {
    private const val TAG = "AppConfigStore"
    private val rcKey = if (BuildConfig.DEBUG) "app_config_debug" else "app_config"

    private val state = MutableStateFlow(AppConfig())
    val current: StateFlow<AppConfig> = state

    /** True once a live config is in [current] (saved or just fetched), or the fetch gave up. The splash waits on it. */
    private val readyState = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = readyState

    /** Set before [init]. */
    var trackResolver: TrackResolver = TrackResolver.Default

    /** True while App.kt calls forcePaidUser(true): a test build. */
    var forcedPaid = false
        private set

    /** Testing: true = every user gets track_b (paid), whatever the install referrer says. Call before [init]; remove the call before a Play release. */
    fun forcePaidUser(force: Boolean) {
        forcedPaid = force
    }

    private var country = ""
    private var app: Context? = null
    private var last: Triple<String, String, String>? = null // the last JSON published
    private val main = Handler(Looper.getMainLooper())
    private var nextSync: Runnable? = null
    private var inFlight: Task<Void>? = null

    fun init(context: Context) {
        val app = context.applicationContext
        this.app = app
        country = runCatching { CountryResolver.countryCode(app) }.getOrDefault("")
        val saved = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        saved.getString(rcKey, null)?.let {
            publish(it, saved.getString(KEY_RULES, "").orEmpty(), saved.getString(KEY_PROBES, "").orEmpty())
            readyState.value = true
        }
        if (FirebaseApp.getApps(app).isEmpty()) { // no google-services.json: stay on the code defaults, as the reference
            readyState.value = true
            return
        }
        runCatching {
            sync(app, FirebaseRemoteConfig.getInstance())
        }.onFailure {
            Log.w(TAG, "Remote Config unavailable", it)
            readyState.value = true
        }
    }

    /**
     * One sync: fetches at most once per `base.rc_sync_hours` (debug: always), activates and publishes, then schedules
     * the next one, since the launcher keeps the app running for days. [force] (silent push) fetches now.
     */
    private fun sync(app: Context, rc: FirebaseRemoteConfig, force: Boolean = false): Task<Void> {
        // A push that starts the process lands while App.onCreate's sync runs: reuse it instead of fetching twice.
        inFlight?.takeIf { !it.isComplete }?.let { return it }
        val hours = state.value.base.rcSyncHours
        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(if (BuildConfig.DEBUG) 0 else hours * 3_600L)
            .build()
        val fetched = if (force) rc.fetch(0).onSuccessTask { rc.activate() }
        else rc.setConfigSettingsAsync(settings).continueWithTask { rc.fetchAndActivate() }
        val done = TaskCompletionSource<Void>()
        fetched.addOnCompleteListener {
            runCatching {
                // Live values only, as the reference: with no fetch yet (first launch offline) the code defaults stay,
                // so ads and the launcher stay off until Firebase answers.
                val json = rc.getString(rcKey)
                if (json.isNotBlank() && rc.getValue(rcKey).source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE) {
                    val rules = rc.getString(KEY_RULES)
                    val probes = rc.getString(KEY_PROBES)
                    if (publish(json, rules, probes)) {
                        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putString(rcKey, json).putString(KEY_RULES, rules).putString(KEY_PROBES, probes).apply()
                    }
                }
            }.onFailure { e -> Log.w(TAG, "Remote Config apply failed", e) }
            readyState.value = true
            scheduleNextSync(app, rc)
            done.trySetResult(null)
        }
        return done.task.also { inFlight = it }
    }

    /** Sync now, ignoring `base.rc_sync_hours` (the silent `rc_sync` push, [RcSyncMessagingService]). Null without Firebase. */
    fun syncNow(context: Context): Task<Void>? {
        val app = context.applicationContext
        if (FirebaseApp.getApps(app).isEmpty()) return null
        return sync(app, FirebaseRemoteConfig.getInstance(), force = true)
    }

    /** No live config yet (first launch was offline): fetch again now; [ready] turns true when it answers. */
    fun syncIfNeverFetched(context: Context) {
        if (last != null) return
        readyState.value = false
        syncNow(context) ?: run { readyState.value = true }
    }

    /** The next [sync] in `base.rc_sync_hours` while the app keeps running; 0 = none (app starts only). */
    private fun scheduleNextSync(app: Context, rc: FirebaseRemoteConfig) {
        nextSync?.let(main::removeCallbacks)
        val hours = state.value.base.rcSyncHours
        if (hours <= 0) return
        nextSync = Runnable { sync(app, rc) }.also { main.postDelayed(it, hours * 3_600_000L) }
    }

    /** Re-resolves the last config for the current track (organic / paid verdict just changed); no-op before one. */
    fun refreshTrack() {
        last?.let { (json, rules, probes) -> publish(json, rules, probes) }
    }

    /** False when the JSON is rejected (the previous config stays). */
    private fun publish(json: String, rulesJson: String, probesJson: String): Boolean =
        runCatching { AppConfigParser.parse(json, if (forcedPaid) Track.B else trackResolver.track(), country, app?.let(ExclusiveLocations::saved)) }
            .onSuccess {
                last = Triple(json, rulesJson, probesJson)
                state.value = it.copy(launcher = it.launcher.copy(homeProfileRulesJson = rulesJson, homeProfileProbesJson = probesJson))
            }
            .onFailure { Log.w(TAG, "Config JSON rejected, keeping the previous one", it) }
            .isSuccess

    private const val PREFS = "live_config"
    private const val KEY_RULES = "home_profile_rules"
    private const val KEY_PROBES = "home_profile_probes"
}
