package com.fffemotes.skintools.launcher.persona

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.appwidget.AppWidgetHost
import android.content.Context
import android.os.Build
import com.fffemotes.skintools.launcher.Launcher
import com.fffemotes.skintools.launcher.api.LauncherSettings
import com.fffemotes.skintools.launcher.exts.homeScreenGridItemsDB
import com.fffemotes.skintools.launcher.exts.launcherConfig
import com.fffemotes.skintools.launcher.support.ITEM_TYPE_WIDGET
import com.fffemotes.skintools.launcher.support.PSEUDO_WIDGET_GOOGLE_SEARCH
import com.fffemotes.skintools.launcher.support.PSEUDO_WIDGET_SEARCH
import com.fffemotes.skintools.launcher.support.PSEUDO_WIDGET_TIME
import com.fffemotes.skintools.launcher.support.WIDGET_HOST_ID
import com.fffemotes.skintools.launcher.entities.LobbyLookerMatrixDatum
import timber.log.Timber

object LobbyWidgetPositioner {
    private val GOOGLE_PROVIDERS = listOf(
        "com.google.android.googlequicksearchbox.SearchWidgetProvider",
        "com.google.android.apps.gsa.staticplugins.searchwidget.GoogleSearchWidgetProvider",
    )

    private val CHROME_PROVIDERS = listOf(
        "org.chromium.chrome.browser.quickactionsearchwidget.QuickActionSearchWidgetProvider\$QuickActionSearchWidgetProviderSearch",
        "org.chromium.chrome.browser.searchwidget.SearchWidgetProvider",
    )

    private const val TIME_COLUMNS = 4
    private const val TIME_ROWS = 2

    /**
     * The row an older build laid the time on, to leave the top free for the "set as default
     * home" ribbon (now gone); [moveTimeWidgetToTop] brings it back to row 0.
     */
    private const val LEGACY_TIME_TOP_ROW = 2

    private const val MAX_ROWS_TALL_DP = 140

    /**
     * Lays down the first-page widgets launcher_config asks for (should_show_time_widget /
     * should_show_search_widget); each is skipped when the page already has it. Records what was
     * applied, for [syncFirstLeaf].
     */
    fun seedFirstLeaf(context: Context) {
        val config = context.launcherConfig
        val prep = Launcher.settings()
        config.timeWidgetShown = prep.showTimeWidget
        config.searchWidgetShown = prep.showSearchWidget
        config.timeWidgetAtTop = true
        if (prep.showTimeWidget) seedTimeWidget(context)
        if (prep.showSearchWidget) seedLookupRow(context)
    }

    fun seedLookupRow(context: Context): Boolean {
        val config = context.launcherConfig
        val columns = config.homeColumnCount
        if (columns <= 0) return false

        val row = config.homeRowCount - 2
        if (row < 0) return false

        val db = context.homeScreenGridItemsDB
        val items = db.pullAllCells()
        if (items.any { isSeededLookupLine(it) }) return false
        val rowTaken = items.any { !it.docked && it.parentId == null && it.page == 0 && it.top <= row && it.bottom >= row }
        if (rowTaken) return false

        val choice = Launcher.settings().searchWidgetChoice
        config.searchWidget = choice
        val provider = lookupProvider(context, choice)
        val item = LobbyLookerMatrixDatum(
            id = null,
            left = 0,
            top = row,
            right = columns - 1,
            bottom = row,
            page = 0,
            packageName = provider?.provider?.packageName ?: context.packageName,
            activityName = "",
            title = "",
            type = ITEM_TYPE_WIDGET,
            className = provider?.provider?.className ?: PSEUDO_WIDGET_GOOGLE_SEARCH,

            widgetId = -1,
            shortcutId = "",
            icon = null,
            docked = false,
            parentId = null,
        )
        db.insert(item)
        Timber.i("HubGadgetPlanter: search row ($choice) = ${item.packageName}/${item.className}")
        return true
    }

    /**
     * Applies launcher_config to a home already built: removes the time / search widget switched
     * off, lays down one switched back on, and swaps the search bar when search_widget changed.
     * Runs off the main thread on every refresh; returns true when the first page changed, so the
     * screen is rebuilt around it.
     */
    fun syncFirstLeaf(context: Context): Boolean {
        val config = context.launcherConfig
        if (!config.homeSeeded) return false
        val prep = Launcher.settings()
        var changed = false

        if (!config.timeWidgetAtTop) {
            config.timeWidgetAtTop = true
            changed = moveTimeWidgetToTop(context)
        }

        if (config.timeWidgetShown != prep.showTimeWidget) {
            config.timeWidgetShown = prep.showTimeWidget
            val done = if (prep.showTimeWidget) seedTimeWidget(context) else deleteWidgets(context) { it.className == PSEUDO_WIDGET_TIME }
            changed = done || changed
            Timber.i("HubGadgetPlanter: time widget ${if (prep.showTimeWidget) "on" else "off"}")
        }

        if (config.searchWidgetShown != prep.showSearchWidget) {
            config.searchWidgetShown = prep.showSearchWidget
            val done = if (prep.showSearchWidget) seedLookupRow(context) else deleteWidgets(context) { isSeededLookupLine(it) }
            changed = done || changed
            Timber.i("HubGadgetPlanter: search widget ${if (prep.showSearchWidget) "on" else "off"}")
        } else if (prep.showSearchWidget && config.searchWidget != prep.searchWidgetChoice) {
            deleteWidgets(context) { isSeededLookupLine(it) }
            // Written even when the row cannot go back (its cells taken), so this is not retried forever.
            config.searchWidget = prep.searchWidgetChoice
            seedLookupRow(context)
            changed = true
            Timber.i("HubGadgetPlanter: search widget switched to ${prep.searchWidgetChoice}")
        }
        return changed
    }

    /**
     * Moves a time widget the previous build laid on [LEGACY_TIME_TOP_ROW] back up to row 0, above
     * the default-home ribbon. Left where it is when those cells are taken, or when it sits
     * anywhere else - the user put it there. Runs once per install.
     */
    private fun moveTimeWidgetToTop(context: Context): Boolean {
        val db = context.homeScreenGridItemsDB
        val items = db.pullAllCells()
        val time = items.firstOrNull {
            it.type == ITEM_TYPE_WIDGET && it.className == PSEUDO_WIDGET_TIME && it.page == 0 && it.top == LEGACY_TIME_TOP_ROW
        } ?: return false
        val bottom = time.bottom - time.top
        val taken = items.any {
            it.id != time.id && !it.docked && it.parentId == null && it.page == 0 &&
                it.left <= time.right && it.right >= time.left && it.top <= bottom
        }
        if (taken) return false
        db.reloadCellPoint(time.left, 0, time.right, bottom, time.page, false, null, time.id ?: return false)
        Timber.i("HubGadgetPlanter: time widget moved back to row 0")
        return true
    }

    /** Deletes every grid widget [match] picks, releasing its bound id. Returns true when any went. */
    private fun deleteWidgets(context: Context, match: (LobbyLookerMatrixDatum) -> Boolean): Boolean {
        val db = context.homeScreenGridItemsDB
        val gone = db.pullAllCells().filter { it.type == ITEM_TYPE_WIDGET && match(it) }
        gone.forEach { item ->
            if (item.widgetId > 0) runCatching { AppWidgetHost(context, WIDGET_HOST_ID).deleteAppWidgetId(item.widgetId) }
            db.eraseCellByKey(item.id ?: return@forEach)
        }
        return gone.isNotEmpty()
    }

    fun seedTimeWidget(context: Context): Boolean {
        val config = context.launcherConfig
        val columns = config.homeColumnCount
        val rows = config.homeRowCount - 1
        if (columns <= 0 || rows < TIME_ROWS + 1) return false
        val right = minOf(TIME_COLUMNS, columns) - 1
        val top = 0
        val bottom = TIME_ROWS - 1

        val db = context.homeScreenGridItemsDB
        val items = db.pullAllCells()
        if (items.any { it.className == PSEUDO_WIDGET_TIME && it.page == 0 }) return false
        val taken = items.any {
            !it.docked && it.parentId == null && it.page == 0 && it.left <= right && it.top <= bottom && it.bottom >= top
        }
        if (taken) return false

        db.insert(
            LobbyLookerMatrixDatum(
                id = null,
                left = 0,
                top = top,
                right = right,
                bottom = bottom,
                page = 0,
                packageName = context.packageName,
                activityName = "",
                title = "",
                type = ITEM_TYPE_WIDGET,
                className = PSEUDO_WIDGET_TIME,
                widgetId = -1,
                shortcutId = "",
                icon = null,
                docked = false,
                parentId = null,
            )
        )
        Timber.i("HubGadgetPlanter: time widget ${right + 1}x$TIME_ROWS at the top left")
        return true
    }

    fun isSeededGadget(item: LobbyLookerMatrixDatum): Boolean =
        isSeededLookupLine(item) ||
            (item.type == ITEM_TYPE_WIDGET && item.page == 0 && item.className == PSEUDO_WIDGET_TIME)

    private fun isSeededLookupLine(item: LobbyLookerMatrixDatum): Boolean =
        item.type == ITEM_TYPE_WIDGET && !item.docked && item.parentId == null && item.page == 0 &&
            item.left == 0 && item.top == item.bottom &&
            (item.className == PSEUDO_WIDGET_GOOGLE_SEARCH || item.className == PSEUDO_WIDGET_SEARCH ||
                item.className in GOOGLE_PROVIDERS || item.className in CHROME_PROVIDERS)

    fun configurationOptional(info: AppWidgetProviderInfo): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0

    private fun lookupProvider(context: Context, choice: String): AppWidgetProviderInfo? = runCatching {
        val installed = AppWidgetManager.getInstance(context).installedProviders
            .filter { it.minHeight <= MAX_ROWS_TALL_DP * context.resources.displayMetrics.density }

            .filter { it.configure == null || configurationOptional(it) }
        val order = if (choice == "chrome") CHROME_PROVIDERS + GOOGLE_PROVIDERS else GOOGLE_PROVIDERS + CHROME_PROVIDERS
        order.firstNotNullOfOrNull { className ->
            installed.firstOrNull { it.provider.className == className }
        }
    }.getOrNull()
}

private val LauncherSettings.showTimeWidget: Boolean get() = timeWidget

// shortcut: LauncherSettings has no search-widget on/off key, so the search row is always laid down (MSG-05 default).
private val LauncherSettings.showSearchWidget: Boolean get() = true

private val LauncherSettings.searchWidgetChoice: String
    get() = searchWidget.trim().lowercase().takeIf { it == "chrome" } ?: "google"
