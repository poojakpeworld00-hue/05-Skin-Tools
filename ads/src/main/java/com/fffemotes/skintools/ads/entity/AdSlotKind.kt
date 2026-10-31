package com.fffemotes.skintools.ads.entity

/** Ad network of the engine (vault `IsAdType`). Facebook Audience Network is not part of this module. */
enum class AdSlotKind {
    GOOGLE,
    CUSTOM,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): AdSlotKind = when (value?.lowercase()) {
            "google" -> GOOGLE
            "custom" -> CUSTOM
            else -> UNKNOWN
        }
    }
}
