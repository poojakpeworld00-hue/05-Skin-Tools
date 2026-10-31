package com.fffemotes.skintools.launcher.persona

import android.appwidget.AppWidgetHost
import android.content.Context
import android.content.pm.ApplicationInfo
import com.fffemotes.skintools.launcher.exts.homeScreenGridItemsDB
import com.fffemotes.skintools.launcher.exts.launcherConfig
import com.fffemotes.skintools.launcher.support.ITEM_TYPE_FOLDER
import com.fffemotes.skintools.launcher.support.ITEM_TYPE_ICON
import com.fffemotes.skintools.launcher.support.ITEM_TYPE_WIDGET
import com.fffemotes.skintools.launcher.support.WIDGET_HOST_ID
import com.fffemotes.skintools.launcher.entities.GadgetHub
import timber.log.Timber

object LobbyPositioner {
    fun seedFirstLeaf(context: Context, launchers: List<GadgetHub>) {
        val config = context.launcherConfig
        if (config.homeSeeded || launchers.isEmpty()) return
        config.homeSeeded = true
        LobbyWidgetPositioner.seedFirstLeaf(context)
        config.lastInstallSeen = installed(context).values.maxOfOrNull { it.at } ?: 0L
        Timber.i("HomeSeeder: first page = time + search, no app icons")
    }

    fun placeNewInstalls(context: Context, launchers: List<GadgetHub>): Int {
        val config = context.launcherConfig
        if (!config.homeSeeded || launchers.isEmpty()) return 0
        val installed = installed(context)
        val newest = installed.values.maxOfOrNull { it.at } ?: 0L
        val since = config.lastInstallSeen
        if (newest <= since) return 0
        config.lastInstallSeen = newest

        if (since == 0L) return 0
        val fresh = launchers
            .filter { installed[it.packageName]?.takeIf { p -> p.at > since && !p.system } != null }
            .sortedBy { installed[it.packageName]?.at ?: 0L }
        if (fresh.isEmpty()) return 0
        val placed = LobbyGadgetsPopulator.place(context, fresh)
        Timber.i("HomeSeeder: $placed newly installed app(s) placed on the workspace")
        return placed
    }

    fun reinitForRebuild(context: Context) {
        val config = context.launcherConfig
        val db = context.homeScreenGridItemsDB
        val items = db.pullAllCells()
        val folders = items.filter { it.type == ITEM_TYPE_FOLDER && !it.docked }.mapNotNull { it.id }.toSet()
        var removed = 0
        val kept = ArrayList<com.fffemotes.skintools.launcher.entities.LobbyLookerMatrixDatum>()
        items.forEach { item ->
            val id = item.id ?: return@forEach
            val drop = when {
                item.docked -> false
                item.type == ITEM_TYPE_FOLDER -> true
                item.type == ITEM_TYPE_ICON -> item.parentId == null || item.parentId in folders
                else -> LobbyWidgetPositioner.isSeededGadget(item)
            }
            if (drop) {
                if (item.type == ITEM_TYPE_WIDGET && item.widgetId > 0) {
                    runCatching { AppWidgetHost(context, WIDGET_HOST_ID).deleteAppWidgetId(item.widgetId) }
                }
                db.eraseCellByKey(id)
                removed++
            } else if (!item.docked && item.parentId == null) {
                kept.add(item)
            }
        }

        val cols = config.homeColumnCount
        val rows = config.homeRowCount - 1
        kept.forEach { item ->
            if (item.right < cols && item.bottom < rows) return@forEach
            val width = (item.right - item.left + 1).coerceIn(1, cols)
            val height = (item.bottom - item.top + 1).coerceIn(1, rows)
            val left = item.left.coerceIn(0, cols - width)
            val top = item.top.coerceIn(0, rows - height)
            db.reloadCellPoint(left, top, left + width - 1, top + height - 1, item.page, false, null, item.id!!)
        }
        Timber.i("HubPlanter: layout switch - $removed item(s) cleared, ${kept.size} widget(s)/shortcut(s) kept")
    }

    private class Installed(val packageName: String, val at: Long, val system: Boolean)

    private fun installed(context: Context): Map<String, Installed> = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getInstalledPackages(0).associate { info ->
            val system = (info.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
            info.packageName to Installed(info.packageName, info.firstInstallTime, system)
        }
    }.getOrDefault(emptyMap())
}
