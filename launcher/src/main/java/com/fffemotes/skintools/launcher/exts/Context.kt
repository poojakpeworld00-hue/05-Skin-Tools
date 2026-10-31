package com.fffemotes.skintools.launcher.exts

import android.app.role.RoleManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import com.fffemotes.skintools.launcher.support.SymbolFormer
import android.os.Build
import android.os.Process
import android.util.Size
import android.provider.Telephony
import androidx.annotation.RequiresApi
import org.fossify.commons.helpers.isQPlus
import org.fossify.commons.helpers.isSPlus
import com.fffemotes.skintools.launcher.storage.GadgetsDb
import com.fffemotes.skintools.launcher.support.Config
import com.fffemotes.skintools.launcher.contracts.appletHomebasesDao
import com.fffemotes.skintools.launcher.contracts.appletActivityDao
import com.fffemotes.skintools.launcher.contracts.hiddenGlyphsDao
import com.fffemotes.skintools.launcher.contracts.atriumViewerGridRecordsDao
import kotlin.math.ceil
import kotlin.math.max

val Context.launcherConfig: Config get() = Config.newInstance(applicationContext)

val Context.launchersDB: appletHomebasesDao
    get() = GadgetsDb.getInstance(applicationContext).appletHomebasesDao()

val Context.homeScreenGridItemsDB: atriumViewerGridRecordsDao
    get() = GadgetsDb.getInstance(
        applicationContext
    ).atriumViewerGridRecordsDao()

val Context.hiddenIconsDB: hiddenGlyphsDao
    get() = GadgetsDb.getInstance(applicationContext).hiddenGlyphsDao()

val Context.appUsageDB: appletActivityDao
    get() = GadgetsDb.getInstance(applicationContext).appletActivityDao()

@get:RequiresApi(Build.VERSION_CODES.Q)
val Context.roleManager: RoleManager
    get() = getSystemService(RoleManager::class.java)

fun Context.pullDrawableForPkgName(packageName: String): Drawable? {
    var drawable: Drawable? = null
    try {
        val launcher = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val activityList = launcher.getActivityList(packageName, Process.myUserHandle())[0]
        drawable = activityList.getBadgedIcon(0)
    } catch (e: Exception) {
    } catch (e: Error) {
    }

    if (drawable == null) {
        drawable = try {
            packageManager.getApplicationIcon(packageName)
        } catch (ignored: Exception) {
            null
        }
    }

    return drawable?.let { SymbolFormer.shape(it, launcherConfig.legacyIconTray) }
}

fun Context.pullInitialSlotDims(
    info: AppWidgetProviderInfo,
    fallbackWidth: Int,
    fallbackHeight: Int
): Size {
    return if (isSPlus() && info.targetCellWidth != 0 && info.targetCellHeight != 0) {
        Size(info.targetCellWidth, info.targetCellHeight)
    } else {
        val widthCells = pullSlotCounter(fallbackWidth)
        val heightCells = pullSlotCounter(fallbackHeight)
        Size(widthCells, heightCells)
    }
}

fun Context.pullSlotCounter(size: Int): Int {
    val tiles = ceil(((size / resources.displayMetrics.density) - 30) / 70.0).toInt()
    return max(tiles, 1)
}

fun Context.isDefaultSmsApp(): Boolean {
    val roleHeld = if (isQPlus()) {
        runCatching {
            with(roleManager) {
                if (isRoleAvailable(RoleManager.ROLE_SMS)) {
                    isRoleHeld(RoleManager.ROLE_SMS)
                } else {
                    null
                }
            }
        }.getOrNull()
    } else {
        null
    }

    return roleHeld ?: (Telephony.Sms.getDefaultSmsPackage(this) == packageName)
}

fun Context.isPrimaryLaunchpad(): Boolean {
    return if (isQPlus()) {
        with(roleManager) {
            isRoleAvailable(RoleManager.ROLE_HOME) && isRoleHeld(RoleManager.ROLE_HOME)
        }
    } else {
        val filters = ArrayList<IntentFilter>()
        val activities = ArrayList<ComponentName>()
        @Suppress("DEPRECATION")
        packageManager.getPreferredActivities(filters, activities, null)
        return activities.indices.any { i ->
            activities[i].packageName == packageName &&
                    filters[i].hasAction(Intent.ACTION_MAIN) &&
                    filters[i].hasCategory(Intent.CATEGORY_HOME)
        }
    }
}
