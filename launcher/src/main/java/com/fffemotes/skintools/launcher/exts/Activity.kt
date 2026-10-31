package com.fffemotes.skintools.launcher.exts

import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import com.fffemotes.skintools.launcher.persona.HubStamp
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.provider.Telephony
import android.view.View
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import com.fffemotes.skintools.launcher.screens.MainLobbyHintStage
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isQPlus
import com.fffemotes.skintools.launcher.R
import com.fffemotes.skintools.launcher.support.ITEM_TYPE_FOLDER
import com.fffemotes.skintools.launcher.support.ITEM_TYPE_ICON
import com.fffemotes.skintools.launcher.support.ITEM_TYPE_WIDGET
import com.fffemotes.skintools.launcher.support.REQUEST_DEFAULT_SMS
import com.fffemotes.skintools.launcher.support.REQUEST_SET_DEFAULT
import com.fffemotes.skintools.launcher.support.UNINSTALL_APP_REQUEST_CODE
import com.fffemotes.skintools.launcher.screens.XHubStage
import com.fffemotes.skintools.launcher.contracts.DatumPrefsGuardian
import com.fffemotes.skintools.launcher.popups.EntryActionsSheet
import com.fffemotes.skintools.launcher.entities.LobbyLookerMatrixDatum

fun Activity.reviveNavigationBand() {
    val decorView = window.decorView

    @Suppress("DEPRECATION")
    decorView.systemUiVisibility = decorView.systemUiVisibility and (
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        ).inv()

    WindowInsetsControllerCompat(window, decorView)
        .show(WindowInsetsCompat.Type.systemBars())
}

fun Activity.fireApplet(packageName: String, activityName: String) {
    if (packageName == applicationContext.packageName) return fireAppletNow(packageName, activityName)
    com.fffemotes.skintools.launcher.intro.HubOfferCoordinator.run(
        this,
        com.fffemotes.skintools.launcher.intro.HubPromosSwitches.APP_LAUNCH,
        packageName,
    ) {
        com.fffemotes.skintools.launcher.intro.HubOfferCoordinator.away = com.fffemotes.skintools.launcher.intro.HubOfferCoordinator.Away.APP
        fireAppletNow(packageName, activityName)
    }
}

fun Activity.fireAppletNow(packageName: String, activityName: String) {
    try {
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            `package` = packageName
            component = ComponentName.unflattenFromString("$packageName/$activityName")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            startActivity(this)
        }
    } catch (e: Exception) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            startActivity(launchIntent)
        } catch (e: Exception) {
            showErrorToast(e)
            return
        }
    }

    ensureBackgroundThread {
        runCatching { appUsageDB.recordFire(packageName, activityName) }
    }
}

fun Activity.fireAppletInfo(packageName: String) {
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        runCatching { startActivity(this) }.onFailure { android.util.Log.w("Launcher", "app info failed", it) }
    }
}

fun Activity.promptAssignAsMainHomebase() {
    HubStamp.guaranteeCaptured(this)

    val intents = buildList<Pair<Intent, Boolean>> {
        add(Intent(Settings.ACTION_HOME_SETTINGS) to false)
        if (isQPlus()) {
            add(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME) to true)
        }
        add(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS) to false)
        add(Intent(Settings.ACTION_SETTINGS) to false)
    }

    for ((intent, isRoleDialog) in intents) {
        try {
            startActivityForResult(intent, REQUEST_SET_DEFAULT)
            com.fffemotes.skintools.launcher.Launcher.events.onStep("default_home", if (isRoleDialog) "dialog_show" else "settings_open")

            if (!isRoleDialog) {
                MainLobbyHintStage.show(this)
            }
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
}

fun Activity.canAppletBeUninstalled(packageName: String): Boolean {
    return try {
        val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
        (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
    } catch (ignored: Exception) {
        false
    }
}

fun Activity.deleteApplet(packageName: String) {
    if (packageName == applicationContext.packageName &&
        runCatching { com.fffemotes.skintools.launcher.Launcher.events.onUninstallHostApp(this) }.getOrDefault(false)
    ) return
    Intent(Intent.ACTION_DELETE).apply {
        data = Uri.fromParts("package", packageName, null)
        runCatching { startActivityForResult(this, UNINSTALL_APP_REQUEST_CODE) }
            .onFailure { android.util.Log.w("Launcher", "uninstall intent failed", it) }
    }

    (this as? com.fffemotes.skintools.launcher.screens.XHubStage)?.watchDelete(packageName)
}

fun Activity.processGridCellOverlayTweaks(
    anchorView: View,
    gridItem: LobbyLookerMatrixDatum,
    isOnAllAppsFragment: Boolean,
    listener: DatumPrefsGuardian,
    centerX: Float,
    itemTop: Float,
    itemBottom: Float,
): EntryActionsSheet {
    val isIcon = gridItem.type == ITEM_TYPE_ICON
    val isOwn = gridItem.packageName == applicationContext.packageName
    // Our own icon, on home and in the drawer alike, offers Hide, plus Uninstall (the host's own flow, see
    // LauncherEvents.onUninstallHostApp) when settings.hostUninstall: no App info, Rename, Remove or app shortcuts.
    val ownIcon = isIcon && isOwn
    val actions = buildList {
        if (ownIcon) {
            add(EntryActionsSheet.Action(org.fossify.commons.R.drawable.ic_hide_vector, org.fossify.commons.R.string.hide) { listener.hide(gridItem) })
            if (com.fffemotes.skintools.launcher.Launcher.settings().hostUninstall) {
                add(EntryActionsSheet.Action(org.fossify.commons.R.drawable.ic_delete_vector, R.string.launcher_uninstall) { listener.uninstall(gridItem) })
            }
            return@buildList
        }
        if (isIcon) add(EntryActionsSheet.Action(org.fossify.commons.R.drawable.ic_info_vector, R.string.launcher_app_info) { listener.appInfoUi(gridItem) })
        if (isIcon && isOnAllAppsFragment) add(EntryActionsSheet.Action(org.fossify.commons.R.drawable.ic_hide_vector, org.fossify.commons.R.string.hide) { listener.hide(gridItem) })
        if ((isIcon || gridItem.type == ITEM_TYPE_FOLDER) && !isOnAllAppsFragment) {
            add(EntryActionsSheet.Action(org.fossify.commons.R.drawable.ic_rename_vector, org.fossify.commons.R.string.rename) { listener.rename(gridItem) })
        }
        if (gridItem.type == ITEM_TYPE_WIDGET) add(EntryActionsSheet.Action(R.drawable.hs_ic_scale_vector, org.fossify.commons.R.string.resize) { listener.resize(gridItem) })
        if (!isOnAllAppsFragment) add(EntryActionsSheet.Action(org.fossify.commons.R.drawable.ic_cross_vector, org.fossify.commons.R.string.remove) { listener.remove(gridItem) })
        if (isIcon && canAppletBeUninstalled(gridItem.packageName)) {
            add(EntryActionsSheet.Action(org.fossify.commons.R.drawable.ic_delete_vector, R.string.launcher_uninstall) { listener.uninstall(gridItem) })
        }
    }.map { action -> EntryActionsSheet.Action(action.icon, action.label) { listener.onAnyClick(); action.onClick() } }

    val launcherApps =
        applicationContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val shortcutInfos = if (!ownIcon && launcherApps.hasShortcutHostPermission()) {
        try {
            val query = LauncherApps.ShortcutQuery().setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            ).setPackage(gridItem.packageName)
            launcherApps.getShortcuts(query, Process.myUserHandle())
        } catch (e: Exception) {
            null
        }
    } else {
        null
    }

    val iconSize = resources.getDimensionPixelSize(R.dimen.launcher_menu_icon_size)
    val shortcuts = shortcutInfos.orEmpty().map { shortcutInfo ->
        val iconDrawable = launcherApps.getShortcutIconDrawable(shortcutInfo, resources.displayMetrics.densityDpi)
        EntryActionsSheet.Shortcut(
            icon = (iconDrawable ?: Color.TRANSPARENT.toDrawable()).toBitmap(width = iconSize, height = iconSize).toDrawable(resources),
            label = shortcutInfo.shortLabel ?: shortcutInfo.longLabel ?: "",
        ) {
            com.fffemotes.skintools.launcher.Launcher.events.onClick("menu_shortcut")
            listener.onAnyClick()
            val id = shortcutInfo.id
            val packageName = shortcutInfo.`package`
            runCatching {
                launcherApps.startShortcut(packageName, id, Rect(), null, Process.myUserHandle())
            }.onFailure { android.util.Log.w("Launcher", "startShortcut failed", it) }
        }
    }

    return EntryActionsSheet(this, shortcuts, actions, onDismiss = { listener.onDismiss() }).also {
        it.show(anchorView, centerX, itemTop, itemBottom)
    }
}
