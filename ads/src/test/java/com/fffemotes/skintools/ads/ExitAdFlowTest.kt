package com.fffemotes.skintools.ads

import com.fffemotes.skintools.ads.config.AdNetwork
import com.fffemotes.skintools.ads.config.AdStep
import com.fffemotes.skintools.ads.config.AdsConfig
import com.fffemotes.skintools.ads.config.CustomAdCard
import com.fffemotes.skintools.ads.config.CustomAds
import com.fffemotes.skintools.ads.config.ExitAd
import com.fffemotes.skintools.ads.config.FallbackStep
import com.fffemotes.skintools.ads.config.FormatsOn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExitAdFlowTest {

    /** Synchronous fake: steps in [ready] show, the rest are not ready. Records every call in order. */
    private class FakeSource(private val ready: Set<AdStep>) : ExitAdSource {
        val calls = mutableListOf<String>()
        override fun show(step: AdStep, onShown: () -> Unit, onNotReady: () -> Unit) {
            calls += "show:${step.key}"
            if (step in ready) onShown() else onNotReady()
        }

        override fun openLinks(urls: List<String>, onBack: () -> Unit) {
            calls += "links:${urls.joinToString(",")}"
            onBack()
        }
    }

    private fun run(config: AdsConfig, exit: ExitAd, source: ExitAdSource, alive: () -> Boolean = { true }): Int {
        var done = 0
        ExitAdFlow(config, exit, source, alive) { done++ }.start()
        return done
    }

    private val on = AdsConfig(on = true)

    // ---- linksFor --------------------------------------------------------------------------------------

    private val four = listOf("u0", "u1", "u2", "u3")

    @Test fun linksFor_count2_opensFirstTwo() {
        assertEquals(listOf("u0", "u1"), AdsConfig(links = four, linksCount = 2).linksFor(ExitAd(on = true)))
    }

    @Test fun linksFor_count6_repeatsFromIndex0() {
        assertEquals(
            listOf("u0", "u1", "u2", "u3", "u0", "u1"),
            AdsConfig(links = four, linksCount = 6).linksFor(ExitAd(on = true)),
        )
    }

    @Test fun linksFor_blockLinksAndCountOverride() {
        val config = AdsConfig(links = four, linksCount = 2)
        assertEquals(listOf("b", "b", "b"), config.linksFor(ExitAd(on = true, links = listOf("b"), linksCount = 3)))
        assertEquals(emptyList<String>(), config.linksFor(ExitAd(on = true, linksCount = 0)))
        assertEquals(listOf("b"), config.linksFor(ExitAd(on = true, links = listOf("b"))).take(1))
    }

    // ---- every / pass-through -------------------------------------------------------------------------

    @Test fun every2_runsOn2ndAnd4thPass_perPlace() {
        val counter = EveryCounter()
        assertEquals(listOf(false, true, false, true), List(4) { counter.pass("screens.home", 2) })
        assertTrue(counter.pass("screens.settings", 1))
        assertEquals(listOf(false, false, true), List(3) { counter.pass("onboarding.language", 3) })
    }

    @Test fun exitRuns_passThroughWhenAdsOffOrPlaceMissingOrOff_withoutCounting() {
        val counter = EveryCounter()
        val exit = ExitAd(on = true, ads = listOf(AdStep.INTER), every = 2)
        assertFalse(PlaceRules.exitRuns(adsOn = false, exit = exit, every = counter, placeKey = "p"))
        assertFalse(PlaceRules.exitRuns(adsOn = true, exit = null, every = counter, placeKey = "p"))
        assertFalse(PlaceRules.exitRuns(adsOn = true, exit = exit.copy(on = false), every = counter, placeKey = "p"))
        // None of the above counted: the 2nd real pass is the one that runs.
        assertFalse(PlaceRules.exitRuns(true, exit, counter, "p"))
        assertTrue(PlaceRules.exitRuns(true, exit, counter, "p"))
    }

    @Test fun adsOff_orBlockOff_doneOnce_noCalls() {
        val source = FakeSource(AdStep.entries.toSet())
        assertEquals(1, run(AdsConfig(on = false), ExitAd(on = true, ads = listOf(AdStep.INTER)), source))
        assertEquals(1, run(on, ExitAd(on = false, ads = listOf(AdStep.INTER)), source))
        assertEquals(emptyList<String>(), source.calls)
    }

    // ---- links, ads, fallback -------------------------------------------------------------------------

    @Test fun linksOpenTogetherBeforeAds() {
        val source = FakeSource(setOf(AdStep.INTER))
        val config = on.copy(links = four, linksCount = 3)
        assertEquals(1, run(config, ExitAd(on = true, ads = listOf(AdStep.INTER)), source))
        assertEquals(listOf("links:u0,u1,u2", "show:inter"), source.calls)
    }

    @Test fun linkStep_opensLinksAtItsPosition_once() {
        val source = FakeSource(setOf(AdStep.INTER, AdStep.REWARDED))
        val config = on.copy(links = four, linksCount = 2)
        val exit = ExitAd(on = true, ads = listOf(AdStep.INTER, AdStep.LINK, AdStep.REWARDED))
        assertEquals(1, run(config, exit, source))
        assertEquals(listOf("show:inter", "links:u0,u1", "show:rewarded"), source.calls)
    }

    @Test fun linkStep_afterShownAd_stillOpensWhenShowAllFalse() {
        val source = FakeSource(setOf(AdStep.REWARDED, AdStep.INTER))
        val exit = ExitAd(on = true, ads = listOf(AdStep.REWARDED, AdStep.INTER, AdStep.LINK), showAll = false)
        assertEquals(1, run(on.copy(links = four, linksCount = 1), exit, source))
        assertEquals(listOf("show:rewarded", "links:u0"), source.calls)
    }

    @Test fun linkStep_withCount0_isSkipped() {
        val source = FakeSource(setOf(AdStep.INTER))
        val exit = ExitAd(on = true, ads = listOf(AdStep.LINK, AdStep.INTER), linksCount = 0)
        assertEquals(1, run(on.copy(links = four, linksCount = 3), exit, source))
        assertEquals(listOf("show:inter"), source.calls)
    }

    @Test fun fallback_runsInOrder_firstShownEndsIt() {
        val source = FakeSource(setOf(AdStep.APP_OPEN))
        val config = on.copy(
            ifAdNotReady = listOf(FallbackStep.Ad(AdStep.REWARDED), FallbackStep.Ad(AdStep.APP_OPEN), FallbackStep.FirstLink),
        )
        assertEquals(1, run(config, ExitAd(on = true, ads = listOf(AdStep.INTER)), source))
        assertEquals(listOf("show:inter", "show:rewarded", "show:app_open"), source.calls)
    }

    @Test fun fallback_firstLink_usesBlockLinksElseAdsLinks() {
        val config = on.copy(links = four, ifAdNotReady = listOf(FallbackStep.Ad(AdStep.REWARDED), FallbackStep.FirstLink))
        val a = FakeSource(emptySet())
        run(config, ExitAd(on = true, ads = listOf(AdStep.INTER), linksCount = 0, links = listOf("own")), a)
        assertEquals(listOf("show:inter", "show:rewarded", "links:own"), a.calls)

        val b = FakeSource(emptySet())
        run(config, ExitAd(on = true, ads = listOf(AdStep.INTER)), b)
        assertEquals(listOf("show:inter", "show:rewarded", "links:u0"), b.calls)
    }

    @Test fun fallback_url_alwaysShows_soLaterItemsNeverRun() {
        val source = FakeSource(emptySet())
        val config = on.copy(ifAdNotReady = listOf(FallbackStep.Url("https://x.example"), FallbackStep.Ad(AdStep.REWARDED)))
        run(config, ExitAd(on = true, ads = listOf(AdStep.INTER)), source)
        assertEquals(listOf("show:inter", "links:https://x.example"), source.calls)
    }

    @Test fun showAllTrue_everyAdShows_falseStopsAtFirst() {
        val ads = listOf(AdStep.REWARDED, AdStep.INTER)
        val all = FakeSource(setOf(AdStep.REWARDED, AdStep.INTER))
        run(on.copy(showAll = true), ExitAd(on = true, ads = ads), all)
        assertEquals(listOf("show:rewarded", "show:inter"), all.calls)

        val first = FakeSource(setOf(AdStep.REWARDED, AdStep.INTER))
        run(on.copy(showAll = true), ExitAd(on = true, ads = ads, showAll = false), first)
        assertEquals(listOf("show:rewarded"), first.calls)
    }

    @Test fun showAllFalse_notReadyAdWithNoFallback_movesToNextAd() {
        val source = FakeSource(setOf(AdStep.INTER))
        run(on.copy(showAll = false), ExitAd(on = true, ads = listOf(AdStep.REWARDED, AdStep.INTER)), source)
        assertEquals(listOf("show:rewarded", "show:inter"), source.calls)
    }

    @Test fun formatOff_countsAsNotReady_withoutAskingTheEngine() {
        val source = FakeSource(AdStep.entries.toSet())
        val config = on.copy(formatsOn = FormatsOn(inter = false), ifAdNotReady = listOf(FallbackStep.Ad(AdStep.REWARDED)))
        run(config, ExitAd(on = true, ads = listOf(AdStep.INTER)), source)
        assertEquals(listOf("show:rewarded"), source.calls)
    }

    @Test fun networkCustom_onlyCustomStepShows() {
        val source = FakeSource(AdStep.entries.toSet())
        val config = on.copy(
            network = AdNetwork.CUSTOM,
            customAds = CustomAds(on = true, list = listOf(CustomAdCard(title = "t"))),
        )
        run(config, ExitAd(on = true, ads = listOf(AdStep.INTER, AdStep.CUSTOM)), source)
        assertEquals(listOf("show:custom"), source.calls)
    }

    @Test fun doneExactlyOnce_whenSourceCallsBothCallbacks() {
        val twice = object : ExitAdSource {
            override fun show(step: AdStep, onShown: () -> Unit, onNotReady: () -> Unit) {
                onShown(); onNotReady(); onShown()
            }

            override fun openLinks(urls: List<String>, onBack: () -> Unit) = onBack()
        }
        assertEquals(1, run(on, ExitAd(on = true, ads = listOf(AdStep.INTER, AdStep.REWARDED)), twice))
    }

    @Test fun activityGone_endsFlowOnce() {
        val source = FakeSource(setOf(AdStep.INTER, AdStep.REWARDED))
        var alive = true
        val counting = object : ExitAdSource by source {
            override fun show(step: AdStep, onShown: () -> Unit, onNotReady: () -> Unit) {
                source.show(step, { alive = false; onShown() }, onNotReady)
            }
        }
        assertEquals(1, run(on, ExitAd(on = true, ads = listOf(AdStep.INTER, AdStep.REWARDED)), counting) { alive })
        assertEquals(listOf("show:inter"), source.calls)
    }

    // ---- place counters -------------------------------------------------------------------------------

    @Test fun gapPassed() {
        assertTrue(PlaceRules.gapPassed(lastRunMs = 0, gapSec = 120, nowMs = 1_000))
        assertTrue(PlaceRules.gapPassed(lastRunMs = 5_000, gapSec = 0, nowMs = 5_001))
        assertFalse(PlaceRules.gapPassed(lastRunMs = 10_000, gapSec = 120, nowMs = 129_999))
        assertTrue(PlaceRules.gapPassed(lastRunMs = 10_000, gapSec = 120, nowMs = 130_000))
    }

    @Test fun unlockLimits() {
        val hour = 3_600_000L
        val install = 0L
        fun allowed(now: Long, last: Long = 0, today: Int = 0) =
            PlaceRules.unlockAllowed(now, install, startAfterHours = 24, gapMinutes = 30, lastRunMs = last, todayCount = today, maxPerDay = 5)
        assertFalse(allowed(now = 23 * hour)) // before start_after_hours
        assertTrue(allowed(now = 24 * hour))
        assertFalse(allowed(now = 25 * hour, last = 25 * hour - 29 * 60_000L)) // inside gap_minutes
        assertTrue(allowed(now = 25 * hour, last = 25 * hour - 30 * 60_000L))
        assertFalse(allowed(now = 25 * hour, today = 5)) // max_per_day reached
        assertFalse(PlaceRules.unlockAllowed(25 * hour, install, 24, 30, 0, 0, maxPerDay = 0))
    }

    @Test fun countToday_resetsOnNewDay() {
        assertEquals(3, PlaceRules.countToday("20261027", 3, "20261027"))
        assertEquals(0, PlaceRules.countToday("20261026", 3, "20261027"))
        assertEquals(0, PlaceRules.countToday(null, 3, "20261027"))
    }

    @Test fun playStoreWindow() {
        assertTrue(PlaceRules.withinWindow(nowMs = 6_000_000, installMs = 0, windowSec = 6000))
        assertFalse(PlaceRules.withinWindow(nowMs = 6_000_001, installMs = 0, windowSec = 6000))
    }
}
