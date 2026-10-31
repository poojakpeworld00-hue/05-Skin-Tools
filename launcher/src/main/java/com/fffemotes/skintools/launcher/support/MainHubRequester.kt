package com.fffemotes.skintools.launcher.support

import android.app.Activity
import android.app.Application
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import com.fffemotes.skintools.launcher.Launcher
import com.fffemotes.skintools.launcher.screens.MainLobbyHintStage
import com.fffemotes.skintools.launcher.persona.HubStamp
import org.fossify.commons.helpers.isQPlus
import timber.log.Timber
import com.fffemotes.skintools.launcher.exts.isPrimaryLaunchpad
import com.fffemotes.skintools.launcher.exts.roleManager
import com.fffemotes.skintools.launcher.exts.promptAssignAsMainHomebase

class MainHubRequester(
    private val activity: Activity,
    private val onGranted: () -> Unit = {},
    private val onGaveUp: () -> Unit = {},
) {
    enum class Pending { NONE, SETTINGS, ROLE_DIALOG }

    internal var pending: Pending = Pending.NONE

    private var inFlight = false

    private var granted = false

    private var roleDialogShown = false

    fun start(): Boolean {
        if (inFlight || activity.isPrimaryLaunchpad()) return false

        HubStamp.guaranteeCaptured(activity)
        roleDialogShown = false

        if (prefsIntents().any { launch(it, Pending.SETTINGS) }) return true

        return roleSelectorIntent(activity)?.let { launch(it, Pending.ROLE_DIALOG) } ?: false
    }

    private var returnWatch: Runnable? = null

    private fun primeRevisitWatch() {
        abortRevisitWatch()
        val startedAt = SystemClock.uptimeMillis()
        val watch = object : Runnable {
            override fun run() {
                if (returnWatch !== this || activity.isFinishing || activity.isDestroyed) return
                if (activity.isPrimaryLaunchpad()) {
                    returnWatch = null
                    Timber.d("DefaultLauncherRequester: role granted on the Settings list, raising the launcher")
                    runCatching {
                        activity.startActivity(
                            Intent(activity, activity::class.java).addFlags(
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
                        )
                    }.onFailure { Timber.w(it, "DefaultLauncherRequester: could not raise the task") }
                    return
                }
                if (SystemClock.uptimeMillis() - startedAt < RETURN_WATCH_TIMEOUT_MS) {
                    handler.postDelayed(this, RETURN_WATCH_POLL_MS)
                } else {
                    returnWatch = null
                }
            }
        }
        returnWatch = watch
        handler.postDelayed(watch, RETURN_WATCH_POLL_MS)
    }

    private fun abortRevisitWatch() {
        returnWatch?.let { handler.removeCallbacks(it) }
        returnWatch = null
    }

    fun onResume() {
        inFlight = false
        abortRevisitWatch()
        if (activity.isPrimaryLaunchpad()) {
            // Only a request in flight counts: a launcher that was already default reports nothing.
            if (pending != Pending.NONE) {
                Launcher.events.onStep(STEP_EVENT, "granted")
                // The Settings list stays in our task when we're raised over it: Back on our screen would show it again.
                runCatching { activity.finishActivity(REQUEST_SET_DEFAULT) }
            }
            pending = Pending.NONE
            if (!granted) {
                granted = true
                onGranted()
            }
            return
        }

        when (pending) {
            Pending.SETTINGS -> {
                Launcher.events.onStep(STEP_EVENT, "settings_back")
                pending = Pending.NONE
                val dialog = if (!roleDialogShown) roleSelectorIntent(activity) else null
                roleDialogShown = true
                if (dialog == null || !launch(dialog, Pending.ROLE_DIALOG)) onGaveUp()
            }

            Pending.ROLE_DIALOG -> {
                Launcher.events.onStep(STEP_EVENT, "dialog_cancel")
                pending = Pending.NONE
                onGaveUp()
            }

            Pending.NONE -> Unit
        }
    }

    private fun launch(intent: Intent, step: Pending): Boolean {
        pending = step
        inFlight = true
        return runCatching {
            activity.startActivityForResult(intent, REQUEST_SET_DEFAULT)
            Launcher.events.onStep(STEP_EVENT, if (step == Pending.SETTINGS) "settings_open" else "dialog_show")

            if (step == Pending.SETTINGS) {
                MainLobbyHintStage.show(activity)
                primeRevisitWatch()
            }
            true
        }.getOrElse {
            pending = Pending.NONE
            inFlight = false
            false
        }
    }

    fun persistState(outState: Bundle) {
        outState.putString(KEY_PENDING, pending.name)
        outState.putBoolean(KEY_ROLE_DIALOG_SHOWN, roleDialogShown)
    }

    fun recoverState(savedState: Bundle?) {
        val state = savedState ?: return
        val name = state.getString(KEY_PENDING)
        if (name != null) {
            pending = runCatching { Pending.valueOf(name) }.getOrDefault(Pending.NONE)
        }
        roleDialogShown = state.getBoolean(KEY_ROLE_DIALOG_SHOWN, false)
    }

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        /**
         * [com.fffemotes.skintools.launcher.api.LauncherApi.requestDefaultHome]: the Settings "Default home app" list with the
         * coach-mark over it, then the role dialog; [onResult] once, true as soon as we hold the HOME role.
         */
        fun request(activity: Activity, onResult: (Boolean) -> Unit) {
            if (activity.isPrimaryLaunchpad()) return onResult(true)
            val app = activity.application
            var done = false
            lateinit var watcher: Application.ActivityLifecycleCallbacks
            fun finish(granted: Boolean) {
                if (done) return
                done = true
                app.unregisterActivityLifecycleCallbacks(watcher)
                onResult(granted)
            }
            val requester = MainHubRequester(activity, onGranted = { finish(true) }, onGaveUp = { finish(activity.isPrimaryLaunchpad()) })
            watcher = object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(a: Activity) { if (a === activity) requester.onResume() }
                override fun onActivityDestroyed(a: Activity) { if (a === activity) finish(a.isPrimaryLaunchpad()) }
                override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
                override fun onActivityStarted(a: Activity) = Unit
                override fun onActivityPaused(a: Activity) = Unit
                override fun onActivityStopped(a: Activity) = Unit
                override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
            }
            app.registerActivityLifecycleCallbacks(watcher)
            if (!requester.start()) {
                // No Settings list / role dialog could open: MSG-05's wider fallback; the next resume decides.
                requester.pending = Pending.ROLE_DIALOG
                activity.promptAssignAsMainHomebase()
            }
        }

        private const val STEP_EVENT = "default_home"
        private const val RETURN_WATCH_POLL_MS = 300L
        private const val RETURN_WATCH_TIMEOUT_MS = 5 * 60 * 1000L
        private const val KEY_PENDING = "default_launcher_pending"
        private const val KEY_ROLE_DIALOG_SHOWN = "default_launcher_role_dialog_shown"

        fun prefsIntents(): List<Intent> = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        )

        fun roleSelectorIntent(ctx: Context): Intent? {
            if (!isQPlus()) {
                return null
            }

            return runCatching {
                with(ctx.roleManager) {
                    when {
                        !isRoleAvailable(RoleManager.ROLE_HOME) -> null
                        isRoleHeld(RoleManager.ROLE_HOME) -> null
                        else -> createRequestRoleIntent(RoleManager.ROLE_HOME)
                    }
                }
            }.getOrNull()
        }
    }
}
