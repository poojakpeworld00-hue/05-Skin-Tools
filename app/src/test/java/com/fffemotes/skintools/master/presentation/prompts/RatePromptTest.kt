package com.fffemotes.skintools.master.presentation.prompts

import com.fffemotes.skintools.master.data.remote.config.Prompts
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RatePromptTest {
    private val prompts = Prompts(rateAfterOpens = 3, rateRetryAfterHours = 24)
    private val hour = 3_600_000L

    @Test
    fun `asks only after enough opens`() {
        assertFalse(RatePrompt.due(opens = 2, askedAtMs = 0, nowMs = 100 * hour, prompts = prompts))
        assertTrue(RatePrompt.due(opens = 3, askedAtMs = 0, nowMs = 100 * hour, prompts = prompts))
    }

    @Test
    fun `waits retry hours after the last ask`() {
        assertFalse(RatePrompt.due(opens = 9, askedAtMs = 90 * hour, nowMs = 100 * hour, prompts = prompts))
        assertTrue(RatePrompt.due(opens = 9, askedAtMs = 76 * hour, nowMs = 100 * hour, prompts = prompts))
    }
}
