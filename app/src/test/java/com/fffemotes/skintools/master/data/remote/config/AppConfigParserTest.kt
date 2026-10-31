package com.fffemotes.skintools.master.data.remote.config

import com.fffemotes.skintools.ads.config.AdStep
import com.fffemotes.skintools.ads.config.SlotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppConfigParserTest {
    // Unit tests run with the module directory as working directory.
    private fun doc(name: String) = File("../docs/remote-config/$name.json").readText()

    @Test
    fun `release template ships with ads off and no unit ids`() {
        val c = AppConfigParser.parse(doc("app_config"), Track.A, "")
        assertFalse(c.ads.on)
        assertEquals("", c.ads.units.inter)
    }

    @Test
    fun `debug template parses every FFF place`() {
        val c = AppConfigParser.parse(doc("app_config_debug"), Track.A, "")
        assertFalse(c.ads.on) // organic track: ads off, as the reference config
        assertEquals(14, c.onboarding.order.size) // OB_1…OB_13 + default_home
        assertEquals(OnboardingScreen.LANGUAGE, c.onboarding.order.first())
        assertTrue(c.launcher.on)
        assertEquals(listOf(AdStep.INTER), c.ads.places["screens.splash"]!!.exitAd!!.ads)
        assertEquals(SlotType.NATIVE_BIG, c.ads.places["onboarding.OB_1"]!!.bottomAd!!.type)
        assertFalse(c.ads.places["onboarding.OB_1"]!!.listAd!!.on)
        assertEquals(SlotType.NATIVE_BIG, c.ads.places["onboarding.OB_2"]!!.listAd!!.type)
        assertEquals(listOf(AdStep.LINK, AdStep.INTER, AdStep.APP_OPEN), c.ads.places["onboarding.OB_4"]!!.exitAd!!.ads)
        assertEquals(SlotType.BANNER, c.ads.places["screens.home"]!!.bottomAd!!.type)
        assertEquals(SlotType.NATIVE_BANNER, c.ads.places["screens.home"]!!.bottomAd!!.onFail)
        assertEquals(null, c.ads.places["screens.home"]!!.listAd!!.onFail)
        assertEquals(SlotType.NATIVE_MID, c.ads.places["screens.home"]!!.listAd!!.type)
        assertEquals("after_tiles", c.ads.places["screens.home"]!!.options["list_ad_position"])
        assertEquals(SlotType.NATIVE_BIG, c.ads.places["launcher.host_app_panel"]!!.listAd!!.type)
        assertTrue(c.ads.places.containsKey("launcher.back_button"))
        assertEquals(30, c.launcher.sponsoredApps.list.size)
    }

    @Test
    fun `missing keys fall back to defaults`() {
        val c = AppConfigParser.parse("{}", Track.A, "IN")
        assertFalse(c.ads.on)
        assertEquals(13, c.onboarding.order.size) // every FFF step, no default_home
        assertFalse(c.launcher.on)
        assertFalse(c.onboarding.skipButton)
        assertEquals(4, c.base.splashWaitSec)
        assertTrue(c.base.noInternetDialog)
        assertTrue(c.ads.places.isEmpty())
    }

    @Test
    fun `settings rows default on and turn off per track`() {
        assertEquals(SettingsRows(), AppConfigParser.parse("{}", Track.A, "").settingsRows)
        val json = """{"track_a": {}, "track_b": {"settings": {"share_app": false, "update_app": false}}}"""
        assertEquals(SettingsRows(), AppConfigParser.parse(json, Track.A, "").settingsRows)
        assertEquals(SettingsRows(share = false, update = false), AppConfigParser.parse(json, Track.B, "").settingsRows)
    }

    @Test
    fun `paid track sets the launcher first, as the reference`() {
        val c = AppConfigParser.parse(doc("app_config_debug"), Track.B, "")
        assertEquals(OnboardingScreen.DEFAULT_HOME, c.onboarding.order.first())
        assertTrue(c.uninstallFlow.premium)
        assertTrue(c.ads.on)
        assertFalse(c.ads.loaderOnMiss)
        assertEquals(true, c.ads.places["onboarding.default_home"]!!.options["loader_on_miss"])
        assertEquals(listOf(AdStep.LINK, AdStep.INTER, AdStep.APP_OPEN), c.ads.places["onboarding.OB_6"]!!.exitAd!!.ads)
        assertFalse(c.ads.places["launcher.app_resume"]!!.exitAd!!.on) // reference: paid has no resume ad
        assertTrue(c.launcher.sponsoredApps.on)
        assertEquals(6, c.ads.places["screens.list_characters"]!!.int("list_ad_after"))
        assertEquals(6, c.ads.places["screens.list_characters"]!!.int("list_ad_every"))
        assertEquals(2, c.ads.places["screens.list_emotes"]!!.int("list_ad_after"))
        assertEquals(SlotType.NATIVE_MID, c.ads.places["screens.list_maps"]!!.listAd!!.type)
        assertEquals(SlotType.NATIVE_BIG, c.ads.places["screens.calc"]!!.listAd!!.type)
        assertEquals(SlotType.NATIVE_BIG, c.ads.places["screens.settings"]!!.listAd!!.type)
    }

    @Test
    fun `country overlay merges into the track`() {
        val json = """{"track_a":{"ads":{"on":false},"countries":{"IN,PK":{"ads":{"on":true}}}}}"""
        assertTrue(AppConfigParser.parse(json, Track.A, "pk").ads.on)
        assertFalse(AppConfigParser.parse(json, Track.A, "US").ads.on)
    }

    @Test(expected = org.json.JSONException::class)
    fun `invalid json is rejected`() {
        AppConfigParser.parse("not json", Track.A, "")
    }
}
