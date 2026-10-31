package com.fffemotes.skintools.master.presentation.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.fffemotes.skintools.ads.analytics.Analytics
import com.fffemotes.skintools.master.R
import com.fffemotes.skintools.master.data.remote.config.AppConfigStore
import com.fffemotes.skintools.master.data.remote.config.AppPermission

/** Icon and name shown for [this] in the Home permission sheet. */
val AppPermission.display: Pair<Int, Int>
    get() = when (this) {
        AppPermission.NOTIFICATION -> R.drawable.ic_bell to R.string.ph_notif
        // FFF declares only the notification permission; the others are never pending, so they share a generic row.
        else -> R.drawable.ic_shield to R.string.perm_title
    }

/** Remote Config `permissions.<screen>`: which runtime permissions a screen asks, in order (reference app rules). */
object Permissions {
    /** A permission prompt (or the overlay settings page) is in front: the screen visit goes on. */
    @Volatile var asking = false

    /** The runtime permission behind [p], or null when none is needed on this device (photos use the system picker). */
    fun manifestName(p: AppPermission): String? = when (p) {
        AppPermission.CAMERA -> Manifest.permission.CAMERA
        AppPermission.PHOTOS -> null
        AppPermission.NOTIFICATION -> if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null
        AppPermission.CONTACTS -> Manifest.permission.READ_CONTACTS
        AppPermission.PHONE -> Manifest.permission.READ_PHONE_STATE
        AppPermission.OVERLAY -> Manifest.permission.SYSTEM_ALERT_WINDOW
    }

    fun isGranted(context: Context, p: AppPermission): Boolean = when {
        p == AppPermission.OVERLAY -> Settings.canDrawOverlays(context)
        else -> manifestName(p)?.let { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } ?: true
    }

    /** False when granted, not needed here, or not declared in the manifest (asking would be auto-denied). */
    fun needsAsking(context: Context, p: AppPermission): Boolean {
        val name = manifestName(p) ?: return false
        return !isGranted(context, p) && name in declared(context)
    }

    /** `permissions[screenKey]` still to ask on this device. */
    fun pending(context: Context, screenKey: String): List<AppPermission> =
        AppConfigStore.current.value.permissions[screenKey].orEmpty().filter { needsAsking(context, it) }

    /**
     * Asks `permissions[screenKey]` one at a time in list order. Granted / not needed ones are skipped; a denial just moves
     * on. [onDone] once the list is done.
     */
    fun askFor(activity: ComponentActivity, screenKey: String, onDone: () -> Unit = {}) {
        val list = AppConfigStore.current.value.permissions[screenKey].orEmpty()
        ask(activity, list, 0, onDone)
    }

    /** Asks just [p] (a row's Allow); [onDone] after the answer, or at once when it needs no asking. */
    fun ask(activity: ComponentActivity, p: AppPermission, onDone: () -> Unit) = ask(activity, listOf(p), 0, onDone)

    private fun ask(activity: ComponentActivity, list: List<AppPermission>, from: Int, onDone: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return
        val i = (from until list.size).firstOrNull { idx ->
            needsAsking(activity, list[idx]).also { needed -> if (!needed && isGranted(activity, list[idx])) Analytics.permission(list[idx].key, "already") }
        } ?: return onDone()
        val p = list[i]
        asking = true
        Analytics.permission(p.key, "show")
        val answered = { granted: Boolean ->
            asking = false
            Analytics.permission(p.key, if (granted) "allow" else "deny")
            ask(activity, list, i + 1, onDone)
        }
        // Registered on demand (any time), unregistered once answered.
        val key = "perm_${p.key}_${System.nanoTime()}"
        if (p == AppPermission.OVERLAY) {
            lateinit var launcher: androidx.activity.result.ActivityResultLauncher<Intent>
            launcher = activity.activityResultRegistry.register(key, ActivityResultContracts.StartActivityForResult()) {
                launcher.unregister(); answered(isGranted(activity, p))
            }
            launcher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${activity.packageName}".toUri()))
        } else {
            lateinit var launcher: androidx.activity.result.ActivityResultLauncher<String>
            launcher = activity.activityResultRegistry.register(key, ActivityResultContracts.RequestPermission()) { granted ->
                launcher.unregister(); answered(granted)
            }
            launcher.launch(manifestName(p)!!)
        }
    }

    // The manifest never changes at runtime: one package-manager call, not one per permission check.
    @Volatile private var declaredCache: List<String>? = null

    private fun declared(context: Context): List<String> = declaredCache
        ?: context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions?.toList().orEmpty()
            .also { declaredCache = it }
}
