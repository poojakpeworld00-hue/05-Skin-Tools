package com.fffemotes.skintools.ads.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsRulesTest {
    @Test fun elevenFixedEvents_allValidNames() {
        assertEquals(12, Analytics.EVENTS.size)
        assertTrue(Analytics.EVENTS.all(EventRules::validName))
    }

    @Test fun clean_dropsNullsAndBadKeys_cutsText_widensNumbers() {
        val out = EventRules.clean(listOf("screen" to "home", "value" to null, "Bad-Key" to "x", "n" to 3, "f" to 1.5f, "t" to "a".repeat(150)))
        assertEquals(listOf("screen" to "home", "n" to 3L, "f" to 1.5, "t" to "a".repeat(100)), out)
        assertFalse(EventRules.validName("x".repeat(41)))
    }
}
