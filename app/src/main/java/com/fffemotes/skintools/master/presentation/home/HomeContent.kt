package com.fffemotes.skintools.master.presentation.home

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import com.fffemotes.skintools.ads.AdKit
import com.fffemotes.skintools.ads.Ads
import com.fffemotes.skintools.ads.analytics.Analytics
import com.fffemotes.skintools.master.R
import com.fffemotes.skintools.master.databinding.ActivityHomeBinding
import com.fffemotes.skintools.master.databinding.ItemToolTileBinding
import com.fffemotes.skintools.master.domain.model.CatalogType
import com.fffemotes.skintools.master.presentation.articles.ArticlesActivity
import com.fffemotes.skintools.master.presentation.calculator.CalculatorActivity
import com.fffemotes.skintools.master.presentation.catalog.ListActivity
import com.fffemotes.skintools.master.presentation.common.accentBold
import com.fffemotes.skintools.master.presentation.common.glowPulse
import com.fffemotes.skintools.master.presentation.settings.SettingsActivity
import com.intuit.sdp.R as SdpR

/**
 * The Home screen's content (Claude Design "Skin Hub Onboarding v2"): tiles, tools, animations and its ads. Shared by
 * [HomeActivity] and the launcher's swipe-right panel (HostPanelFragment), as the reference app shares its HomeScreen.
 */
class HomeContent(
    private val activity: Activity,
    private val b: ActivityHomeBinding,
    /** RC place for the bottom and list ads: `screens.home`, or `launcher.host_app_panel` inside the launcher. */
    private val adPlace: String = PLACE,
) {
    private val resources = activity.resources

    private data class Tool(val icon: Int, val name: Int, val sub: Int, val open: () -> Intent)

    private val tools = listOf(
        Tool(R.drawable.ic_bulb, R.string.tool_tips, R.string.tool_tips_sub) { ArticlesActivity.intent(activity, guides = false) },
        Tool(R.drawable.ic_map, R.string.tool_maps, R.string.tool_maps_sub) { ListActivity.intent(activity, CatalogType.MAPS) },
        Tool(R.drawable.ic_diamond, R.string.tool_diamond, R.string.tool_diamond_sub) { ArticlesActivity.intent(activity, guides = true) },
        Tool(R.drawable.ic_calc, R.string.tool_calculator, R.string.tool_calculator_sub) { Intent(activity, CalculatorActivity::class.java) },
    )

    fun bind() {
        b.settings.setOnClickListener { open("settings") { Intent(activity, SettingsActivity::class.java) } }
        b.headline.text = activity.getText(R.string.explore_today).accentBold(b.headline)
        b.glow.glowPulse()

        // 2×2 grid of tool cards.
        val gap = resources.getDimensionPixelSize(SdpR.dimen._8sdp)
        tools.chunked(2).forEach { pair ->
            val line = LinearLayout(activity)
            b.tools.addView(line, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = gap })
            pair.forEachIndexed { i, tool ->
                val card = ItemToolTileBinding.inflate(activity.layoutInflater, line, true)
                if (i > 0) (card.root.layoutParams as LinearLayout.LayoutParams).marginStart = gap
                card.icon.setImageResource(tool.icon)
                card.name.setText(tool.name)
                card.sub.setText(tool.sub)
                card.root.setOnClickListener { open(resources.getResourceEntryName(tool.name)) { tool.open() } }
            }
        }
        mapOf(
            b.tileCharacters to CatalogType.CHARACTERS, b.tileBundles to CatalogType.BUNDLES, b.tileEmotes to CatalogType.EMOTES,
            b.tileWeapons to CatalogType.WEAPONS, b.tilePets to CatalogType.PETS, b.tileVehicles to CatalogType.VEHICLES,
        ).forEach { (tile, catalog) ->
            tile.setOnClickListener { open(catalog.name.lowercase()) { ListActivity.intent(activity, catalog) } }
        }
        bindAds()

        // Staggered entrance, as in the design's hcUp keyframes.
        listOf(b.header, b.tileCharacters, b.tileBundles, b.tileEmotes, b.tileWeapons, b.tilePets, b.tileVehicles, b.toolsTitle, b.tools)
            .forEachIndexed { i, v -> v.enter(i * 70L) }

        b.tileCharactersArt.loop("translationY", -resources.getDimension(SdpR.dimen._6sdp), 2000, 1000)
        b.tileBundlesArt.loop("translationY", -resources.getDimension(SdpR.dimen._4sdp), 1750, 1200)
        b.tileEmotesArt.loop("translationY", -resources.getDimension(SdpR.dimen._4sdp), 1300, 1400)
        b.tileWeaponsArt.loop("rotation", -6f, 1200, 1200)
        b.tilePetsArt.loop("translationY", -resources.getDimension(SdpR.dimen._5sdp), 1100, 1300)
        // Vehicle drives in from the right, then idles with a small back-and-forth.
        b.tileVehiclesArt.apply {
            translationX = resources.getDimension(SdpR.dimen._44sdp); alpha = 0f
            animate().translationX(0f).alpha(1f).setStartDelay(900).setDuration(900)
                .setInterpolator(DecelerateInterpolator(2f))
                .withEndAction { loop("translationX", -resources.getDimension(SdpR.dimen._4sdp), 900, 0) }.start()
        }
    }

    /** `bottom_ad` pinned under the list; `list_ad` inside the list at `list_ad_position` (after_header / after_tiles / after_tools). */
    private fun bindAds() {
        Ads.bottom(activity, b.adSlot, adPlace)
        val place = AdKit.config.places[adPlace]
        val list = b.adInline.parent as ViewGroup
        list.removeView(b.adInline)
        val at = when (place?.options?.get("list_ad_position") as? String) {
            "after_header" -> list.indexOfChild(b.header) + 1
            "after_tools" -> list.childCount
            else -> list.indexOfChild(b.toolsTitle) // after_tiles: right above the "Tools & guides" title
        }
        list.addView(b.adInline, at)
        if (place?.listAd?.on == true) Ads.list(activity, b.adInline, adPlace) else b.adInline.visibility = View.GONE
    }

    /** Every Home destination runs the `screens.home` exit ad first; navigation always continues. */
    private fun open(button: String, intent: () -> Intent) {
        Analytics.click(button)
        Ads.exit(activity, PLACE) { activity.startActivity(intent()) }
    }

    private fun View.enter(delay: Long) {
        alpha = 0f
        translationY = resources.getDimension(SdpR.dimen._13sdp)
        animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(500)
            .setInterpolator(DecelerateInterpolator(2f)).start()
    }

    // Animators honour the system "remove animations" setting, matching the design's reduced-motion rule.
    private fun View.loop(property: String, to: Float, halfPeriodMs: Long, delayMs: Long) {
        ObjectAnimator.ofFloat(this, property, 0f, to).apply {
            duration = halfPeriodMs
            startDelay = delayMs
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    companion object {
        const val PLACE = "screens.home"
    }
}
