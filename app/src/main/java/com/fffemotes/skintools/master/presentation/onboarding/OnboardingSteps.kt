package com.fffemotes.skintools.master.presentation.onboarding

import android.content.Context
import android.widget.ImageView.ScaleType
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.fffemotes.skintools.master.R
import com.fffemotes.skintools.master.domain.model.Language
import com.intuit.sdp.R as SdpR

/** One selectable answer: a row shows [glyph] (or [image] as an avatar) in its icon circle; a card shows [image] with [glyph] as watermark. */
data class Opt(
    @StringRes val name: Int,
    val sub: (Context) -> String = { "" },
    @DrawableRes val image: Int = 0,
    val glyph: String = "",
)

data class Step(
    /** Stable id (OB_1…) used by Remote Config (`onboarding.order`, ad place `onboarding.<key>`) and saved answers. */
    val key: String,
    @StringRes val title: Int,
    @StringRes val sub: Int,
    /** Short name shown under the step counter. */
    @StringRes val name: Int,
    val opts: List<Opt>,
    /** Image cards instead of icon rows. */
    val cards: Boolean = false,
    val columns: Int = 1,
    val multi: Boolean = false,
    @DrawableRes val banner: Int = 0,
    /** Card image-well height. */
    @DimenRes val imageHeight: Int = SdpR.dimen._66sdp,
    val scale: ScaleType = ScaleType.CENTER_CROP,
    val centered: Boolean = false,
    val defaultSel: Set<Int> = setOf(0),
)

private fun s(@StringRes id: Int): (Context) -> String = { it.getString(id) }
private fun map(@StringRes kind: Int, players: Int): (Context) -> String =
    { it.getString(R.string.map_players, it.getString(kind), players) }

/** Age-step portraits: game characters whose in-game age falls in each band (17, 20, 25, 35, 45). */
private val AGE_AVATARS = listOf(R.drawable.av_age1, R.drawable.av_age2, R.drawable.av_age3, R.drawable.av_age4, R.drawable.av_age5)

const val LANG_KEY = "OB_1"
const val FAV_KEY = "OB_5"
const val DEFAULT_HOME_KEY = "default_home"
const val WELCOME_KEY = "welcome"
const val PERMISSION_HUB_KEY = "permission_hub"

/** Steps whose rows are information, not answers: a tap does what the button does. */
val INFO_STEPS = setOf(DEFAULT_HOME_KEY, WELCOME_KEY, PERMISSION_HUB_KEY)

/** Step 1, also reused by the Settings language screen: native name first, English-language name below. */
fun languageStep(languages: List<Language>) =
    Step(
        LANG_KEY, R.string.lang_title, R.string.lang_sub, R.string.step_lang,
        languages.map { l ->
            Opt(l.nativeName, { c -> c.getString(l.name).takeIf { it != c.getString(l.nativeName) }.orEmpty() }, glyph = l.glyph)
        },
    )

/** Every onboarding step (the 13 FFF questions + the launcher's set-as-default-home step); Remote Config picks and orders them. */
fun onboardingSteps(languages: List<Language>): List<Step> =
    listOf(languageStep(languages)) + QUESTION_STEPS + listOf(WELCOME_STEP, PERMISSION_HUB_STEP, DEFAULT_HOME_STEP)

private val WELCOME_STEP = Step(
    WELCOME_KEY, R.string.wl_title, R.string.wl_sub, R.string.step_welcome, defaultSel = emptySet(),
    opts = listOf(Opt(R.string.wl_c1, glyph = "★"), Opt(R.string.wl_c2, glyph = "◆"), Opt(R.string.wl_c3, glyph = "◈")),
)

private val PERMISSION_HUB_STEP = Step(
    PERMISSION_HUB_KEY, R.string.ph_title, R.string.ph_sub, R.string.step_permissions, defaultSel = emptySet(),
    opts = listOf(Opt(R.string.ph_notif, { it.getString(R.string.ph_notif_sub) }, glyph = "✦")),
)

/** Set as default home: three numbered instructions (not answers), then the system "Default home app" chooser. */
private val DEFAULT_HOME_STEP = Step(
    DEFAULT_HOME_KEY, R.string.dh_title, R.string.dh_sub, R.string.step_default_home, defaultSel = emptySet(),
    opts = listOf(Opt(R.string.dh_c1, glyph = "1"), Opt(R.string.dh_c2, glyph = "2"), Opt(R.string.dh_c3, glyph = "3")),
)

private val QUESTION_STEPS = listOf(
    Step(
        "OB_2", R.string.gender_title, R.string.gender_sub, R.string.step_gender,
        opts = listOf(
            Opt(R.string.gender_male, image = R.drawable.av_male),
            Opt(R.string.gender_female, image = R.drawable.av_female),
            Opt(R.string.gender_other, image = R.drawable.av_other),
        ),
    ),
    Step(
        "OB_3", R.string.age_title, R.string.age_sub, R.string.step_age, columns = 2, defaultSel = setOf(1),
        opts = listOf(
            R.string.age_under_18 to R.string.age_teen, R.string.age_18_24 to R.string.age_young_adult,
            R.string.age_25_34 to R.string.age_adult, R.string.age_35_44 to R.string.age_professional,
            R.string.age_45 to R.string.age_mature,
        ).zip(AGE_AVATARS) { (n, sub), avatar -> Opt(n, s(sub), avatar) },
    ),
    Step(
        "OB_4", R.string.style_title, R.string.style_sub, R.string.step_style, cards = true, columns = 2, imageHeight = SdpR.dimen._65sdp,
        opts = listOf(
            Opt(R.string.style_stealth, s(R.string.style_stealth_sub), R.drawable.gc1, "S"),
            Opt(R.string.style_rush, s(R.string.style_rush_sub), R.drawable.gc2, "R"),
            Opt(R.string.style_street, s(R.string.style_street_sub), R.drawable.gc3, "St"),
            Opt(R.string.style_tactical, s(R.string.style_tactical_sub), R.drawable.gc4, "T"),
            Opt(R.string.style_survivor, s(R.string.style_survivor_sub), R.drawable.gc5, "Su"),
            Opt(R.string.style_chill, s(R.string.style_chill_sub), R.drawable.gc6, "C"),
        ),
    ),
    Step(
        FAV_KEY, R.string.fav_title, R.string.fav_sub, R.string.step_fav, cards = true, columns = 2, multi = true,
        imageHeight = SdpR.dimen._53sdp, scale = ScaleType.FIT_CENTER,
        opts = listOf(
            Opt(R.string.cat_characters, s(R.string.cat_characters_sub), R.drawable.fc_characters, "C"),
            Opt(R.string.cat_bundles, s(R.string.cat_bundles_sub), R.drawable.fc_bundles2, "B"),
            Opt(R.string.cat_weapons, s(R.string.cat_weapons_sub), R.drawable.fc_weapons2, "W"),
            Opt(R.string.cat_pets, s(R.string.cat_pets_sub), R.drawable.fc_pets2, "P"),
            Opt(R.string.cat_emotes, s(R.string.cat_emotes_sub), R.drawable.fc_emotes, "E"),
            Opt(R.string.cat_vehicles, s(R.string.cat_vehicles_sub), R.drawable.fc_vehicles, "V"),
        ),
    ),
    Step(
        "OB_6", R.string.q_mode_title, R.string.q_mode_sub, R.string.step_mode, cards = true, columns = 2,
        banner = R.drawable.q_mode, imageHeight = SdpR.dimen._77sdp,
        opts = listOf(
            Opt(R.string.mode_br, image = R.drawable.gm1, glyph = "BR"),
            Opt(R.string.mode_br_ranked, image = R.drawable.gm2, glyph = "R"),
            Opt(R.string.mode_cs, image = R.drawable.gm3, glyph = "CS"),
            Opt(R.string.mode_cs_ranked, image = R.drawable.gm4, glyph = "R"),
        ),
    ),
    Step(
        "OB_7", R.string.q_weapon_title, R.string.q_weapon_sub, R.string.step_weapon, cards = true, columns = 2,
        banner = R.drawable.q_weapon, imageHeight = SdpR.dimen._58sdp, scale = ScaleType.FIT_CENTER,
        opts = listOf(
            Opt(R.string.weapon_rifle, s(R.string.weapon_rifle_models), R.drawable.fc_weapons2, "R"),
            Opt(R.string.weapon_smg, s(R.string.weapon_smg_models), R.drawable.w_smg, "S"),
            Opt(R.string.weapon_sniper, s(R.string.weapon_sniper_models), R.drawable.w_sni, "Sn"),
            Opt(R.string.weapon_shotgun, s(R.string.weapon_shotgun_models), R.drawable.w_sg, "Sg"),
        ),
    ),
    Step(
        "OB_8", R.string.q_goal_title, R.string.q_goal_sub, R.string.step_goal, banner = R.drawable.q_goal,
        opts = listOf(
            Opt(R.string.goal_headshot, glyph = "◎"),
            Opt(R.string.goal_movement, glyph = "↗"),
            Opt(R.string.goal_diamonds, glyph = "◆"),
            Opt(R.string.goal_rank, glyph = "▲"),
        ),
    ),
    Step(
        "OB_9", R.string.q_char_title, R.string.q_char_sub, R.string.step_char, cards = true, columns = 2, centered = true,
        banner = R.drawable.q_char, imageHeight = SdpR.dimen._93sdp, scale = ScaleType.FIT_END,
        opts = listOf(
            Opt(R.string.char_alok, image = R.drawable.ch_alok, glyph = "A"),
            Opt(R.string.char_chrono, image = R.drawable.ch_chrono, glyph = "C"),
            Opt(R.string.char_k, image = R.drawable.ch_k, glyph = "K"),
            Opt(R.string.char_kelly, image = R.drawable.ch_kelly, glyph = "Ke"),
        ),
    ),
    Step(
        "OB_10", R.string.q_guide_title, R.string.q_guide_sub, R.string.step_tips, banner = R.drawable.q_guide,
        opts = listOf(
            Opt(R.string.guide_pro, glyph = "★"),
            Opt(R.string.guide_settings, glyph = "⚙"),
            Opt(R.string.guide_maps, glyph = "◈"),
            Opt(R.string.guide_events, glyph = "✦"),
        ),
    ),
    Step(
        "OB_11", R.string.q_room_title, R.string.q_room_sub, R.string.step_room, banner = R.drawable.q_room,
        opts = listOf(
            Opt(R.string.room_love, glyph = "♥"),
            Opt(R.string.room_sometimes, glyph = "~"),
            Opt(R.string.room_no, glyph = "✕"),
        ),
    ),
    Step(
        "OB_12", R.string.q_map_title, R.string.q_map_sub, R.string.step_map, cards = true, columns = 2,
        banner = R.drawable.q_map, imageHeight = SdpR.dimen._72sdp,
        opts = listOf(
            Opt(R.string.map_bermuda, map(R.string.map_classic, 52), R.drawable.map_bermuda, "B"),
            Opt(R.string.map_purgatory, map(R.string.map_strategy, 48), R.drawable.map_purgatory, "P"),
            Opt(R.string.map_kalahari, map(R.string.map_desert, 40), R.drawable.map_kalahari, "K"),
            Opt(R.string.map_alpine, map(R.string.map_ranked, 50), R.drawable.map_alpine, "A"),
        ),
    ),
    Step(
        "OB_13", R.string.q_daily_title, R.string.q_daily_sub, R.string.step_daily, banner = R.drawable.q_tips,
        opts = listOf(
            Opt(R.string.daily_yes, glyph = "✓"),
            Opt(R.string.daily_later, glyph = "…"),
            Opt(R.string.daily_no, glyph = "✕"),
        ),
    ),
)
