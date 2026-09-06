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
        val pMid = SyllableAnimator.getEasedProgress(0.5f, "yeah")
        assertEquals(0.7117718f, pMid, 0.0001f)

        val info = SyllableAnimator.getSyllableInfo("chandelier")
        assertEquals(3, info.syllableCount)

        assertEquals(0f, SyllableAnimator.getEasedProgress(0f, "chandelier"), 0.0001f)
        assertEquals(1f, SyllableAnimator.getEasedProgress(1f, "chandelier"), 0.0001f)

        val p1 = SyllableAnimator.getEasedProgress(0.1f, "chandelier")
        val p2 = SyllableAnimator.getEasedProgress(0.5f, "chandelier")
        val p3 = SyllableAnimator.getEasedProgress(0.9f, "chandelier")

        assertTrue(p1 > 0f)
        assertTrue(p2 > p1)
        assertTrue(p3 > p2)
        assertTrue(p3 < 1f)
    }

    @Test
    fun easeSyllableOutClampsAtEndpointsAndHandlesOutOfRangeInputs() {
        assertEquals(0f, SyllableAnimator.easeSyllableOut(0f), 0.0001f)
        assertEquals(1f, SyllableAnimator.easeSyllableOut(1f), 0.0001f)
        assertEquals(0f, SyllableAnimator.easeSyllableOut(-0.5f), 0.0001f)
        assertEquals(1f, SyllableAnimator.easeSyllableOut(1.5f), 0.0001f)
    }

    @Test
    fun easeSyllableOutIsStrictlyIncreasingAcrossFineSweep() {
        var prev = SyllableAnimator.easeSyllableOut(0f)
        val steps = 1000
        for (i in 1..steps) {
            val u = i.toFloat() / steps.toFloat()
            val current = SyllableAnimator.easeSyllableOut(u)
            assertTrue("Expected current ($current) > prev ($prev) at u=$u", current > prev)
            prev = current
        }
    }

    @Test
    fun easeSyllableOutIsDeceleratingAndAboveLinearInOpenInterval() {
        val steps = 100
        for (i in 1 until steps) {
            val u = i.toFloat() / steps.toFloat()
            val eased = SyllableAnimator.easeSyllableOut(u)
            assertTrue("Expected eased ($eased) > linear ($u) at u=$u", eased > u)
        }
    }

    @Test
    fun easeSyllableOutVelocityIsStrictlyDecreasingAcrossUnitInterval() {
        val steps = 100
        var prevDiff = Float.MAX_VALUE
        for (i in 1..steps) {
            val u0 = (i - 1).toFloat() / steps.toFloat()
            val u1 = i.toFloat() / steps.toFloat()
            val diff = SyllableAnimator.easeSyllableOut(u1) - SyllableAnimator.easeSyllableOut(u0)
            assertTrue("Expected diff ($diff) < prevDiff ($prevDiff) at step $i", diff < prevDiff)
            prevDiff = diff
        }
    }

    @Test
    fun easeSyllableOutTerminalVelocityIsNonZero() {
        val steps = 1000
        val u0 = (steps - 1).toFloat() / steps.toFloat()
        val u1 = 1f
        val finalDiff = SyllableAnimator.easeSyllableOut(u1) - SyllableAnimator.easeSyllableOut(u0)
        assertTrue("Terminal velocity must be non-zero", finalDiff > 0.0001f)
    }

    @Test
    fun getEasedProgressIsGloballyMonotoneNonDecreasingAcrossFineSweep() {
        val words = listOf("yeah", "chandelier")
        val steps = 1000
        for (word in words) {
            var prev = SyllableAnimator.getEasedProgress(0f, word)
            for (i in 1..steps) {
                val p = i.toFloat() / steps.toFloat()
                val current = SyllableAnimator.getEasedProgress(p, word)
                assertTrue("Expected current ($current) >= prev ($prev) for word '$word' at p=$p", current >= prev)
                prev = current
            }
        }
    }

    @Test
    fun getEasedProgressLandsOnBoundsAtInternalSyllableBoundaries() {
        val word = "chandelier"
        val info = SyllableAnimator.getSyllableInfo(word)
        val n = info.syllableCount
        val r = info.bounds
        for (i in 1 until n) {
            val tBoundary = 0.7f * (i.toFloat() / n) + 0.3f * r[i]
            val eased = SyllableAnimator.getEasedProgress(tBoundary, word)
            assertEquals("Progress at syllable boundary $i must equal character bound", r[i], eased, 0.0001f)
        }
    }

    @Test
    fun getEasedProgressHandlesBlankPunctuationAndSingleSyllableWords() {
        assertEquals(0f, SyllableAnimator.getEasedProgress(0f, ""), 0.0001f)
        assertEquals(0.7117718f, SyllableAnimator.getEasedProgress(0.5f, ""), 0.0001f)
        assertEquals(1f, SyllableAnimator.getEasedProgress(1f, ""), 0.0001f)

        assertEquals(0f, SyllableAnimator.getEasedProgress(0f, "♪"), 0.0001f)
        assertEquals(0.7117718f, SyllableAnimator.getEasedProgress(0.5f, "♪"), 0.0001f)
        assertEquals(1f, SyllableAnimator.getEasedProgress(1f, "♪"), 0.0001f)

        assertEquals(0f, SyllableAnimator.getEasedProgress(0f, "the"), 0.0001f)
        assertEquals(0.7117718f, SyllableAnimator.getEasedProgress(0.5f, "the"), 0.0001f)
        assertEquals(1f, SyllableAnimator.getEasedProgress(1f, "the"), 0.0001f)
    }

    @Test
    fun wordMotionScaleRestsAtEndpointsAndPeaksAtMidpoint() {
        assertEquals(1f, SyllableAnimator.getWordMotionScale(0f), 0.0001f)
        assertEquals(1.04f, SyllableAnimator.getWordMotionScale(0.5f), 0.0001f)
        assertEquals(1f, SyllableAnimator.getWordMotionScale(1f), 0.0001f)
    }

    @Test
    fun wordMotionLiftIsBoundedAndReturnsToBaseline() {
        val textSize = 100f
        assertEquals(0f, SyllableAnimator.getLetterLift(0f, 0, 3, textSize), 0.0001f)
        assertEquals(0f, SyllableAnimator.getLetterLift(1f, 2, 3, textSize), 0.0001f)
        assertTrue(SyllableAnimator.getLetterLift(0.25f, 0, 3, textSize) in 0f..6f)
    }

    @Test
    fun wordMotionLiftPeaksInCodePointOrder() {
        val textSize = 100f
        val firstLift = SyllableAnimator.getLetterLift(0.2f, 0, 3, textSize)
        val secondLift = SyllableAnimator.getLetterLift(0.2f, 1, 3, textSize)
        val thirdLift = SyllableAnimator.getLetterLift(0.2f, 2, 3, textSize)

        assertTrue(firstLift > secondLift)
        assertEquals(0f, thirdLift, 0.0001f)
    }

    @Test
    fun perLetterMotionRequiresAtLeast150MillisecondsPerCodePoint() {
        assertTrue(!SyllableAnimator.usesPerLetterMotion(299L, 2))
        assertTrue(SyllableAnimator.usesPerLetterMotion(300L, 2))
        assertTrue(SyllableAnimator.usesPerLetterMotion(750L, 5))
        assertTrue(SyllableAnimator.usesPerLetterMotion(751L, 5))
        assertTrue(SyllableAnimator.usesPerLetterMotion(1500L, 10))
    }

    @Test
    fun perLetterMotionUsesWholeWordForInvalidCodePointCounts() {
        assertTrue(!SyllableAnimator.usesPerLetterMotion(10_000L, 0))
    }

    @Test
    fun wholeWordLiftUsesTheSamePeakAndEndpointEnvelope() {
        assertEquals(0f, SyllableAnimator.getWholeWordLift(0f, 100f), 0.0001f)
        assertEquals(6f, SyllableAnimator.getWholeWordLift(0.5f, 100f), 0.0001f)
        assertEquals(0f, SyllableAnimator.getWholeWordLift(1f, 100f), 0.0001f)
    }

    @Test
    fun extendedWordEndExtendsByOverlapForStandardWordWithDistantLineEnd() {
        val extended = SyllableAnimator.getExtendedWordEnd(1000L, 1300L, 5000L)
        assertEquals(1350L, extended)
    }

    @Test
    fun extendedWordEndAppliesMinimumAnimationDurationFloorOnShortWord() {
        val extended = SyllableAnimator.getExtendedWordEnd(1000L, 1100L, 5000L)
        assertEquals(1200L, extended)
    }

    @Test
    fun extendedWordEndBindsLineEndClampWhenWordEndsAtLineEnd() {
        val extended = SyllableAnimator.getExtendedWordEnd(1000L, 1300L, 1300L)
        assertEquals(1300L, extended)
    }

    @Test
    fun extendedWordEndBindsPartialClampWhenLineEndFallsWithinExtension() {
        val extended = SyllableAnimator.getExtendedWordEnd(1000L, 1300L, 1320L)
        assertEquals(1320L, extended)
    }

    @Test
    fun extendedWordEndReturnsFloorOrOriginalEndForDegenerateZeroLengthOrInvertedWindow() {
        assertEquals(1200L, SyllableAnimator.getExtendedWordEnd(1000L, 1000L, 2000L))
        assertEquals(1400L, SyllableAnimator.getExtendedWordEnd(1200L, 1000L, 2000L))
        assertEquals(1300L, SyllableAnimator.getExtendedWordEnd(1000L, 1300L, 1200L))
    }

    @Test
    fun extendedWordEndAppliesFloorToMedianLengthWord() {
        assertEquals(1200L, SyllableAnimator.getExtendedWordEnd(1000L, 1090L, 5000L))
    }

    @Test
    fun extendedWordEndAppliesFloorToTenthPercentileWord() {
        assertEquals(1200L, SyllableAnimator.getExtendedWordEnd(1000L, 1030L, 5000L))
    }

    @Test
    fun extendedWordEndAppliesFloorAtBoundary() {
        assertEquals(1200L, SyllableAnimator.getExtendedWordEnd(1000L, 1150L, 5000L))
    }

    @Test
    fun extendedWordEndAppliesOverlapJustPastFloorBoundary() {
        assertEquals(1210L, SyllableAnimator.getExtendedWordEnd(1000L, 1160L, 5000L))
    }

    @Test
    fun testWordMotionSpanLayerBoundsCalculation() {
        val bounds = WordMotionSpan.computeLayerBoundsValues(
            x = 100f,
            top = 50,
            bottom = 150,
            measuredAdvance = 200,
            textSize = 96f
        )
        // horizPad = 200 * 0.02f + 96f * 0.10f = 4f + 9.6f = 13.6f
        // topPad = 96f * 0.15f = 14.4f
        // bottomPad = 96f * 0.08f = 7.68f
        assertEquals(86.4f, bounds[0], 0.001f) // left = 100 - 13.6
        assertEquals(35.6f, bounds[1], 0.001f) // top = 50 - 14.4
        assertEquals(313.6f, bounds[2], 0.001f) // right = 100 + 200 + 13.6
        assertEquals(157.68f, bounds[3], 0.001f) // bottom = 150 + 7.68
    }

    @Test
    fun testWordMotionSpanLayerBoundsZeroAdvance() {
        val bounds = WordMotionSpan.computeLayerBoundsValues(
            x = 0f,
            top = 0,
            bottom = 100,
            measuredAdvance = 0,
            textSize = 96f
        )
        // horizPad = 0 + 9.6f = 9.6f
        // topPad = 14.4f
        // bottomPad = 7.68f
        assertEquals(-9.6f, bounds[0], 0.001f)
        assertEquals(-14.4f, bounds[1], 0.001f)
        assertEquals(9.6f, bounds[2], 0.001f)
        assertEquals(107.68f, bounds[3], 0.001f)
    }

    @Test
    fun easeOutGlideRestAtEndpoints() {
        assertEquals(0f, SyllableAnimator.easeOutGlide(0f), 0.0001f)
        assertEquals(1f, SyllableAnimator.easeOutGlide(1f), 0.0001f)
        assertEquals(0f, SyllableAnimator.easeOutGlide(-0.5f), 0.0001f)
        assertEquals(1f, SyllableAnimator.easeOutGlide(1.5f), 0.0001f)
    }

    @Test
    fun easeOutGlideIsMonotonicallyIncreasingAcrossSweep() {
        var prev = SyllableAnimator.easeOutGlide(0f)
        val steps = 100
        for (i in 1..steps) {
            val p = i.toFloat() / steps.toFloat()
            val current = SyllableAnimator.easeOutGlide(p)
            assertTrue("Expected current ($current) > prev ($prev) at p=$p", current > prev)
            prev = current
        }
    }

    @Test
    fun easeOutGlideFrontLoadsLikeAnEaseOut() {
        val mid = SyllableAnimator.easeOutGlide(0.5f)
        assertTrue(mid > 0.5f)
        assertEquals(1f - Math.pow(0.5, 1.5).toFloat(), mid, 0.0001f)
    }

    @Test
    fun glideDurationMsReturnsBaseDurationForReferenceDistance() {
        assertEquals(200f, SyllableAnimator.glideDurationMs(158f), 0.0001f)
    }

    @Test
    fun glideDurationMsClampsToMaxCapForLongDistance() {
        assertEquals(320f, SyllableAnimator.glideDurationMs(488f), 0.0001f)
        assertEquals(320f, SyllableAnimator.glideDurationMs(422f), 0.0001f)
        assertEquals(320f, SyllableAnimator.glideDurationMs(1000f), 0.0001f)
    }

    @Test
    fun glideDurationMsScalesSmoothlyForIntermediateDistance() {
        val duration = SyllableAnimator.glideDurationMs(290f)
        assertTrue(duration > 200f)
        assertTrue(duration < 320f)
        val expected = 200f * Math.sqrt((290.0 / 158.0)).toFloat()
        assertEquals(expected, duration, 0.001f)
    }

    @Test
    fun glideDurationMsHandlesZeroNegativeAndNaNInputsSafely() {
        assertEquals(200f, SyllableAnimator.glideDurationMs(0f), 0.0001f)
        assertEquals(200f, SyllableAnimator.glideDurationMs(-100f), 0.0001f)
        assertEquals(200f, SyllableAnimator.glideDurationMs(Float.NaN), 0.0001f)
    }
}
