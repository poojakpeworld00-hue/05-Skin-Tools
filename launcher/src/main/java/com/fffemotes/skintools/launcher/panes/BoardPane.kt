package com.fffemotes.skintools.launcher.panes

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.content.pm.ApplicationInfo
import android.graphics.Rect
import android.content.pm.LauncherApps
import android.os.Process
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.fffemotes.skintools.launcher.databinding.LxBinQueryShortcutBinding
import com.fffemotes.skintools.launcher.exts.appUsageDB
import com.fffemotes.skintools.launcher.support.GadgetSelections
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.helpers.ensureBackgroundThread
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.views.MyGridLayoutManager
import com.fffemotes.skintools.launcher.R
import com.fffemotes.skintools.launcher.screens.XHubStage
import com.fffemotes.skintools.launcher.lists.BoardPagesLister
import com.fffemotes.skintools.launcher.lists.HubsLister
import com.fffemotes.skintools.launcher.persona.BoardStyle
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs
import com.fffemotes.skintools.launcher.databinding.LxAllAppsZoneBinding
import com.fffemotes.skintools.launcher.exts.putRackSkin
import com.fffemotes.skintools.launcher.exts.launcherConfig
import com.fffemotes.skintools.launcher.exts.fireApplet
import com.fffemotes.skintools.launcher.intro.HubPromosSwitches
import com.fffemotes.skintools.launcher.intro.HubInlinePromo
import com.fffemotes.skintools.launcher.intro.HubOfferCoordinator
import com.fffemotes.skintools.launcher.exts.prepRackBackdrop
import com.fffemotes.skintools.launcher.support.ITEM_TYPE_ICON
import com.fffemotes.skintools.launcher.contracts.AllGadgetsGuardian
import com.fffemotes.skintools.launcher.entities.GadgetHub
import com.fffemotes.skintools.launcher.entities.LobbyLookerMatrixDatum
import com.fffemotes.skintools.launcher.entities.appLauncherComparator

class BoardPane(
    context: Context,
    attributeSet: AttributeSet
) : HubPane<LxAllAppsZoneBinding>(context, attributeSet), AllGadgetsGuardian {
    companion object {
        private const val SEARCH_COLUMNS = 4
        private const val SEARCH_MAX_RESULTS = 8

        /** Rows of app cells inflated while idle so the first fling recycles instead of inflating. */
        private const val PREWARM_ROWS = 4
        private const val SEARCH_MAX_SHORTCUTS = 4
    }

    private var lastTouchCoords = Pair(0f, 0f)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    var touchDownY = -1
    var ignoreTouches = false

    private var launchers = emptyList<GadgetHub>()

    private var scrollListenerAdded = false

    private val adSlot by lazy {
        FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
        }
    }

    private var launchersAdapter: HubsLister? = null

    private var pagesAdapter: BoardPagesLister? = null
    private var shownPaged: Boolean? = null
    private var touchDownX = -1

    private val isPaged: Boolean get() = context.launcherConfig.rackStyle == BoardStyle.PAGED

    @SuppressLint("ClickableViewAccessibility")
    override fun setupFragment(activity: XHubStage) {
        this.activity = activity
        this.binding = LxAllAppsZoneBinding.bind(this)

        val layoutManager = binding.allAppsGridIx.layoutManager as MyGridLayoutManager
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) =
                if (getLister()?.isPromoSlotSpot(position) == true) layoutManager.spanCount else 1
        }

        binding.allAppsGridIx.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                touchDownY = -1
            }

            return@setOnTouchListener false
        }

        binding.allAppsFlipperIx.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = drawDots(selected = position)
        })
    }

    fun reloadUnits() {
        HubInlinePromo.wireOnReveal(activity, HubPromosSwitches.KEY_APP_DRAWER, adSlot)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prepRackBackdrop(context.getColor(R.color.launcher_all_app_bg))
    }

    @SuppressLint("NotifyDataSetChanged")
    fun onResume() {
        if (binding.allAppsGridIx.layoutManager == null || (binding.allAppsGridIx.adapter == null && pagesAdapter == null)) {
            return
        }

        val layoutManager = binding.allAppsGridIx.layoutManager as MyGridLayoutManager
        if (layoutManager.spanCount != context.launcherConfig.drawerColumnCount || shownPaged != isPaged) {
            onConfigurationChanged()

            getLister()?.notifyDataSetChanged()
        }
    }

    fun onConfigurationChanged() {
        binding.allAppsGridIx.scrollToPosition(0)
        binding.allAppsFlipperIx.setCurrentItem(0, false)
        prepareViews()

        val layoutManager = binding.allAppsGridIx.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = context.launcherConfig.drawerColumnCount
        prepBridge(launchers)
    }

    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) {
            return super.onInterceptTouchEvent(event)
        }

        var shouldIntercept = false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownY = event.y.toInt()
                touchDownX = event.x.toInt()
            }

            MotionEvent.ACTION_MOVE -> {
                if (ignoreTouches) {
                    if (lastTouchCoords.first != event.x || lastTouchCoords.second != event.y) {
                        touchDownY = -1
                        return true
                    }
                }

                if (touchDownY != -1) {
                    val distance = event.y.toInt() - touchDownY

                    val mostlyVertical = !isPaged || abs(distance) > abs(event.x.toInt() - touchDownX)
                    shouldIntercept = distance > touchSlop && mostlyVertical && isMatrixAtTop()
                    if (shouldIntercept) {
                        if (binding.queryBarIx.hasFocus()) {
                            activity?.hideKeyboard()
                        }
                        activity?.startHandlingTouches(touchDownY)
                        touchDownY = -1
                    }
                }
            }
        }

        lastTouchCoords = Pair(event.x, event.y)
        return shouldIntercept
    }

    private fun isMatrixAtTop(): Boolean {
        if (isPaged) return true
        val grid = binding.allAppsGridIx
        val first = grid.getChildAt(0) ?: return true
        val layoutManager = grid.layoutManager ?: return true
        return grid.getChildAdapterPosition(first) <= 0 &&
            layoutManager.getDecoratedTop(first) >= grid.paddingTop
    }

    /** The layout [launchers] was last submitted for - see [gotLaunchers]. */
    @Volatile
    private var submittedShape: List<Any>? = null

    /** Everything in the config that changes how the same list is laid out. */
    private fun shelfShape(): List<Any> = with(context.launcherConfig) {
        listOf(rackStyle, drawerColumnCount, drawerRowCount, showDrawerAppLabels)
    }

    /** Same apps, in the same order, with the same names, tints and decoded icons. */
    private fun sameLaunchers(a: List<GadgetHub>, b: List<GadgetHub>): Boolean =
        a.size == b.size && a.indices.all { i ->
            val x = a[i]
            val y = b[i]
            x.packageName == y.packageName && x.activityName == y.activityName && x.title == y.title &&
                x.thumbnailColor == y.thumbnailColor && x.drawable === y.drawable
        }

    /**
     * Shows [appLaunchers]. Every resume hands the drawer the launcher list twice (the cached one,
     * then the scan's), and almost always nothing has changed; the paged drawer rebuilt every page
     * and jumped back to the first, so the icons visibly repainted on each return. A list identical
     * to the one on screen, for a drawer laid out the same way, is not resubmitted. [force]
     * resubmits anyway: a config change that moves rows (the ad row) without changing the apps.
     */
    fun gotLaunchers(appLaunchers: List<GadgetHub>, force: Boolean = false) {
        val own = context.applicationContext.packageName
        val showOwn = com.fffemotes.skintools.launcher.Launcher.settings().iconInDrawer
        val sorted = appLaunchers.filter { showOwn || it.packageName != own }.sortedWith(appLauncherComparator)
        val shape = shelfShape()
        if (!force && shape == submittedShape && sameLaunchers(sorted, launchers)) return
        launchers = sorted
        submittedShape = shape

        prepBridge(launchers)
    }

    private fun live(apps: List<GadgetHub>, current: List<GadgetHub>): List<GadgetHub> {
        val byKey = current.associateBy { it.packageName to it.activityName }
        return apps.mapNotNull { byKey[it.packageName to it.activityName] ?: current.firstOrNull { c -> c.packageName == it.packageName } }
    }

    private fun getLister() = launchersAdapter

    private val onItemClick: (Any) -> Unit = {
        val launcher = it as GadgetHub
        val where = if (binding.queryBarIx.isSearchOpen) "search" else "drawer"
        when {
            com.fffemotes.skintools.launcher.intro.PromotedTiles.isSponsored(launcher) -> com.fffemotes.skintools.launcher.Launcher.events.onClick("sponsored_tile", launcher.title)
            launcher.packageName == context.applicationContext.packageName -> com.fffemotes.skintools.launcher.Launcher.events.onClick("our_icon", where)
            else -> com.fffemotes.skintools.launcher.Launcher.events.onClick("app_icon", where)
        }
        if (com.fffemotes.skintools.launcher.intro.PromotedTiles.isSponsored(launcher)) {
            activity?.let { a -> com.fffemotes.skintools.launcher.intro.PromotedTiles.open(a, launcher) }
        } else if (launcher.packageName == context.applicationContext.packageName) {
            activity?.wakeOwnApplet()
        } else {
            activity?.fireApplet(launcher.packageName, launcher.activityName)
        }
        if (activity?.launcherConfig?.closeAppletRack == true) {
            activity?.closeAppletRack(delayed = true)
        }
        ignoreTouches = false
        touchDownY = -1
    }

    private fun prepBridge(launchers: List<GadgetHub>) {
        activity?.runOnUiThread {
            val paged = isPaged
            val layoutManager = binding.allAppsGridIx.layoutManager as MyGridLayoutManager
            layoutManager.spanCount = context.launcherConfig.drawerColumnCount

            if (shownPaged != paged) {
                shownPaged = paged
                binding.allAppsGridIx.beVisibleIf(!paged)
                binding.allAppsFlipperIx.beVisibleIf(paged)
                binding.allAppsLeafPipsIx.beVisibleIf(paged)
                (adSlot.parent as? ViewGroup)?.removeView(adSlot)
                if (paged) {
                    launchersAdapter = null
                    binding.allAppsGridIx.adapter = null
                } else {
                    pagesAdapter = null
                    binding.allAppsFlipperIx.adapter = null
                }
            }

            if (paged) {
                if (pagesAdapter == null) {
                    pagesAdapter = BoardPagesLister(activity!!, this, adSlot, onItemClick)
                    binding.allAppsFlipperIx.adapter = pagesAdapter
                }
            } else if (getLister() == null) {
                HubsLister(activity!!, this, adSlot, onItemClick).apply {
                    launchersAdapter = this
                    binding.allAppsGridIx.itemAnimator = null
                    binding.allAppsGridIx.adapter = this
                    prewarm(binding.allAppsGridIx, layoutManager.spanCount * PREWARM_ROWS)
                }
            }

            submitList(launchers.toMutableList())
        }
    }

    private fun chunkPages(apps: List<GadgetHub>): List<List<GadgetHub>> {
        if (apps.isEmpty()) return emptyList()
        val columns = context.launcherConfig.drawerColumnCount.coerceAtLeast(1)
        val rows = context.launcherConfig.drawerRowCount.coerceAtLeast(2)
        val perLeaf = columns * rows
        val firstPageApps = (perLeaf - columns).coerceAtLeast(columns)
        val pages = ArrayList<List<GadgetHub>>()
        val first = apps.take(firstPageApps).toMutableList()
        val adIndex = (HubPromosSwitches.rackAdvertRowPoint() * columns).coerceIn(0, first.size)
        first.add(adIndex, HubsLister.AD_SLOT)
        pages.add(first)
        apps.drop(firstPageApps).chunked(perLeaf).forEach(pages::add)
        return pages
    }

    private fun drawDots(count: Int = pagesAdapter?.itemCount ?: 0, selected: Int = binding.allAppsFlipperIx.currentItem) {
        val dots = binding.allAppsLeafPipsIx
        val size = resources.getDimensionPixelSize(R.dimen.launcher_drawer_page_dot)
        val gap = resources.getDimensionPixelSize(R.dimen.launcher_drawer_page_dot_gap)
        while (dots.childCount > count) dots.removeViewAt(dots.childCount - 1)
        while (dots.childCount < count) {
            dots.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).also { it.marginStart = gap; it.marginEnd = gap }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(context.getColor(android.R.color.white))
                }
            })
        }
        for (i in 0 until dots.childCount) dots.getChildAt(i).alpha = if (i == selected) 1f else 0.35f
        dots.beVisibleIf(isPaged && count > 1)
    }

    fun restoreToFirstPage() {
        if (binding.allAppsFlipperIx.adapter != null) binding.allAppsFlipperIx.setCurrentItem(0, false)
    }

    fun onIconHidden(item: LobbyLookerMatrixDatum) {
        val itemToRemove = launchers.firstOrNull {
            it.pullHomebaseIdentifier() == item.pullCellIdentifier()
        }

        if (itemToRemove != null) {
            val position = launchers.indexOfFirst {
                it.pullHomebaseIdentifier() == item.pullCellIdentifier()
            }

            launchers = launchers.toMutableList().apply {
                removeAt(position)
            }

            submitList(launchers.toMutableList())
        }
    }

    fun prepareViews() {
        if (activity == null) {
            return
        }

        if (!scrollListenerAdded) {
            scrollListenerAdded = true
            binding.allAppsGridIx.addOnScrollListener(object : OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (binding.queryBarIx.hasFocus() && dy > 0 && binding.allAppsGridIx.computeVerticalScrollOffset() > 0) {
                        activity?.hideKeyboard()
                    }
                }
            })
        }

        prepRackBackdrop(context.getColor(R.color.launcher_all_app_bg))

        putLookupPoint()
        putBottomLookupClearance()
        binding.queryBarIx.beVisibleIf(context.launcherConfig.showSearchBar)
        binding.queryBarIx.requireToolbar().beGone()
        binding.queryBarIx.updateColors()
        binding.queryBarIx.putRackSkin()
        binding.queryBarIx.setupMenu()

        binding.queryBarIx.onSearchTextChangedListener = {
            submitList(launchers)
        }
        binding.queryBarIx.onSearchOpenListener = { surfaceLookupDeck(true) }
        binding.queryBarIx.onSearchClosedListener = { surfaceLookupDeck(false) }

        binding.queryBarIx.binding.topToolbarSearch.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP) com.fffemotes.skintools.launcher.Launcher.events.onClick("drawer_search")
            false
        }
        binding.queryBarIx.binding.topToolbarSearch.setOnEditorActionListener { _, actionId, _ ->
            if (binding.queryBarIx.getCurrentQuery().isEmpty()) return@setOnEditorActionListener false
            when (actionId) {
                EditorInfo.IME_ACTION_DONE,
                EditorInfo.IME_ACTION_SEARCH,
                EditorInfo.IME_ACTION_GO -> (if (isPaged) pagesAdapter?.firstPageAdapter else getLister())?.fireFirstApplet() == true
                else -> false
            }
        }
    }

    private var appliedSearchAtBottom: Boolean? = null

    private fun putLookupPoint() {
        val bottom = context.launcherConfig.drawerSearchAtBottom

        if (appliedSearchAtBottom == bottom) return
        appliedSearchAtBottom = bottom
        val search = binding.queryBarIx.id
        val dots = binding.allAppsLeafPipsIx.id

        fun RelativeLayout.LayoutParams.anchor(vararg rules: Pair<Int, Int>) {
            for (verb in listOf(RelativeLayout.ABOVE, RelativeLayout.BELOW, RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.ALIGN_PARENT_BOTTOM)) {
                removeRule(verb)
            }
            rules.forEach { (verb, subject) -> addRule(verb, subject) }
        }

        (binding.queryBarIx.layoutParams as RelativeLayout.LayoutParams).anchor(
            if (bottom) RelativeLayout.ALIGN_PARENT_BOTTOM to RelativeLayout.TRUE else RelativeLayout.ALIGN_PARENT_TOP to RelativeLayout.TRUE
        )
        (binding.allAppsGridIx.layoutParams as RelativeLayout.LayoutParams).anchor(
            if (bottom) RelativeLayout.ABOVE to search else RelativeLayout.BELOW to search
        )
        (binding.allAppsLeafPipsIx.layoutParams as RelativeLayout.LayoutParams).anchor(
            if (bottom) RelativeLayout.ABOVE to search else RelativeLayout.ALIGN_PARENT_BOTTOM to RelativeLayout.TRUE
        )
        (binding.allAppsFlipperIx.layoutParams as RelativeLayout.LayoutParams).anchor(
            if (bottom) RelativeLayout.ALIGN_PARENT_TOP to RelativeLayout.TRUE else RelativeLayout.BELOW to search,
            RelativeLayout.ABOVE to dots,
        )
        (binding.noResultsStubIx.layoutParams as RelativeLayout.LayoutParams).anchor(
            if (bottom) RelativeLayout.ALIGN_PARENT_TOP to RelativeLayout.TRUE else RelativeLayout.BELOW to search
        )

        (binding.queryPageIx.layoutParams as RelativeLayout.LayoutParams).anchor(
            if (bottom) RelativeLayout.ALIGN_PARENT_TOP to RelativeLayout.TRUE else RelativeLayout.BELOW to search,
            if (bottom) RelativeLayout.ABOVE to search else RelativeLayout.ALIGN_PARENT_BOTTOM to RelativeLayout.TRUE,
        )
        binding.queryPageIx.gravity = Gravity.TOP
        binding.queryBarIx.requestLayout()
    }

    private fun putBottomLookupClearance() {
        if (!clearanceWatchAdded) {
            clearanceWatchAdded = true
            viewTreeObserver.addOnGlobalLayoutListener { syncBottomLookupClearance() }
        }
        syncBottomLookupClearance()
    }

    private var clearanceWatchAdded = false

    private fun syncBottomLookupClearance() {
        val bottom = context.launcherConfig.drawerSearchAtBottom
        val wanted = if (bottom) binding.allAppsGridIx.paddingBottom else 0
        val bar = binding.queryBarIx
        if (bar.paddingBottom != wanted) {
            bar.setPadding(bar.paddingLeft, bar.paddingTop, bar.paddingRight, wanted)
        }

        val grid = binding.allAppsGridIx
        val topGap = if (bottom) resources.getDimensionPixelSize(R.dimen.launcher_drawer_list_top_gap) else 0
        if (grid.paddingTop != topGap) {
            grid.setPadding(grid.paddingLeft, topGap, grid.paddingRight, grid.paddingBottom)
        }
        syncDotsToLookupCapsule(bottom)
    }

    private fun syncDotsToLookupCapsule(bottom: Boolean) {
        val dots = binding.allAppsLeafPipsIx
        val params = dots.layoutParams as? RelativeLayout.LayoutParams ?: return

        if (bottom) {
            val own = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.medium_margin)
            if (dots.paddingBottom != own) dots.setPadding(dots.paddingLeft, dots.paddingTop, dots.paddingRight, own)
        }
        val margin = if (bottom) {
            val pill = binding.queryBarIx.binding.toolbarContainer
            var pillTop = 0
            var v: View? = pill
            while (v != null && v !== binding.queryBarIx) { pillTop += v.top; v = v.parent as? View }
            if (v == null || pill.height == 0) return
            val gap = resources.getDimensionPixelSize(R.dimen.launcher_page_dots_search_gap)
            -(pillTop - gap).coerceAtLeast(0)
        } else {
            0
        }
        if (params.bottomMargin != margin) {
            params.bottomMargin = margin
            dots.layoutParams = params
        }
    }

    private fun surfaceNoOutcomesPlaceholderIfNeeded() {
        val itemCount = if (isPaged) pagesAdapter?.itemCount else getLister()?.itemCount
        binding.noResultsStubIx.beVisibleIf(itemCount != null && itemCount == 0)
    }

    override fun onAppLauncherLongPressed(x: Float, y: Float, appLauncher: GadgetHub) {
        val gridItem = LobbyLookerMatrixDatum(
            id = null,
            left = -1,
            top = -1,
            right = -1,
            bottom = -1,
            page = 0,
            packageName = appLauncher.packageName,
            activityName = appLauncher.activityName,
            title = appLauncher.title,
            type = ITEM_TYPE_ICON,
            className = "",
            widgetId = -1,
            shortcutId = "",
            icon = null,
            docked = false,
            parentId = null,
            drawable = appLauncher.drawable
        )

        activity?.surfaceAtriumGlyphTweaks(x, y, gridItem, true)
        ignoreTouches = true

        binding.queryBarIx.closeSearch()
    }

    fun restoreLookup() {
        if (binding.queryBarIx.isSearchOpen) {
            binding.queryBarIx.closeSearch()
        }
    }

    private fun submitList(items: List<GadgetHub>) {
        val searchQuery = binding.queryBarIx.getCurrentQuery()
        if (binding.queryBarIx.isSearchOpen) {
            drawLookupDeck(items, searchQuery)
            return
        }
        val filtered = if (searchQuery.isNotEmpty()) {
            items.filter {
                it.title.normalizeString()
                    .contains(searchQuery.normalizeString(), ignoreCase = true)
            }
        } else {
            com.fffemotes.skintools.launcher.intro.PromotedTiles.interleave(items)
        }

        if (isPaged) {
            pagesAdapter?.submitPages(chunkPages(filtered), context.launcherConfig.drawerColumnCount, context.launcherConfig.drawerRowCount.coerceAtLeast(2))
            binding.allAppsFlipperIx.setCurrentItem(0, false)
            drawDots(selected = 0)
            surfaceNoOutcomesPlaceholderIfNeeded()
            return
        }
        getLister()?.submitList(withAdvertRow(filtered)) {
            surfaceNoOutcomesPlaceholderIfNeeded()
        }
    }

    private var searchAdapter: HubsLister? = null
    private var suggestions: List<GadgetHub>? = null
    private var suggestionsGeneration = 0

    private fun surfaceLookupDeck(open: Boolean) {
        binding.queryPageIx.beVisibleIf(open)
        if (open) {
            binding.allAppsGridIx.beGone()
            binding.allAppsFlipperIx.beGone()
            binding.allAppsLeafPipsIx.beGone()
            binding.noResultsStubIx.beGone()
            suggestions = null
            pullSelections()
            drawLookupDeck(launchers, binding.queryBarIx.getCurrentQuery())
        } else {
            val paged = isPaged
            binding.allAppsGridIx.beVisibleIf(!paged)
            binding.allAppsFlipperIx.beVisibleIf(paged)
            binding.allAppsLeafPipsIx.beVisibleIf(paged)
            searchAdapter?.highlight = ""
            submitList(launchers)
        }
    }

    private fun lookupGridBridge(): HubsLister {
        searchAdapter?.let { return it }
        val grid = binding.queryAppsGridIx
        grid.layoutManager = object : GridLayoutManager(context, SEARCH_COLUMNS) {
            override fun canScrollVertically() = false
        }
        grid.itemAnimator = null
        grid.isNestedScrollingEnabled = false

        val rowGap = resources.getDimensionPixelSize(R.dimen.launcher_search_card_row_gap)
        grid.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val position = parent.getChildAdapterPosition(view)
                outRect.top = if (position >= SEARCH_COLUMNS) rowGap else 0
            }
        })
        return HubsLister(activity!!, this, null, onItemClick).also {
            searchAdapter = it
            grid.adapter = it
        }
    }

    private fun pullSelections() {
        val generation = ++suggestionsGeneration
        val installed = launchers
        val pm = context.packageManager
        ensureBackgroundThread {
            val usage = runCatching { context.appUsageDB.getAll() }.getOrDefault(emptyList())
            val facts = HashMap<String, GadgetSelections.Facts?>()
            val ranked = GadgetSelections.rank(installed, usage, { pkg ->
                facts.getOrPut(pkg) {
                    runCatching {
                        val info = pm.getPackageInfo(pkg, 0)
                        GadgetSelections.Facts(info.firstInstallTime, info.applicationInfo?.category ?: ApplicationInfo.CATEGORY_UNDEFINED)
                    }.getOrNull()
                }
            }, System.currentTimeMillis())
            activity?.runOnUiThread {
                if (generation != suggestionsGeneration) return@runOnUiThread
                suggestions = ranked
                if (binding.queryBarIx.isSearchOpen && binding.queryBarIx.getCurrentQuery().isEmpty()) {
                    drawLookupDeck(launchers, "")
                }
            }
        }
    }

    private fun drawLookupDeck(items: List<GadgetHub>, query: String) {
        val adapter = lookupGridBridge()
        if (query.isEmpty()) {
            val apps = live(suggestions ?: emptyList(), items)
            binding.queryAppsTitleIx.setText(R.string.launcher_suggested_apps)
            adapter.highlight = ""
            adapter.submitList(apps.toMutableList())
            binding.queryAppsCellIx.beVisibleIf(apps.size >= GadgetSelections.MIN_TO_SHOW)
            binding.queryHotlinksCellIx.beGone()
            binding.noResultsStubIx.beGone()
            return
        }
        val needle = query.normalizeString()
        val matches = items.filter { it.title.normalizeString().contains(needle, ignoreCase = true) }
            .sortedWith(compareBy<GadgetHub> { !it.title.normalizeString().startsWith(needle, ignoreCase = true) }.thenBy { it.title.lowercase() })
        binding.queryAppsTitleIx.setText(R.string.launcher_searched_apps)
        adapter.highlight = query
        adapter.submitList(matches.take(SEARCH_MAX_RESULTS).toMutableList())
        binding.queryAppsCellIx.beVisibleIf(matches.isNotEmpty())
        binding.noResultsStubIx.beVisibleIf(matches.isEmpty())
        drawShortcuts(matches.firstOrNull())
    }

    private fun drawShortcuts(app: GadgetHub?) {
        val card = binding.queryHotlinksCellIx
        val list = binding.queryHotlinksRollIx
        list.removeAllViews()
        if (app == null) { card.beGone(); return }
        val launcherApps = context.applicationContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val shortcuts = runCatching {
            if (!launcherApps.hasShortcutHostPermission()) return@runCatching null
            val flags = LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
            launcherApps.getShortcuts(LauncherApps.ShortcutQuery().setQueryFlags(flags).setPackage(app.packageName), Process.myUserHandle())
        }.getOrNull()?.filter { it.isEnabled }?.take(SEARCH_MAX_SHORTCUTS).orEmpty()
        if (shortcuts.isEmpty()) { card.beGone(); return }
        binding.queryHotlinksTitleIx.text = app.title
        val inflater = LayoutInflater.from(context)
        val density = resources.displayMetrics.densityDpi
        shortcuts.forEach { shortcut ->
            val row = LxBinQueryShortcutBinding.inflate(inflater, list, false)
            row.queryHotlinkTextIx.text = shortcut.shortLabel ?: shortcut.longLabel ?: ""
            row.queryHotlinkIconIx.setImageDrawable(runCatching { launcherApps.getShortcutIconDrawable(shortcut, density) }.getOrNull())
            row.root.setOnClickListener {
                com.fffemotes.skintools.launcher.Launcher.events.onClick("search_shortcut")
                runCatching {
                    launcherApps.startShortcut(shortcut.`package`, shortcut.id, null, null, Process.myUserHandle())
                }.onFailure { activity?.showErrorToast(it as? Exception ?: Exception(it)) }
                if (activity?.launcherConfig?.closeAppletRack == true) activity?.closeAppletRack(delayed = true)
            }
            list.addView(row.root)
        }
        card.beVisible()
    }

    private fun withAdvertRow(apps: List<GadgetHub>): MutableList<GadgetHub> {
        if (apps.isEmpty()) {
            return apps.toMutableList()
        }

        val spanCount = (binding.allAppsGridIx.layoutManager as MyGridLayoutManager).spanCount
        val row = HubPromosSwitches.rackAdvertRowPoint()
        val index = (row * spanCount).coerceIn(0, apps.size)
        return apps.toMutableList().apply { add(index, HubsLister.AD_SLOT) }
    }
}
