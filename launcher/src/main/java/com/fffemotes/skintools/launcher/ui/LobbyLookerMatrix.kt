package com.fffemotes.skintools.launcher.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Size
import android.util.SizeF
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.RelativeLayout
import androidx.core.graphics.createBitmap
import java.util.concurrent.Executors
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toRect
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.google.android.material.math.MathUtils
import kotlinx.collections.immutable.toImmutableList
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.performHapticFeedback
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isSPlus
import com.fffemotes.skintools.launcher.Launcher
import com.fffemotes.skintools.launcher.R
import com.fffemotes.skintools.launcher.screens.XHubStage
import com.fffemotes.skintools.launcher.databinding.LxHomeShowGridBinding
import com.fffemotes.skintools.launcher.exts.launcherConfig
import com.fffemotes.skintools.launcher.exts.pullDrawableForPkgName
import com.fffemotes.skintools.launcher.exts.homeScreenGridItemsDB
import com.fffemotes.skintools.launcher.support.ITEM_TYPE_FOLDER
import com.fffemotes.skintools.launcher.support.MAX_DOCK_SLOTS
import com.fffemotes.skintools.launcher.support.ITEM_TYPE_ICON
import com.fffemotes.skintools.launcher.support.ITEM_TYPE_SHORTCUT
import com.fffemotes.skintools.launcher.support.ITEM_TYPE_WIDGET
import com.fffemotes.skintools.launcher.support.PSEUDO_WIDGET_CLOCK
import com.fffemotes.skintools.launcher.support.PSEUDO_WIDGET_GOOGLE_SEARCH
import com.fffemotes.skintools.launcher.support.PSEUDO_WIDGET_SEARCH
import com.fffemotes.skintools.launcher.support.PSEUDO_WIDGET_TIME
import com.fffemotes.skintools.launcher.support.WIDGET_HOST_ID
import com.fffemotes.skintools.launcher.entities.LobbyLookerMatrixDatum
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

class LobbyLookerMatrix(context: Context, attrs: AttributeSet, defStyle: Int) :
    RelativeLayout(context, attrs, defStyle) {
    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    private lateinit var binding: LxHomeShowGridBinding
    private var columnCount = context.launcherConfig.homeColumnCount
    private var dockSlotCount = context.launcherConfig.dockColumnCount

    private var dockCellWidth = 0
    private var dockIconSize = 0
    private var dockIconMargin = 0

    private var pageTransitAlpha = 255

    private val pageTransitPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var rowCount = context.launcherConfig.homeRowCount
    private var pageIndicatorsYPos = 0
    private val cells = mutableMapOf<Point, Rect>()
    private var dockCellY = 0
    var cellWidth = 0
    var cellHeight = 0

    private var iconMargin =
        (context.resources.getDimension(R.dimen.launcher_icon_side_margin) * 5 / columnCount).toInt()
    private var labelSideMargin =
        context.resources.getDimension(org.fossify.commons.R.dimen.small_margin).toInt()
    private var roundedCornerRadius =
        context.resources.getDimension(org.fossify.commons.R.dimen.activity_margin)
    private var folderPadding =
        context.resources.getDimension(org.fossify.commons.R.dimen.medium_margin)
    private var pageIndicatorRadius =
        context.resources.getDimension(R.dimen.launcher_page_indicator_dot_radius)
    private var pageIndicatorMargin = context.resources.getDimension(R.dimen.launcher_page_indicator_margin)
    private var pageIndicatorInactiveRadius =
        context.resources.getDimension(R.dimen.launcher_page_indicator_inactive_radius)
    private var pageIndicatorActiveExtra =
        context.resources.getDimension(R.dimen.launcher_page_indicator_active_extra)
    private var pageIndicatorClearance =
        context.resources.getDimension(R.dimen.launcher_page_indicator_clearance)
    private var dockBottomMargin =
        context.resources.getDimensionPixelSize(R.dimen.launcher_dock_bottom_margin)
    private var textPaint: TextPaint
    private var contrastTextPaint: TextPaint
    private var folderTitleTextPaint: TextPaint
    private var dragShadowCirclePaint: Paint
    private var emptyPageIndicatorPaint: Paint
    private var currentPageIndicatorPaint: Paint
    private var folderBackgroundPaint: Paint
    private var folderIconBackgroundPaint: Paint
    private var draggedItem: LobbyLookerMatrixDatum? = null
    private var resizedWidget: LobbyLookerMatrixDatum? = null
    private var isFirstDraw = true
    private var iconSize = 0

    // The pager's timers run on this, not View.getHandler(): that is null once the grid is detached,
    // and a page slide can still end after it - a recreate() mid-swipe threw on the animation's end.
    private val pagerHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val pager = AnimatedMatrixPager(
        pullMaxLeaf = ::pullMaxLeaf,
        redrawGrid = ::redrawGrid,
        getWidth = { width },
        getHandler = { pagerHandler },
        getNextPageBound = { right - sideMargins.right - cellWidth / 2 },
        getPrevPageBound = { left + sideMargins.left + cellWidth / 2 },
        pageChangeStarted = {
            widgetViews.forEach { it.reinitTouches() }
            dismissFolder()
            accessibilityHelper.invalidateRoot()
        }
    )

    private var currentlyOpenFolder: HomeScreenFolder? = null
    private var draggingLeftFolderAt: Long? = null
    private var draggingEnteredNewFolderAt: Long? = null

    var sideMargins = Rect()

    // Swapped in from a background refetch while the main thread draws and edits it: a
    // copy-on-write list makes every walk see a stable snapshot instead of throwing
    // ConcurrentModificationException mid-draw.
    @Volatile
    private var gridItems: MutableList<LobbyLookerMatrixDatum> =
        java.util.concurrent.CopyOnWriteArrayList<LobbyLookerMatrixDatum>()
    private var gridCenters = ArrayList<Point>()
    private var draggedItemCurrentCoords = Pair(-1, -1)
    private var widgetViews = ArrayList<CustomGadgetWidgetHolderPane>()

    /** The grid row each placed widget view belongs to, so a view whose row is gone can be found - see [dropOrphanWidgetWidgets]. */
    private val widgetViewItemIds = java.util.WeakHashMap<CustomGadgetWidgetHolderPane, Long>()

    private val bindAttempted = HashSet<Long>()

    private val bindPending = HashSet<Long>()

    val appWidgetHost = CustomGadgetWidgetHolder(context, WIDGET_HOST_ID)
    private val appWidgetManager = AppWidgetManager.getInstance(context)

    private val accessibilityHelper = HomeScreenGridTouchHelper(this)
    var itemClickListener: ((LobbyLookerMatrixDatum) -> Unit)? = null
    var itemLongClickListener: ((LobbyLookerMatrixDatum) -> Unit)? = null

    init {
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper)

        val customTypeface = FontHelper.getTypeface(context)

        val profileLabelSp = context.launcherConfig.labelTextSp
        val labelTextSize = if (profileLabelSp > 0f) {
            profileLabelSp * context.resources.displayMetrics.scaledDensity
        } else {
            context.resources.getDimension(org.fossify.commons.R.dimen.smaller_text_size)
        }
        textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = labelTextSize
            setShadowLayer(2f, 0f, 0f, Color.BLACK)
            typeface = customTypeface
        }

        contrastTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getProperTextColor()
            textSize = labelTextSize
            setShadowLayer(2f, 0f, 0f, context.getProperTextColor().getContrastColor())
            typeface = customTypeface
        }

        folderTitleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getProperTextColor()
            textSize = context.resources.getDimension(org.fossify.commons.R.dimen.medium_text_size)
            typeface = customTypeface
        }

        dragShadowCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.resources.getColor(org.fossify.commons.R.color.hint_white)
            strokeWidth = context.resources.getDimension(org.fossify.commons.R.dimen.small_margin)
            style = Paint.Style.STROKE
        }

        emptyPageIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.resources.getColor(android.R.color.white)
            style = Paint.Style.FILL
        }
        currentPageIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.resources.getColor(android.R.color.white)
            style = Paint.Style.FILL
        }

        folderBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getProperBackgroundColor()
            style = Paint.Style.FILL
        }

        folderIconBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getProperBackgroundColor().adjustAlpha(0.9f)
            style = Paint.Style.FILL
        }

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(Type.systemBars() or Type.displayCutout())
            val gestures = insets.getInsets(Type.systemGestures())
            val navIgnoring = insets.getInsetsIgnoringVisibility(Type.navigationBars())

            val sideMargin = context.resources
                .getDimension(org.fossify.commons.R.dimen.normal_margin).toInt()

            val newLeft   = sideMargin + bars.left
            val newRight  = sideMargin + bars.right
            val newTop    = bars.top
            val newBottom = max(bars.bottom, max(gestures.bottom, navIgnoring.bottom))
            val marginsChanged = sideMargins.left != newLeft
                    || sideMargins.top != newTop
                    || sideMargins.right != newRight
                    || sideMargins.bottom != newBottom
            if (marginsChanged) {
                sideMargins.set(newLeft, newTop, newRight, newBottom)
                cells.clear()
                gridCenters.clear()
                isFirstDraw = true
                redrawGrid()
            }

            insets
        }

        pullGridItems()
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        if (accessibilityHelper.dispatchHoverEvent(event)) return true
        return super.dispatchHoverEvent(event)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = LxHomeShowGridBinding.bind(this)
    }

    fun pullGridItems() {
        ensureBackgroundThread {
            runCatching {
                val started = android.os.SystemClock.elapsedRealtime()
                val providers = appWidgetManager.installedProviders

                val loaded = gridItems
                    .filter { it.type == ITEM_TYPE_ICON && it.drawable != null }
                    .associateBy { Pair(it.id, it.packageName) }
                val items = context.homeScreenGridItemsDB.pullAllCells() as ArrayList<LobbyLookerMatrixDatum>

                // Settings iconOnHome: our own icon is left out of the drawn grid but kept in the DB, so it comes back when switched on.
                if (!com.fffemotes.skintools.launcher.Launcher.settings().iconOnHome) {
                    val own = context.packageName
                    items.removeAll { it.type == ITEM_TYPE_ICON && it.packageName == own }
                }
                items.forEach { item ->
                    if (item.type == ITEM_TYPE_ICON) {
                        loaded[Pair(item.id, item.packageName)]?.let { old ->
                            item.drawable = old.drawable
                            item.iconRaster = old.iconRaster
                            item.iconRasterSource = old.iconRasterSource
                        }
                    } else if (item.type == ITEM_TYPE_FOLDER) {
                        item.drawable = item.toBundle().generateDrawable()
                    } else if (item.type == ITEM_TYPE_SHORTCUT) {
                        if (item.icon != null) {
                            item.drawable = item.icon?.toDrawable(context.resources)
                        } else {
                            ensureBackgroundThread {
                                context.homeScreenGridItemsDB.eraseByKey(item.id!!)
                            }
                        }
                    }

                    item.providerInfo =
                        providers.firstOrNull { it.provider.className == item.className }
                }

                val missing = items.filter { it.type == ITEM_TYPE_ICON && it.drawable == null }
                val page = pager.getCurrentPage()
                val (now, later) = missing.partition { it.docked || it.parentId != null || it.page == page }
                decodeGlyphs(now)
                gridItems = java.util.concurrent.CopyOnWriteArrayList(items)
                redrawGrid()
                post { dropOrphanWidgetWidgets() }
                val shown = android.os.SystemClock.elapsedRealtime() - started
                if (later.isNotEmpty()) {
                    decodeGlyphs(later.sortedBy { kotlin.math.abs(it.page - page) })
                    redrawGrid()
                }
                timber.log.Timber.d(
                    "HomeScreenGrid: fetched ${items.size} items - page in view drawn in $shown ms (${now.size} icons), " +
                        "all in ${android.os.SystemClock.elapsedRealtime() - started} ms (${later.size} more, " +
                        "${items.count { it.type == ITEM_TYPE_ICON } - missing.size} kept)"
                )
            }.onFailure { timber.log.Timber.w(it, "HomeScreenGrid: pullGridItems failed") }
        }
    }

    private fun decodeGlyphs(icons: List<LobbyLookerMatrixDatum>) {
        if (icons.isEmpty()) return
        val pool = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        )
        try {
            icons.map { item ->
                pool.submit { item.drawable = context.pullDrawableForPkgName(item.packageName) }
            }.forEach { future -> runCatching { future.get() } }
        } finally {
            pool.shutdown()
        }
    }

    fun resizeGrid(newRowCount: Int, newColumnCount: Int) {
        if (columnCount != newColumnCount || rowCount != newRowCount) {
            rowCount = newRowCount
            columnCount = newColumnCount
            cells.clear()
            gridCenters.clear()
            iconMargin =
                (context.resources.getDimension(R.dimen.launcher_icon_side_margin) * 5 / columnCount).toInt()
            isFirstDraw = true

            gridItems
                .filter { it.type == ITEM_TYPE_WIDGET && it.pseudoWidgetLayout() == null }
                .forEach { appWidgetHost.deleteAppWidgetId(it.widgetId) }
            widgetViews.forEach { removeView(it) }
            widgetViews.clear()
            redrawGrid()
        }
    }

    fun updateColors() {
        folderTitleTextPaint.color = context.getProperTextColor()
        contrastTextPaint.color = context.getProperTextColor()
        contrastTextPaint.setShadowLayer(
            2f, 0f, 0f, context.getProperTextColor().getContrastColor()
        )
        folderBackgroundPaint.color = context.getProperBackgroundColor()
    }

    fun deleteAppIcon(item: LobbyLookerMatrixDatum) {
        ensureBackgroundThread {
            dropCellFromAtriumViewer(item)
            post {
                removeView(widgetViews.firstOrNull { it.tag == item.widgetId })
            }

            gridItems.removeIf { it.id == item.id }
            if (pager.isOutsideOfLeafRange()) {
                post {
                    prevPage()
                }
            }
            redrawGrid()
        }
    }

    /**
     * Takes off the grid every widget view whose row is no longer in [gridItems]. A row can be
     * deleted without the grid being told; the per-frame positioning only walks [gridItems], so
     * such a view froze where it was (usually page 1's search bar) and showed on every page.
     * Runs on the main thread after each refetch.
     */
    private fun dropOrphanWidgetWidgets() {
        val live = gridItems.mapNotNullTo(HashSet()) { it.id }
        val orphans = orphanWidgetViews(widgetViews.associateWith { widgetViewItemIds[it] }, live)
        if (orphans.isEmpty()) return
        orphans.forEach { removeView(it) }
        widgetViews.removeAll(orphans.toSet())
        val detail = orphans.joinToString { "${it.appWidgetInfo?.provider?.flattenToShortString() ?: "pseudo"}#${widgetViewItemIds[it]}" }
        timber.log.Timber.w("HomeScreenGrid: dropped %d widget view(s) whose row is gone: %s", orphans.size, detail)
    }

    fun deletePlacedLookupBars() {
        post {
            gridItems
                .filter { it.className == PSEUDO_WIDGET_SEARCH }
                .forEach { item ->
                    removeView(widgetViews.firstOrNull { it.tag == item.widgetId })
                    widgetViews.removeIf { it.tag == item.widgetId }
                }

            gridItems.removeIf { it.className == PSEUDO_WIDGET_SEARCH }
            redrawGrid()
        }
    }

    fun dropWidget(item: LobbyLookerMatrixDatum) {
        ensureBackgroundThread {
            dropCellFromAtriumViewer(item)
            post {
                removeView(widgetViews.firstOrNull { it.tag == item.widgetId })
                widgetViews.removeIf { it.tag == item.widgetId }
            }
            gridItems.removeIf { it.id == item.id }
        }
    }

    private fun dropCellFromAtriumViewer(item: LobbyLookerMatrixDatum) {
        ensureBackgroundThread {
            if (item.id != null) {
                context.homeScreenGridItemsDB.eraseByKey(item.id!!)
                if (item.parentId != null) {
                    gridItems
                        .filter {
                            it.parentId == item.parentId && it.left > item.left && it.id != item.id
                        }
                        .forEach {
                            it.left -= 1
                        }

                    context.homeScreenGridItemsDB.shiftBundleCells(
                        folderId = item.parentId!!,
                        shiftFrom = item.left,
                        shiftBy = -1,
                        excludingId = item.id
                    )
                }
            }

            if (item.type == ITEM_TYPE_WIDGET) {
                appWidgetHost.deleteAppWidgetId(item.widgetId)
            }

            if (
                item.page != 0 && gridItems
                    .none { it.page == item.page && it.id != item.id && it.parentId == null }
            ) {
                eraseLeaf(item.page)
            }
        }
    }

    fun itemDraggingStarted(draggedGridItem: LobbyLookerMatrixDatum) {
        draggedItem = draggedGridItem

        if (draggedGridItem.type == ITEM_TYPE_WIDGET) {
            dismissFolder()
        }

        if (draggedItem!!.drawable == null) {
            if (draggedItem?.type == ITEM_TYPE_FOLDER) {
                draggedItem!!.drawable = draggedGridItem.toBundle().generateDrawable()
            } else {
                draggedItem!!.drawable =
                    context.pullDrawableForPkgName(draggedGridItem.packageName)
            }
        }

        redrawGrid()
    }

    fun draggedItemMoved(x: Int, y: Int) {
        if (draggedItem == null) {
            return
        }

        currentlyOpenFolder?.also { folder ->
            if (folder.getDrawingRect().contains(x.toFloat(), y.toFloat())) {
                draggingLeftFolderAt = null
            } else {
                draggingLeftFolderAt.also {
                    if (it == null) {
                        draggingLeftFolderAt = System.currentTimeMillis()
                    } else if (System.currentTimeMillis() - it > FOLDER_CLOSE_HOLD_THRESHOLD) {
                        dismissFolder()
                    }
                }
            }
        }

        if (
            draggedItemCurrentCoords.first == -1
            && draggedItemCurrentCoords.second == -1 && draggedItem != null
        ) {
            if (draggedItem!!.type == ITEM_TYPE_WIDGET) {
                val draggedWidgetView = widgetViews.firstOrNull { it.tag == draggedItem?.widgetId }
                if (draggedWidgetView != null) {
                    draggedWidgetView.buildDrawingCache()
                    draggedItem!!.drawable = Bitmap.createBitmap(draggedWidgetView.drawingCache)
                        .toDrawable(context.resources)
                    draggedWidgetView.beGone()
                }
            }
        }

        draggedItemCurrentCoords = Pair(x, y)

        if (draggedItem?.type != ITEM_TYPE_FOLDER && draggedItem?.type != ITEM_TYPE_WIDGET) {
            val center = gridCenters.minBy {
                abs(it.x - draggedItemCurrentCoords.first + sideMargins.left) + abs(it.y - draggedItemCurrentCoords.second + sideMargins.top)
            }
            val coveredCell = pullClosestGridCells(center)
            if (coveredCell != null) {
                val coveredFolder = gridItems.firstOrNull {
                    it.type == ITEM_TYPE_FOLDER
                            && it.left == coveredCell.x && it.top == coveredCell.y
                }

                if (
                    coveredFolder != null
                    && coveredFolder.id != draggedItem?.id
                    && currentlyOpenFolder == null
                ) {
                    draggingEnteredNewFolderAt.also {
                        if (it == null) {
                            draggingEnteredNewFolderAt = System.currentTimeMillis()
                        } else if (System.currentTimeMillis() - it > FOLDER_OPEN_HOLD_THRESHOLD) {
                            if (
                                coveredFolder.toBundle().getItems()
                                    .count() >= LobbyLookerMatrixDatum.FOLDER_MAX_CAPACITY
                                && draggedItem?.parentId != coveredFolder.id
                            ) {
                                performHapticFeedback()
                                draggingEnteredNewFolderAt = null
                            } else {
                                wakeFolder(coveredFolder)
                            }
                        }
                    }
                } else {
                    draggingEnteredNewFolderAt = null
                }
            } else {
                draggingEnteredNewFolderAt = null
            }
        }

        pager.processCellMovement(x, y)
        redrawGrid()
    }

    fun itemDraggingStopped() {
        widgetViews.forEach {
            it.hasLongPressed = false
        }

        if (draggedItem == null) {
            return
        }

        pager.cellMovementStopped()
        when (draggedItem!!.type) {
            ITEM_TYPE_FOLDER -> moveElement()
            ITEM_TYPE_ICON, ITEM_TYPE_SHORTCUT -> appendAppletGlyphOrShortcut()
            ITEM_TYPE_WIDGET -> appendWidget()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun widgetLongPressed(item: LobbyLookerMatrixDatum) {
        resizedWidget = item
        redrawGrid()

        val widgetView = widgetViews.firstOrNull { it.tag == resizedWidget!!.widgetId }
        binding.sizeCasingIx.beGone()
        if (widgetView != null) {
            val viewX = widgetView.x.toInt()
            val viewY = widgetView.y.toInt()
            val frameRect = Rect(viewX, viewY, viewX + widgetView.width, viewY + widgetView.height)
            val otherGridItems = gridItems.siftShownOnLiveLeafOnly()
                .filter { !it.outOfBounds() }
                .filter { it.widgetId != item.widgetId }
                .toMutableList() as ArrayList<LobbyLookerMatrixDatum>
            binding.sizeCasingIx.reloadSnapshotCoords(
                coords = frameRect,
                cellWidth = cellWidth,
                cellHeight = cellHeight,
                sideMargins = sideMargins,
                gridItem = item,
                allGridItems = otherGridItems
            )
            binding.sizeCasingIx.beVisible()
            binding.sizeCasingIx.z = 1f
            binding.sizeCasingIx.onClickListener = {
                Launcher.events.onClick("widget_resize_done")
                concealResizeLines()
            }

            binding.sizeCasingIx.onResizeListener = { cellsRect ->
                item.left = cellsRect.left
                item.top = cellsRect.top
                item.right = cellsRect.right
                item.bottom = if (cellsRect.bottom > rowCount - 2) {
                    rowCount - 2
                } else {
                    cellsRect.bottom
                }
                reloadWidgetPointAndDims(widgetView, item)

                val widgetTargetCells = ArrayList<Pair<Int, Int>>()
                for (xCell in item.left..item.right) {
                    for (yCell in item.top..item.bottom) {
                        widgetTargetCells.add(Pair(xCell, yCell))
                    }
                }

                gridItems.siftShownOnLiveLeafOnly().filter { it.id != item.id }
                    .forEach { gridItem ->
                        for (xCell in gridItem.left..gridItem.right) {
                            for (
                            yCell in gridItem.pullDockAdjustedTop(rowCount)
                                .rangeTo(gridItem.pullDockAdjustedBottom(rowCount))
                            ) {
                                val cell = Pair(xCell, yCell)
                                val isAnyCellOccupied = widgetTargetCells.contains(cell)
                                if (isAnyCellOccupied) {
                                    if (
                                        gridItem.type == ITEM_TYPE_WIDGET && gridItem.outOfBounds()
                                    ) {
                                        dropWidget(gridItem)
                                    }
                                }
                            }
                        }
                    }

                ensureBackgroundThread {
                    context.homeScreenGridItemsDB.reloadCellPoint(
                        left = item.left,
                        top = item.top,
                        right = item.right,
                        bottom = item.bottom,
                        page = item.page,
                        docked = false,
                        parentId = null,
                        id = item.id!!
                    )
                }
            }

            widgetView.ignoreTouches = true
            widgetView.setOnTouchListener { v, event ->
                binding.sizeCasingIx.onTouchEvent(event)
                return@setOnTouchListener true
            }
        }
    }

    fun concealResizeLines() {
        if (resizedWidget == null) {
            return
        }

        binding.sizeCasingIx.beGone()
        widgetViews.firstOrNull { it.tag == resizedWidget!!.widgetId }?.apply {
            ignoreTouches = false
            setOnTouchListener(null)
        }
        resizedWidget = null
    }

    private fun moveElement() {
        val draggedHomeGridItem = gridItems.firstOrNull { it.id == draggedItem?.id }
        val center = gridCenters.minBy {
            abs(it.x - draggedItemCurrentCoords.first + sideMargins.left) + abs(it.y - draggedItemCurrentCoords.second + sideMargins.top)
        }

        var redrawIcons = false
        val gridCells = pullClosestGridCells(center)
        if (gridCells != null) {
            val xIndex = gridCells.x
            val yIndex = gridCells.y

            var isDroppingPositionValid = true
            val wantedCell = Pair(xIndex, yIndex)

            if (draggedHomeGridItem?.type == ITEM_TYPE_FOLDER && yIndex == rowCount - 1) {
                isDroppingPositionValid = false
            } else {
                gridItems.siftShownOnLiveLeafOnly().forEach { item ->
                    for (xCell in item.left..item.right) {
                        for (
                        yCell in item.pullDockAdjustedTop(rowCount)
                            .rangeTo(item.pullDockAdjustedBottom(rowCount))
                        ) {
                            val cell = Pair(xCell, yCell)
                            val isAnyCellOccupied = wantedCell == cell
                            if (isAnyCellOccupied) {
                                if (item.type == ITEM_TYPE_WIDGET && item.outOfBounds()) {
                                    dropWidget(item)
                                } else {
                                    isDroppingPositionValid = false
                                }
                                return@forEach
                            }
                        }
                    }
                }
            }

            if (isDroppingPositionValid) {
                draggedHomeGridItem?.apply {
                    val oldPage = page
                    left = xIndex
                    top = yIndex
                    right = xIndex
                    bottom = yIndex
                    page = pager.getCurrentPage()
                    docked = yIndex == rowCount - 1

                    ensureBackgroundThread {
                        context.homeScreenGridItemsDB.reloadCellPoint(
                            left = left,
                            top = top,
                            right = right,
                            bottom = bottom,
                            page = page,
                            docked = docked,
                            parentId = parentId,
                            id = id!!
                        )

                        if (page != oldPage && oldPage != 0) {
                            if (gridItems.none { it.page == oldPage && it.parentId == null }) {
                                eraseLeaf(oldPage)
                            }
                        }
                    }
                }
                redrawIcons = true
            }
        }

        draggedItem = null
        draggedItemCurrentCoords = Pair(-1, -1)
        if (redrawIcons) {
            redrawGrid()
        }
    }

    private fun appendAppletGlyphOrShortcut() {
        var isDroppingPositionValid = false
        var potentialParent: LobbyLookerMatrixDatum? = null
        var xIndex: Int? = null
        var yIndex: Int? = null
        var redrawIcons = false

        val folder = currentlyOpenFolder
        if (folder != null && folder.pullCellsDrawingRect().contains(
                (draggedItemCurrentCoords.first).toFloat(),
                (draggedItemCurrentCoords.second).toFloat()
            )
        ) {
            val centers = folder.pullCellsGridCenters()
            if (centers.isNotEmpty()) {
                val center = centers.minByOrNull {
                    abs(it.second - draggedItemCurrentCoords.first + sideMargins.left)
                        .plus(abs(it.third - draggedItemCurrentCoords.second + sideMargins.top))
                }
                xIndex = center?.first ?: 0
            } else {
                xIndex = 0
            }

            isDroppingPositionValid = true
            potentialParent = folder.item
            yIndex = 0
            redrawIcons = true
        } else {
            val center = gridCenters.minBy {
                abs(it.x - draggedItemCurrentCoords.first + sideMargins.left)
                    .plus(abs(it.y - draggedItemCurrentCoords.second + sideMargins.top))
            }

            val gridCells = pullClosestGridCells(center)
            if (gridCells != null) {
                xIndex = gridCells.x
                yIndex = gridCells.y

                isDroppingPositionValid = true
                val wantedCell = Pair(xIndex, yIndex)
                gridItems.siftShownOnLiveLeafOnly().filter { it.id != draggedItem?.id }
                    .forEach { item ->
                        for (xCell in item.left..item.right) {
                            for (
                            yCell in item.pullDockAdjustedTop(rowCount)
                                .rangeTo(item.pullDockAdjustedBottom(rowCount))
                            ) {
                                val cell = Pair(xCell, yCell)
                                val isAnyCellOccupied = wantedCell == cell
                                if (isAnyCellOccupied) {
                                    if (item.type != ITEM_TYPE_WIDGET && !item.docked) {
                                        potentialParent = item
                                    } else {
                                        if (item.type == ITEM_TYPE_WIDGET && item.outOfBounds()) {
                                            dropWidget(item)
                                        } else {
                                            isDroppingPositionValid = false
                                        }
                                    }
                                    return@forEach
                                }
                            }
                        }
                    }
            }
        }

        if (isDroppingPositionValid) {
            val draggedHomeGridItem = gridItems.firstOrNull { it.id == draggedItem?.id }

            if (potentialParent != null) {
                if (potentialParent.type == ITEM_TYPE_FOLDER) {
                    appendAppletGlyphOrShortcut(
                        draggedHomeGridItem,
                        xIndex!!,
                        yIndex!!,
                        potentialParent.id,
                        toFolderEnd = potentialParent != currentlyOpenFolder?.item
                    )
                } else {
                    val parentItem = potentialParent.copy(
                        type = ITEM_TYPE_FOLDER,
                        id = null,
                        title = resources.getString(org.fossify.commons.R.string.folder)
                    )
                    ensureBackgroundThread {
                        val newId = context.homeScreenGridItemsDB.insert(parentItem)
                        parentItem.id = newId
                        potentialParent.apply {
                            parentId = newId
                            left = 0
                            context.homeScreenGridItemsDB.reloadCellPoint(
                                left = left,
                                top = top,
                                right = right,
                                bottom = bottom,
                                page = page,
                                docked = docked,
                                parentId = newId,
                                id = id!!
                            )
                        }
                        (context as? XHubStage)?.runOnUiThread {
                            gridItems.add(parentItem)
                            appendAppletGlyphOrShortcut(draggedHomeGridItem, xIndex!!, yIndex!!, newId)
                        }
                    }
                }
                return
            } else {
                appendAppletGlyphOrShortcut(draggedHomeGridItem, xIndex!!, yIndex!!)
                return
            }
        } else {
            performHapticFeedback()
            redrawIcons = true
        }

        draggedItem = null
        draggedItemCurrentCoords = Pair(-1, -1)
        if (redrawIcons) {
            redrawGrid()
        }
    }

    private fun appendAppletGlyphOrShortcut(
        draggedHomeGridItem: LobbyLookerMatrixDatum?,
        xIndex: Int,
        yIndex: Int,
        newParentId: Long? = null,
        toFolderEnd: Boolean = true,
    ) {
        if (newParentId != null && newParentId != draggedHomeGridItem?.parentId) {
            gridItems.firstOrNull { it.id == newParentId }?.also {
                if (it.toBundle().getItems().count() >= LobbyLookerMatrixDatum.FOLDER_MAX_CAPACITY) {
                    performHapticFeedback()
                    draggedItem = null
                    draggedItemCurrentCoords = Pair(-1, -1)
                    redrawGrid()
                    return
                }
            }
        }

        val finalXIndex = if (newParentId != null) {
            if (toFolderEnd) {
                gridItems
                    .firstOrNull { it.id == newParentId }
                    ?.toBundle()
                    ?.getItems()
                    ?.maxOfOrNull { it.left + 1 } ?: 0
            } else {
                min(
                    a = xIndex,
                    b = gridItems
                        .firstOrNull { it.id == newParentId }
                        ?.toBundle()
                        ?.getItems()
                        ?.maxOfOrNull {
                            if (draggedHomeGridItem?.parentId == newParentId) {
                                it.left
                            } else {
                                it.left + 1
                            }
                        } ?: 0)
            }
        } else {
            xIndex
        }

        if (draggedHomeGridItem != null) {
            draggedHomeGridItem.apply {
                val oldParentId = parentId
                val oldLeft = left
                val oldPage = page
                left = finalXIndex
                top = yIndex
                right = finalXIndex
                bottom = yIndex
                page = pager.getCurrentPage()
                docked = yIndex == rowCount - 1
                parentId = newParentId

                val oldParent = gridItems.firstOrNull { it.id == oldParentId }
                val deleteOldParent = if (oldParent?.toBundle()?.getItems()?.isEmpty() == true) {
                    gridItems.remove(oldParent)
                    true
                } else {
                    false
                }

                ensureBackgroundThread {
                    context.homeScreenGridItemsDB.reloadCellPoint(
                        left = left,
                        top = top,
                        right = right,
                        bottom = bottom,
                        page = page,
                        docked = docked,
                        parentId = newParentId,
                        id = id!!
                    )
                    if (deleteOldParent && oldParentId != null) {
                        context.homeScreenGridItemsDB.eraseByKey(oldParentId)
                    } else if (
                        oldParentId != null
                        && gridItems.none { it.parentId == oldParentId && it.left == oldLeft }
                    ) {
                        gridItems
                            .filter {
                                it.parentId == oldParentId && it.left > oldLeft && it.id != id
                            }
                            .forEach {
                                it.left -= 1
                            }
                        context.homeScreenGridItemsDB.shiftBundleCells(oldParentId, oldLeft, -1, id)
                    }

                    if (
                        newParentId != null
                        && gridItems.any { it.parentId == newParentId && it.left == left }
                        && (newParentId != oldParentId || left != oldLeft)
                    ) {
                        gridItems
                            .filter { it.parentId == newParentId && it.left >= left && it.id != id }
                            .forEach {
                                it.left += 1
                            }

                        context.homeScreenGridItemsDB.shiftBundleCells(
                            folderId = newParentId,
                            shiftFrom = left - 1,
                            shiftBy = +1,
                            excludingId = id
                        )
                    }

                    if (page != oldPage && oldPage != 0) {
                        if (gridItems.none { it.page == oldPage && it.parentId == null }) {
                            eraseLeaf(oldPage)
                        }
                    }
                }
            }
        } else if (draggedItem != null) {
            val newHomeScreenGridItem = LobbyLookerMatrixDatum(
                id = null,
                left = finalXIndex,
                top = yIndex,
                right = finalXIndex,
                bottom = yIndex,
                page = pager.getCurrentPage(),
                packageName = draggedItem!!.packageName,
                activityName = draggedItem!!.activityName,
                title = draggedItem!!.title,
                type = draggedItem!!.type,
                className = "",
                widgetId = -1,
                shortcutId = "",
                icon = draggedItem!!.icon,
                docked = yIndex == rowCount - 1,
                parentId = newParentId,
                drawable = draggedItem!!.drawable,
                providerInfo = draggedItem!!.providerInfo,
                activityInfo = draggedItem!!.activityInfo
            )

            fun finalizeBundleOrder(newItem: LobbyLookerMatrixDatum) {
                if (
                    newParentId != null
                    && gridItems.any { it.parentId == newParentId && it.left == newItem.left }
                ) {
                    gridItems
                        .filter {
                            it.parentId == newParentId
                                    && it.left >= newItem.left && it.id != newItem.id
                        }
                        .forEach {
                            it.left += 1
                        }

                    context.homeScreenGridItemsDB.shiftBundleCells(
                        folderId = newParentId,
                        shiftFrom = newItem.left - 1,
                        shiftBy = +1,
                        excludingId = newItem.id
                    )
                }
            }

            if (newHomeScreenGridItem.type == ITEM_TYPE_ICON) {
                ensureBackgroundThread {
                    repoAndDisplayGridItem(newHomeScreenGridItem)
                    finalizeBundleOrder(newHomeScreenGridItem)
                }
            } else if (newHomeScreenGridItem.type == ITEM_TYPE_SHORTCUT) {
                (context as? XHubStage)?.processShorcutCreation(newHomeScreenGridItem.activityInfo!!) { shortcutId, label, icon ->
                    ensureBackgroundThread {
                        newHomeScreenGridItem.shortcutId = shortcutId
                        newHomeScreenGridItem.title = label
                        newHomeScreenGridItem.icon = icon.toBitmap()
                        newHomeScreenGridItem.drawable = icon
                        repoAndDisplayGridItem(newHomeScreenGridItem)
                        finalizeBundleOrder(newHomeScreenGridItem)
                    }
                }
            }
        }

        draggedItem = null
        draggedItemCurrentCoords = Pair(-1, -1)
        redrawGrid()
    }

    fun repoAndDisplayGridItem(item: LobbyLookerMatrixDatum) {
        val newId = context.homeScreenGridItemsDB.insert(item)
        item.id = newId
        gridItems.add(item)
        redrawGrid()
    }

    private fun appendWidget() {
        val center = gridCenters.minBy {
            abs(it.x - draggedItemCurrentCoords.first + sideMargins.left) + abs(it.y - draggedItemCurrentCoords.second + sideMargins.top)
        }

        val gridCells = pullClosestGridCells(center)
        if (gridCells != null) {
            val widgetRect = pullWidgetOccupiedRect(gridCells)
            val widgetTargetCells = ArrayList<Pair<Int, Int>>()
            for (xCell in widgetRect.left..widgetRect.right) {
                for (yCell in widgetRect.top..widgetRect.bottom) {
                    widgetTargetCells.add(Pair(xCell, yCell))
                }
            }

            var areAllCellsEmpty = true
            gridItems.siftShownOnLiveLeafOnly().filter { it.id != draggedItem?.id }
                .forEach { item ->
                    for (xCell in item.left..item.right) {
                        for (yCell in item.pullDockAdjustedTop(rowCount)
                            .rangeTo(item.pullDockAdjustedBottom(rowCount))
                        ) {
                            val cell = Pair(xCell, yCell)
                            val isAnyCellOccupied = widgetTargetCells.contains(cell)
                            if (isAnyCellOccupied) {
                                if (item.type == ITEM_TYPE_WIDGET && item.outOfBounds()) {
                                    dropWidget(item)
                                } else {
                                    areAllCellsEmpty = false
                                    return@forEach
                                }
                            }
                        }
                    }
                }

            if (areAllCellsEmpty) {
                val widgetItem = draggedItem!!.copy()
                val oldPage = widgetItem.page
                widgetItem.apply {
                    left = widgetRect.left
                    top = widgetRect.top
                    right = widgetRect.right
                    bottom = widgetRect.bottom
                    page = pager.getCurrentPage()
                }

                ensureBackgroundThread {
                    if (widgetItem.id == null) {
                        val itemId = context.homeScreenGridItemsDB.insert(widgetItem)
                        widgetItem.id = itemId
                        post {
                            wireWidget(widgetItem)
                        }
                    } else {
                        context.homeScreenGridItemsDB.reloadCellPoint(
                            widgetItem.left,
                            widgetItem.top,
                            widgetItem.right,
                            widgetItem.bottom,
                            pager.getCurrentPage(),
                            false,
                            null,
                            widgetItem.id!!
                        )
                        if (!widgetItem.outOfBounds()) {
                            post {
                                val widgetView = widgetViews.firstOrNull { it.tag == widgetItem.widgetId } ?: return@post
                                val widgetPos = calcWidgetPos(widgetItem.getTopLeft(rowCount))
                                widgetView.x = widgetPos.x.toFloat()
                                widgetView.y = widgetPos.y.toFloat()
                                widgetView.beVisible()
                            }
                        }

                        gridItems.firstOrNull { it.id == widgetItem.id }?.apply {
                            left = widgetItem.left
                            right = widgetItem.right
                            top = widgetItem.top
                            bottom = widgetItem.bottom
                            page = pager.getCurrentPage()
                        }

                        if (widgetItem.page != oldPage && oldPage != 0) {
                            if (gridItems.none { it.page == oldPage && it.parentId == null }) {
                                eraseLeaf(oldPage)
                            }
                        }
                    }
                }
            } else {
                performHapticFeedback()
                widgetViews.firstOrNull { it.tag == draggedItem?.widgetId }?.apply {
                    post {
                        beVisible()
                    }
                }
            }
        }

        draggedItem = null
        draggedItemCurrentCoords = Pair(-1, -1)
        redrawGrid()
    }

    private fun LobbyLookerMatrixDatum.pseudoWidgetLayout() = when (className) {
        PSEUDO_WIDGET_CLOCK -> R.layout.lx_fake_gadget_digital_clock
        PSEUDO_WIDGET_SEARCH -> R.layout.lx_fake_gadget_query_strip
        PSEUDO_WIDGET_TIME -> R.layout.lx_fake_gadget_time
        PSEUDO_WIDGET_GOOGLE_SEARCH -> R.layout.lx_fake_gadget_gfind_query
        else -> null
    }

    private fun revealClock() {
        val intent = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure { timber.log.Timber.w(it, "HomeScreenGrid: no clock app") }
    }

    private fun revealGoogleLookup(voice: Boolean) {
        val google = "com.google.android.googlequicksearchbox"
        val attempts = listOf(
            Intent(if (voice) android.speech.RecognizerIntent.ACTION_WEB_SEARCH else "android.search.action.GLOBAL_SEARCH").setPackage(google),
            Intent(Intent.ACTION_WEB_SEARCH),
        )
        val opened = attempts.any { intent ->
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
        if (!opened) (context as? XHubStage)?.openAppsPanel()
    }

    private fun wireWidget(item: LobbyLookerMatrixDatum) {
        if (item.outOfBounds()) {
            return
        }

        if (item.pseudoWidgetLayout() != null) {
            item.widgetId = appWidgetHost.allocateAppWidgetId()
            ensureBackgroundThread {
                context.homeScreenGridItemsDB.reloadWidgetKey(item.widgetId, item.id!!)
            }

            placeAppletWidget(null, item)
            return
        }

        val activity = context as XHubStage
        val appWidgetProviderInfo = item.providerInfo
            ?: appWidgetManager!!.installedProviders
                .firstOrNull { it.provider.className == item.className }
        if (appWidgetProviderInfo != null) {
            item.widgetId = appWidgetHost.allocateAppWidgetId()
            ensureBackgroundThread {
                context.homeScreenGridItemsDB.reloadWidgetKey(item.widgetId, item.id!!)
            }

            item.id?.let { bindPending.add(it) }
            activity.processWidgetBinding(
                appWidgetManager = appWidgetManager,
                appWidgetId = item.widgetId,
                appWidgetInfo = appWidgetProviderInfo
            ) { canBind ->
                val settle = { item.id?.let { bindPending.remove(it) } }
                if (canBind) {
                    if (appWidgetProviderInfo.configure != null &&
                        !com.fffemotes.skintools.launcher.persona.LobbyWidgetPositioner.configurationOptional(appWidgetProviderInfo)
                    ) {
                        item.id?.let { bindPending.add(it) }
                        activity.processWidgetTuneViewer(
                            appWidgetHost = appWidgetHost,
                            appWidgetId = item.widgetId
                        ) { success ->
                            settle()
                            if (success) {
                                placeAppletWidget(appWidgetProviderInfo, item)
                            } else {
                                dropCellFromAtriumViewer(item)
                            }
                        }
                    } else {
                        settle()
                        placeAppletWidget(appWidgetProviderInfo, item)
                    }
                } else {
                    settle()
                    dropCellFromAtriumViewer(item)
                }

                if (pager.isOutsideOfLeafRange()) {
                    prevPage(redraw = true)
                }
            }
        }
    }

    private fun placeAppletWidget(
        appWidgetProviderInfo: AppWidgetProviderInfo?,
        item: LobbyLookerMatrixDatum,
    ) {
        val pseudoWidgetLayout = item.pseudoWidgetLayout()

        val widgetView = if (pseudoWidgetLayout != null) {
            CustomGadgetWidgetHolderPane((context as XHubStage).baseContext).apply {
                View.inflate(context, pseudoWidgetLayout, this)
                when (item.className) {
                    PSEUDO_WIDGET_SEARCH -> setOnClickListener {
                        Launcher.events.onClick("search_widget")
                        (this@LobbyLookerMatrix.context as XHubStage).openAppsPanel()
                    }
                    PSEUDO_WIDGET_TIME -> setOnClickListener {
                        Launcher.events.onClick("time_widget")
                        revealClock()
                    }
                    PSEUDO_WIDGET_GOOGLE_SEARCH -> {
                        setOnClickListener {
                            Launcher.events.onClick("google_search_widget")
                            revealGoogleLookup(voice = false)
                        }
                        findViewById<View>(R.id.google_bar_voiceIx)?.setOnClickListener {
                            Launcher.events.onClick("google_search_widget", "voice")
                            revealGoogleLookup(voice = true)
                        }
                    }
                }
            }
        } else {
            (appWidgetHost.createView(
                (context as XHubStage).baseContext,
                item.widgetId,
                appWidgetProviderInfo
            ) as CustomGadgetWidgetHolderPane).apply {
                // The tap itself goes to the widget's own app; this only reports it.
                tapListener = { Launcher.events.onClick("widget") }
            }
        }

        widgetView.tag = item.widgetId
        widgetView.longPressListener = { x, y ->
            val activity = context as? XHubStage
            if (activity?.isAllProgramsSectionExpanded() == false) {
                activity.surfaceAtriumGlyphTweaks(x, widgetView.y, item, false)
                performHapticFeedback()
            }
        }

        widgetView.onIgnoreInterceptedListener = {
            concealResizeLines()
        }

        widgetViewsOfRow(widgetViews, { it.tag }, { widgetViewItemIds[it] }, item.widgetId, item.id)
            .forEach { stale ->
                removeView(stale)
                widgetViews.remove(stale)
            }
        val widgetSizeUi = reloadWidgetPointAndDims(widgetView, item)
        addView(widgetView, widgetSizeUi.width, widgetSizeUi.height)
        widgetViews.add(widgetView)
        item.id?.let { widgetViewItemIds[widgetView] = it }

        item.drawable = null

        gridItems.removeIf { it.id == item.id }
        gridItems.add(item)
        (context as XHubStage).wipeWidgetsLookup()
    }

    private fun reloadWidgetPointAndDims(
        widgetView: AppWidgetHostView,
        item: LobbyLookerMatrixDatum,
    ): Size {
        val currentViewPosition = pager.pullLivePanePointInFullLeafSpace() * width.toFloat()
        val widgetPos = calcWidgetPos(item.getTopLeft(rowCount))
        widgetView.x = widgetPos.x + width * item.page - currentViewPosition
        widgetView.y = widgetPos.y.toFloat()
        val widgetWidth = item.pullBreadthInCells() * cellWidth
        val widgetHeight = item.pullTallnessInCells() * cellHeight

        val density = context.resources.displayMetrics.density
        val widgetDpWidth = (widgetWidth / density).toInt()
        val widgetDpHeight = (widgetHeight / density).toInt()

        if (widgetView.appWidgetInfo != null) reportWidgetDims(widgetView, widgetDpWidth, widgetDpHeight)

        widgetView.layoutParams?.width = widgetWidth
        widgetView.layoutParams?.height = widgetHeight
        return Size(widgetWidth, widgetHeight)
    }

    private val reportedWidgetSizes = java.util.WeakHashMap<AppWidgetHostView, Pair<Int, Int>>()

    private fun reportWidgetDims(widgetView: AppWidgetHostView, dpWidth: Int, dpHeight: Int) {
        val size = dpWidth to dpHeight
        if (reportedWidgetSizes[widgetView] == size) return
        reportedWidgetSizes[widgetView] = size
        try {
            if (isSPlus()) {
                widgetView.updateAppWidgetSize(Bundle(), listOf(SizeF(dpWidth.toFloat(), dpHeight.toFloat())))
            } else {
                widgetView.updateAppWidgetSize(Bundle(), dpWidth, dpHeight, dpWidth, dpHeight)
            }
        } catch (e: RuntimeException) {
            timber.log.Timber.w(e, "HomeScreenGrid: widget %d rejected its size - binding lost?", widgetView.appWidgetId)
        }
    }

    private fun calcWidgetPos(topLeft: Point): Point {
        if (cells.isEmpty()) {
            fillSlotSizes()
        }
        val cell = cells[topLeft]!!
        return Point(
            cell.left + sideMargins.left,
            cell.top + sideMargins.top
        )
    }

    private fun pullClosestGridCells(center: Point): Point? {
        return cells.entries.firstOrNull { (_, cell) -> center.x == cell.centerX() && center.y == cell.centerY() }?.key
    }

    private fun redrawGrid() {
        post {
            setWillNotDraw(false)
            invalidate()
            binding.drawingAreaIx.invalidate()
        }
    }

    private fun pullFakeBreadth() = width - sideMargins.left - sideMargins.right

    private fun pullFakeTallness() = height - sideMargins.top - sideMargins.bottom

    private val brokenItems = HashSet<Long>()

    private inline fun guardCell(canvas: Canvas, item: LobbyLookerMatrixDatum, draw: () -> Unit) {
        val saveCount = canvas.saveCount
        try {
            draw()
        } catch (e: RuntimeException) {
            runCatching { canvas.restoreToCount(saveCount) }
            if (brokenItems.add(item.id ?: -1L)) {
                timber.log.Timber.w(e, "HomeScreenGrid: skipping item %s (%s) that failed to draw", item.id, item.packageName)
            }
        }
    }

    fun drawInto(canvas: Canvas) {
        if (cells.isEmpty()) {
            fillSlotSizes()
        }

        val currentXFactor = pager.pullXFactorForLiveLeaf()
        val lastXFactor = pager.pullXFactorForLastLeaf()

        fun processCoreGridCellDrawing(item: LobbyLookerMatrixDatum, xFactor: Float) = guardCell(canvas, item) {
            val offsetX = sideMargins.left + (this@LobbyLookerMatrix.width * xFactor).toInt()
            val offsetY = sideMargins.top
            cells[item.getTopLeft(rowCount)]!!.withOffset(offsetX, offsetY) {
                canvas.inLeafTransit(xFactor) { canvas.paintCellInSlot(item, this) }
            }
        }

        gridItems
            .filter {
                it.isSingleSlotType() && pager.isCellOnActiveLeaf(it)
                        && !it.docked && it.parentId == null
            }
            .forEach { item ->
                if (item.outOfBounds()) {
                    return@forEach
                }

                processCoreGridCellDrawing(item, currentXFactor)
            }

        gridItems
            .filter { it.isSingleSlotType() && it.docked && it.parentId == null }
            .forEach { item ->
                if (item.outOfBounds()) {
                    return@forEach
                }

                processCoreGridCellDrawing(item, 0f)
            }

        if (pager.isAnimatingLeafChange()) {
            gridItems
                .filter {
                    it.isSingleSlotType() && pager.isCellOnLastLeaf(it)
                            && !it.docked && it.parentId == null
                }
                .forEach { item ->
                    if (item.outOfBounds()) {
                        return@forEach
                    }

                    processCoreGridCellDrawing(item, lastXFactor)
                }
        }

        if (pager.isSwiped()) {
            gridItems
                .filter {
                    it.isSingleSlotType()
                            && pager.isCellInSlideRange(it)
                            && !it.docked
                            && it.parentId == null
                }.forEach { item ->
                    if (item.outOfBounds()) {
                        return@forEach
                    }

                    processCoreGridCellDrawing(item, lastXFactor)
                }
        }

        gridItems
            .filter { it.type == ITEM_TYPE_WIDGET && !it.outOfBounds() }
            .forEach { item -> guardCell(canvas, item) {
                val placed = placedWidgetView(widgetViews, { it.tag }, { widgetViewItemIds[it] }, item.widgetId, item.id)
                if (placed != null) {
                    // A refetch that read the database before a fresh bind wrote its id hands back
                    // the row still at -1; matched by the tag alone its view was never moved again
                    // and showed on every page. The view holds the real id.
                    val tag = placed.tag as? Int
                    if (tag != null && tag != item.widgetId) {
                        timber.log.Timber.w("HomeScreenGrid: row %s had widget id %d, its view %d - kept the view's", item.id, item.widgetId, tag)
                        item.widgetId = tag
                    }
                    reloadWidgetPointAndDims(placed, item)
                    return@forEach
                }

                if (item.widgetId < 0) {
                    if (bindAttempted.add(item.id ?: return@forEach)) wireWidget(item)
                    return@forEach
                }
                if (item.id in bindPending) return@forEach
                val providerInfo = item.providerInfo
                    ?: appWidgetManager!!.installedProviders
                        .firstOrNull { it.provider.className == item.className }

                if (item.pseudoWidgetLayout() != null) {
                    placeAppletWidget(null, item)
                } else if (providerInfo != null) {
                    placeAppletWidget(providerInfo, item)
                } else {
                    dropWidget(item)
                }
            } }

        if (pager.shouldViewerLeafShiftIndicator()) {
            val pageCount = pager.getPageCount()
            val step = pageIndicatorRadius * 2 + pageIndicatorMargin
            val rowWidth = (pageCount - 1) * step + 2 * pageIndicatorRadius
            val firstCenterX = (pullFakeBreadth() - rowWidth) / 2 + sideMargins.left + pageIndicatorRadius
            val centerY = pageIndicatorsYPos.toFloat() + sideMargins.top

            val emphasis = pager.pullIndicatorsEmphasis()
            val strength = PAGE_INDICATOR_RESTING_ALPHA + (255 - PAGE_INDICATOR_RESTING_ALPHA) * emphasis
            val fadeIn = pager.pullIndicatorsFadeIn()
            val alpha = (strength * fadeIn).toInt().coerceIn(0, 255)

            val appear = PAGE_INDICATOR_APPEAR_SCALE + (1f - PAGE_INDICATOR_APPEAR_SCALE) * fadeIn

            val position = pager.pullLivePanePointInFullLeafSpace().coerceIn(0f, (pageCount - 1).toFloat())

            emptyPageIndicatorPaint.alpha = alpha * PAGE_INDICATOR_INACTIVE_ALPHA / 255
            for (page in 0 until pageCount) {
                val cx = firstCenterX + page * step

                val nearness = (1f - abs(position - page)).coerceAtLeast(0f)
                val radius = pageIndicatorInactiveRadius * (1f + PAGE_INDICATOR_DOT_SWELL * nearness) * appear
                canvas.drawCircle(cx, centerY, radius, emptyPageIndicatorPaint)
            }

            val fraction = position - floor(position)
            val stretch = 1f - abs(2f * fraction - 1f)
            val maxHalfLength = step / 2f - 1f
            val halfLength = (pageIndicatorRadius + pageIndicatorActiveExtra + PAGE_INDICATOR_STRETCH * step * stretch / 2f)
                .coerceAtMost(maxHalfLength) * appear
            val capsuleRadius = pageIndicatorRadius * appear
            val cx = firstCenterX + position * step
            currentPageIndicatorPaint.alpha = alpha
            canvas.drawRoundRect(
                cx - halfLength, centerY - capsuleRadius, cx + halfLength, centerY + capsuleRadius,
                capsuleRadius, capsuleRadius, currentPageIndicatorPaint,
            )

            if (pager.pullIndicatorsFadeIn() < 1f) {
                postOnAnimation { redrawGrid() }
            }
        }

        val folder = currentlyOpenFolder
        if (folder != null && folder.getItems().isNotEmpty()) {
            val items = folder.getItems()
            val folderRect = folder.getDrawingRect()

            val currentViewPosition =
                pager.pullLivePanePointInFullLeafSpace() * width.toFloat()
            val rectOffset = width * folder.item.page - currentViewPosition
            folderRect.offset(rectOffset, 0f)

            canvas.withScale(
                x = folder.scale,
                y = folder.scale,
                pivotX = folderRect.centerX(),
                pivotY = folderRect.centerY()
            ) {
                canvas.drawRoundRect(
                    folderRect,
                    roundedCornerRadius / folder.scale,
                    roundedCornerRadius / folder.scale,
                    folderBackgroundPaint
                )
                val textX = folderRect.left + folderPadding
                val textY = folderRect.top + folderPadding
                val staticLayout = StaticLayout.Builder
                    .obtain(
                        folder.item.title,
                        0,
                        folder.item.title.length,
                        folderTitleTextPaint,
                        (folderRect.width() - 2 * folderPadding * folder.scale).toInt()
                    )
                    .setMaxLines(1)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .build()

                withTranslation(textX, textY) {
                    staticLayout.draw(canvas)
                }

                items.forEach { item ->
                    val itemRect = folder.pullCellRect(item)
                    canvas.paintCellInSlot(item, itemRect)
                }
            }
        }

        if (draggedItem != null && draggedItemCurrentCoords.first != -1 && draggedItemCurrentCoords.second != -1) {
            if (draggedItem!!.isSingleSlotType()) {
                if (folder != null && folder.pullCellsDrawingRect().contains(
                        (draggedItemCurrentCoords.first).toFloat(),
                        (draggedItemCurrentCoords.second).toFloat()
                    )
                ) {
                    val center = folder.pullCellsGridCenters().minBy {
                        abs(it.second - draggedItemCurrentCoords.first + sideMargins.left) + abs(it.third - draggedItemCurrentCoords.second + sideMargins.top)
                    }
                    val cellSize = folder.pullSlotDims()

                    val shadowX = center.second - cellSize / 2 + iconMargin + iconSize / 2f
                    val shadowY = center.third - cellSize / 2 + iconMargin + iconSize / 2

                    canvas.drawCircle(
                        shadowX,
                        shadowY.toFloat(),
                        iconSize / 2f,
                        dragShadowCirclePaint
                    )
                } else {
                    val center = gridCenters.minBy {
                        abs(it.x - draggedItemCurrentCoords.first + sideMargins.left) + abs(it.y - draggedItemCurrentCoords.second + sideMargins.top)
                    }

                    val gridCells = pullClosestGridCells(center)
                    if (gridCells != null) {
                        cells[gridCells]?.let { cell ->
                            val shadowX = cell.left + iconMargin + iconSize / 2f + sideMargins.left
                            val shadowY = if (gridCells.y == rowCount - 1) {
                                cellHeight - iconMargin - iconSize / 2f
                            } else {
                                iconMargin + iconSize / 2f
                            } + sideMargins.top + cell.top

                            canvas.drawCircle(
                                shadowX, shadowY, iconSize / 2f, dragShadowCirclePaint
                            )
                        }
                    }
                }

                val drawableX = (draggedItemCurrentCoords.first - iconSize / 1.5f).toInt()
                val drawableY = (draggedItemCurrentCoords.second - iconSize / 1.2f).toInt()
                val newDrawable = if (draggedItem?.type == ITEM_TYPE_FOLDER) {
                    draggedItem!!.toBundle().generateDrawable()
                } else {
                    draggedItem!!.drawable?.constantState?.newDrawable()?.mutate()
                }
                newDrawable?.setBounds(
                    drawableX,
                    drawableY,
                    drawableX + iconSize,
                    drawableY + iconSize
                )
                newDrawable?.draw(canvas)
            } else if (draggedItem!!.type == ITEM_TYPE_WIDGET) {
                if (!isFirstDraw) {
                    val center = gridCenters.minBy {
                        abs(it.x - draggedItemCurrentCoords.first + sideMargins.left) + abs(it.y - draggedItemCurrentCoords.second + sideMargins.top)
                    }

                    val gridCells = pullClosestGridCells(center)
                    if (gridCells != null) {
                        val widgetRect = pullWidgetOccupiedRect(gridCells)
                        val widgetPos = calcWidgetPos(Point(widgetRect.left, widgetRect.top))
                        val leftSide = widgetPos.x.toFloat()
                        val topSide = widgetPos.y.toFloat()
                        val rightSide = leftSide + draggedItem!!.pullBreadthInCells() * cellWidth
                        val bottomSide = topSide + draggedItem!!.pullTallnessInCells() * cellHeight
                        canvas.drawRoundRect(
                            leftSide,
                            topSide,
                            rightSide,
                            bottomSide,
                            roundedCornerRadius,
                            roundedCornerRadius,
                            dragShadowCirclePaint
                        )
                    }

                    draggedItem!!.drawable?.also { drawable ->
                        val aspectRatio = drawable.minimumHeight / drawable.minimumWidth.toFloat()
                        val drawableX =
                            (draggedItemCurrentCoords.first - drawable.minimumWidth / 2f).toInt()
                        val drawableY =
                            (draggedItemCurrentCoords.second - drawable.minimumHeight / 3f).toInt()
                        val drawableWidth =
                            draggedItem!!.pullBreadthInCells() * cellWidth - iconMargin * (draggedItem!!.pullBreadthInCells() - 1)
                        drawable.setBounds(
                            drawableX,
                            drawableY,
                            drawableX + drawableWidth,
                            (drawableY + drawableWidth * aspectRatio).toInt()
                        )
                        drawable.draw(canvas)
                    }
                }
            }
        }

        isFirstDraw = false
    }

    private fun fillSlotSizes() {
        labelLayouts.clear()
        cellWidth = pullFakeBreadth() / columnCount
        cellHeight = pullFakeTallness() / rowCount
        val extraXMargin = if (cellWidth > cellHeight) {
            (cellWidth - cellHeight) / 2
        } else {
            0
        }
        val extraYMargin = if (cellHeight > cellWidth) {
            (cellHeight - cellWidth) / 2
        } else {
            0
        }

        val baseMargin = (context.resources.getDimension(R.dimen.launcher_icon_side_margin) * 5 / columnCount).toInt()
        val cell = min(cellWidth, cellHeight)

        val profileIconDp = context.launcherConfig.iconSizeDp
        val scaledIcon = if (profileIconDp > 0) {
            (profileIconDp * context.resources.displayMetrics.density).toInt().coerceIn(1, cell)
        } else {
            ((cell - 2 * baseMargin) * context.launcherConfig.iconScale).toInt().coerceIn(1, cell)
        }
        iconMargin = ((cell - scaledIcon) / 2).coerceAtLeast(0)
        iconSize = cell - 2 * iconMargin

        dockSlotCount = context.launcherConfig.dockColumnCount.coerceIn(1, MAX_DOCK_SLOTS)
        val dockColumnCount = dockSlotCount
        val dockSlotWidth = pullFakeBreadth() / dockColumnCount

        dockCellWidth = min(cellWidth, dockSlotWidth)

        val dockDp = context.launcherConfig.dockIconSizeDp
        dockIconSize = if (dockDp > 0) {
            (dockDp * context.resources.displayMetrics.density).toInt().coerceIn(1, min(dockCellWidth, cellHeight))
        } else {
            min(iconSize, dockCellWidth - 2 * iconMargin).coerceAtLeast(1)
        }
        dockIconMargin = ((dockCellWidth - dockIconSize) / 2).coerceAtLeast(0)

        val dockRowTop = (rowCount - 1) * cellHeight
        val dockIconTop = dockRowTop + cellHeight - dockBottomMargin - dockIconSize
        val labelBottom = if (context.launcherConfig.showHomeAppLabels) {
            val lineHeight = textPaint.fontMetrics.bottom - textPaint.fontMetrics.top
            (rowCount - 2) * cellHeight + iconMargin + iconSize + labelSideMargin + (LABEL_MAX_LINES * lineHeight).toInt()
        } else {
            dockRowTop
        }
        val bandTop = max(dockRowTop, labelBottom)
        val clearance = pageIndicatorClearance.toInt()
        pageIndicatorsYPos = ((bandTop + dockIconTop) / 2)
            .coerceAtLeast(bandTop + clearance)
            .coerceAtMost(maxOf(dockIconTop - clearance, bandTop + clearance))
        val dockCellOffset = (dockSlotWidth - dockCellWidth) / 2
        val dockExtraXMargin = if (dockCellWidth < cellWidth) 0 else extraXMargin
        for (i in 0 until max(columnCount, dockColumnCount)) {
            for (j in 0 until rowCount) {
                val isDock = j == rowCount - 1
                if (isDock && i >= dockColumnCount) {
                    continue
                }
                if (!isDock && i >= columnCount) {
                    continue
                }
                val yMarginToAdd = if (isDock) 0 else extraYMargin
                val cellLeft = if (isDock) i * dockSlotWidth + dockCellOffset else i * cellWidth
                val rect = Rect(
                    cellLeft + (if (isDock) dockExtraXMargin else extraXMargin),
                    j * cellHeight + yMarginToAdd,
                    cellLeft + (if (isDock) dockCellWidth - dockExtraXMargin else cellWidth - extraXMargin),
                    (j + 1) * cellHeight - yMarginToAdd,
                )
                cells[Point(i, j)] = rect
                gridCenters.add(Point(rect.centerX(), rect.centerY()))
                if (isDock) {
                    dockCellY = j * cellHeight
                }
            }
        }
    }

    fun fragmentExpanded() {
        widgetViews.forEach {
            it.ignoreTouches = true
        }
        dismissFolder(true)
    }

    fun fragmentCollapsed() {
        animate().cancel()
        alpha = 1f
        widgetViews.forEach {
            it.ignoreTouches = false
        }
    }

    fun getClickableRect(item: LobbyLookerMatrixDatum): Rect {
        if (cells.isEmpty()) {
            fillSlotSizes()
        }

        val folder = currentlyOpenFolder
        val clickableLeft: Int
        val clickableTop: Int
        if (folder != null && item.parentId == folder.item.id) {
            val itemRect = folder.pullCellRect(item)
            clickableLeft = itemRect.left
            clickableTop = itemRect.top - iconMargin
        } else {
            val cell = cells[item.getTopLeft(rowCount)] ?: return Rect(0, 0, 0, 0)
            clickableLeft = cell.left + sideMargins.left
            clickableTop = if (item.docked) {
                dockCellY + cellHeight - dockIconSize - dockBottomMargin
            } else {
                cell.top - iconMargin
            } + sideMargins.top
        }
        val additionalHeight = if (!item.docked && context.launcherConfig.showHomeAppLabels) {
            (LABEL_MAX_LINES * (textPaint.fontMetrics.bottom - textPaint.fontMetrics.top)).toInt()
        } else 0
        val size = if (item.docked) dockIconSize else iconSize
        val margin = if (item.docked) dockIconMargin else iconMargin
        return Rect(
            clickableLeft,
            clickableTop,
            clickableLeft + size + 2 * margin,
            clickableTop + size + 2 * margin + additionalHeight
        )
    }

    private fun pullWidgetOccupiedRect(item: Point): Rect {
        val left = item.x - floor((draggedItem!!.pullBreadthInCells() - 1) / 2.0).toInt()
        val rect = Rect(
            left,
            item.y,
            left + draggedItem!!.pullBreadthInCells() - 1,
            item.y + draggedItem!!.pullTallnessInCells() - 1
        )
        if (rect.left < 0) {
            rect.right -= rect.left
            rect.left = 0
        } else if (rect.right > columnCount - 1) {
            val diff = rect.right - columnCount + 1
            rect.right -= diff
            rect.left -= diff
        }

        if (rect.top < 0) {
            rect.bottom -= rect.top
            rect.top = 0
        } else if (rect.bottom > rowCount - 2) {
            val diff = rect.bottom - rowCount + 2
            rect.bottom -= diff
            rect.top -= diff
        }

        return rect
    }

    fun isClickingGridItem(x: Int, y: Int): LobbyLookerMatrixDatum? {
        if (pager.isAnimatingLeafChange() || pager.isSwiped()) {
            return null
        }

        currentlyOpenFolder?.also { folder ->
            folder.getItems().forEach { gridItem ->
                val rect = getClickableRect(gridItem)
                if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) {
                    return gridItem
                }
            }
        }

        if (currentlyOpenFolder != null) {
            return null
        }

        for (gridItem in gridItems.siftShownOnLiveLeafOnly()) {
            if (gridItem.outOfBounds()) {
                continue
            }

            if (gridItem.isSingleSlotType()) {
                val rect = getClickableRect(gridItem)
                if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) {
                    return gridItem
                }
            } else if (gridItem.type == ITEM_TYPE_WIDGET) {
                val widgetPos = calcWidgetPos(gridItem.getTopLeft(rowCount))
                val left = widgetPos.x.toFloat()
                val top = widgetPos.y.toFloat()
                val right = left + gridItem.pullBreadthInCells() * cellWidth
                val bottom = top + gridItem.pullTallnessInCells() * cellHeight

                if (x >= left && x <= right && y >= top && y <= bottom) {
                    return gridItem
                }
            }
        }

        return null
    }

    fun intoViewSpaceCoords(screenSpaceX: Float, screenSpaceY: Float): Pair<Float, Float> {
        val viewLocation = IntArray(2)
        getLocationOnScreen(viewLocation)
        val x = screenSpaceX - viewLocation[0]
        val y = screenSpaceY - viewLocation[1]
        return Pair(x, y)
    }

    private fun LobbyLookerMatrixDatum.outOfBounds(): Boolean {
        return ((!docked && (left >= columnCount || right >= columnCount))

                || (docked && right >= dockSlotCount)
                || (!docked && (top >= rowCount - 1 || bottom >= rowCount - 1))
                || (type == ITEM_TYPE_WIDGET && (bottom - top > rowCount - 1 || right - left > columnCount - 1))
                )
    }

    private inner class HomeScreenGridTouchHelper(host: View) : ExploreByTouchHelper(host) {
        override fun getVirtualViewAt(x: Float, y: Float): Int {
            val item = isClickingGridItem(x.toInt(), y.toInt())

            return if (item != null) {
                item.id?.toInt() ?: INVALID_ID
            } else {
                INVALID_ID
            }
        }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>?) {
            val sorted =
                gridItems
                    .filter {
                        it.shownOnLiveLeaf()
                                || (
                                currentlyOpenFolder != null
                                        && it.parentId == currentlyOpenFolder?.item?.id
                                )
                    }
                    .sortedBy {
                        (if (it.parentId == null) {
                            it.pullDockAdjustedTop(rowCount)
                        } else {
                            1
                        }) * 100 + it.left
                    }
            sorted.forEachIndexed { index, homeScreenGridItem ->
                virtualViewIds?.add(index, homeScreenGridItem.id?.toInt() ?: index)
            }
        }

        override fun onPopulateNodeForVirtualView(
            virtualViewId: Int,
            node: AccessibilityNodeInfoCompat,
        ) {
            val viewLocation = IntArray(2)
            getLocationOnScreen(viewLocation)

            if (virtualViewId == -1) {
                node.text = context.applicationInfo.loadLabel(context.packageManager)
                val viewBounds = Rect(left, top, right, bottom)
                val onScreenBounds = Rect(viewBounds)
                onScreenBounds.offset(viewLocation[0], viewLocation[1])
                node.setBoundsInScreen(onScreenBounds)
                node.setBoundsInParent(viewBounds)

                node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
                node.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK)
                node.setParent(this@LobbyLookerMatrix)
            }

            val item = gridItems.firstOrNull { it.id?.toInt() == virtualViewId }
                ?: throw IllegalArgumentException("Unknown id")

            node.text = if (item.type == ITEM_TYPE_WIDGET) {
                item.providerInfo?.loadLabel(context.packageManager) ?: item.title
            } else {
                item.title
            }

            val viewBounds = if (item == currentlyOpenFolder?.item) {
                currentlyOpenFolder?.getDrawingRect()?.toRect()
            } else if (item.type == ITEM_TYPE_WIDGET) {
                val widgetPos = calcWidgetPos(item.getTopLeft(rowCount))
                val left = widgetPos.x
                val top = widgetPos.y
                val right = left + item.pullBreadthInCells() * cellWidth
                val bottom = top + item.pullTallnessInCells() * cellHeight

                Rect(left, top, right, bottom)
            } else {
                getClickableRect(item)
            }
            val onScreenBounds = Rect(viewBounds)
            onScreenBounds.offset(viewLocation[0], viewLocation[1])
            node.setBoundsInScreen(onScreenBounds)
            node.setBoundsInParent(viewBounds)

            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            node.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK)
            node.setParent(this@LobbyLookerMatrix)
        }

        override fun onPerformActionForVirtualView(
            virtualViewId: Int,
            action: Int,
            arguments: Bundle?,
        ): Boolean {
            val item = gridItems.firstOrNull { it.id?.toInt() == virtualViewId }
                ?: throw IllegalArgumentException("Unknown id")
            when (action) {
                AccessibilityNodeInfoCompat.ACTION_CLICK -> itemClickListener?.apply {
                    if (item == currentlyOpenFolder?.item) {
                        dismissFolder(true)
                    } else {
                        invoke(item)
                    }
                    return true
                }

                AccessibilityNodeInfoCompat.ACTION_LONG_CLICK -> itemLongClickListener?.apply {
                    invoke(item)
                    return true
                }
            }

            return false
        }
    }

    private fun eraseLeaf(page: Int) {
        gridItems.filter { it.page > page }.forEach {
            it.page -= 1
        }
        context.homeScreenGridItemsDB.shiftLeaf(page, -1)

        if (pager.isOutsideOfLeafRange()) {
            post {
                prevPage()
            }
        }
    }

    private fun pullMaxLeaf() =
        gridItems.filter { !it.docked && !it.outOfBounds() }.maxOfOrNull { it.page } ?: 0

    fun nextPage(redraw: Boolean = false): Boolean {
        return pager.nextPage(redraw)
    }

    fun prevPage(redraw: Boolean = false): Boolean {
        return pager.prevPage(redraw)
    }

    fun skipToPage(targetPage: Int): Boolean {
        return pager.skipToPage(targetPage)
    }

    fun getCurrentIconSize(): Int = iconSize

    fun setSwipeMovement(diffX: Float) {
        if (draggedItem == null) {
            pager.setSwipeMovement(diffX)
        }
    }

    override fun onDetachedFromWindow() {
        // Nothing left to fade or page on a grid that is gone.
        pagerHandler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    fun finalizeSwipe() {
        pager.finalizeSwipe()
    }

    fun flingSwipe(towardsNext: Boolean): Boolean = draggedItem == null && pager.flingSwipe(towardsNext)

    /** The first page - the home page, the only one the left-edge (messages) panel opens from. */
    fun isOnFirstPage() = pager.getCurrentPage() == 0 && !pager.isPaging()

    /** The last page, the only one the right-edge (apps) panel opens from. */
    fun isOnLastPage() = pager.getCurrentPage() >= pullMaxLeaf() && !pager.isPaging()

    fun wakeFolder(folder: LobbyLookerMatrixDatum) {
        if (currentlyOpenFolder == null) {
            currentlyOpenFolder = folder.toBundle(animateOpening = true)
            redrawGrid()
        } else if (currentlyOpenFolder?.item?.id != folder.id) {
            dismissFolder()
        }
        accessibilityHelper.invalidateRoot()
    }

    fun dismissFolder(redraw: Boolean = false) {
        currentlyOpenFolder?.tweenClosing {
            currentlyOpenFolder = null
            if (redraw) {
                redrawGrid()
            }
            accessibilityHelper.invalidateRoot()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return if (currentlyOpenFolder != null) {
            true
        } else {
            super.onInterceptTouchEvent(ev)
        }
    }

    private fun Canvas.paintCellInSlot(item: LobbyLookerMatrixDatum, cell: Rect) {
        if (item.id != draggedItem?.id) {
            val drawableX = cell.left + (if (item.docked) dockIconMargin else iconMargin)

            val drawable = if (item.type == ITEM_TYPE_FOLDER) {
                item.toBundle().generateDrawable()
            } else {
                null
            }
            val bitmap = if (drawable == null) item.iconBitmap(if (item.docked) dockIconSize else iconSize) else null

            if (item.docked) {
                val drawableY = dockCellY + cellHeight - dockBottomMargin - dockIconSize + sideMargins.top

                drawable?.setBounds(
                    drawableX,
                    drawableY,
                    drawableX + dockIconSize,
                    drawableY + dockIconSize
                )
                bitmap?.let { drawBitmap(it, drawableX.toFloat(), drawableY.toFloat(), transitPaint()) }
            } else {
                val drawableY = cell.top + iconMargin
                drawable?.setBounds(
                    drawableX,
                    drawableY,
                    drawableX + iconSize,
                    drawableY + iconSize
                )
                bitmap?.let { drawBitmap(it, drawableX.toFloat(), drawableY.toFloat(), transitPaint()) }

                if (item.id != draggedItem?.id && item.title.isNotEmpty() && context.launcherConfig.showHomeAppLabels) {
                    val textX = cell.left.toFloat() + labelSideMargin
                    val textY = cell.top.toFloat() + iconSize + iconMargin + labelSideMargin
                    val textPaintToUse = if (item.parentId == null) {
                        textPaint
                    } else {
                        contrastTextPaint
                    }
                    val staticLayout = captionLayout(item.title, textPaintToUse)

                    val fullAlpha = textPaintToUse.alpha
                    textPaintToUse.alpha = pageTransitAlpha
                    withTranslation(textX, textY) {
                        staticLayout.draw(this)
                    }
                    textPaintToUse.alpha = fullAlpha
                }
            }

            drawable?.alpha = pageTransitAlpha
            drawable?.draw(this)
        }
    }

    private fun transitPaint(): Paint? = if (pageTransitAlpha >= 255) {
        null
    } else {
        pageTransitPaint.apply { alpha = pageTransitAlpha }
    }

    private fun LobbyLookerMatrixDatum.iconBitmap(size: Int): Bitmap? {
        val source = drawable ?: return null
        if (size <= 0) return null
        iconRaster?.takeIf { iconRasterSource === source && it.width == size }?.let { return it }
        val bitmap = createBitmap(size, size)
        val painter = source.constantState?.newDrawable()?.mutate() ?: source
        painter.setBounds(0, 0, size, size)
        painter.draw(Canvas(bitmap))
        iconRaster = bitmap
        iconRasterSource = source
        return bitmap
    }

    private val labelLayouts = HashMap<String, StaticLayout>()

    private fun captionLayout(title: String, paint: TextPaint): StaticLayout {
        val key = if (paint === contrastTextPaint) "c:" + title else "n:" + title
        return labelLayouts.getOrPut(key) {
            StaticLayout.Builder
                .obtain(title, 0, title.length, paint, cellWidth - 2 * labelSideMargin)
                .setMaxLines(LABEL_MAX_LINES)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()
        }
    }

    private fun Canvas.inLeafTransit(xFactor: Float, block: () -> Unit) {
        val progress = min(abs(xFactor), 1f)
        if (progress == 0f) {
            block()
            return
        }
        pageTransitAlpha = (255 * (1f - PAGE_TRANSIT_FADE * progress)).toInt().coerceIn(0, 255)
        val scale = 1f - PAGE_TRANSIT_SCALE * progress
        val pivotY = sideMargins.top + (rowCount - 1) * cellHeight / 2f
        withScale(scale, scale, width / 2f, pivotY) {
            block()
        }
        pageTransitAlpha = 255
    }

    private fun Rect.withOffset(offsetX: Int, offsetY: Int, block: Rect.() -> Unit) {
        offset(offsetX, offsetY)
        try {
            block()
        } finally {
            offset(-offsetX, -offsetY)
        }
    }

    private fun List<LobbyLookerMatrixDatum>.siftShownOnLiveLeafOnly() =
        filter { it.shownOnLiveLeaf() }

    private fun LobbyLookerMatrixDatum.shownOnLiveLeaf() =
        (pager.isCellOnActiveLeaf(this) || docked) && parentId == null

    private fun LobbyLookerMatrixDatum.isSingleSlotType() =
        (type == ITEM_TYPE_ICON || type == ITEM_TYPE_SHORTCUT || type == ITEM_TYPE_FOLDER)

    private fun LobbyLookerMatrixDatum.toBundle(animateOpening: Boolean = false) =
        HomeScreenFolder(this, animateOpening)

    private inner class HomeScreenFolder(
        val item: LobbyLookerMatrixDatum,
        animateOpening: Boolean,
    ) {
        var scale: Float = 1f
        private var closing = false

        init {
            if (animateOpening) {
                scale = 0f
                post {
                    ValueAnimator.ofFloat(0f, 1f)
                        .apply {
                            interpolator = DecelerateInterpolator()
                            addUpdateListener {
                                scale = it.animatedValue as Float
                                redrawGrid()
                            }
                            addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    super.onAnimationEnd(animation)
                                    scale = 1f
                                    redrawGrid()
                                }
                            })
                            duration = FOLDER_ANIMATION_DURATION
                            start()
                        }
                }
            }
        }

        fun getItems(): List<LobbyLookerMatrixDatum> {
            return gridItems
                .toList()
                .filter { it.isSingleSlotType() && it.parentId == item.id }
        }

        fun generateDrawable(): Drawable? {
            if (iconSize == 0) {
                return null
            }

            val items = getItems()
            val itemsCount = getItems().count()

            if (itemsCount == 0) {
                return null
            }

            val bitmap = createBitmap(iconSize, iconSize)
            val canvas = Canvas(bitmap)
            val circlePath = Path().apply {
                addCircle(
                    (iconSize / 2).toFloat(),
                    (iconSize / 2).toFloat(),
                    (iconSize / 2).toFloat(),
                    Path.Direction.CCW
                )
            }
            canvas.clipPath(circlePath)
            canvas.drawPaint(folderIconBackgroundPaint)
            val folderColumnCount = ceil(sqrt(itemsCount.toDouble())).roundToInt()
            val folderRowCount = ceil(itemsCount.toFloat() / folderColumnCount).roundToInt()
            val scaledCellSize = (iconSize.toFloat() / folderColumnCount)
            val scaledGap = scaledCellSize / 4f
            val scaledIconSize =
                (iconSize - (folderColumnCount + 1) * scaledGap) / folderColumnCount
            val extraYMargin =
                if (folderRowCount < folderColumnCount) (scaledIconSize + scaledGap) / 2 else 0f
            items.forEach {
                val (row, column) = getElementPosition(it)
                val drawableX = (scaledGap + column * scaledIconSize + column * scaledGap).toInt()
                val drawableY =
                    (extraYMargin + scaledGap + row * scaledIconSize + row * scaledGap).toInt()
                val newDrawable = it.drawable?.constantState?.newDrawable()?.mutate()
                newDrawable?.setBounds(
                    drawableX,
                    drawableY,
                    drawableX + scaledIconSize.toInt(),
                    drawableY + scaledIconSize.toInt()
                )
                newDrawable?.draw(canvas)
            }
            return bitmap.toDrawable(resources)
        }

        fun getDrawingRect(): RectF {
            val count = getItems().count()
            if (count == 0) {
                return RectF(0f, 0f, 0f, 0f)
            }
            val columnsCount = ceil(sqrt(count.toDouble())).toInt()
            val rowsCount = ceil(count.toFloat() / columnsCount).toInt()
            val cellSize = pullSlotDims()
            val gap = getGapSize()
            val yGap = if (context.launcherConfig.showHomeAppLabels) {
                gap + textPaint.textSize + 2 * labelSideMargin
            } else gap
            val cell = cells[item.getTopLeft(rowCount)] ?: return RectF(0f, 0f, 0f, 0f)
            val centerX = sideMargins.left + cell.centerX()
            val centerY = sideMargins.top + cell.centerY()
            val folderDialogWidth =
                columnsCount * cellSize + 2 * folderPadding + (columnsCount - 1) * gap
            val folderDialogHeight =
                rowsCount * cellSize + 3 * folderPadding + folderTitleTextPaint.textSize + rowsCount * yGap
            var folderDialogTop = centerY - folderDialogHeight / 2
            var folderDialogLeft = centerX - folderDialogWidth / 2

            if (folderDialogLeft < left + sideMargins.left) {
                folderDialogLeft += left + sideMargins.left - folderDialogLeft
            }
            if (folderDialogLeft + folderDialogWidth > right - sideMargins.right) {
                folderDialogLeft -= folderDialogLeft + folderDialogWidth - (right - sideMargins.right)
            }
            if (folderDialogTop < top + sideMargins.top) {
                folderDialogTop += top + sideMargins.top - folderDialogTop
            }
            if (folderDialogTop + folderDialogHeight > bottom - sideMargins.bottom) {
                folderDialogTop -= folderDialogTop + folderDialogHeight - (bottom - sideMargins.bottom)
            }

            return RectF(
                folderDialogLeft,
                folderDialogTop,
                folderDialogLeft + folderDialogWidth,
                folderDialogTop + folderDialogHeight
            )
        }

        fun pullCellsDrawingRect(): RectF {
            val folderRect = getDrawingRect()
            return RectF(
                folderRect.left + folderPadding,
                folderRect.top + folderPadding * 2 + folderTitleTextPaint.textSize,
                folderRect.right - folderPadding,
                folderRect.bottom - folderPadding
            )
        }

        fun pullCellsGridCenters(): List<Triple<Int, Int, Int>> {
            val count = getItems().count()
            if (count == 0) {
                return emptyList()
            }

            val columnsCount = ceil(sqrt(count.toDouble())).roundToInt()
            val rowsCount = ceil(count.toFloat() / columnsCount).roundToInt()
            val folderItemsRect = pullCellsDrawingRect()
            val cellSize = pullSlotDims()
            val gap = getGapSize()
            val yGap = if (context.launcherConfig.showHomeAppLabels) {
                gap + textPaint.textSize + 2 * labelSideMargin
            } else gap
            return (0 until columnsCount * rowsCount)
                .toList()
                .map { Pair(it % columnsCount, it / columnsCount) }
                .mapIndexed { index, (x, y) ->
                    Triple(
                        index,
                        (folderItemsRect.left + x * cellSize + x * gap + cellSize / 2).toInt(),
                        (folderItemsRect.top + y * cellSize + y * yGap + cellSize / 2).toInt()
                    )
                }
        }

        private fun getElementPosition(item: LobbyLookerMatrixDatum): Pair<Int, Int> {
            val count = getItems().count()
            val columnsCount = ceil(sqrt(count.toDouble())).roundToInt()
            val column = item.left % columnsCount
            val row = item.left / columnsCount
            return Pair(row, column)
        }

        fun pullCellRect(item: LobbyLookerMatrixDatum): Rect {
            val (row, column) = getElementPosition(item)
            val itemsRect = pullCellsDrawingRect()
            val cellSize = pullSlotDims()
            val gapSize = getGapSize()
            val yGapSize = if (context.launcherConfig.showHomeAppLabels) {
                gapSize + textPaint.textSize + 2 * labelSideMargin
            } else gapSize
            val left = (itemsRect.left + column * cellSize + column * gapSize).roundToInt()
            val top = (itemsRect.top + row * cellSize + row * yGapSize).roundToInt()
            return Rect(
                left,
                top,
                left + cellSize,
                top + cellSize
            )
        }

        fun tweenClosing(callback: () -> Unit) {
            post {
                if (closing) {
                    return@post
                }
                closing = true
                ValueAnimator.ofFloat(scale, 0.2f)
                    .apply {
                        interpolator = DecelerateInterpolator()
                        addUpdateListener {
                            scale = it.animatedValue as Float
                            redrawGrid()
                        }
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                super.onAnimationEnd(animation)
                                scale = 0.2f
                                callback()
                            }
                        })
                        duration = (FOLDER_ANIMATION_DURATION * (max(0f, scale - 0.2f))).toLong()
                        start()
                    }
            }
        }

        fun pullSlotDims(): Int = min(cellWidth, cellHeight)

        private fun getGapSize(): Float {
            val cellSize = pullSlotDims()
            return if (cellSize == cellWidth) {
                0f
            } else {
                cellSize / 5f
            }
        }
    }

    companion object {
        /**
         * The view placed for the grid row [itemId]: matched by the row first, since the row's
         * [widgetId] can be stale after a refetch that raced its bind; by the widget id otherwise.
         */
        internal fun <V> placedWidgetView(
            views: List<V>,
            tagOf: (V) -> Any?,
            itemIdOf: (V) -> Long?,
            widgetId: Int,
            itemId: Long?,
        ): V? = views.firstOrNull { itemId != null && itemIdOf(it) == itemId }
            // A row without a bound id has -1: by the tag alone a second such row would be handed
            // the first one's view.
            ?: views.firstOrNull { widgetId != -1 && tagOf(it) == widgetId }

        /**
         * Every view placed for the grid row [itemId] (or, for a view tied to no row, for
         * [widgetId]), so placing the row's view can replace any it already has instead of
         * leaving a second one frozen at its page-1 spot.
         */
        internal fun <V> widgetViewsOfRow(
            views: List<V>,
            tagOf: (V) -> Any?,
            itemIdOf: (V) -> Long?,
            widgetId: Int,
            itemId: Long?,
        ): List<V> = views.filter { view ->
            val ownId = itemIdOf(view)
            (itemId != null && ownId == itemId) || (ownId == null && tagOf(view) == widgetId)
        }

        /** The views whose grid row id is not in [liveItemIds]. A view with no row recorded is kept. */
        internal fun <V> orphanWidgetViews(viewItemIds: Map<V, Long?>, liveItemIds: Set<Long>): List<V> =
            viewItemIds.filter { (_, id) -> id != null && id !in liveItemIds }.keys.toList()

        private const val LABEL_MAX_LINES = 1

        private const val PAGE_INDICATOR_STRETCH = 0.6f
        private const val PAGE_INDICATOR_INACTIVE_ALPHA = 112
        private const val PAGE_INDICATOR_RESTING_ALPHA = 210f
        private const val PAGE_TRANSIT_SCALE = 0.08f
        private const val PAGE_TRANSIT_FADE = 0.45f

        private const val PAGE_INDICATOR_DOT_SWELL = 0.18f
        private const val PAGE_INDICATOR_APPEAR_SCALE = 0.75f
        private const val FOLDER_OPEN_HOLD_THRESHOLD = 500L
        private const val FOLDER_CLOSE_HOLD_THRESHOLD = 300L
        private const val FOLDER_ANIMATION_DURATION = 200L
    }
}

private class AnimatedMatrixPager(
    private val pullMaxLeaf: () -> Int,
    private val redrawGrid: () -> Unit,
    private val getWidth: () -> Int,
    private val getHandler: () -> Handler,
    private val getNextPageBound: () -> Int,
    private val getPrevPageBound: () -> Int,
    private val pageChangeStarted: () -> Unit,
) {
    companion object {
        private const val PAGE_CHANGE_HOLD_THRESHOLD = 500L

        /** A whole page's settle; a partly dragged page gets its share of this. */
        private const val PAGE_SETTLE_MS = 220L

        /** A page walked from a swipe queued while the previous one settled. */
        private const val PAGE_SETTLE_QUEUED_MS = 160L

        /** Floor, so a page released almost in place still visibly lands. */
        private const val PAGE_SETTLE_MIN_MS = 80L
        private const val PAGE_INDICATORS_FADE_DELAY = 600L
        private const val PAGE_INDICATORS_FADE_IN = 120L

        private enum class PageChangeArea {
            LEFT,
            MIDDLE,
            RIGHT
        }
    }

    private var lastPage = 0
    private var currentPage = 0
    private var pageChangeLastArea = PageChangeArea.MIDDLE
    private var pageChangeLastAreaEntryTime = 0L
    private var pageChangeAnimLeftPercentage = 0f
    private var pageChangeEnabled = true

    /** Flings that arrived while a page was settling, net: >0 pages ahead, <0 pages back; see [flingSwipe]. */
    private var pendingStep = 0
    private var pageChangeIndicatorsAlpha = 0f
    private var indicatorsShownAt = 0L
    private var pageChangeSwipedPercentage = 0f

    fun getCurrentPage() = currentPage

    fun isCellOnActiveLeaf(item: LobbyLookerMatrixDatum) = item.page == currentPage

    fun isCellOnLastLeaf(item: LobbyLookerMatrixDatum) = item.page == lastPage

    fun getPageCount() = max(pullMaxLeaf(), currentPage) + 1

    fun isOutsideOfLeafRange() = currentPage > pullMaxLeaf()

    fun isCellInSlideRange(item: LobbyLookerMatrixDatum) =
        ((pageChangeSwipedPercentage > 0f && item.page == currentPage - 1) || (pageChangeSwipedPercentage < 0f && item.page == currentPage + 1))

    fun isSwiped() = abs(pageChangeSwipedPercentage) > 0f

    fun isAnimatingLeafChange() = pageChangeAnimLeftPercentage != 0f

    /** True while a page is being dragged or is still settling - a horizontal gesture is paging. */
    fun isPaging() = isSwiped() || isAnimatingLeafChange() || !pageChangeEnabled

    fun shouldViewerLeafShiftIndicator() =
        getPageCount() > 1 || isSwiped() || isAnimatingLeafChange() || pageChangeIndicatorsAlpha != 0f

    fun pullIndicatorsEmphasis(): Float =
        if (isSwiped() || isAnimatingLeafChange()) 1f else pageChangeIndicatorsAlpha

    fun pullIndicatorsFadeIn(): Float =
        ((SystemClock.uptimeMillis() - indicatorsShownAt).toFloat() / PAGE_INDICATORS_FADE_IN).coerceIn(0f, 1f)

    private fun flagIndicatorsShown() {
        if (!shouldViewerLeafShiftIndicator()) {
            indicatorsShownAt = SystemClock.uptimeMillis()
        }
    }

    fun pullLeafShiftIndicatorsAlpha() = if (pageChangeIndicatorsAlpha != 0f) {
        (pageChangeIndicatorsAlpha * 255.0f).toInt()
    } else {
        255
    }

    fun pullXFactorForLiveLeaf(): Float {
        return if (abs(pageChangeSwipedPercentage) > 0f) {
            pageChangeSwipedPercentage
        } else {
            if (currentPage > lastPage) {
                pageChangeAnimLeftPercentage
            } else {
                -pageChangeAnimLeftPercentage
            }
        }
    }

    fun pullXFactorForLastLeaf(): Float {
        return if (abs(pageChangeSwipedPercentage) > 0f) {
            (1 - abs(pageChangeSwipedPercentage)) * -sign(pageChangeSwipedPercentage)
        } else {
            if (currentPage > lastPage) {
                pageChangeAnimLeftPercentage - 1
            } else {
                1 - pageChangeAnimLeftPercentage
            }
        }
    }

    fun pullLivePanePointInFullLeafSpace(): Float {
        val rangeStart = lastPage.toFloat()
        val rangeEndPage = if (abs(pageChangeSwipedPercentage) > 0f) {
            if (pageChangeSwipedPercentage > 0f) {
                currentPage - 1
            } else {
                currentPage + 1
            }
        } else {
            currentPage
        }
        val rangeEnd = rangeEndPage.toFloat()
        val lerpAmount = if (pageChangeAnimLeftPercentage != 0f) {
            1 - pageChangeAnimLeftPercentage
        } else {
            abs(pageChangeSwipedPercentage)
        }
        return MathUtils.lerp(rangeStart, rangeEnd, lerpAmount)
    }

    fun setSwipeMovement(diffX: Float) {
        if (!pageChangeEnabled) {
            return
        }

        if (currentPage < pullMaxLeaf() && diffX > 0f || currentPage > 0 && diffX < 0f) {
            flagIndicatorsShown()
            pageChangeSwipedPercentage = (-diffX / getWidth().toFloat()).coerceIn(-1f, 1f)
            pageChangeStarted()
            redrawGrid()
        }
    }

    fun flingSwipe(towardsNext: Boolean): Boolean {
        if (isAnimatingLeafChange() || !pageChangeEnabled) {
            // A fling while a page is still settling is queued and played when it lands: three
            // quick flings walk three pages; one back cancels one ahead.
            pendingStep += if (towardsNext) 1 else -1
            return true
        }
        if (!isSwiped()) {
            return false
        }

        // A fling against the drag is consumed too: the release settles the page by distance.
        if ((pageChangeSwipedPercentage < 0f) != towardsNext) {
            return true
        }
        lastPage = currentPage
        currentPage += if (towardsNext) 1 else -1
        treatLeafShift(true)
        return true
    }

    fun finalizeSwipe() {
        if (abs(pageChangeSwipedPercentage) == 0f) {
            return
        }

        if (abs(pageChangeSwipedPercentage) > 0.5f) {
            lastPage = currentPage
            currentPage = if (pageChangeSwipedPercentage > 0f) {
                currentPage - 1
            } else {
                currentPage + 1
            }
            treatLeafShift(true)
        } else {
            lastPage = if (pageChangeSwipedPercentage > 0f) {
                currentPage - 1
            } else {
                currentPage + 1
            }
            pageChangeSwipedPercentage =
                sign(pageChangeSwipedPercentage) * (1 - abs(pageChangeSwipedPercentage))
            treatLeafShift(true)
        }
    }

    fun processCellMovement(x: Int, y: Int) {
        surfaceIndicators()
        if (x > getNextPageBound()) {
            doWithLeafShiftLag(PageChangeArea.RIGHT) {
                nextOrAdditionalLeaf()
            }
        } else if (x < getPrevPageBound()) {
            doWithLeafShiftLag(PageChangeArea.LEFT) {
                prevPage()
            }
        } else {
            wipeLeafShiftFlags()
        }
    }

    fun cellMovementStopped() {
        planIndicatorsFade()
    }

    fun nextPage(redraw: Boolean = false): Boolean {
        if (currentPage < pullMaxLeaf() && pageChangeEnabled) {
            lastPage = currentPage
            currentPage++
            treatLeafShift(redraw)
            return true
        }

        return false
    }

    fun prevPage(redraw: Boolean = false): Boolean {
        if (currentPage > 0 && pageChangeEnabled) {
            lastPage = currentPage
            currentPage--
            treatLeafShift(redraw)
            return true
        }

        return false
    }

    fun skipToPage(targetPage: Int): Boolean {
        if (currentPage != targetPage && targetPage < pullMaxLeaf() + 1) {
            lastPage = currentPage
            currentPage = targetPage
            treatLeafShift()
            return true
        }

        return false
    }

    private val checkAndExecuteDelayedPageChange: Runnable = Runnable {
        if (System.currentTimeMillis() - pageChangeLastAreaEntryTime > PAGE_CHANGE_HOLD_THRESHOLD) {
            when (pageChangeLastArea) {
                PageChangeArea.RIGHT -> nextOrAdditionalLeaf(true)
                PageChangeArea.LEFT -> prevPage(true)
                else -> wipeLeafShiftFlags()
            }
        }
    }

    private val startFadingIndicators: Runnable = Runnable {
        ValueAnimator.ofFloat(1f, 0f)
            .apply {
                addUpdateListener {
                    pageChangeIndicatorsAlpha = it.animatedValue as Float
                    redrawGrid()
                }
                start()
            }
    }

    private fun surfaceIndicators() {
        flagIndicatorsShown()
        pageChangeIndicatorsAlpha = 1f
        getHandler().removeCallbacks(startFadingIndicators)
    }

    private fun wipeLeafShiftFlags() {
        pageChangeLastArea = PageChangeArea.MIDDLE
        pageChangeLastAreaEntryTime = 0
        getHandler().removeCallbacks(checkAndExecuteDelayedPageChange)
    }

    private fun planLeafShift() {
        pageChangeLastAreaEntryTime = System.currentTimeMillis()
        getHandler().postDelayed(checkAndExecuteDelayedPageChange, PAGE_CHANGE_HOLD_THRESHOLD)
    }

    private fun planIndicatorsFade() {
        pageChangeIndicatorsAlpha = 1f
        getHandler().postDelayed(startFadingIndicators, PAGE_INDICATORS_FADE_DELAY)
    }

    private fun doWithLeafShiftLag(needed: PageChangeArea, pageChangeFunction: () -> Boolean) {
        if (pageChangeLastArea != needed) {
            pageChangeLastArea = needed
            planLeafShift()
        } else if (System.currentTimeMillis() - pageChangeLastAreaEntryTime > PAGE_CHANGE_HOLD_THRESHOLD) {
            if (pageChangeFunction()) {
                wipeLeafShiftFlags()
            }
        }
    }

    private fun nextOrAdditionalLeaf(redraw: Boolean = false): Boolean {
        if (currentPage < pullMaxLeaf() + 1 && pageChangeEnabled) {
            lastPage = currentPage
            currentPage++
            treatLeafShift(redraw)
            return true
        }

        return false
    }

    private fun treatLeafShift(redraw: Boolean = false) {
        flagIndicatorsShown()
        pageChangeEnabled = false
        pageChangeIndicatorsAlpha = 0f
        pageChangeStarted()
        val startingAt = 1 - abs(pageChangeSwipedPercentage)
        pageChangeSwipedPercentage = 0f
        getHandler().removeCallbacks(startFadingIndicators)
        pageChangeStarted()
        if (redraw) {
            redrawGrid()
        }
        ValueAnimator.ofFloat(startingAt, 0f)
            .apply {
                // Timed by what is left to travel: left at the framework's 300 ms, a page the finger
                // had already dragged most of the way still took the full time for its last
                // stretch, and the next swipe waited behind it - paging read as slow. A page walked
                // from swipes queued while this one settled goes quicker still.
                val full = if (pendingStep != 0) PAGE_SETTLE_QUEUED_MS else PAGE_SETTLE_MS
                duration = (full * startingAt).toLong().coerceAtLeast(PAGE_SETTLE_MIN_MS)
                // Settles rather than bounces: a page that springs past its edge and comes back
                // is the part of this that looked wrong. A shorter tail than (0.2, 0, 0, 1), which
                // spent its last third crawling the final few pixels.
                interpolator = PathInterpolator(0.2f, 0f, 0.2f, 1f)
                addUpdateListener {
                    if (it.animatedValue != 0f) {
                        pageChangeAnimLeftPercentage = it.animatedValue as Float
                        redrawGrid()
                    }
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        pageChangeAnimLeftPercentage = 0f
                        pageChangeEnabled = true
                        lastPage = currentPage
                        wipeLeafShiftFlags()
                        // Swipes made while this page was settling, played one page at a time from
                        // rest; any that would run past the first or last page are dropped.
                        val step = pendingStep.sign
                        if (step > 0 && currentPage < pullMaxLeaf() || step < 0 && currentPage > 0) {
                            pendingStep -= step
                            currentPage += step
                            treatLeafShift(true)
                            return
                        }
                        pendingStep = 0
                        planIndicatorsFade()
                        redrawGrid()
                    }
                })
                start()
            }
    }
}
