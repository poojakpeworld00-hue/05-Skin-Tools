package com.fffemotes.skintools.master.data.remote.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttributionRulesTest {
    private val spec = AttributionSpec(
        gclidMinLength = 20,
        fakeGclids = listOf("123456789"),
        googleIpCheck = true,
        ipField = "asn_org",
        ipMatch = listOf("Google LLC"),
    )
    private val realGclid = "Cj0KCQjw-test-gclid-value-1234567890"

    @Test fun organicPlayReferrer_isNotPaid() {
        assertFalse(AttributionRules.paidGclid("utm_source=google-play&utm_medium=organic", spec))
        assertFalse(AttributionRules.paidGclid("", spec))
        assertFalse(AttributionRules.paidGclid(null, spec))
    }

    @Test fun realGclid_isPaid_anyPositionAndEncoded() {
        assertTrue(AttributionRules.paidGclid("gclid=$realGclid&utm_source=google-play", spec))
        assertTrue(AttributionRules.paidGclid("utm_source=google-play&GCLID=Cj0KCQjw%2Dtest%2Dgclid%2Dvalue%2D12345", spec))
        assertEquals("Cj0KCQjw-test-gclid-value-12345", AttributionRules.gclid("a=1&gclid=Cj0KCQjw%2Dtest%2Dgclid%2Dvalue%2D12345"))
    }

    @Test fun shortOrFakeGclid_isOrganic() {
        assertFalse(AttributionRules.paidGclid("gclid=123456789", spec.copy(gclidMinLength = 1))) // fake signature
        assertFalse(AttributionRules.paidGclid("gclid=short", spec))
        assertNull(AttributionRules.gclid("gclid=&utm_medium=cpc"))
        assertFalse(AttributionRules.paidGclid("utm_medium=cpc", spec)) // like LightHouse, only a gclid counts
    }

    @Test fun googleNetwork_matchesField() {
        assertTrue(AttributionRules.googleNetwork("""{"ip":"1.2.3.4","asn_org":"Google LLC"}""", spec))
        assertTrue(AttributionRules.googleNetwork("""{"asn_org":" google llc "}""", spec))
        assertFalse(AttributionRules.googleNetwork("""{"asn_org":"Reliance Jio"}""", spec))
        assertFalse(AttributionRules.googleNetwork("not json", spec))
        assertFalse(AttributionRules.googleNetwork("""{"asn_org":""}""", spec.copy(ipMatch = listOf(""))))
    }

    @Test fun installSourceReason() {
        assertEquals("no_referrer", AttributionRules.reason("", spec, false))
        assertEquals("no_gclid", AttributionRules.reason("utm_source=google-play&utm_medium=organic", spec, false))
        assertEquals("fake_gclid", AttributionRules.reason("gclid=123456789", spec, false))
        assertEquals("google_ip", AttributionRules.reason("gclid=$realGclid", spec, true))
        assertEquals("gclid", AttributionRules.reason("gclid=$realGclid", spec, false))
    }
}
