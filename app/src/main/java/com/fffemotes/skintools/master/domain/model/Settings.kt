package com.fffemotes.skintools.master.domain.model

import androidx.annotation.StringRes

/** A supported app language: [tag] is its BCP 47 code, [glyph] its short onboarding mark, [nativeName] its own spelling. */
data class Language(val tag: String, val flag: String, val glyph: String, @StringRes val name: Int, @StringRes val nativeName: Int)
