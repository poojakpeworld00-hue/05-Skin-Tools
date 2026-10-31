package com.fffemotes.skintools.master.presentation.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class AdRowPositionsTest {
    @Test
    fun `every 6 repeats and never ends the list with an ad`() {
        assertEquals(listOf(6, 13), adRowPositions(tiles = 14, after = 6, every = 6)) // 6 tiles, ad, 6 tiles, ad, 2 tiles
        assertEquals(listOf(6), adRowPositions(tiles = 12, after = 6, every = 6))
    }

    @Test
    fun `once, at the top, or after a short list`() {
        assertEquals(listOf(2), adRowPositions(tiles = 30, after = 2, every = 0))
        assertEquals(listOf(0), adRowPositions(tiles = 5, after = 0, every = 0))
        assertEquals(listOf(4), adRowPositions(tiles = 4, after = 6, every = 6))
    }
}
