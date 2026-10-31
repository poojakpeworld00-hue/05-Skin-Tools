package com.fffemotes.skintools.launcher.panes

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat
import androidx.core.widget.doAfterTextChanged
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.normalizeString
import com.fffemotes.skintools.launcher.R
import com.fffemotes.skintools.launcher.screens.XHubStage
import com.fffemotes.skintools.launcher.lists.StageGadgetsLister
import com.fffemotes.skintools.launcher.databinding.LxSideScrZoneBinding
import com.fffemotes.skintools.launcher.exts.fireApplet
import com.fffemotes.skintools.launcher.entities.GadgetHub
import com.fffemotes.skintools.launcher.intro.HubPromosSwitches
import com.fffemotes.skintools.launcher.intro.HubInlinePromo
import com.fffemotes.skintools.launcher.entities.appLauncherComparator
import kotlin.math.abs

class GadgetsStagePane(
    context: Context,
    attributeSet: AttributeSet,
) : HubPane<LxSideScrZoneBinding>(context, attributeSet) {
    private var launchers = emptyList<GadgetHub>()
    private var resultsCap = COLLAPSED_RESULTS

    private lateinit var suggestedAdapter: StageGadgetsLister
    private lateinit var recentAdapter: StageGadgetsLister
    private lateinit var resultsAdapter: StageGadgetsLister
    private lateinit var searchInAdapter: StageGadgetsLister

    private val gestureDetector = GestureDetectorCompat(context, object : SimpleOnGestureListener() {
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            if (velocityX > 0 && abs(velocityX) > abs(velocityY)) {
                activity?.concealLeftDeck()
                return true
            }

            return false
        }
    })

    override fun setupFragment(activity: XHubStage) {
        this.activity = activity
        this.binding = LxSideScrZoneBinding.bind(this)

        suggestedAdapter = StageGadgetsLister(R.layout.lx_bin_scr_grid_app) { tapApp(it, "panel") }
        recentAdapter = StageGadgetsLister(R.layout.lx_bin_scr_grid_app) { tapApp(it, "panel_recent") }
        resultsAdapter = StageGadgetsLister(R.layout.lx_bin_scr_outcome) { tapApp(it, "panel_search") }
        searchInAdapter = StageGadgetsLister(R.layout.lx_bin_scr_query_inc) {
            com.fffemotes.skintools.launcher.Launcher.events.onClick("panel_search_in_app")
            lookupInApplet(it)
        }

        binding.pageSuggestedGridIx.adapter = suggestedAdapter
        binding.pageRecentsGridIx.adapter = recentAdapter
        binding.pageResultsRollIx.adapter = resultsAdapter
        binding.pageQueryIncRollIx.adapter = searchInAdapter

        binding.pageQueryIx.doAfterTextChanged {
            resultsCap = COLLAPSED_RESULTS
            reloadSections()
        }

        binding.pageQueryIx.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP) com.fffemotes.skintools.launcher.Launcher.events.onClick("panel_search")
            false
        }

        binding.pageQueryWipeIx.setOnClickListener {
            com.fffemotes.skintools.launcher.Launcher.events.onClick("panel_search_clear")
            binding.pageQueryIx.setText("")
        }

        binding.pageSeeExtraIx.setOnClickListener {
            com.fffemotes.skintools.launcher.Launcher.events.onClick("panel_see_more")
            resultsCap = if (resultsCap == COLLAPSED_RESULTS) {
                EXPANDED_RESULTS
            } else {
                COLLAPSED_RESULTS
            }
            reloadSections()
        }
    }

    fun onOpened() {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        binding.pageHelloIx.setText(
            when {
                hour < 12 -> R.string.launcher_greeting_morning
                hour < 17 -> R.string.launcher_greeting_afternoon
                else -> R.string.launcher_greeting_evening
            }
        )
        val locale = java.util.Locale.getDefault()
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
        binding.pageDayIx.text = java.text.SimpleDateFormat(pattern, locale).format(java.util.Date())
        com.fffemotes.skintools.launcher.exts.enterMotion(
            binding.pageCrestIx,
            binding.pageQueryHolderIx,
            binding.pageSuggestedCrestIx,
            binding.pageSuggestedGridIx,
            binding.pageRecentsCrestIx,
            binding.pageRecentsGridIx,
        )
    }

    fun reloadUnits() {
        val activity = activity ?: return
        HubInlinePromo.wireOnReveal(
            activity, HubPromosSwitches.KEY_PANEL_MIDDLE, binding.pageMidEmbeddedCasingIx
        )
        HubInlinePromo.wireOnReveal(
            activity, HubPromosSwitches.KEY_PANEL_BOTTOM, binding.pageEmbeddedCasingIx
        )
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    fun gotLaunchers(appLaunchers: List<GadgetHub>) {
        val own = context.applicationContext.packageName
        val showOwn = com.fffemotes.skintools.launcher.Launcher.settings().iconInDrawer
        launchers = appLaunchers.filter { showOwn || it.packageName != own }.sortedWith(appLauncherComparator)
        activity?.runOnUiThread {
            reloadSections()
        }
    }

    fun hasProbe() = getQuery().isNotEmpty()

    fun restoreLookup() {
        binding.pageQueryIx.setText("")
        binding.pageScrollIx.scrollTo(0, 0)
    }

    private fun getQuery() = binding.pageQueryIx.text.toString().trim()

    private fun reloadSections() {
        val query = getQuery()
        val hasProbe = query.isNotEmpty()

        binding.pageQueryWipeIx.beVisibleIf(hasProbe)
        binding.pageSuggestedCrestIx.beVisibleIf(!hasProbe)
        binding.pageSuggestedGridIx.beVisibleIf(!hasProbe)

        if (hasProbe) {
            val results = launchers.filter {
                it.title.normalizeString().contains(query.normalizeString(), ignoreCase = true)
            }

            binding.pageRecentsCrestIx.beGone()
            binding.pageRecentsGridIx.beGone()
            binding.pageResultsCrestIx.beVisible()
            binding.pageResultsRollIx.beVisible()
            binding.pageSeeExtraIx.beVisibleIf(results.size > COLLAPSED_RESULTS)
            binding.pageSeeExtraIx.setText(
                if (resultsCap == COLLAPSED_RESULTS) R.string.launcher_see_more else R.string.launcher_see_less
            )
            resultsAdapter.submitList(results.take(resultsCap))

            val searchTargets = launchers.filter { it.packageName in SEARCH_IN_PACKAGES }
            binding.pageQueryIncCrestIx.beVisibleIf(searchTargets.isNotEmpty())
            binding.pageQueryIncRollIx.beVisibleIf(searchTargets.isNotEmpty())
            searchInAdapter.submitList(searchTargets)
        } else {
            val recent = launchers.drop(SUGGESTED_COUNT).take(RECENT_COUNT)
            binding.pageResultsCrestIx.beGone()
            binding.pageResultsRollIx.beGone()
            binding.pageQueryIncCrestIx.beGone()
            binding.pageQueryIncRollIx.beGone()
            binding.pageRecentsCrestIx.beVisibleIf(recent.isNotEmpty())
            binding.pageRecentsGridIx.beVisibleIf(recent.isNotEmpty())

            suggestedAdapter.submitList(launchers.take(SUGGESTED_COUNT))
            recentAdapter.submitList(recent)
        }
    }

    private fun tapApp(launcher: GadgetHub, where: String) {
        val own = launcher.packageName == context.applicationContext.packageName
        com.fffemotes.skintools.launcher.Launcher.events.onClick(if (own) "our_icon" else "app_icon", where)
        fireHomebase(launcher)
    }

    private fun fireHomebase(launcher: GadgetHub) {
        if (launcher.packageName == context.applicationContext.packageName) {
            activity?.wakeOwnApplet()
            return
        }
        activity?.fireApplet(launcher.packageName, launcher.activityName)
        activity?.concealLeftDeck()
    }

    private fun lookupInApplet(launcher: GadgetHub) {
        val query = getQuery()
        val uri = when (launcher.packageName) {
            PACKAGE_MAPS -> "geo:0,0?q=${Uri.encode(query)}"
            PACKAGE_PLAY_STORE -> "market://search?q=${Uri.encode(query)}"
            else -> "https://www.google.com/search?q=${Uri.encode(query)}"
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            setPackage(launcher.packageName)
        }

        try {
            activity?.startActivity(intent)
            activity?.concealLeftDeck()
        } catch (_: ActivityNotFoundException) {
            fireHomebase(launcher)
        }
    }

    companion object {
        private const val SUGGESTED_COUNT = 8
        private const val RECENT_COUNT = 4
        private const val COLLAPSED_RESULTS = 4
        private const val EXPANDED_RESULTS = 8

        private const val PACKAGE_CHROME = "com.android.chrome"
        private const val PACKAGE_MAPS = "com.google.android.apps.maps"
        private const val PACKAGE_PLAY_STORE = "com.android.vending"
        private val SEARCH_IN_PACKAGES =
            setOf(PACKAGE_CHROME, PACKAGE_MAPS, PACKAGE_PLAY_STORE)
    }
}
