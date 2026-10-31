package com.fffemotes.skintools.launcher.persona

import android.content.Context
import com.fffemotes.skintools.launcher.R
import com.fffemotes.skintools.launcher.Launcher
import timber.log.Timber

object LobbyPersonaLocator {
    // Served from app_config / app_config_debug -> launcher.home_profile_rules / home_profile_probes.
    const val RC_RULES_KEY = "home_profile_rules"
    const val RC_PROBES_KEY = "home_profile_probes"

    fun resolve(context: Context, fp: HubStamp): LobbyPersona? {
        val remote = LobbyPersonaPolicies.parse(Launcher.settings().homeProfileRulesJson, PersonaBase.RC_RULE)
        if (remote?.disabled == true) {
            Timber.i("HubPersona: disabled by $RC_RULES_KEY")
            return null
        }
        val base = remote?.select(fp)
            ?: LobbyPersonaPolicies.builtIn.select(fp)
            ?: LobbyPersona.AOSP
        val probes = Launcher.settings().homeProfileProbesJson.ifBlank { ApkPersonaDecoder.DEFAULT_PROBES_JSON }
        val facts = ApkPersonaDecoder.read(context, fp, probes)
        val widthDp = fp.widthDp.takeIf { it > 0 }
            ?: (context.resources.displayMetrics.let { minOf(it.widthPixels, it.heightPixels) / it.density }).toInt()
        val profile = (facts?.numbers?.let { base.overlay(it, ownGlyphDp = { cols -> ownGlyphDp(context, fp, cols) }) } ?: base)
            .settleFractions(widthDp, fp.densityDpi.takeIf { it > 0 } ?: context.resources.displayMetrics.densityDpi)
        Timber.i(
            "HubPersona: ${fp.homePackage.ifEmpty { "<none>" }} on ${fp.manufacturer}/${fp.model} sw${fp.swDp} " +
                "-> ${profile.id} [apk readers: ${facts?.sources?.joinToString(",").orEmpty().ifEmpty { "none" }}] ${profile.describeSources()}"
        )
        return profile
    }

    private fun ownGlyphDp(context: Context, fp: HubStamp, cols: Int): Float {
        if (cols <= 0) return 0f
        val widthDp = fp.widthDp.takeIf { it > 0 }
            ?: (context.resources.displayMetrics.let { minOf(it.widthPixels, it.heightPixels) / it.density }).toInt()
        val density = context.resources.displayMetrics.density
        val marginDp = context.resources.getDimension(R.dimen.launcher_icon_side_margin) / density

        val sideDp = 2 * context.resources.getDimension(org.fossify.commons.R.dimen.normal_margin) / density
        val cell = (widthDp - sideDp) / cols
        return cell - 2 * (marginDp * 5 / cols)
    }
}
