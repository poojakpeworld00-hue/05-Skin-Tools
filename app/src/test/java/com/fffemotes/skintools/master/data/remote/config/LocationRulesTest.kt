package com.fffemotes.skintools.master.data.remote.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationRulesTest {
    private val surat = Location(country = "India", countryCode = "IN", state = "Gujarat", stateCode = "GJ", city = "Surat")

    @Test fun anyOfTheFiveFieldsMatches() {
        listOf("India", "IN", "Gujarat", "GJ", "Surat").forEach { assertTrue(it, LocationRules.matches(listOf(it), surat)) }
    }

    @Test fun caseAndSpacesDoNotMatter() {
        listOf("india", "InDia", " INDIA ", "gj", "surat").forEach { assertTrue(it, LocationRules.matches(listOf(it), surat)) }
    }

    @Test fun otherPlacesDoNotMatch() {
        assertFalse(LocationRules.matches(listOf("Pakistan", "MH", "Mumbai"), surat))
    }

    @Test fun emptyListOrUnknownLocationNeverMatches() {
        assertFalse(LocationRules.matches(emptyList(), surat))
        assertFalse(LocationRules.matches(listOf("India"), null))
        assertFalse(LocationRules.matches(listOf("", " "), Location()))
    }
}
