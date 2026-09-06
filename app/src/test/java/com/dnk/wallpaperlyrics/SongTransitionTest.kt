package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongTransitionTest {

    @Test
    fun `phaseAt 0 and 999 are HOLD_OLD`() {
        assertEquals(SongTransition.Phase.HOLD_OLD, SongTransition.phaseAt(0L))
        assertEquals(SongTransition.Phase.HOLD_OLD, SongTransition.phaseAt(500L))
        assertEquals(SongTransition.Phase.HOLD_OLD, SongTransition.phaseAt(999L))
    }

    @Test
    fun `phaseAt 1000 and 1499 are SWAP`() {
        assertEquals(SongTransition.Phase.SWAP, SongTransition.phaseAt(1000L))
        assertEquals(SongTransition.Phase.SWAP, SongTransition.phaseAt(1250L))
        assertEquals(SongTransition.Phase.SWAP, SongTransition.phaseAt(1499L))
    }

    @Test
    fun `phaseAt 1500 and 2499 are HOLD_NEW`() {
        assertEquals(SongTransition.Phase.HOLD_NEW, SongTransition.phaseAt(1500L))
        assertEquals(SongTransition.Phase.HOLD_NEW, SongTransition.phaseAt(2000L))
        assertEquals(SongTransition.Phase.HOLD_NEW, SongTransition.phaseAt(2499L))
    }

    @Test
    fun `phaseAt 2500 and beyond are NONE`() {
        assertEquals(SongTransition.Phase.NONE, SongTransition.phaseAt(2500L))
        assertEquals(SongTransition.Phase.NONE, SongTransition.phaseAt(2501L))
        assertEquals(SongTransition.Phase.NONE, SongTransition.phaseAt(10000L))
    }

    @Test
    fun `phaseAt negative is NONE`() {
        assertEquals(SongTransition.Phase.NONE, SongTransition.phaseAt(-1L))
        assertEquals(SongTransition.Phase.NONE, SongTransition.phaseAt(-1000L))
    }

    @Test
    fun `isActive is true across 0 until TOTAL_MS and false at TOTAL_MS and beyond`() {
        assertFalse(SongTransition.isActive(-1L))
        assertTrue(SongTransition.isActive(0L))
        assertTrue(SongTransition.isActive(999L))
        assertTrue(SongTransition.isActive(1000L))
        assertTrue(SongTransition.isActive(1499L))
        assertTrue(SongTransition.isActive(1500L))
        assertTrue(SongTransition.isActive(2499L))
        assertFalse(SongTransition.isActive(2500L))
        assertFalse(SongTransition.isActive(2501L))
        assertFalse(SongTransition.isActive(5000L))
    }

    @Test
    fun `shouldCommit is false before HOLD_OLD_MS and true from HOLD_OLD_MS onward`() {
        assertFalse(SongTransition.shouldCommit(-1L))
        assertFalse(SongTransition.shouldCommit(0L))
        assertFalse(SongTransition.shouldCommit(999L))
        assertTrue(SongTransition.shouldCommit(1000L))
        assertTrue(SongTransition.shouldCommit(1001L))
        assertTrue(SongTransition.shouldCommit(1500L))
        assertTrue(SongTransition.shouldCommit(2500L))
    }

    @Test
    fun `TOTAL_MS equals the sum of the three phase constants`() {
        val expected = SongTransition.HOLD_OLD_MS + SongTransition.SWAP_MS + SongTransition.HOLD_NEW_MS
        assertEquals(expected, SongTransition.TOTAL_MS)
    }
}
