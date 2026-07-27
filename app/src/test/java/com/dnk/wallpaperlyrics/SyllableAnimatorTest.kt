package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyllableAnimatorTest {

    @Test
    fun testSyllableCounting() {
        // Single syllable words
        assertEquals(1, SyllableAnimator.getSyllableInfo("yeah").syllableCount)
        assertEquals(1, SyllableAnimator.getSyllableInfo("what").syllableCount)
        assertEquals(1, SyllableAnimator.getSyllableInfo("now").syllableCount)
        assertEquals(1, SyllableAnimator.getSyllableInfo("when").syllableCount)
        assertEquals(1, SyllableAnimator.getSyllableInfo("the").syllableCount)
        assertEquals(1, SyllableAnimator.getSyllableInfo("please").syllableCount)

        // Multi syllable words
        assertEquals(3, SyllableAnimator.getSyllableInfo("chandelier").syllableCount)
        assertEquals(2, SyllableAnimator.getSyllableInfo("lyrics").syllableCount)
        assertEquals(2, SyllableAnimator.getSyllableInfo("guitar").syllableCount)
        assertEquals(2, SyllableAnimator.getSyllableInfo("singing").syllableCount)
        assertEquals(3, SyllableAnimator.getSyllableInfo("beautiful").syllableCount)
    }

    @Test
    fun testPunctuationMapping() {
        val info1 = SyllableAnimator.getSyllableInfo("yeah!")
        assertEquals(1, info1.syllableCount)
        assertEquals(0f, info1.bounds[0], 0.0001f)
        assertEquals(1f, info1.bounds[1], 0.0001f)

        val info2 = SyllableAnimator.getSyllableInfo("(chandelier)")
        assertEquals(3, info2.syllableCount)
        assertEquals(0f, info2.bounds[0], 0.0001f)
        // clean word is "chandelier" (length 10). Split indices are 4 and 6.
        // original word is "(chandelier)" (length 12), first letter is at index 1.
        // mapped splits are at 1 + 4 = 5 and 1 + 6 = 7.
        // relative bounds should be 5/12 and 7/12.
        assertEquals(5f / 12f, info2.bounds[1], 0.0001f)
        assertEquals(7f / 12f, info2.bounds[2], 0.0001f)
        assertEquals(1f, info2.bounds[3], 0.0001f)
    }

    @Test
    fun testEasedProgress() {
        // Test single syllable easeSyllable
        val pMid = SyllableAnimator.getEasedProgress(0.5f, "yeah")
        // easeSyllable(0.5) = 0.5
        assertEquals(0.5f, pMid, 0.0001f)

        // Test multi syllable steps
        val info = SyllableAnimator.getSyllableInfo("chandelier")
        assertEquals(3, info.syllableCount)

        // Eased progress should start at 0f, end at 1f
        assertEquals(0f, SyllableAnimator.getEasedProgress(0f, "chandelier"), 0.0001f)
        assertEquals(1f, SyllableAnimator.getEasedProgress(1f, "chandelier"), 0.0001f)

        // Progress at intermediate steps
        val p1 = SyllableAnimator.getEasedProgress(0.1f, "chandelier")
        val p2 = SyllableAnimator.getEasedProgress(0.5f, "chandelier")
        val p3 = SyllableAnimator.getEasedProgress(0.9f, "chandelier")

        assertTrue(p1 > 0f)
        assertTrue(p2 > p1)
        assertTrue(p3 > p2)
        assertTrue(p3 < 1f)
    }
}
