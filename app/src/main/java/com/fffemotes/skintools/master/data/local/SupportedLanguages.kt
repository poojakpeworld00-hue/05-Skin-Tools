package com.fffemotes.skintools.master.data.local

import com.fffemotes.skintools.master.R
import com.fffemotes.skintools.master.domain.model.Language

/** Languages the app is translated into; keep in sync with res/xml/locales_config.xml. */
val SUPPORTED_LANGUAGES = listOf(
    Language("en", "🇬🇧", "Aa", R.string.lang_en, R.string.lang_en),
    Language("hi", "🇮🇳", "अ", R.string.lang_hi, R.string.lang_hi_native),
    Language("id", "🇮🇩", "Id", R.string.lang_id, R.string.lang_id_native),
    Language("tr", "🇹🇷", "Tr", R.string.lang_tr, R.string.lang_tr_native),
    Language("vi", "🇻🇳", "Vi", R.string.lang_vi, R.string.lang_vi_native),
    Language("ar", "🇸🇦", "ع", R.string.lang_ar, R.string.lang_ar_native),
    Language("fr", "🇫🇷", "Fr", R.string.lang_fr, R.string.lang_fr_native),
    Language("de", "🇩🇪", "De", R.string.lang_de, R.string.lang_de_native),
    Language("ja", "🇯🇵", "日", R.string.lang_ja, R.string.lang_ja_native),
)
