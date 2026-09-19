package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MotionCurveTuningTest {

    @Before
    fun setUp() {
        Tuning.resetAll()
    }

    @org.junit.After
    fun tearDown() {
        Tuning.resetAll()
    }

    // 3. The curve functions produce identical output to the previous hardcoded behaviour when Tuning holds its defaults.
    // Pin the existing motion assertions against the defaults.
    @Test
    fun testWordMotionScaleMatchesPinnedDefaults() {
        assertEquals(1.0f, SyllableAnimator.getWordMotionScale(0f), 0.0001f)
        assertEquals(0.97f, SyllableAnimator.getWordMotionScale(0.12f), 0.0001f)
        assertEquals(1.0f, SyllableAnimator.getWordMotionScale(1f), 0.0001f)
        assertEquals(1.025f, SyllableAnimator.getWordMotionScale(0.60f), 0.001f)

        val peakVal = SyllableAnimator.getWordMotionScale(0.60f)
        val beforePeak = SyllableAnimator.getWordMotionScale(0.50f)
        val afterPeak = SyllableAnimator.getWordMotionScale(0.70f)
        assertTrue(peakVal > beforePeak)
        assertTrue(peakVal > afterPeak)

        for (i in 0..1000) {
            val p = i / 1000f
            val s = SyllableAnimator.getWordMotionScale(p)
            assertTrue("Scale at $p must not exceed 1.0251 (was $s)", s <= 1.0251f)
            assertTrue("Scale at $p must not be below 0.9699 (was $s)", s >= 0.9699f)
        }
    }

    @Test
    fun testWordLiftMatchesPinnedDefaults() {
        val textSize = 100f
        assertEquals(0f, SyllableAnimator.getWordLift(0f, textSize), 0.0001f)
        assertEquals(0f, SyllableAnimator.getWordLift(1f, textSize), 0.0001f)
        assertEquals(0.05f * textSize, SyllableAnimator.getWordLift(0.55f, textSize), 0.0001f)

        val peakVal = SyllableAnimator.getWordLift(0.55f, textSize)
        for (i in 0..1000) {
            val p = i / 1000f
            val lift = SyllableAnimator.getWordLift(p, textSize)
            assertTrue(lift >= -0.0001f)
            assertTrue(lift <= peakVal + 0.0001f)
        }
    }

    @Test
    fun testWordGlowMatchesPinnedDefaults() {
        assertEquals(0f, SyllableAnimator.getWordGlow(0f), 0.0001f)
        assertEquals(0f, SyllableAnimator.getWordGlow(1f), 0.0001f)

        for (i in 200..550) {
            val p = i / 1000f
            assertEquals(1.0f, SyllableAnimator.getWordGlow(p), 0.0001f)
        }

        var prevGlow = -1f
        for (i in 0..200) {
            val p = i / 1000f
            val g = SyllableAnimator.getWordGlow(p)
            assertTrue(g > prevGlow)
            prevGlow = g
        }

        prevGlow = 1.001f
        for (i in 551..1000) {
            val p = i / 1000f
            val g = SyllableAnimator.getWordGlow(p)
            assertTrue(g < prevGlow)
            prevGlow = g
        }
    }

    @Test
    fun testHeldWordThresholdMatchesPinnedDefaults() {
        assertFalse(SyllableAnimator.isHeldWord(574L))
        assertTrue(SyllableAnimator.isHeldWord(575L))
        assertFalse(SyllableAnimator.isHeldWord(0L))
        assertFalse(SyllableAnimator.isHeldWord(500L))
        assertTrue(SyllableAnimator.isHeldWord(1500L))
    }

    @Test
    fun testHeldWordLetterScaleMatchesPinnedDefaults() {
        assertEquals(1.14f, SyllableAnimator.getHeldWordLetterScale(0.60f, 0), 0.001f)
        assertEquals(1.0f, SyllableAnimator.getHeldWordLetterScale(1.0f, 0), 0.0001f)
    }

    @Test
    fun testRippleFalloffMatchesPinnedDefaults() {
        assertEquals(1.0f, SyllableAnimator.getRippleFalloff(0), 0.0001f)
        assertEquals(0.5f, SyllableAnimator.getRippleFalloff(1), 0.0001f)
        assertEquals(0.111f, SyllableAnimator.getRippleFalloff(2), 0.001f)
        assertEquals(0.036f, SyllableAnimator.getRippleFalloff(3), 0.001f)
    }

    // Dynamic tuning response tests
    @Test
    fun testTunedScaleCurveRespondsToOverrides() {
        Tuning.wordScalePeak = 1.20f
        Tuning.wordScaleStart = 0.90f
        Tuning.wordScalePeakPosition = 0.50f

        assertEquals(1.0f, SyllableAnimator.getWordMotionScale(0f), 0.0001f)
        assertEquals(0.90f, SyllableAnimator.getWordMotionScale(0.10f), 0.001f)
        assertEquals(1.0f, SyllableAnimator.getWordMotionScale(1f), 0.0001f)
        assertEquals(1.20f, SyllableAnimator.getWordMotionScale(0.50f), 0.001f)

        val peakVal = SyllableAnimator.getWordMotionScale(0.50f)
        val beforePeak = SyllableAnimator.getWordMotionScale(0.40f)
        val afterPeak = SyllableAnimator.getWordMotionScale(0.60f)
        assertTrue("Peak at 0.50 must exceed 0.40", peakVal > beforePeak)
        assertTrue("Peak at 0.50 must exceed 0.60", peakVal > afterPeak)
    }

    @Test
    fun testTunedLiftCurveRespondsToOverrides() {
        val textSize = 100f
        Tuning.wordLiftPeakFraction = 0.10f
        Tuning.wordLiftPeakPosition = 0.40f

        assertEquals(0f, SyllableAnimator.getWordLift(0f, textSize), 0.0001f)
        assertEquals(0f, SyllableAnimator.getWordLift(1f, textSize), 0.0001f)
        assertEquals(10.0f, SyllableAnimator.getWordLift(0.40f, textSize), 0.001f)
    }

    @Test
    fun testTunedGlowCurveRespondsToOverrides() {
        Tuning.wordGlowRiseEnd = 0.30f
        Tuning.wordGlowHoldEnd = 0.70f

        assertEquals(0f, SyllableAnimator.getWordGlow(0f), 0.0001f)
        assertEquals(0.5f, SyllableAnimator.getWordGlow(0.15f), 0.001f)
        assertEquals(1.0f, SyllableAnimator.getWordGlow(0.30f), 0.0001f)
        assertEquals(1.0f, SyllableAnimator.getWordGlow(0.50f), 0.0001f)
        assertEquals(1.0f, SyllableAnimator.getWordGlow(0.70f), 0.0001f)
        assertEquals(0f, SyllableAnimator.getWordGlow(1f), 0.0001f)
    }

    @Test
    fun testTunedHeldWordThresholdRespondsToOverrides() {
        Tuning.heldWordMinDurationMs = 1500L
        assertFalse(SyllableAnimator.isHeldWord(1400L))
        assertTrue(SyllableAnimator.isHeldWord(1500L))
        assertTrue(SyllableAnimator.isHeldWord(1600L))
    }

    @Test
    fun testTunedFalloffPowerRespondsToOverrides() {
        Tuning.letterFalloffPower = 2f
        // With power 2: 1 / (1 + 2^2) = 1/5 = 0.20
        assertEquals(0.20f, SyllableAnimator.getRippleFalloff(2), 0.001f)
    }

    @Test
    fun testTunedHeldLetterScalePeakRespondsToOverrides() {
        Tuning.heldWordLetterScalePeak = 1.25f
        assertEquals(1.25f, SyllableAnimator.getHeldWordLetterScale(0.60f, 0), 0.001f)
    }

    @Test
    fun testTunedMotionAmplitudeRespondsToOverrides() {
        Tuning.wordMotionMinAmplitude = 0.20f
        Tuning.wordMotionMinDurationMs = 200L
        Tuning.wordMotionMaxDurationMs = 600L

        assertEquals(0.20f, SyllableAnimator.getMotionAmplitude(200L), 0.001f)
        assertEquals(0.20f, SyllableAnimator.getMotionAmplitude(100L), 0.001f)
        assertEquals(1.00f, SyllableAnimator.getMotionAmplitude(600L), 0.001f)
        assertEquals(1.00f, SyllableAnimator.getMotionAmplitude(700L), 0.001f)
    }

    @Test
    fun testTunedMotionDurationFloorRespondsToOverrides() {
        Tuning.wordMotionDurationFloorMs = 600L
        val motionEnd = SyllableAnimator.getMotionWordEnd(1000L, 1100L, 5000L)
        assertTrue(motionEnd >= 1600L)
    }

    @Test
    fun testTunedRiseDurationRespondsToOverrides() {
        Tuning.wordRiseDurationMs = 400L
        val windowMs = 1000L
        var peakP = 0f
        var peakScale = Float.NEGATIVE_INFINITY
        for (i in 0..1000) {
            val p = i / 1000f
            val s = SyllableAnimator.getWordMotionScale(p, motionWindowMs = windowMs)
            if (s > peakScale) {
                peakScale = s
                peakP = p
            }
        }
        val peakTimeMs = peakP * windowMs
        assertEquals(400f, peakTimeMs, 10f)
    }

    @Test
    fun testTunedLeadInRespondsToOverrides() {
        Tuning.wordLeadInMs = 80L
        val motionStart = SyllableAnimator.getMotionWordStart(1000L, 800L, 850L)
        assertEquals(920L, motionStart)
    }

    @Test
    fun testTunedScaleEaseInFractionRespondsToOverrides() {
        Tuning.wordScaleEaseInFraction = 0.30f
        Tuning.wordScalePeakPosition = 0.50f
        Tuning.wordScaleStart = 0.92f

        assertEquals(1.0f, SyllableAnimator.getWordMotionScale(0f), 0.0001f)
        assertEquals(0.92f, SyllableAnimator.getWordMotionScale(0.15f), 0.001f)
    }

    @Test
    fun testWordLiftNeverNegativeAcrossPeakPositions() {
        val peakPositions = listOf(0.10f, 0.20f, 0.34f, 0.50f, 0.66f, 0.75f, 0.90f)
        val peakFraction = 0.05f
        val textSize = 100f

        try {
            for (pos in peakPositions) {
                Tuning.wordLiftPeakFraction = peakFraction
                Tuning.wordLiftPeakPosition = pos

                for (i in 0..1000) {
                    val p = i / 1000f
                    val lift = SyllableAnimator.getWordLift(p, textSize)
                    assertTrue("Lift at p=$p with peakPos=$pos must be >= -0.0001 (was $lift)", lift >= -0.0001f)
                }
            }
        } finally {
            Tuning.resetAll()
        }
    }

    @Test
    fun testWordLiftUpperBoundAcrossPeakPositions() {
        val peakPositions = listOf(0.10f, 0.20f, 0.34f, 0.50f, 0.66f, 0.75f, 0.90f)
        val peakFraction = 0.05f
        val textSize = 100f
        val expectedPeak = peakFraction * textSize

        try {
            for (pos in peakPositions) {
                Tuning.wordLiftPeakFraction = peakFraction
                Tuning.wordLiftPeakPosition = pos

                for (i in 0..1000) {
                    val p = i / 1000f
                    val lift = SyllableAnimator.getWordLift(p, textSize)
                    assertTrue("Lift at p=$p with peakPos=$pos must be <= peak + 0.0001 (was $lift)", lift <= expectedPeak + 0.0001f)
                }
            }
        } finally {
            Tuning.resetAll()
        }
    }

    @Test
    fun testWordLiftPeakHeightAtPeakPosition() {
        val peakPositions = listOf(0.10f, 0.20f, 0.34f, 0.50f, 0.66f, 0.75f, 0.90f)
        val peakFraction = 0.05f
        val textSize = 100f
        val expectedPeak = peakFraction * textSize

        try {
            for (pos in peakPositions) {
                Tuning.wordLiftPeakFraction = peakFraction
                Tuning.wordLiftPeakPosition = pos

                assertEquals(expectedPeak, SyllableAnimator.getWordLift(pos, textSize), 0.001f)
                assertEquals(expectedPeak, SyllableAnimator.getWordLift(pos, textSize, peakFraction, pos), 0.001f)
            }
        } finally {
            Tuning.resetAll()
        }
    }

    @Test
    fun testWordLiftEndpointsZeroAcrossPeakPositions() {
        val peakPositions = listOf(0.10f, 0.20f, 0.34f, 0.50f, 0.66f, 0.75f, 0.90f)
        val peakFraction = 0.05f
        val textSize = 100f

        try {
            for (pos in peakPositions) {
                Tuning.wordLiftPeakFraction = peakFraction
                Tuning.wordLiftPeakPosition = pos

                assertEquals(0f, SyllableAnimator.getWordLift(0f, textSize), 0.0001f)
                assertEquals(0f, SyllableAnimator.getWordLift(1f, textSize), 0.0001f)
                assertEquals(0f, SyllableAnimator.getWordLift(0f, textSize, peakFraction, pos), 0.0001f)
                assertEquals(0f, SyllableAnimator.getWordLift(1f, textSize, peakFraction, pos), 0.0001f)
            }
        } finally {
            Tuning.resetAll()
        }
    }

    @Test
    fun testWordLiftMonotonicRiseAndFallAtLatePeakPosition() {
        val peakPos = 0.90f
        val peakFraction = 0.05f
        val textSize = 100f

        try {
            Tuning.wordLiftPeakFraction = peakFraction
            Tuning.wordLiftPeakPosition = peakPos

            var prevLift = SyllableAnimator.getWordLift(0f, textSize)
            for (i in 1..1000) {
                val p = i / 1000f
                val lift = SyllableAnimator.getWordLift(p, textSize)
                if (p <= peakPos) {
                    assertTrue("Lift must rise monotonically up to peak at p=$p (prev=$prevLift, curr=$lift)", lift >= prevLift)
                } else {
                    assertTrue("Lift must fall monotonically after peak at p=$p (prev=$prevLift, curr=$lift)", lift <= prevLift)
                }
                prevLift = lift
            }
        } finally {
            Tuning.resetAll()
        }
    }
}
