package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionSettleTest {

    @Test
    fun `shouldSettle returns true when screen is off and false when screen is on`() {
        assertTrue(TransitionSettle.shouldSettle(isScreenOff = true))
        assertFalse(TransitionSettle.shouldSettle(isScreenOff = false))
    }

    @Test
    fun `progressAt returns 1_0f for any elapsed time when screen is off`() {
        assertEquals(1.0f, TransitionSettle.progressAt(-100L, isScreenOff = true), 0.0001f)
        assertEquals(1.0f, TransitionSettle.progressAt(0L, isScreenOff = true), 0.0001f)
        assertEquals(1.0f, TransitionSettle.progressAt(250L, isScreenOff = true), 0.0001f)
        assertEquals(1.0f, TransitionSettle.progressAt(500L, isScreenOff = true), 0.0001f)
        assertEquals(1.0f, TransitionSettle.progressAt(1000L, isScreenOff = true), 0.0001f)
    }

    @Test
    fun `progressAt respects CardFade timing when screen is on`() {
        assertEquals(0.0f, TransitionSettle.progressAt(-50L, isScreenOff = false), 0.0001f)
        assertEquals(0.0f, TransitionSettle.progressAt(0L, isScreenOff = false), 0.0001f)
        assertEquals(0.5f, TransitionSettle.progressAt(250L, isScreenOff = false), 0.0001f)
        assertEquals(1.0f, TransitionSettle.progressAt(500L, isScreenOff = false), 0.0001f)
        assertEquals(1.0f, TransitionSettle.progressAt(800L, isScreenOff = false), 0.0001f)
    }

    @Test
    fun `shouldFadeCard returns false when screen is off even with different art`() {
        assertFalse(TransitionSettle.shouldFadeCard("artA", "artB", isScreenOff = true))
        assertFalse(TransitionSettle.shouldFadeCard(null, "artB", isScreenOff = true))
    }

    @Test
    fun `shouldFadeCard delegates to CardFade when screen is on`() {
        assertTrue(TransitionSettle.shouldFadeCard("artA", "artB", isScreenOff = false))
        assertTrue(TransitionSettle.shouldFadeCard(null, "artB", isScreenOff = false))
        assertFalse(TransitionSettle.shouldFadeCard("artA", "artA", isScreenOff = false))
        assertFalse(TransitionSettle.shouldFadeCard(null, null, isScreenOff = false))
    }
}
