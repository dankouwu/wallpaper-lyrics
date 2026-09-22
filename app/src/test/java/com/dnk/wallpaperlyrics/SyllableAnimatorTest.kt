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
    fun scaleCurveMatchesSwellAndSettleSpecification() {
        assertEquals(0.95f, SyllableAnimator.getWordMotionScale(0f), 0.0001f)
        assertEquals(0.98f, SyllableAnimator.getWordMotionScale(1f), 0.0001f)
        assertEquals(1.00f, SyllableAnimator.getWordMotionScale(0.60f), 0.001f)

        val peakVal = SyllableAnimator.getWordMotionScale(0.60f)
        val beforePeak = SyllableAnimator.getWordMotionScale(0.50f)
        val afterPeak = SyllableAnimator.getWordMotionScale(0.70f)
        assertTrue("Peak at 0.60 must exceed value at 0.50", peakVal > beforePeak)
        assertTrue("Peak at 0.60 must exceed value at 0.70", peakVal > afterPeak)

        for (i in 0..10000) {
            val p = i / 10000f
            val s = SyllableAnimator.getWordMotionScale(p)
            assertTrue("Scale at $p must not exceed 1.00 (was $s)", s <= 1.00001f)
        }
    }

    @Test
    fun liftCurveMatchesSpecification() {
        val textSize = 100f
        assertEquals(0f, SyllableAnimator.getWordLift(0f, textSize), 0.0001f)
        assertEquals(0f, SyllableAnimator.getWordLift(1f, textSize), 0.0001f)
        assertEquals(0.05f * textSize, SyllableAnimator.getWordLift(0.58f, textSize), 0.0001f)

        val peakVal = SyllableAnimator.getWordLift(0.58f, textSize)
        for (i in 0..10000) {
            val p = i / 10000f
            val lift = SyllableAnimator.getWordLift(p, textSize)
            assertTrue("Lift at $p must not be negative (was $lift)", lift >= -0.0001f)
            assertTrue("Lift at $p must not exceed peak (was $lift)", lift <= peakVal + 0.0001f)
        }
    }

    @Test
    fun glowCurveMatchesSpecification() {
        assertEquals(0f, SyllableAnimator.getWordGlow(0f), 0.0001f)
        assertEquals(0f, SyllableAnimator.getWordGlow(1f), 0.0001f)

        for (i in 200..550) {
            val p = i / 1000f
            assertEquals("Glow must hold 1.0 across 0.20 to 0.55 plateau at p=$p", 1.0f, SyllableAnimator.getWordGlow(p), 0.0001f)
        }

        var prevGlow = -1f
        for (i in 0..200) {
            val p = i / 1000f
            val g = SyllableAnimator.getWordGlow(p)
            assertTrue("Glow must strictly rise before plateau at p=$p ($g > $prevGlow)", g > prevGlow)
            prevGlow = g
        }

        prevGlow = 1.001f
        for (i in 551..1000) {
            val p = i / 1000f
            val g = SyllableAnimator.getWordGlow(p)
            assertTrue("Glow must strictly fall after plateau at p=$p ($g < $prevGlow)", g < prevGlow)
            prevGlow = g
        }
    }

    @Test
    fun heldGateDistinguishesSubSecondAndHeldWords() {
        assertEquals(false, SyllableAnimator.isHeldWord(574L))
        assertEquals(true, SyllableAnimator.isHeldWord(575L))
        assertEquals(false, SyllableAnimator.isHeldWord(0L))
        assertEquals(false, SyllableAnimator.isHeldWord(500L))
        assertEquals(true, SyllableAnimator.isHeldWord(1500L))
    }

    @Test
    fun activeLetterTracksProgressAcrossWordCodePoints() {
        assertEquals(0, SyllableAnimator.getActiveLetterIndex(0.0001f, 10))
        assertEquals(9, SyllableAnimator.getActiveLetterIndex(0.9999f, 10))

        var lastIdx = 0
        for (i in 0..10000) {
            val p = i / 10000f
            val idx = SyllableAnimator.getActiveLetterIndex(p, 10)
            assertTrue("Active index $idx out of range at p=$p", idx in 0..9)
            assertTrue("Active index must be non-decreasing at p=$p ($idx >= $lastIdx)", idx >= lastIdx)
            lastIdx = idx
        }

        for (i in 0..10000) {
            val p = i / 10000f
            val idx = SyllableAnimator.getActiveLetterIndex(p, 1)
            assertEquals("Single letter word must always yield index 0 at p=$p", 0, idx)
        }
    }

    @Test
    fun rippleFalloffFollowsInverseCubicDistanceCurve() {
        assertEquals(1.0f, SyllableAnimator.getRippleFalloff(0), 0.0001f)
        assertEquals(0.5f, SyllableAnimator.getRippleFalloff(1), 0.0001f)
        assertEquals(0.111f, SyllableAnimator.getRippleFalloff(2), 0.001f)
        assertEquals(0.036f, SyllableAnimator.getRippleFalloff(3), 0.001f)

        assertEquals(SyllableAnimator.getRippleFalloff(1), SyllableAnimator.getRippleFalloff(-1), 0.0001f)
        assertEquals(SyllableAnimator.getRippleFalloff(2), SyllableAnimator.getRippleFalloff(-2), 0.0001f)
        assertEquals(SyllableAnimator.getRippleFalloff(3), SyllableAnimator.getRippleFalloff(-3), 0.0001f)

        for (d in 0..10) {
            assertTrue(
                "Falloff must strictly decrease with distance ($d vs ${d + 1})",
                SyllableAnimator.getRippleFalloff(d) > SyllableAnimator.getRippleFalloff(d + 1)
            )
        }
    }

    @Test
    fun rippleShapeDistinguishesRippleFromBulgeForHeldWord() {
        val p = 0.55f
        val wordDuration = 1200L
        val codePointCount = 10
        val textSize = 100f

        val activeIndex = SyllableAnimator.getActiveLetterIndex(p, codePointCount)
        val activeLift = SyllableAnimator.getLetterLift(p, activeIndex, codePointCount, textSize, wordDuration)
        val neighbourLift = SyllableAnimator.getLetterLift(p, activeIndex + 1, codePointCount, textSize, wordDuration)
        val threeAwayLift = SyllableAnimator.getLetterLift(p, activeIndex + 3, codePointCount, textSize, wordDuration)

        assertTrue(
            "Active letter lift ($activeLift) must exceed neighbour lift ($neighbourLift)",
            activeLift > neighbourLift
        )
        assertTrue(
            "Neighbour lift ($neighbourLift) must exceed letter three away lift ($threeAwayLift)",
            neighbourLift > threeAwayLift
        )
    }

    @Test
    fun shortWordUniformityMaintainsSameLiftForAllLetters() {
        val wordDuration = 500L
        val codePointCount = 6
        val textSize = 100f

        for (i in 0..100) {
            val p = i / 100f
            val baseLift = SyllableAnimator.getLetterLift(p, 0, codePointCount, textSize, wordDuration)
            for (letter in 1 until codePointCount) {
                val letterLift = SyllableAnimator.getLetterLift(p, letter, codePointCount, textSize, wordDuration)
                assertEquals(
                    "All letters in 500ms word must share identical lift at p=$p",
                    baseLift,
                    letterLift,
                    0.0001f
                )
            }
        }
    }

    @Test
    fun endpointSafetyEnsuresUnmodifiedWordAtRest() {
        val textSize = 100f
        for (duration in listOf(200L, 500L, 1000L, 3000L)) {
            assertEquals(0f, SyllableAnimator.getWordLift(0f, textSize), 0.0001f)
            assertEquals(0f, SyllableAnimator.getWordLift(1f, textSize), 0.0001f)
            assertEquals(0.98f, SyllableAnimator.getWordMotionScale(1f), 0.0001f)

            for (codePointCount in listOf(1, 4, 10)) {
                for (letter in 0 until codePointCount) {
                    assertEquals(
                        0f,
                        SyllableAnimator.getLetterLift(0f, letter, codePointCount, textSize, duration),
                        0.0001f
                    )
                    assertEquals(
                        0f,
                        SyllableAnimator.getLetterLift(1f, letter, codePointCount, textSize, duration),
                        0.0001f
                    )
                    assertEquals(
                        0.98f,
                        SyllableAnimator.getLetterScale(1f, letter, codePointCount, duration),
                        0.0001f
                    )
                }
            }
        }
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
    fun motionWordEndExtendsBeyondSweepForStandardWordWithDistantLineEnd() {
        val motionEnd = SyllableAnimator.getMotionWordEnd(1000L, 1300L, 5000L)
        assertEquals(1480L, motionEnd)
    }

    @Test
    fun motionWordEndIsNeverBeforeSweepEndAcrossDegenerateWordShapes() {
        assertEquals(1480L, SyllableAnimator.getMotionWordEnd(1000L, 1000L, 2000L))
        assertEquals(1680L, SyllableAnimator.getMotionWordEnd(1200L, 1000L, 2000L))
        assertEquals(1300L, SyllableAnimator.getMotionWordEnd(1000L, 1300L, 1200L))
        assertTrue(SyllableAnimator.getMotionWordEnd(1000L, 1000L, 2000L) >= SyllableAnimator.getExtendedWordEnd(1000L, 1000L, 2000L))
        assertTrue(SyllableAnimator.getMotionWordEnd(1200L, 1000L, 2000L) >= SyllableAnimator.getExtendedWordEnd(1200L, 1000L, 2000L))
        assertTrue(SyllableAnimator.getMotionWordEnd(1000L, 1300L, 1200L) >= SyllableAnimator.getExtendedWordEnd(1000L, 1300L, 1200L))
    }

    @Test
    fun motionWordEndBindsLineEndClampWhenWordEndsAtLineEnd() {
        val motionEnd = SyllableAnimator.getMotionWordEnd(1000L, 1300L, 1300L)
        assertEquals(1300L, motionEnd)
    }

    @Test
    fun motionWordEndBindsPartialClampWhenLineEndFallsWithinTrail() {
        val motionEnd = SyllableAnimator.getMotionWordEnd(1000L, 1300L, 1400L)
        assertEquals(1400L, motionEnd)
    }

    @Test
    fun motionWordEndAppliesTrailOnTopOfMinimumAnimationDurationFloor() {
        val motionEnd = SyllableAnimator.getMotionWordEnd(1000L, 1100L, 5000L)
        assertEquals(1480L, motionEnd)
    }

    @Test
    fun motionWordEndBindsTrailCapForLongWord() {
        val motionEnd = SyllableAnimator.getMotionWordEnd(1000L, 2000L, 9000L)
        assertEquals(2300L, motionEnd)
    }

    @Test
    fun motionWordEndBindsTrailFloorForZeroLengthWord() {
        val motionEnd = SyllableAnimator.getMotionWordEnd(1000L, 1000L, 5000L)
        assertEquals(1480L, motionEnd)
    }

    @Test
    fun motionWordEndTrailGrowsWithWordDuration() {
        val distantLineEnd = 10000L
        val shortTrail = SyllableAnimator.getMotionWordEnd(1000L, 1300L, distantLineEnd) -
            SyllableAnimator.getExtendedWordEnd(1000L, 1300L, distantLineEnd)
        val longTrail = SyllableAnimator.getMotionWordEnd(1000L, 2000L, distantLineEnd) -
            SyllableAnimator.getExtendedWordEnd(1000L, 2000L, distantLineEnd)
        assertTrue(longTrail > shortTrail)
    }

    @Test
    fun testWordMotionSpanLayerBoundsCalculation() {
        val x = 100f
        val top = 50
        val bottom = 150
        val measuredAdvance = 200
        val textSize = 96f

        val blurRadius = WordMotionSpan.computeBlurRadius(textSize)
        val horizPad = WordMotionSpan.computeHorizontalPadding(measuredAdvance, textSize, blurRadius)
        val topPad = WordMotionSpan.computeTopPadding(textSize, blurRadius)
        val bottomPad = WordMotionSpan.computeBottomPadding(textSize, blurRadius)

        val expectedHoriz = measuredAdvance * 0.02f + 3f * blurRadius
        val expectedTop = textSize * 0.15f + 3f * blurRadius
        val expectedBottom = 3f * blurRadius

        assertEquals(expectedHoriz, horizPad, 0.001f)
        assertEquals(expectedTop, topPad, 0.001f)
        assertEquals(expectedBottom, bottomPad, 0.001f)

        assertEquals(32.8f, horizPad, 0.001f)
        assertEquals(43.2f, topPad, 0.001f)
        assertEquals(28.8f, bottomPad, 0.001f)

        assertEquals(67.2f, x - horizPad, 0.001f)
        assertEquals(6.8f, top.toFloat() - topPad, 0.001f)
        assertEquals(332.8f, x + measuredAdvance.toFloat() + horizPad, 0.001f)
        assertEquals(178.8f, bottom.toFloat() + bottomPad, 0.001f)
    }

    @Test
    fun testWordMotionSpanLayerBoundsZeroAdvance() {
        val x = 0f
        val top = 0
        val bottom = 100
        val measuredAdvance = 0
        val textSize = 96f

        val blurRadius = WordMotionSpan.computeBlurRadius(textSize)
        val horizPad = WordMotionSpan.computeHorizontalPadding(measuredAdvance, textSize, blurRadius)
        val topPad = WordMotionSpan.computeTopPadding(textSize, blurRadius)
        val bottomPad = WordMotionSpan.computeBottomPadding(textSize, blurRadius)

        val expectedHoriz = 3f * blurRadius
        val expectedTop = textSize * 0.15f + 3f * blurRadius
        val expectedBottom = 3f * blurRadius

        assertEquals(expectedHoriz, horizPad, 0.001f)
        assertEquals(expectedTop, topPad, 0.001f)
        assertEquals(expectedBottom, bottomPad, 0.001f)

        assertEquals(28.8f, horizPad, 0.001f)
        assertEquals(43.2f, topPad, 0.001f)
        assertEquals(28.8f, bottomPad, 0.001f)

        assertEquals(-28.8f, x - horizPad, 0.001f)
        assertEquals(-43.2f, top.toFloat() - topPad, 0.001f)
        assertEquals(28.8f, x + measuredAdvance.toFloat() + horizPad, 0.001f)
        assertEquals(128.8f, bottom.toFloat() + bottomPad, 0.001f)
    }

    @Test
    fun testWordMotionSpanLayerBoundsGrowWithBlurRadius() {
        val textSize = 96f
        val measuredAdvance = 200
        val baseRadius = WordMotionSpan.computeBlurRadius(textSize)
        val largerRadius = baseRadius * 2f

        val baseHorizPad = WordMotionSpan.computeHorizontalPadding(measuredAdvance, textSize, baseRadius)
        val baseTopPad = WordMotionSpan.computeTopPadding(textSize, baseRadius)
        val baseBottomPad = WordMotionSpan.computeBottomPadding(textSize, baseRadius)

        assertTrue(baseHorizPad >= 3f * baseRadius)
        assertTrue(baseTopPad >= 3f * baseRadius)
        assertTrue(baseBottomPad >= 3f * baseRadius)

        val largerHorizPad = WordMotionSpan.computeHorizontalPadding(measuredAdvance, textSize, largerRadius)
        val largerTopPad = WordMotionSpan.computeTopPadding(textSize, largerRadius)
        val largerBottomPad = WordMotionSpan.computeBottomPadding(textSize, largerRadius)

        assertTrue(largerHorizPad > baseHorizPad)
        assertTrue(largerTopPad > baseTopPad)
        assertTrue(largerBottomPad > baseBottomPad)

        val baseBoundWidth = measuredAdvance + 2f * baseHorizPad
        val largerBoundWidth = measuredAdvance + 2f * largerHorizPad
        assertTrue(largerBoundWidth > baseBoundWidth)

        val baseBoundHeight = 100f + baseTopPad + baseBottomPad
        val largerBoundHeight = 100f + largerTopPad + largerBottomPad
        assertTrue(largerBoundHeight > baseBoundHeight)

        assertEquals(largerHorizPad - baseHorizPad, 3f * (largerRadius - baseRadius), 0.001f)
        assertEquals(largerTopPad - baseTopPad, 3f * (largerRadius - baseRadius), 0.001f)
        assertEquals(largerBottomPad - baseBottomPad, 3f * (largerRadius - baseRadius), 0.001f)
    }

    @Test
    fun springPositionAtZeroDtReturnsInitialDisplacement() {
        val x0 = 100f
        val v0 = 50f
        val omega = 4.74f / 0.42f
        assertEquals(x0, SyllableAnimator.springPosition(x0, v0, omega, 0f), 0.0001f)
    }

    @Test
    fun springPositionCriticallyDampedDoesNotOvershootFromRest() {
        val omega = 4.74f / 0.42f
        val dt = 1f / 60f
        var x = 100f
        var v = 0f
        var elapsed = 0f
        while (elapsed < 0.42f) {
            val nextX = SyllableAnimator.springPosition(x, v, omega, dt)
            val nextV = SyllableAnimator.springVelocity(x, v, omega, dt)
            assertTrue("Displacement should never be negative on the way: $nextX", nextX >= 0f)
            x = nextX
            v = nextV
            elapsed += dt
        }
        assertTrue("Displacement after 0.42s must be between 0 and 5.5: $x", x in 0f..5.5f)
    }

    @Test
    fun springPositionStableUnderHugeDt() {
        val omega = 4.74f / 0.42f
        val pos = SyllableAnimator.springPosition(100f, 0f, omega, 10f)
        assertEquals(0f, pos, 0.001f)
    }

    @Test
    fun springSimulationIndependentOfFrameRate() {
        fun run(dt: Float): Float {
            val omega = 4.74f / 0.42f
            var x = 100f
            var v = 0f
            var elapsed = 0f
            while (elapsed < 0.42f) {
                val nextX = SyllableAnimator.springPosition(x, v, omega, dt)
                val nextV = SyllableAnimator.springVelocity(x, v, omega, dt)
                x = nextX
                v = nextV
                elapsed += dt
            }
            return x
        }

        val run60 = run(1f / 60f)
        val run120 = run(1f / 120f)
        val run30 = run(1f / 30f)

        assertEquals(run60, run120, 0.5f)
        assertEquals(run60, run30, 0.5f)
    }

    @Test
    fun glideDurationMsReturnsBaseDurationForReferenceDistance() {
        assertEquals(420f, SyllableAnimator.glideDurationMs(158f), 0.0001f)
    }

    @Test
    fun glideDurationMsClampsToMaxCapForLongDistance() {
        assertEquals(672f, SyllableAnimator.glideDurationMs(488f), 0.0001f)
        assertEquals(672f, SyllableAnimator.glideDurationMs(422f), 0.0001f)
        assertEquals(672f, SyllableAnimator.glideDurationMs(1000f), 0.0001f)
    }

    @Test
    fun glideDurationMsScalesSmoothlyForIntermediateDistance() {
        val duration = SyllableAnimator.glideDurationMs(290f)
        assertTrue(duration > 420f)
        assertTrue(duration < 672f)
        val expected = 420f * Math.sqrt((290.0 / 158.0)).toFloat()
        assertEquals(expected, duration, 0.001f)
    }

    @Test
    fun glideDurationMsHandlesZeroNegativeAndNaNInputsSafely() {
        assertEquals(420f, SyllableAnimator.glideDurationMs(0f), 0.0001f)
        assertEquals(420f, SyllableAnimator.glideDurationMs(-100f), 0.0001f)
        assertEquals(420f, SyllableAnimator.glideDurationMs(Float.NaN), 0.0001f)
    }

    @Test
    fun preRollHandoverAlphaHasNoStepAtFirstWordOnset() {
        val lineStart = 1000L
        val onset = 1400L
        val alphaBeforeOnset = SyllableAnimator.getPreRollInactiveAlpha(onset - 1, lineStart, onset)
        val alphaAtOnset = SyllableAnimator.getPreRollInactiveAlpha(onset, lineStart, onset)
        val step = Math.abs(alphaAtOnset - alphaBeforeOnset)
        assertTrue(
            "Alpha difference across handover must be at most 2, but was $step (before=$alphaBeforeOnset, at=$alphaAtOnset)",
            step <= 2
        )
    }

    @Test
    fun preRollInactiveAlphaIsMonotonicallyNonIncreasingAcrossSettleWindowAndReachesInactiveAlpha() {
        val lineStart = 1000L
        val onset = 1400L
        val settleDuration = SyllableAnimator.PRE_ROLL_SETTLE_MS
        var prevAlpha = SyllableAnimator.getPreRollInactiveAlpha(onset, lineStart, onset, settleDuration)
        val steps = 100
        for (i in 1..steps) {
            val t = onset + (i.toFloat() / steps.toFloat() * settleDuration).toLong()
            val currentAlpha = SyllableAnimator.getPreRollInactiveAlpha(t, lineStart, onset, settleDuration)
            assertTrue(
                "Expected currentAlpha ($currentAlpha) <= prevAlpha ($prevAlpha) at t=$t",
                currentAlpha <= prevAlpha
            )
            prevAlpha = currentAlpha
        }
        val alphaAtWindowEnd = SyllableAnimator.getPreRollInactiveAlpha(onset + settleDuration, lineStart, onset, settleDuration)
        assertEquals(INACTIVE_LYRIC_ALPHA, alphaAtWindowEnd)
    }

    @Test
    fun preRollInactiveAlphaEqualsRestingInactiveAlphaAfterSettleWindowElapsed() {
        val lineStart = 1000L
        val onset = 1400L
        val settleDuration = SyllableAnimator.PRE_ROLL_SETTLE_MS
        assertEquals(
            INACTIVE_LYRIC_ALPHA,
            SyllableAnimator.getPreRollInactiveAlpha(onset + settleDuration, lineStart, onset, settleDuration)
        )
        assertEquals(
            INACTIVE_LYRIC_ALPHA,
            SyllableAnimator.getPreRollInactiveAlpha(onset + settleDuration + 100L, lineStart, onset, settleDuration)
        )
        assertEquals(
            INACTIVE_LYRIC_ALPHA,
            SyllableAnimator.getPreRollInactiveAlpha(onset + settleDuration + 10_000L, lineStart, onset, settleDuration)
        )
    }

    @Test
    fun preRollInactiveAlphaHandlesDegenerateInputsWithoutThrowingOrOutOfRangeValues() {
        val validOnset = 1400L
        val lineStart = 1000L

        val zeroGapAlpha = SyllableAnimator.getPreRollInactiveAlpha(1000L, 1000L, 1000L)
        assertTrue("Zero-length gap alpha must be in 0..255", zeroGapAlpha in 0..255)
        assertEquals(INACTIVE_LYRIC_ALPHA, zeroGapAlpha)

        val negativeGapAlpha = SyllableAnimator.getPreRollInactiveAlpha(1000L, 1000L, 800L)
        assertTrue("Negative gap alpha must be in 0..255", negativeGapAlpha in 0..255)
        assertEquals(INACTIVE_LYRIC_ALPHA, negativeGapAlpha)

        val zeroSettleAlphaAtOnset = SyllableAnimator.getPreRollInactiveAlpha(validOnset, lineStart, validOnset, settleDurationMs = 0L)
        assertTrue("Zero-length settle alpha at onset must be in 0..255", zeroSettleAlphaAtOnset in 0..255)
        assertEquals(INACTIVE_LYRIC_ALPHA, zeroSettleAlphaAtOnset)

        val zeroSettleAlphaAfterOnset = SyllableAnimator.getPreRollInactiveAlpha(validOnset + 50L, lineStart, validOnset, settleDurationMs = 0L)
        assertTrue("Zero-length settle alpha after onset must be in 0..255", zeroSettleAlphaAfterOnset in 0..255)
        assertEquals(INACTIVE_LYRIC_ALPHA, zeroSettleAlphaAfterOnset)

        val beforeStartAlpha = SyllableAnimator.getPreRollInactiveAlpha(500L, lineStart, validOnset)
        assertTrue("Before start alpha must be in 0..255", beforeStartAlpha in 0..255)
        assertEquals(INACTIVE_LYRIC_ALPHA, beforeStartAlpha)

        val wayAfterAlpha = SyllableAnimator.getPreRollInactiveAlpha(5000L, lineStart, validOnset)
        assertTrue("Way after onset alpha must be in 0..255", wayAfterAlpha in 0..255)
        assertEquals(INACTIVE_LYRIC_ALPHA, wayAfterAlpha)
    }

    @Test
    fun testBlurredDrawCountDoesNotDependOnLetterCount() {
        assertEquals(0, WordMotionSpan.computeBlurredDrawCount(hasGlow = false, isHeld = false, codePointCount = 10))
        assertEquals(0, WordMotionSpan.computeBlurredDrawCount(hasGlow = false, isHeld = true, codePointCount = 10))

        val countSingleLetter = WordMotionSpan.computeBlurredDrawCount(hasGlow = true, isHeld = true, codePointCount = 1)
        val countTenLetters = WordMotionSpan.computeBlurredDrawCount(hasGlow = true, isHeld = true, codePointCount = 10)
        val countFiftyLetters = WordMotionSpan.computeBlurredDrawCount(hasGlow = true, isHeld = true, codePointCount = 50)
        val countNonHeld = WordMotionSpan.computeBlurredDrawCount(hasGlow = true, isHeld = false, codePointCount = 10)

        assertEquals(1, countSingleLetter)
        assertEquals(1, countTenLetters)
        assertEquals(1, countFiftyLetters)
        assertEquals(1, countNonHeld)
        assertEquals(countSingleLetter, countTenLetters)
    }

    @Test
    fun testRippleFalloffIntegerParity() {
        assertEquals(1.0f, SyllableAnimator.getRippleFalloff(0), 0.0001f)
        assertEquals(0.5f, SyllableAnimator.getRippleFalloff(1), 0.0001f)
        assertEquals(1f / 9f, SyllableAnimator.getRippleFalloff(2), 0.001f)
        assertEquals(0.111f, SyllableAnimator.getRippleFalloff(2), 0.001f)

        assertEquals(1.0f, SyllableAnimator.getRippleFalloff(0f), 0.0001f)
        assertEquals(0.5f, SyllableAnimator.getRippleFalloff(1f), 0.0001f)
        assertEquals(1f / 9f, SyllableAnimator.getRippleFalloff(2f), 0.001f)
        assertEquals(0.111f, SyllableAnimator.getRippleFalloff(2f), 0.001f)
    }

    @Test
    fun testRippleFalloffFractionalDistance() {
        val f0 = SyllableAnimator.getRippleFalloff(0f)
        val fHalf = SyllableAnimator.getRippleFalloff(0.5f)
        val f1 = SyllableAnimator.getRippleFalloff(1f)

        assertTrue("Falloff at 0.5 ($fHalf) must be strictly less than at 0 ($f0)", fHalf < f0)
        assertTrue("Falloff at 0.5 ($fHalf) must be strictly greater than at 1 ($f1)", fHalf > f1)

        for (d in listOf(0.25f, 0.5f, 1.2f, 2.7f, 4.0f)) {
            assertEquals(
                "Falloff must be symmetric for d=$d",
                SyllableAnimator.getRippleFalloff(d),
                SyllableAnimator.getRippleFalloff(-d),
                0.0001f
            )
        }

        var prev = f0
        for (i in 1..20) {
            val d = i * 0.1f
            val current = SyllableAnimator.getRippleFalloff(d)
            assertTrue("Falloff must strictly decrease as distance increases: d=$d", current < prev)
            prev = current
        }
    }

    @Test
    fun testRipplePeakAlignmentAtLetterCenters() {
        val n = 10
        val firstLetterCenterProgress = (0 + 0.5f) / n.toFloat()
        val firstLetterPos = SyllableAnimator.getActiveLetterPosition(firstLetterCenterProgress, n)
        val firstDistance = 0f - firstLetterPos
        assertEquals(0f, firstDistance, 0.0001f)
        assertEquals(1.0f, SyllableAnimator.getRippleFalloff(firstDistance), 0.0001f)

        val lastLetterCenterProgress = (n - 1 + 0.5f) / n.toFloat()
        val lastLetterPos = SyllableAnimator.getActiveLetterPosition(lastLetterCenterProgress, n)
        val lastDistance = (n - 1).toFloat() - lastLetterPos
        assertEquals(0f, lastDistance, 0.0001f)
        assertEquals(1.0f, SyllableAnimator.getRippleFalloff(lastDistance), 0.0001f)

        val midIndex = 4
        val midLetterCenterProgress = (midIndex + 0.5f) / n.toFloat()
        val midLetterPos = SyllableAnimator.getActiveLetterPosition(midLetterCenterProgress, n)
        val midDistance = midIndex.toFloat() - midLetterPos
        assertEquals(0f, midDistance, 0.0001f)
        assertEquals(1.0f, SyllableAnimator.getRippleFalloff(midDistance), 0.0001f)
    }

    @Test
    fun testContinuousRippleLiftAndFalloffSamples() {
        val n = 10
        val targetLetter = 4
        val textSize = 100f
        val durationMs = 1500L
        val samplePoints = 200

        val liftValues = mutableSetOf<Float>()
        val falloffValues = mutableSetOf<Float>()

        for (i in 1..samplePoints) {
            val p = i.toFloat() / (samplePoints + 1).toFloat()
            val lift = SyllableAnimator.getLetterLift(p, targetLetter, n, textSize, durationMs = durationMs)
            val activePos = SyllableAnimator.getActiveLetterPosition(p, n)
            val distance = targetLetter.toFloat() - activePos
            val falloff = SyllableAnimator.getRippleFalloff(distance)

            liftValues.add(lift)
            falloffValues.add(falloff)
        }

        assertTrue(
            "Expected close to 200 distinct lift values, got ${liftValues.size}",
            liftValues.size >= 195
        )
        assertTrue(
            "Expected close to 200 distinct falloff values, got ${falloffValues.size}",
            falloffValues.size >= 195
        )
    }

    @Test
    fun testMotionAmplitudeScalingAcrossDurations() {
        val amp150 = SyllableAnimator.getMotionAmplitude(150L)
        val amp800 = SyllableAnimator.getMotionAmplitude(800L)
        val amp500 = SyllableAnimator.getMotionAmplitude(500L)
        val amp100 = SyllableAnimator.getMotionAmplitude(100L)

        assertEquals(0.40f, amp150, 0.001f)
        assertEquals(0.40f, amp100, 0.001f)
        assertEquals(1.00f, amp500, 0.001f)
        assertEquals(1.00f, amp800, 0.001f)

        val textSize = 100f
        val p = 0.55f
        val lift150 = SyllableAnimator.getWordLift(p, textSize, wordDurationMs = 150L)
        val lift800 = SyllableAnimator.getWordLift(p, textSize, wordDurationMs = 800L)
        val defaultLift = SyllableAnimator.getWordLift(p, textSize)

        assertEquals(defaultLift, lift800, 0.0001f)
        assertTrue("Lift at 150ms ($lift150) must be smaller than at 800ms ($lift800)", lift150 < lift800)
        assertEquals(0.40f, lift150 / lift800, 0.001f)

        val scale150 = SyllableAnimator.getWordMotionScale(0.60f, wordDurationMs = 150L)
        val scale800 = SyllableAnimator.getWordMotionScale(0.60f, wordDurationMs = 800L)
        val defaultScale = SyllableAnimator.getWordMotionScale(0.60f)

        assertEquals(defaultScale, scale800, 0.0001f)
        val dev150 = Math.abs(scale150 - 0.98f)
        val dev800 = Math.abs(scale800 - 0.98f)
        assertTrue("Scale deviation at 150ms ($dev150) must be smaller than at 800ms ($dev800)", dev150 < dev800)
    }

    @Test
    fun testMotionAmplitudeContinuityAndMonotonicity() {
        var prevAmp = SyllableAnimator.getMotionAmplitude(150L)
        for (duration in 151L..500L) {
            val amp = SyllableAnimator.getMotionAmplitude(duration)
            assertTrue("Amplitude must increase monotonically: duration=$duration", amp >= prevAmp)
            val step = amp - prevAmp
            assertTrue("Step must be small and smooth: step=$step at duration=$duration", step < 0.01f)
            prevAmp = amp
        }
    }

    @Test
    fun testEndpointSafetyAcrossVariousDurations() {
        val textSize = 100f
        val durations = listOf(100L, 150L, 250L, 500L, 800L, 1500L, 3000L)
        val counts = listOf(1, 4, 10)

        for (duration in durations) {
            assertEquals(0f, SyllableAnimator.getWordLift(0f, textSize, wordDurationMs = duration), 0.0001f)
            assertEquals(0f, SyllableAnimator.getWordLift(1f, textSize, wordDurationMs = duration), 0.0001f)
            assertEquals(0.98f, SyllableAnimator.getWordMotionScale(1f, wordDurationMs = duration), 0.0001f)

            for (count in counts) {
                for (letter in 0 until count) {
                    assertEquals(
                        0f,
                        SyllableAnimator.getLetterLift(0f, letter, count, textSize, durationMs = duration),
                        0.0001f
                    )
                    assertEquals(
                        0f,
                        SyllableAnimator.getLetterLift(1f, letter, count, textSize, durationMs = duration),
                        0.0001f
                    )
                    assertEquals(
                        0.98f,
                        SyllableAnimator.getLetterScale(1f, letter, count, durationMs = duration),
                        0.0001f
                    )
                }
            }
        }
    }

    @Test
    fun testMotionDurationFloor() {
        val start = 1000L
        val end = 1050L
        val lineEnd = 5000L

        val motionEnd = SyllableAnimator.getMotionWordEnd(start, end, lineEnd)
        assertTrue(motionEnd - start >= 480L)

        val flooredMotionEnd = SyllableAnimator.getMotionWordEnd(start, end, lineEnd, motionDurationFloorMs = 450L)
        assertEquals(1450L, flooredMotionEnd)

        val clampedMotionEnd = SyllableAnimator.getMotionWordEnd(start, end, lineEndMs = 1250L, motionDurationFloorMs = 450L)
        assertEquals(1250L, clampedMotionEnd)
    }

    @Test
    fun testRiseDurationIsTempoIndependentAcrossWindows() {
        val windows = listOf(320L, 600L, 1200L)
        val defaultRiseMs = 220f
        val frameToleranceMs = 16.7f

        for (windowMs in windows) {
            var peakP = 0f
            var peakScale = Float.NEGATIVE_INFINITY
            val steps = 10000
            for (i in 0..steps) {
                val p = i.toFloat() / steps.toFloat()
                val scale = SyllableAnimator.getWordMotionScale(p, motionWindowMs = windowMs)
                if (scale > peakScale) {
                    peakScale = scale
                    peakP = p
                }
            }
            val peakTimeMs = peakP * windowMs.toFloat()
            val diffFromTarget = Math.abs(peakTimeMs - defaultRiseMs)
            assertTrue(
                "Window ${windowMs}ms peak reached at ${peakTimeMs}ms, expected within ${frameToleranceMs}ms of ${defaultRiseMs}ms (diff=$diffFromTarget)",
                diffFromTarget <= frameToleranceMs
            )
            assertEquals("Peak scale must reach 1.00", 1.00f, peakScale, 0.001f)
        }
    }

    @Test
    fun testSlowWordsPreserveExistingCurveShape() {
        // Quantify similarity: for a 367ms window at default 220ms rise, the curve
        // matches the baseline normalized curve within 0.02 scale tolerance (2% font size).
        val windowMs = 367L
        val maxAllowedDiff = 0.02f
        var maxDiff = 0f
        var sumDiff = 0f
        val steps = 1000

        for (i in 0..steps) {
            val p = i.toFloat() / steps.toFloat()
            val newScale = SyllableAnimator.getWordMotionScale(p, motionWindowMs = windowMs)
            val baselineScale = SyllableAnimator.getWordMotionScale(p)
            val diff = Math.abs(newScale - baselineScale)
            if (diff > maxDiff) maxDiff = diff
            sumDiff += diff
        }
        val avgDiff = sumDiff / (steps + 1).toFloat()

        assertTrue("Max difference ($maxDiff) must be <= $maxAllowedDiff", maxDiff <= maxAllowedDiff)
        assertTrue("Average difference ($avgDiff) must be <= 0.01", avgDiff <= 0.01f)
    }

    @Test
    fun testScaleAndLiftContinuityAcrossFineSweepAndHandover() {
        val testWindows = listOf(1200L, 600L, 320L, 200L, 100L)
        val textSize = 100f
        val steps = 10000

        for (windowMs in testWindows) {
            var prevScale = SyllableAnimator.getWordMotionScale(0f, motionWindowMs = windowMs)
            var prevLift = SyllableAnimator.getWordLift(0f, textSize)

            for (i in 1..steps) {
                val p = i.toFloat() / steps.toFloat()
                val scale = SyllableAnimator.getWordMotionScale(p, motionWindowMs = windowMs)
                val lift = SyllableAnimator.getWordLift(p, textSize)

                val scaleStep = Math.abs(scale - prevScale)
                val liftStep = Math.abs(lift - prevLift)

                assertTrue("Scale must have no discontinuity at step $i for window $windowMs (was $scaleStep)", scaleStep < 0.005f)
                assertTrue("Lift must have no discontinuity at step $i for window $windowMs (was $liftStep)", liftStep < 0.05f)

                prevScale = scale
                prevLift = lift
            }

            // Direct check at handover boundary
            val pPeak = Math.min(260f / windowMs.toFloat(), 0.85f)
            val pBefore = Math.max(0f, pPeak - 0.0001f)
            val pAfter = Math.min(1f, pPeak + 0.0001f)
            val stepAtHandover = Math.abs(
                SyllableAnimator.getWordMotionScale(pAfter, motionWindowMs = windowMs) -
                SyllableAnimator.getWordMotionScale(pBefore, motionWindowMs = windowMs)
            )
            assertTrue("Handover boundary must be continuous for window $windowMs (was $stepAtHandover)", stepAtHandover < 0.001f)
        }
    }

    @Test
    fun testShortWindowDegradationReturnsToRestAndNeverExceedsPeak() {
        val shortWindows = listOf(200L, 150L, 100L, 50L)
        val peakCeiling = 1.00001f

        for (windowMs in shortWindows) {
            var observedPeak = Float.NEGATIVE_INFINITY
            val steps = 1000
            for (i in 0..steps) {
                val p = i.toFloat() / steps.toFloat()
                val s = SyllableAnimator.getWordMotionScale(p, motionWindowMs = windowMs)
                if (s > observedPeak) observedPeak = s
                assertTrue("Scale at p=$p for window $windowMs must not exceed ceiling $peakCeiling (was $s)", s <= peakCeiling)
            }
            // Word shorter than rise duration does not reach full 1.00 peak
            assertTrue("Short window $windowMs must not finish full rise (peak was $observedPeak)", observedPeak < 1.00f)
            // Returns to rest at end of window
            val endScale = SyllableAnimator.getWordMotionScale(1f, motionWindowMs = windowMs)
            assertEquals("Short window $windowMs must return to 0.98 at rest", 0.98f, endScale, 0.0001f)
        }
    }

    @Test
    fun testMonotonicRiseFromUndershootUntilPeak() {
        val windows = listOf(1200L, 600L, 320L, 200L, 100L)
        val riseF = Tuning.wordRiseDurationMs.toFloat()
        for (windowMs in windows) {
            val pPeak = Math.min(riseF / windowMs.toFloat(), 0.85f)
            val pDip = (Tuning.wordScaleEaseInFraction * riseF) / windowMs.toFloat()
            val steps = 2000
            val dipStep = (pDip * steps).toInt()
            val peakStep = (pPeak * steps).toInt()

            var prev = SyllableAnimator.getWordMotionScale(0f, motionWindowMs = windowMs)
            for (i in 1..dipStep) {
                val p = i.toFloat() / steps.toFloat()
                val scale = SyllableAnimator.getWordMotionScale(p, motionWindowMs = windowMs)
                assertTrue("Scale must ease down into undershoot at p=$p for window $windowMs", scale <= prev + 0.00001f)
                prev = scale
            }

            for (i in (dipStep + 1)..peakStep) {
                val p = i.toFloat() / steps.toFloat()
                val scale = SyllableAnimator.getWordMotionScale(p, motionWindowMs = windowMs)
                assertTrue("Scale must rise monotonically from undershoot at p=$p for window $windowMs", scale >= prev - 0.00001f)
                prev = scale
            }
        }
    }

    @Test
    fun testLeadInAnticipationBehavior() {
        // Zero lead in: identical to no lead in
        val noLeadIn = SyllableAnimator.getMotionWordStart(1000L, 800L, 850L, leadInMs = 0L)
        assertEquals(1000L, noLeadIn)

        // Non-zero lead in: starts earlier so peak lands earlier
        val withLeadIn = SyllableAnimator.getMotionWordStart(1000L, 800L, 850L, leadInMs = 50L)
        assertEquals(950L, withLeadIn)

        // Clamped so motion never starts before line start
        val clampedLineStart = SyllableAnimator.getMotionWordStart(1000L, 980L, 850L, leadInMs = 50L)
        assertEquals(980L, clampedLineStart)

        // Clamped so motion never starts before previous word vocal end
        val clampedPrevWord = SyllableAnimator.getMotionWordStart(1000L, 800L, 975L, leadInMs = 50L)
        assertEquals(975L, clampedPrevWord)

        // Never starts after word onset even if invalid timestamps
        val boundOnset = SyllableAnimator.getMotionWordStart(1000L, 1100L, 1100L, leadInMs = 50L)
        assertEquals(1000L, boundOnset)
    }

    @Test
    fun testEndpointSafetyAcrossAllWindowLengths() {
        val windows = listOf(50L, 100L, 200L, 320L, 600L, 1200L, 3000L)
        val textSize = 100f

        for (windowMs in windows) {
            assertEquals("Scale at p=0 must be 0.95 for window $windowMs", 0.95f, SyllableAnimator.getWordMotionScale(0f, motionWindowMs = windowMs), 0.0001f)
            assertEquals("Scale at p=1 must be 0.98 for window $windowMs", 0.98f, SyllableAnimator.getWordMotionScale(1f, motionWindowMs = windowMs), 0.0001f)
            assertEquals("Lift at p=0 must be 0 for window $windowMs", 0f, SyllableAnimator.getWordLift(0f, textSize), 0.0001f)
            assertEquals("Lift at p=1 must be 0 for window $windowMs", 0f, SyllableAnimator.getWordLift(1f, textSize), 0.0001f)
        }
    }

    @Test
    fun testNoSnapAtMotionStart() {
        val windowMs = 600L
        val startScale = SyllableAnimator.getWordMotionScale(0f, motionWindowMs = windowMs)
        assertEquals("Scale at progress 0 must be exactly 0.95", 0.95f, startScale, 0.00001f)
        assertEquals("Normalized scale at progress 0 must be exactly 0.95", 0.95f, SyllableAnimator.getWordMotionScale(0f), 0.00001f)

        // Measure consecutive samples across 1000 step sweep
        val steps = 1000
        var maxStepChange = 0f
        var prevScale = startScale
        for (i in 1..steps) {
            val p = i.toFloat() / steps.toFloat()
            val currentScale = SyllableAnimator.getWordMotionScale(p, motionWindowMs = windowMs)
            val stepChange = Math.abs(currentScale - prevScale)
            if (stepChange > maxStepChange) {
                maxStepChange = stepChange
            }
            prevScale = currentScale
        }
        // Step change must never exceed 0.002f across 1000 steps (was 0.030 at motion start before fix)
        assertTrue("Max step change ($maxStepChange) must be <= 0.002f", maxStepChange <= 0.002f)
    }

    @Test
    fun testNoSnapAtMotionEnd() {
        val windowMs = 600L
        val endScale = SyllableAnimator.getWordMotionScale(1f, motionWindowMs = windowMs)
        assertEquals("Scale at progress 1 must be exactly 0.98", 0.98f, endScale, 0.00001f)
        assertEquals("Normalized scale at progress 1 must be exactly 0.98", 0.98f, SyllableAnimator.getWordMotionScale(1f), 0.00001f)
    }

    @Test
    fun testUndershootSurvivesInEarlyPhase() {
        val windowMs = 600L
        val steps = 1000
        var minObserved = Float.POSITIVE_INFINITY
        var minProgress = 0f
        for (i in 0..steps) {
            val p = i.toFloat() / steps.toFloat()
            val s = SyllableAnimator.getWordMotionScale(p, motionWindowMs = windowMs)
            if (s < minObserved) {
                minObserved = s
                minProgress = p
            }
        }
        assertEquals("Undershoot must reach configured wordScaleStart", Tuning.wordScaleStart, minObserved, 0.005f)
        assertTrue("Undershoot must occur early in the rise before peak", minProgress < 0.30f)
    }

    @Test
    fun testPeakPreservedAndTempoIndependentAcrossWindows() {
        val windows = listOf(320L, 600L, 1200L)
        val expectedRiseMs = Tuning.wordRiseDurationMs.toFloat()
        val expectedPeak = Tuning.wordScalePeak
        val frameToleranceMs = 16f

        for (windowMs in windows) {
            var peakP = 0f
            var peakScale = Float.NEGATIVE_INFINITY
            val steps = 10000
            for (i in 0..steps) {
                val p = i.toFloat() / steps.toFloat()
                val scale = SyllableAnimator.getWordMotionScale(p, motionWindowMs = windowMs)
                if (scale > peakScale) {
                    peakScale = scale
                    peakP = p
                }
            }
            val peakTimeMs = peakP * windowMs.toFloat()
            val diffFromTarget = Math.abs(peakTimeMs - expectedRiseMs)
            assertTrue(
                "Window ${windowMs}ms peak reached at ${peakTimeMs}ms, expected within ${frameToleranceMs}ms of ${expectedRiseMs}ms",
                diffFromTarget <= frameToleranceMs
            )
            assertEquals("Peak scale must reach wordScalePeak", expectedPeak, peakScale, 0.002f)
        }
    }

    @Test
    fun testLiftAndGlowContinuityAtEndpoints() {
        val textSize = 100f
        val steps = 1000

        assertEquals("Lift at p=0 must be 0", 0f, SyllableAnimator.getWordLift(0f, textSize), 0.0001f)
        assertEquals("Lift at p=1 must be 0", 0f, SyllableAnimator.getWordLift(1f, textSize), 0.0001f)
        assertEquals("Glow at p=0 must be 0", 0f, SyllableAnimator.getWordGlow(0f), 0.0001f)
        assertEquals("Glow at p=1 must be 0", 0f, SyllableAnimator.getWordGlow(1f), 0.0001f)

        var maxLiftStep = 0f
        var maxGlowStep = 0f
        var prevLift = SyllableAnimator.getWordLift(0f, textSize)
        var prevGlow = SyllableAnimator.getWordGlow(0f)

        for (i in 1..steps) {
            val p = i.toFloat() / steps.toFloat()
            val lift = SyllableAnimator.getWordLift(p, textSize)
            val glow = SyllableAnimator.getWordGlow(p)

            val liftDiff = Math.abs(lift - prevLift)
            val glowDiff = Math.abs(glow - prevGlow)

            if (liftDiff > maxLiftStep) maxLiftStep = liftDiff
            if (glowDiff > maxGlowStep) maxGlowStep = glowDiff

            prevLift = lift
            prevGlow = glow
        }

        assertTrue("Max lift step change ($maxLiftStep) must be small", maxLiftStep < 0.05f)
        assertTrue("Max glow step change ($maxGlowStep) must be small", maxGlowStep < 0.01f)
    }

    @Test
    fun testHeldWordsSwell() {
        val durationMs = 1000L
        val windowMs = 800L
        val codePointCount = 5
        val riseF = Tuning.wordRiseDurationMs.toFloat()
        val windowF = windowMs.toFloat()
        val peakP = (riseF / windowF).coerceIn(0.01f, 0.85f)

        val amp = SyllableAnimator.getMotionAmplitude(durationMs)
        val expectedWordPeak = 1f + (Tuning.wordScalePeak - 1f) * amp
        val actualWordScale = SyllableAnimator.getWordMotionScale(peakP, durationMs, windowMs)
        assertEquals("Word-level scale reaches wordScalePeak composed with amplitude", expectedWordPeak, actualWordScale, 0.005f)

        for (letterIdx in 0 until codePointCount) {
            val letterScale = SyllableAnimator.getLetterScale(
                linearProgress = peakP,
                codePointIndex = letterIdx,
                codePointCount = codePointCount,
                durationMs = durationMs,
                motionWindowMs = windowMs
            )
            assertTrue(
                "Letter $letterIdx scale ($letterScale) must be >= word scale ($actualWordScale)",
                letterScale >= actualWordScale - 0.0001f
            )
        }
    }

    @Test
    fun testRippleRidesOnTopOfWordScale() {
        val durationMs = 1200L
        val windowMs = 1000L
        val codePointCount = 15
        val p = 0.5f

        val wordScale = SyllableAnimator.getWordMotionScale(p, durationMs, windowMs)
        val activePos = SyllableAnimator.getActiveLetterPosition(p, codePointCount)
        val activeIndex = Math.round(activePos)

        val activeLetterScale = SyllableAnimator.getLetterScale(
            linearProgress = p,
            codePointIndex = activeIndex,
            codePointCount = codePointCount,
            durationMs = durationMs,
            motionWindowMs = windowMs
        )
        assertTrue(
            "Letter near active position must have letterScale ($activeLetterScale) > wordScale ($wordScale)",
            activeLetterScale > wordScale + 0.01f
        )

        val farIndex = 0
        val farLetterScale = SyllableAnimator.getLetterScale(
            linearProgress = p,
            codePointIndex = farIndex,
            codePointCount = codePointCount,
            durationMs = durationMs,
            motionWindowMs = windowMs
        )
        assertEquals(
            "Letter far from active position must have letterScale == wordScale",
            wordScale,
            farLetterScale,
            0.002f
        )

        var sumLetterScale = 0f
        for (i in 0 until codePointCount) {
            sumLetterScale += SyllableAnimator.getLetterScale(
                linearProgress = p,
                codePointIndex = i,
                codePointCount = codePointCount,
                durationMs = durationMs,
                motionWindowMs = windowMs
            )
        }
        val meanScale = sumLetterScale / codePointCount.toFloat()
        assertTrue("Mean letter scale ($meanScale) must be >= wordScale ($wordScale)", meanScale >= wordScale - 0.0001f)
    }

    @Test
    fun testSettleDurationIsTempoIndependent() {
        val wordDurations = listOf(150L, 500L, 1200L)
        val windowMs = 1200L
        val riseMs = 220L
        val settleMs = 420L
        val frameToleranceMs = 16L

        for (durationMs in wordDurations) {
            val pPeak = (riseMs.toFloat() / windowMs.toFloat()).coerceIn(0.01f, 0.85f)
            val tPeak = pPeak * windowMs.toFloat()

            // Scale must be at rest (1.0 within 0.001) at tPeak + settleMs
            val pAtSettle = (tPeak + settleMs) / windowMs.toFloat()
            val scaleAtSettle = SyllableAnimator.getWordMotionScale(
                linearProgress = pAtSettle,
                wordDurationMs = durationMs,
                motionWindowMs = windowMs,
                riseDurationMs = riseMs,
                settleDurationMs = settleMs
            )
            assertEquals(
                "Scale at tPeak + settleMs must be at rest (0.98 within 0.001) for duration ${durationMs}ms",
                0.98f,
                scaleAtSettle,
                0.001f
            )

            var restTimeMs = -1f
            val steps = 1200
            for (step in 0..steps) {
                val t = (step.toFloat() / steps.toFloat()) * windowMs.toFloat()
                if (t <= tPeak) continue
                val p = t / windowMs.toFloat()
                val scale = SyllableAnimator.getWordMotionScale(
                    linearProgress = p,
                    wordDurationMs = durationMs,
                    motionWindowMs = windowMs,
                    riseDurationMs = riseMs,
                    settleDurationMs = settleMs
                )
                if (Math.abs(scale - 0.98f) <= 0.0002f) {
                    restTimeMs = t
                    break
                }
            }

            assertTrue("Rest time must be found for duration ${durationMs}ms", restTimeMs > 0f)
            val measuredSettleDuration = (restTimeMs - tPeak).toLong()
            val diff = Math.abs(measuredSettleDuration - settleMs)
            assertTrue(
                "Settle duration for ${durationMs}ms word was ${measuredSettleDuration}ms, expected within ${frameToleranceMs}ms of ${settleMs}ms",
                diff <= frameToleranceMs
            )
        }
    }

    @Test
    fun testShapeAlwaysFits() {
        val leadIns = listOf(0L, 100L, 300L)
        val rises = listOf(80L, 220L, 800L)
        val settles = listOf(150L, 420L, 1200L)
        val floors = listOf(100L, 480L, 800L)

        for (leadIn in leadIns) {
            for (rise in rises) {
                for (settle in settles) {
                    for (floor in floors) {
                        val effective = SyllableAnimator.getEffectiveMotionFloor(floor, leadIn, rise, settle)
                        val shapeDuration = leadIn + rise + settle
                        assertTrue(
                            "Effective floor ($effective) must be >= shapeDuration ($shapeDuration)",
                            effective >= shapeDuration
                        )
                        assertTrue(
                            "Effective floor ($effective) must be >= floor ($floor)",
                            effective >= floor
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testContinuityAcrossLineExit() {
        val lineStart = 1000L
        val lineEnd = 5000L
        val transitionDuration = 200f
        val wordStart = 4500L
        val wordEnd = 5000L

        val effectiveFloor = SyllableAnimator.getEffectiveMotionFloor()
        val motionStartT = SyllableAnimator.getMotionWordStart(wordStart, lineStart)
        val motionEndT = SyllableAnimator.getMotionWordEnd(
            wordStart, wordEnd, lineEnd,
            motionDurationFloorMs = effectiveFloor,
            maxOverrunMs = transitionDuration.toLong()
        )

        assertTrue("Motion window must overrun line end into the fade", motionEndT > lineEnd)
        assertTrue("Overrun must not exceed transitionDuration", motionEndT <= lineEnd + transitionDuration.toLong())

        val motionWindowMs = motionEndT - motionStartT
        var prevScale = Float.MAX_VALUE

        for (step in 0..20) {
            val playhead = lineEnd + (step * 10L)

            val motionLinearProgress = when {
                playhead >= motionEndT -> 1f
                playhead <= motionStartT -> 0f
                else -> ((playhead - motionStartT).toFloat() / motionWindowMs.toFloat()).coerceIn(0f, 1f)
            }

            val motionProgress = motionLinearProgress.coerceIn(0f, 1f)
            if (playhead < motionEndT) {
                assertTrue("Motion progress must be non-zero during fade before motionEnd", motionProgress > 0f)
                val scale = SyllableAnimator.getWordMotionScale(motionProgress, motionWindowMs = motionWindowMs)
                assertTrue("Scale must be continuously decreasing during settle", scale <= prevScale + 0.0001f)
                prevScale = scale
            } else {
                assertEquals("Motion progress must hold at 1 once motion window ends", 1f, motionProgress, 0.0001f)
                val settled = SyllableAnimator.getWordMotionScale(motionProgress, motionWindowMs = motionWindowMs)
                assertEquals("A finished word must hold its settle scale", Tuning.wordScaleSettle, settled, 0.0001f)
            }
        }
    }

    @Test
    fun testBoundedOverrun() {
        val lineEnd = 5000L
        val maxOverrun = 200L

        val motionEndBounded = SyllableAnimator.getMotionWordEnd(
            startMs = 4800L,
            endMs = 5000L,
            lineEndMs = lineEnd,
            trailMaxMs = 10000L,
            motionDurationFloorMs = 20000L,
            maxOverrunMs = maxOverrun
        )
        assertTrue("Motion end ($motionEndBounded) must never exceed lineEnd + maxOverrun (${lineEnd + maxOverrun})", motionEndBounded <= lineEnd + maxOverrun)
        assertEquals(lineEnd + maxOverrun, motionEndBounded)

        val motionEndDefault = SyllableAnimator.getMotionWordEnd(
            startMs = 4800L,
            endMs = 5000L,
            lineEndMs = lineEnd,
            trailMaxMs = 10000L,
            motionDurationFloorMs = 20000L
        )
        assertTrue("Default motion end ($motionEndDefault) must never exceed lineEnd ($lineEnd)", motionEndDefault <= lineEnd)
        assertEquals(lineEnd, motionEndDefault)
    }

    @Test
    fun testSweepStillRespectsLineEnd() {
        val lineEnd = 5000L
        val sweepEnd = SyllableAnimator.getExtendedWordEnd(
            startMs = 4800L,
            endMs = 5000L,
            lineEndMs = lineEnd,
            overlapMs = 1000L,
            minAnimationMs = 2000L
        )
        assertTrue("Sweep end ($sweepEnd) must not exceed lineEnd ($lineEnd)", sweepEnd <= lineEnd)
        assertEquals(lineEnd, sweepEnd)
    }

    @Test
    fun testContinuityAcrossFineSweepAndExit() {
        val lineStart = 1000L
        val lineEnd = 5000L
        val transitionDuration = 200f
        val wordStart = 4500L
        val wordEnd = 5000L

        val effectiveFloor = SyllableAnimator.getEffectiveMotionFloor()
        val motionStartT = SyllableAnimator.getMotionWordStart(wordStart, lineStart)
        val motionEndT = SyllableAnimator.getMotionWordEnd(
            wordStart, wordEnd, lineEnd,
            motionDurationFloorMs = effectiveFloor,
            maxOverrunMs = transitionDuration.toLong()
        )
        val motionWindowMs = motionEndT - motionStartT

        var prevScale = SyllableAnimator.getWordMotionScale(
            ((lineEnd - 50L - motionStartT).toFloat() / motionWindowMs.toFloat()).coerceIn(0f, 1f),
            motionWindowMs = motionWindowMs
        )
        var maxDelta = 0f

        for (t in (lineEnd - 50L)..(lineEnd + 50L)) {
            val progress = ((t - motionStartT).toFloat() / motionWindowMs.toFloat()).coerceIn(0f, 1f)
            val scale = SyllableAnimator.getWordMotionScale(progress, motionWindowMs = motionWindowMs)
            val delta = Math.abs(scale - prevScale)
            if (delta > maxDelta) maxDelta = delta
            prevScale = scale
        }

        assertTrue("Max scale step across line exit handover ($maxDelta) must be smooth (<= 0.005f)", maxDelta <= 0.005f)
    }

    @Test
    fun testNoRippleImpulseAtMotionStart() {
        // At progress 0, ripple emphasis must be exactly 0 for every letter
        val codePointCounts = listOf(2, 5, 8, 12)
        for (count in codePointCounts) {
            for (letterIndex in 0 until count) {
                val emphasis = SyllableAnimator.getRippleEmphasis(
                    linearProgress = 0.0f,
                    codePointIndex = letterIndex,
                    codePointCount = count
                )
                assertEquals(
                    "Letter $letterIndex with total $count must have exactly 0 ripple emphasis at progress 0",
                    0.0f,
                    emphasis,
                    0.00001f
                )
            }
        }
    }

    @Test
    fun testRippleEasesInSmoothly() {
        val count = 6
        val steps = 1000
        var prevEmphasis = SyllableAnimator.getRippleEmphasis(0.0f, 0, count)
        var maxDelta = 0.0f

        for (step in 1..steps) {
            val progress = step.toFloat() / steps.toFloat()
            val currentEmphasis = SyllableAnimator.getRippleEmphasis(progress, 0, count)
            val delta = Math.abs(currentEmphasis - prevEmphasis)
            if (delta > maxDelta) {
                maxDelta = delta
            }
            prevEmphasis = currentEmphasis
        }

        // Across 1000 steps (step size 0.001), with max smoothstep slope of 1.5 / 0.15 = 10.0,
        // the theoretical maximum step change is 10.0 * 0.001 = 0.010.
        // We assert maxDelta < 0.015f, proving smooth C1 continuity without impulses.
        assertTrue("Max single step change ($maxDelta) must be smooth (< 0.015f)", maxDelta < 0.015f)
    }

    @Test
    fun testMidWordRippleEmphasisMatchesCurrentImplementation() {
        val count = 7
        val midProgress = 0.5f
        val activePos = SyllableAnimator.getActiveLetterPosition(midProgress, count)

        for (i in 0 until count) {
            val distance = i.toFloat() - activePos
            val expectedFalloff = SyllableAnimator.getRippleFalloff(distance)
            val actualEmphasis = SyllableAnimator.getRippleEmphasis(midProgress, i, count)
            assertEquals(
                "Mid-word emphasis for letter $i must match getRippleFalloff within tight tolerance",
                expectedFalloff,
                actualEmphasis,
                0.0001f
            )
        }
    }

    @Test
    fun testRippleEndsSymmetricallyAtMotionEnd() {
        // At progress 1.0, ripple emphasis must be exactly 0 for every letter
        val codePointCounts = listOf(2, 5, 8, 12)
        for (count in codePointCounts) {
            for (letterIndex in 0 until count) {
                val emphasis = SyllableAnimator.getRippleEmphasis(
                    linearProgress = 1.0f,
                    codePointIndex = letterIndex,
                    codePointCount = count
                )
                assertEquals(
                    "Letter $letterIndex with total $count must have exactly 0 ripple emphasis at progress 1.0",
                    0.0f,
                    emphasis,
                    0.00001f
                )
            }
        }
    }
    @Test
    fun testEqualPeakEmphasisAcrossFeelingsLetters() {
        val word = "feelings"
        val count = word.length
        val windowMs = 1000L
        val steps = 10000
        val peakScale = Tuning.heldWordLetterScalePeak

        val peakHeldScales = FloatArray(count)
        val peakRippleEmphases = FloatArray(count)

        for (i in 0 until count) {
            var maxHeldScale = 0f
            var maxEmphasis = 0f
            for (step in 0..steps) {
                val p = step.toFloat() / steps.toFloat()
                val activePos = SyllableAnimator.getActiveLetterPosition(p, count)
                val distance = i.toFloat() - activePos
                val heldScale = SyllableAnimator.getHeldWordLetterScale(
                    linearProgress = p,
                    distance = distance,
                    peakScale = peakScale,
                    motionWindowMs = windowMs
                )
                if (heldScale > maxHeldScale) maxHeldScale = heldScale

                val emphasis = SyllableAnimator.getRippleEmphasis(p, i, count)
                if (emphasis > maxEmphasis) maxEmphasis = emphasis
            }
            peakHeldScales[i] = maxHeldScale
            peakRippleEmphases[i] = maxEmphasis
        }

        // Middle letters (index 1 to 6, 'e' through 'g') must have equal peak held scale of peakScale (1.14) within tight tolerance.
        // The first and last letters ('f' and 's') are shaped only by the boundary ease-in/ease-out envelope.
        for (i in 1 until count - 1) {
            assertEquals(
                "Letter $i ('${word[i]}') of '$word' must reach peakScale ($peakScale) within 0.001f",
                peakScale,
                peakHeldScales[i],
                0.001f
            )
            assertEquals(
                "Letter $i ('${word[i]}') of '$word' must have peak ripple emphasis of 1.0 within 0.001f",
                1.0f,
                peakRippleEmphases[i],
                0.001f
            )
        }

        // All letters across the word must maintain substantial emphasis (> 1.11f) without decaying toward 1.0.
        // Before the fix, letter 6 decayed to 1.005 and letter 7 to 1.001.
        for (i in 0 until count) {
            assertTrue(
                "Letter $i ('${word[i]}') scale (${peakHeldScales[i]}) must be >= 1.01f and within 0.025f of peak",
                peakHeldScales[i] >= 1.01f && Math.abs(peakHeldScales[i] - peakScale) <= 0.025f
            )
        }
    }

    @Test
    fun testNoDoubleApplicationOfWordScaleForHeldWord() {
        val durationMs = 1200L
        val windowMs = 1000L
        val count = 8
        val steps = 100
        val amp = SyllableAnimator.getMotionAmplitude(durationMs)
        val heldPeak = Tuning.heldWordLetterScalePeak

        for (i in 0 until count) {
            for (step in 0..steps) {
                val p = step.toFloat() / steps.toFloat()
                val totalScale = SyllableAnimator.getLetterScale(
                    linearProgress = p,
                    codePointIndex = i,
                    codePointCount = count,
                    durationMs = durationMs,
                    motionWindowMs = windowMs
                )

                val wordScale = SyllableAnimator.getWordMotionScale(p, durationMs, windowMs)
                val activePos = SyllableAnimator.getActiveLetterPosition(p, count)
                val distance = i.toFloat() - activePos
                val rippleScale = SyllableAnimator.getHeldWordLetterScale(
                    linearProgress = p,
                    distance = distance,
                    amplitude = amp,
                    motionWindowMs = windowMs
                )

                // Total scale must strictly equal wordScale multiplied by rippleScale
                val expectedTotal = wordScale * rippleScale
                assertEquals(
                    "Total scale must equal wordScale * rippleScale at p=$p, letter=$i",
                    expectedTotal,
                    totalScale,
                    0.0001f
                )
            }
        }

        // When ripple is at its peak (distance 0 at middle progress where envelope is 1.0),
        // rippleScale must equal 1f + (heldPeak - 1f) * amp, independent of word envelope decay
        val midProgress = 0.5f
        val ripplePeak = SyllableAnimator.getHeldWordLetterScale(
            linearProgress = midProgress,
            distance = 0f,
            amplitude = amp,
            motionWindowMs = windowMs
        )
        val expectedRipplePeak = 1f + (heldPeak - 1f) * amp
        assertEquals(
            "Ripple term peak must equal 1 + (heldPeak - 1) * amp",
            expectedRipplePeak,
            ripplePeak,
            0.0001f
        )
    }

    @Test
    fun testRippleTravelTimingUnchanged() {
        val count = 8
        val steps = 10000
        var prevPeakProgress = -1f

        for (i in 0 until count) {
            var maxEmphasis = -1f
            var peakP = -1f

            for (step in 0..steps) {
                val p = step.toFloat() / steps.toFloat()
                val emphasis = SyllableAnimator.getRippleEmphasis(p, i, count)
                if (emphasis > maxEmphasis) {
                    maxEmphasis = emphasis
                    peakP = p
                }
            }

            // Letters must peak in strictly increasing index order
            assertTrue("Peak progress for letter $i ($peakP) must be > previous ($prevPeakProgress)", peakP > prevPeakProgress)
            prevPeakProgress = peakP

            // For middle letters where envelope is flat (1.0), peak must occur exactly at activePos = i
            // activePos = p * count - 0.5f = i => p = (i + 0.5f) / count
            if (i in 1 until count - 1) {
                val expectedP = (i.toFloat() + 0.5f) / count.toFloat()
                assertEquals("Peak progress for letter $i must match expected active center", expectedP, peakP, 0.001f)
            }
        }
    }

    @Test
    fun testRippleFalloffShapeUnchangedAtIntegerDistances() {
        assertEquals(1.0f, SyllableAnimator.getRippleFalloff(0f), 0.0001f)
        assertEquals(0.5f, SyllableAnimator.getRippleFalloff(1f), 0.0001f)
        assertEquals(0.111f, SyllableAnimator.getRippleFalloff(2f), 0.001f)
        assertEquals(0.036f, SyllableAnimator.getRippleFalloff(3f), 0.001f)
    }

    @Test
    fun testRippleEntryAndExitSmoothnessBounded() {
        val count = 8
        // No impulse at p=0 and p=1
        for (i in 0 until count) {
            assertEquals("Emphasis at p=0 must be 0 for letter $i", 0f, SyllableAnimator.getRippleEmphasis(0f, i, count), 0.00001f)
            assertEquals("Emphasis at p=1 must be 0 for letter $i", 0f, SyllableAnimator.getRippleEmphasis(1f, i, count), 0.00001f)
        }

        // Single-step change bounded across 1000 steps
        val steps = 1000
        for (i in 0 until count) {
            var prevEmphasis = SyllableAnimator.getRippleEmphasis(0f, i, count)
            var maxDelta = 0f
            for (step in 1..steps) {
                val p = step.toFloat() / steps.toFloat()
                val currentEmphasis = SyllableAnimator.getRippleEmphasis(p, i, count)
                val delta = Math.abs(currentEmphasis - prevEmphasis)
                if (delta > maxDelta) maxDelta = delta
                prevEmphasis = currentEmphasis
            }
            assertTrue("Max step delta for letter $i ($maxDelta) must be < 0.015f", maxDelta < 0.015f)
        }
    }

    @Test
    fun testEqualPeakLiftAcrossHeldLetters() {
        val word = "feelings"
        val count = word.length
        val durationMs = 1200L
        val textSize = 100f
        val steps = 10000
        val amp = SyllableAnimator.getMotionAmplitude(durationMs)
        val expectedPeakLift = Tuning.wordLiftPeakFraction * textSize * amp

        val peakLifts = FloatArray(count)
        for (i in 0 until count) {
            var maxLift = 0f
            for (step in 0..steps) {
                val p = step.toFloat() / steps.toFloat()
                val lift = SyllableAnimator.getLetterLift(
                    linearProgress = p,
                    codePointIndex = i,
                    codePointCount = count,
                    textSize = textSize,
                    durationMs = durationMs
                )
                if (lift > maxLift) maxLift = lift
            }
            peakLifts[i] = maxLift
        }

        // Middle letters (1 to 6) must reach identical peak lift of expectedPeakLift (5.0px)
        for (i in 1 until count - 1) {
            assertEquals(
                "Letter $i ('${word[i]}') must reach expected peak lift ($expectedPeakLift)",
                expectedPeakLift,
                peakLifts[i],
                0.01f
            )
        }

        // All letters must maintain high peak lift (>= 4.0px, not decayed to 1.6px or 2.1px)
        for (i in 0 until count) {
            assertTrue(
                "Letter $i lift (${peakLifts[i]}) must be >= 4.0px",
                peakLifts[i] >= 4.0f
            )
        }
    }

    @Test
    fun testNonHeldWordsScaleUnaffected() {
        val count = 8
        val durationMs = 400L // Below 575ms threshold
        val windowMs = 500L
        val steps = 200

        for (i in 0 until count) {
            for (step in 0..steps) {
                val p = step.toFloat() / steps.toFloat()
                val letterScale = SyllableAnimator.getLetterScale(
                    linearProgress = p,
                    codePointIndex = i,
                    codePointCount = count,
                    durationMs = durationMs,
                    motionWindowMs = windowMs
                )
                val wordScale = SyllableAnimator.getWordMotionScale(p, durationMs, windowMs)
                assertEquals(
                    "Non-held word scale must equal wordScale exactly at p=$p, letter=$i",
                    wordScale,
                    letterScale,
                    0.00001f
                )
            }
        }
    }

    @Test
    fun testLineReleaseTimeWithHoldMaxZeroEqualsNextLineStartTime() {
        val words = listOf(
            LyricWord(startTime = 1000L, endTime = 1400L, text = "hello", startIndex = 0, endIndex = 5)
        )
        val release = SyllableAnimator.getLineReleaseTime(
            words = words,
            lineEndTime = 1400L,
            nextStartTime = 1500L,
            nextNextStartTime = 3000L,
            holdMax = 0L
        )
        assertEquals(1500L, release)

        val releaseNullWords = SyllableAnimator.getLineReleaseTime(
            words = null,
            lineEndTime = 1400L,
            nextStartTime = 1500L,
            holdMax = 0L
        )
        assertEquals(1500L, releaseNullWords)
    }

    @Test
    fun testLineReleaseTimeWithWordEnding100msBeforeNextLineStarts() {
        val nextStartTime = 2000L
        val wordEnd = nextStartTime - 100L
        val wordStart = wordEnd - 100L
        val words = listOf(
            LyricWord(startTime = wordStart, endTime = wordEnd, text = "word", startIndex = 0, endIndex = 4)
        )
        val holdMax = 650L
        val release = SyllableAnimator.getLineReleaseTime(
            words = words,
            lineEndTime = wordEnd,
            nextStartTime = nextStartTime,
            nextNextStartTime = 5000L,
            holdMax = holdMax
        )
        assertTrue("Release ($release) must be after next start ($nextStartTime)", release > nextStartTime)
        assertTrue("Release ($release) must be at most next start + holdMax (${nextStartTime + holdMax})", release <= nextStartTime + holdMax)
    }

    @Test
    fun testLineReleaseTimeNeverPassesLineAfterNextStartTime() {
        val nextStartTime = 2000L
        val wordEnd = nextStartTime - 100L
        val wordStart = wordEnd - 100L
        val words = listOf(
            LyricWord(startTime = wordStart, endTime = wordEnd, text = "word", startIndex = 0, endIndex = 4)
        )
        val nextNextStartTime = 2100L
        val release = SyllableAnimator.getLineReleaseTime(
            words = words,
            lineEndTime = wordEnd,
            nextStartTime = nextStartTime,
            nextNextStartTime = nextNextStartTime,
            holdMax = 650L
        )
        assertTrue("Release ($release) must not pass next-next start ($nextNextStartTime)", release <= nextNextStartTime)
        assertEquals(nextNextStartTime, release)
    }

    @Test
    fun testLineReleaseTimeWhenWordsSettleBeforeNextLineStarts() {
        val words = listOf(
            LyricWord(startTime = 1000L, endTime = 1100L, text = "early", startIndex = 0, endIndex = 5)
        )
        val nextStartTime = 5000L
        val release = SyllableAnimator.getLineReleaseTime(
            words = words,
            lineEndTime = 1100L,
            nextStartTime = nextStartTime,
            nextNextStartTime = 8000L,
            holdMax = 650L
        )
        assertEquals(nextStartTime, release)
    }
}



