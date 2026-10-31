package com.fffemotes.skintools.master.core.crash

import com.fffemotes.skintools.master.core.crash.CrashLoopGuard.Action.GIVE_UP
import com.fffemotes.skintools.master.core.crash.CrashLoopGuard.Action.RESTART
import com.fffemotes.skintools.master.core.crash.CrashLoopGuard.Action.SAFE_SCREEN
import org.junit.Assert.assertEquals
import org.junit.Test

class CrashLoopGuardTest {
    private val guard = CrashLoopGuard(windowMs = 60_000, safeAt = 3, giveUpAt = 5)

    @Test
    fun `restart, then safe screen, then give up within the window`() {
        var history = emptyList<Long>()
        val actions = (1..5).map { i -> guard.onCrash(history, i * 1_000L).also { history = it.second }.first }
        assertEquals(listOf(RESTART, RESTART, SAFE_SCREEN, SAFE_SCREEN, GIVE_UP), actions)
    }

    @Test
    fun `crashes older than the window are forgotten`() {
        assertEquals(SAFE_SCREEN, guard.onCrash(listOf(0L, 1_000L), 60_000).first)
        assertEquals(RESTART to listOf(130_000L), guard.onCrash(listOf(0L, 1_000L, 60_000L), 130_000))
    }
}
