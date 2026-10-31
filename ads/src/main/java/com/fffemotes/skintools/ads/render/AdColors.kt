package com.fffemotes.skintools.ads.render

/**
 * Ad colours (pure, no Android). RC `ad_colors` decides, exactly like the reference app and our previous code;
 * the previous version's defaults (white card, black CTA, black text, white CTA text) apply only when RC has none.
 */
object AdColors {
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()

    /** `#RRGGBB` or `#AARRGGBB`; anything else → null. */
    fun parse(value: String?): Int? {
        val v = value?.trim()?.removePrefix("#") ?: return null
        if (v.length != 6 && v.length != 8) return null
        val n = v.toLongOrNull(16) ?: return null
        return if (v.length == 6) (0xFF000000L or n).toInt() else n.toInt()
    }

    fun card(configured: String?): Int = parse(configured) ?: WHITE
    fun cta(configured: String?): Int = parse(configured) ?: BLACK

    /** [card] is kept for callers; the configured colour is never overridden. */
    @Suppress("UNUSED_PARAMETER")
    fun text(configured: String?, card: Int): Int = parse(configured) ?: BLACK

    @Suppress("UNUSED_PARAMETER")
    fun ctaText(configured: String?, cta: Int): Int = parse(configured) ?: WHITE
}
