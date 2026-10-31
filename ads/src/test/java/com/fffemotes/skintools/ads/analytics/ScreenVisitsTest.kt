package com.fffemotes.skintools.ads.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenVisitsTest {
    private var now = 1_000_000L
    private val sent = mutableListOf<Map<String, Any?>>()
    private val visits = ScreenVisits(clock = { now }, uptime = { now }) { sent += it.toMap() }

    @Test fun oneEventPerVisit_withEnterExitAndDuration() {
        visits.show("splash")
        now += 4_200
        visits.exitBy("next")
        visits.show("language")
        assertEquals(1, sent.size)
        assertEquals(mapOf("screen" to "splash", "enter_time" to 1_000_000L, "exit_time" to 1_004_200L, "duration_sec" to 4L, "exit_by" to "next"), sent[0])
    }

    @Test fun sameScreenAgain_isNotANewVisit_defaultExitIsButton() {
        visits.show("home"); visits.show("home")
        now += 1_000
        visits.show("setting")
        assertEquals(1, sent.size)
        assertEquals("button", sent[0]["exit_by"])
    }

    @Test fun leavingTheApp_endsVisit_returnStartsNewOne() {
        visits.show("home")
        now += 2_000
        visits.exitBy("home")
        visits.leaveApp()
        assertEquals("home", sent.last()["exit_by"])
        now += 60_000
        visits.returnToApp()
        now += 5_000
        visits.leaveApp()
        assertEquals(listOf("home", "background"), sent.map { it["exit_by"] })
        assertEquals(5L, sent.last()["duration_sec"])
        assertEquals("home", visits.current)
    }

    @Test fun stalePendingReason_isIgnored() {
        visits.show("home")
        visits.exitBy("back")
        now += 10_000
        visits.show("setting")
        assertEquals("button", sent[0]["exit_by"])
    }

    @Test fun screenChangeWhileAway_startsOnlyWhenBack() {
        visits.show("app_drawer")
        visits.leaveApp() // launched another app from the drawer
        visits.show("launcher") // drawer closes behind it
        now += 30_000 // user in the other app
        visits.returnToApp()
        now += 2_000
        visits.show("app_drawer")
        assertEquals(listOf("app_drawer", "launcher"), sent.map { it["screen"] })
        assertEquals(2L, sent.last()["duration_sec"])
    }
}
