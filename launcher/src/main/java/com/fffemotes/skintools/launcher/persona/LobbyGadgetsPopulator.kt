package com.fffemotes.skintools.launcher.persona

import android.content.Context
import com.fffemotes.skintools.launcher.exts.homeScreenGridItemsDB
import com.fffemotes.skintools.launcher.exts.launcherConfig
import com.fffemotes.skintools.launcher.support.FIRST_APPS_PAGE
import com.fffemotes.skintools.launcher.support.ITEM_TYPE_ICON
import com.fffemotes.skintools.launcher.entities.GadgetHub
import com.fffemotes.skintools.launcher.entities.LobbyLookerMatrixDatum
import com.fffemotes.skintools.launcher.entities.appLauncherComparator
import timber.log.Timber

object LobbyGadgetsPopulator {
    fun placeMissing(context: Context, launchers: List<GadgetHub>): Int =
        place(context, launchers.sortedWith(appLauncherComparator), spread = true)

    fun perLeaf(count: Int, capacity: Int, pages: Int): Int {
        if (capacity <= 0 || pages <= 0) return capacity
        val even = (count + pages - 1) / pages
        return even.coerceIn(1, capacity)
    }

    fun place(context: Context, apps: List<GadgetHub>, spread: Boolean = false): Int {
        val config = context.launcherConfig
        val columns = config.homeColumnCount

        val rows = config.homeRowCount - 1
        if (columns <= 0 || rows <= 0 || apps.isEmpty()) return 0

        val db = context.homeScreenGridItemsDB
        val items = db.pullAllCells()

        val placedIds = HashSet<String>()
        val placedPackages = HashSet<String>()
        items.filter { it.type == ITEM_TYPE_ICON }.forEach {
            if (it.activityName.isEmpty()) placedPackages.add(it.packageName) else placedIds.add(it.pullCellIdentifier())
        }

        val occupied = HashSet<Triple<Int, Int, Int>>()
        items.filter { it.parentId == null && !it.docked }.forEach { item ->
            for (x in item.left..item.right) for (y in item.top..item.bottom) {
                occupied.add(Triple(item.page, x, y))
            }
        }

        // Our own app is in the launcher list (the drawer shows it), but its dock row is built by
        // hand and points at the inbox screen rather than this LAUNCHER entry - so it never matches
        // above and would be placed a second time. Only while one of our rows is already on the
        // grid: when another app holds the dock's messaging slot our icon is placed like any app.
        val own = context.packageName
        val ownPlaced = items.any { it.type == ITEM_TYPE_ICON && it.packageName == own }
        val missing = apps
            .filter { !(it.packageName == own && ownPlaced) }
            .filter { it.pullHomebaseIdentifier() !in placedIds && it.packageName !in placedPackages }
        if (missing.isEmpty()) return 0

        val capacity = columns * rows
        val pageCap = if (spread) {
            val pages = ((missing.size + capacity - 1) / capacity).coerceAtLeast(1)
            perLeaf(missing.size, capacity, pages)
        } else {
            null
        }

        var page = FIRST_APPS_PAGE
        var row = 0
        var col = 0
        fun advance() {
            col++
            if (col >= columns) {
                col = 0
                row++
                if (row >= rows) {
                    row = 0
                    page++
                }
            }
        }

        val added = ArrayList<LobbyLookerMatrixDatum>(missing.size)
        var pageOfCount = -1
        var placedOnPage = 0
        for (launcher in missing) {
            while (Triple(page, col, row) in occupied) advance()
            if (page != pageOfCount) {
                pageOfCount = page
                placedOnPage = 0
            }

            if (pageCap != null && placedOnPage >= pageCap) {
                page++
                row = 0
                col = 0
                pageOfCount = page
                placedOnPage = 0
                while (Triple(page, col, row) in occupied) advance()
            }
            added.add(
                LobbyLookerMatrixDatum(
                    id = null,
                    left = col,
                    top = row,
                    right = col,
                    bottom = row,
                    page = page,
                    packageName = launcher.packageName,
                    activityName = launcher.activityName,
                    title = launcher.title,
                    type = ITEM_TYPE_ICON,
                    className = "",
                    widgetId = -1,
                    shortcutId = "",
                    icon = null,
                    docked = false,
                    parentId = null,
                )
            )
            occupied.add(Triple(page, col, row))
            placedOnPage++
            advance()
        }
        db.insertAll(added)
        Timber.i("HomeAppsFiller: placed ${added.size} apps on the workspace (pages up to $page)")
        return added.size
    }
}
