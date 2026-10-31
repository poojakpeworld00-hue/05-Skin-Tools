package com.fffemotes.skintools.launcher.intro

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.fffemotes.skintools.launcher.Launcher
import com.fffemotes.skintools.launcher.api.SponsoredApp
import com.fffemotes.skintools.launcher.entities.GadgetHub

/** Sponsored drawer tiles (settings `sponsoredApps`), one after every `every` apps. */
object PromotedTiles {

    private const val PACKAGE_PREFIX = "com.fffemotes.skintools.launcher.sponsored."

    private val tiles = HashMap<String, SponsoredApp>()

    fun isSponsored(item: GadgetHub) = item.packageName.startsWith(PACKAGE_PREFIX)

    fun logo(item: GadgetHub): String = tiles[item.packageName]?.logo.orEmpty()

    fun interleave(apps: List<GadgetHub>): List<GadgetHub> {
        val setup = Launcher.settings().sponsoredApps
        val feed = setup.list.filter { it.landingUrl.isNotBlank() }
        if (!setup.on || feed.isEmpty() || apps.isEmpty()) return apps
        tiles.clear()
        val every = setup.every.coerceAtLeast(1)
        val out = ArrayList<GadgetHub>(apps.size + feed.size)
        var fed = 0
        apps.forEachIndexed { i, app ->
            out.add(app)
            if ((i + 1) % every == 0 && fed < feed.size) out.add(marker(feed[fed], fed++))
        }
        return out
    }

    private fun marker(ad: SponsoredApp, index: Int): GadgetHub {
        tiles["$PACKAGE_PREFIX$index"] = ad // per tile: several tiles may share one landing_url
        return GadgetHub(
            id = null,
            title = ad.label,
            packageName = "$PACKAGE_PREFIX$index",
            activityName = ad.landingUrl,
            order = 0,
            thumbnailColor = 0,
            drawable = null,
        )
    }

    /** Tap: host moment first, then the landing URL; only a link that really opened counts as "away". */
    fun open(activity: Activity, item: GadgetHub) {
        val tile = tiles[item.packageName] ?: SponsoredApp(logo = "", landingUrl = item.activityName, label = item.title)
        var done = false
        val proceed = {
            if (!done) {
                done = true
                val opened = runCatching {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tile.landingUrl)))
                }.onFailure { Log.w("LAUNCHER_PROMO", "could not open sponsored link", it) }.isSuccess
                if (opened) HubOfferCoordinator.away = HubOfferCoordinator.Away.SPONSORED
            }
        }
        runCatching { Launcher.events.onSponsoredTileTapped(activity, tile, proceed) }.onFailure { proceed() }
    }
}
