package com.fffemotes.skintools.ads.host

import com.fffemotes.skintools.ads.config.AdStep
import org.junit.Assert.assertEquals
import org.junit.Test

class AdEventsTest {
    @Test
    fun errorCode3IsNoFillOthersFailed() {
        assertEquals("no_fill", AdEvents.failStep(3))
        assertEquals("failed", AdEvents.failStep(0))
        assertEquals("failed", AdEvents.failStep(2))
    }

    @Test
    fun engineLabelsMapToFirebaseAdFormat() {
        mapOf(
            "banner" to "banner",
            "native_big" to "native", "native_medium2" to "native", "native_full_unit" to "native", "native_strip" to "native",
            "inter_chain" to "interstitial", "inter_navigation" to "interstitial",
            "rewarded" to "rewarded", "rewarded_fallback" to "rewarded",
            "app_open" to "app_open", "app_open_resume" to "app_open",
        ).forEach { (label, format) -> assertEquals(label, format, AdEvents.standardFormat(label)) }
    }

    @Test
    fun exitStepsMapToAdFormat() {
        assertEquals("inter", AdEvents.formatOf(AdStep.INTER))
        assertEquals("rewarded", AdEvents.formatOf(AdStep.REWARDED))
        assertEquals("app_open", AdEvents.formatOf(AdStep.APP_OPEN))
        assertEquals("native", AdEvents.formatOf(AdStep.FULL_NATIVE))
        assertEquals("custom", AdEvents.formatOf(AdStep.CUSTOM))
    }
}
