package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardFadeTest {

    @Test
    fun `shouldFade returns false when both art references are null`() {
        assertFalse(CardFade.shouldFade(null, null))
    }

    @Test
    fun `shouldFade returns false when incoming art is identical reference`() {
        val dummy = Any()
        assertFalse(CardFade.shouldFade(dummy, dummy))
    }

    @Test
    fun `shouldFade returns true when art arrives for empty card`() {
        val incoming = Any()
        assertTrue(CardFade.shouldFade(null, incoming))
    }

    @Test
    fun `shouldFade returns true when incoming art is different reference`() {
        val current = Any()
        val incoming = Any()
        assertTrue(CardFade.shouldFade(current, incoming))
    }

    @Test
    fun `progressAt computes correct progress across duration`() {
        assertEquals(0.0f, CardFade.progressAt(-50L), 0.0001f)
        assertEquals(0.0f, CardFade.progressAt(0L), 0.0001f)
        assertEquals(0.5f, CardFade.progressAt(250L), 0.0001f)
        assertEquals(1.0f, CardFade.progressAt(500L), 0.0001f)
        assertEquals(1.0f, CardFade.progressAt(800L), 0.0001f)
    }


    @Test
    fun `isComplete checks if transition is finished`() {
        assertFalse(CardFade.isComplete(0.0f))
        assertFalse(CardFade.isComplete(0.99f))
        assertTrue(CardFade.isComplete(1.0f))
        assertTrue(CardFade.isComplete(1.1f))
    }

    @Test
    fun `outgoingAlpha returns fully opaque when rectangles match`() {
        assertEquals(1.0f, CardFade.outgoingAlpha(0.0f, rectanglesMatch = true), 0.0001f)
        assertEquals(1.0f, CardFade.outgoingAlpha(0.5f, rectanglesMatch = true), 0.0001f)
        assertEquals(1.0f, CardFade.outgoingAlpha(1.0f, rectanglesMatch = true), 0.0001f)
        assertEquals(255, CardFade.outgoingAlphaInt(0.0f, rectanglesMatch = true))
        assertEquals(255, CardFade.outgoingAlphaInt(0.5f, rectanglesMatch = true))
        assertEquals(255, CardFade.outgoingAlphaInt(1.0f, rectanglesMatch = true))
    }

    @Test
    fun `outgoingAlpha fades out when rectangles differ`() {
        assertEquals(1.0f, CardFade.outgoingAlpha(0.0f, rectanglesMatch = false), 0.0001f)
        assertEquals(0.5f, CardFade.outgoingAlpha(0.5f, rectanglesMatch = false), 0.0001f)
        assertEquals(0.0f, CardFade.outgoingAlpha(1.0f, rectanglesMatch = false), 0.0001f)
        assertEquals(255, CardFade.outgoingAlphaInt(0.0f, rectanglesMatch = false))
        assertEquals(127, CardFade.outgoingAlphaInt(0.5f, rectanglesMatch = false))
        assertEquals(0, CardFade.outgoingAlphaInt(1.0f, rectanglesMatch = false))
    }

    @Test
    fun `incomingAlpha computes correct opacities`() {
        assertEquals(0.0f, CardFade.incomingAlpha(0.0f), 0.0001f)
        assertEquals(0.5f, CardFade.incomingAlpha(0.5f), 0.0001f)
        assertEquals(1.0f, CardFade.incomingAlpha(1.0f), 0.0001f)
        assertEquals(0, CardFade.incomingAlphaInt(0.0f))
        assertEquals(127, CardFade.incomingAlphaInt(0.5f))
        assertEquals(255, CardFade.incomingAlphaInt(1.0f))
    }
}
