package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LetterSpringTest {

    @Before
    fun setUp() {
        Tuning.resetAll()
    }

    @org.junit.After
    fun tearDown() {
        Tuning.resetAll()
    }

    @Test
    fun testActiveIndexAndLetterProgressOverEqualSlots() {
        val n = 4

        assertEquals(0, SyllableAnimator.getSpringActiveLetterIndex(0f, n))
        assertEquals(0f, SyllableAnimator.getSpringActiveLetterProgress(0f, n), 0.0001f)

        assertEquals(0, SyllableAnimator.getSpringActiveLetterIndex(0.249f, n))
        assertEquals(0.996f, SyllableAnimator.getSpringActiveLetterProgress(0.249f, n), 0.001f)

        assertEquals(1, SyllableAnimator.getSpringActiveLetterIndex(0.25f, n))
        assertEquals(0f, SyllableAnimator.getSpringActiveLetterProgress(0.25f, n), 0.0001f)

        assertEquals(2, SyllableAnimator.getSpringActiveLetterIndex(0.625f, n))
        assertEquals(0.5f, SyllableAnimator.getSpringActiveLetterProgress(0.625f, n), 0.0001f)

        assertEquals(3, SyllableAnimator.getSpringActiveLetterIndex(0.75f, n))
        assertEquals(0f, SyllableAnimator.getSpringActiveLetterProgress(0.75f, n), 0.0001f)

        assertEquals(-1, SyllableAnimator.getSpringActiveLetterIndex(1.0f, n))
        assertEquals(-1, SyllableAnimator.getSpringActiveLetterIndex(1.5f, n))
        assertEquals(1.0f, SyllableAnimator.getSpringActiveLetterProgress(1.0f, n), 0.0001f)

        assertEquals(0, SyllableAnimator.getSpringActiveLetterIndex(0.5f, 1))
        assertEquals(0.5f, SyllableAnimator.getSpringActiveLetterProgress(0.5f, 1), 0.0001f)
        assertEquals(-1, SyllableAnimator.getSpringActiveLetterIndex(1.0f, 1))

        assertEquals(-1, SyllableAnimator.getSpringActiveLetterIndex(Float.NaN, n))
        assertEquals(0f, SyllableAnimator.getSpringActiveLetterProgress(Float.NaN, n), 0.0001f)

        assertEquals(-1, SyllableAnimator.getSpringActiveLetterIndex(-0.1f, n))
        assertEquals(0f, SyllableAnimator.getSpringActiveLetterProgress(-0.1f, n), 0.0001f)
    }

    @Test
    fun testLetterEndLeadTiming() {
        val wordDuration600 = 600L
        val lead250 = 250L

        val pFinish600 = 350f / 600f
        val qFinish600 = SyllableAnimator.getSpringLetterProgress(pFinish600, wordDuration600, lead250)
        assertEquals(1.0f, qFinish600, 0.0001f)

        val pHalf600 = 175f / 600f
        val qHalf600 = SyllableAnimator.getSpringLetterProgress(pHalf600, wordDuration600, lead250)
        assertEquals(0.5f, qHalf600, 0.0001f)

        val pPast600 = 400f / 600f
        val qPast600 = SyllableAnimator.getSpringLetterProgress(pPast600, wordDuration600, lead250)
        assertEquals(1.0f, qPast600, 0.0001f)

        // 300 ms lead on a 400 ms word: 400 - 300 = 100 ms, but capped at 400 * 0.5 = 200 ms
        val wordDuration400 = 400L
        val lead300 = 300L
        val pFinish400 = 200f / 400f
        val qFinish400 = SyllableAnimator.getSpringLetterProgress(pFinish400, wordDuration400, lead300)
        assertEquals(1.0f, qFinish400, 0.0001f)

        val pHalf400 = 100f / 400f
        val qHalf400 = SyllableAnimator.getSpringLetterProgress(pHalf400, wordDuration400, lead300)
        assertEquals(0.5f, qHalf400, 0.0001f)

        assertEquals(0f, SyllableAnimator.getSpringLetterProgress(Float.NaN, wordDuration600, lead250), 0.0001f)
    }

    @Test
    fun testSplitWordGivesSameActiveLetterAsUnsplitWord() {
        val totalChars = 8
        val durationMs = 800L
        val leadMs = 250L

        val part1StartProp = 0f
        val part1EndProp = 0.5f
        val part2StartProp = 0.5f
        val part2EndProp = 1.0f

        val testProgresses = floatArrayOf(0.1f, 0.25f, 0.4f, 0.55f, 0.7f, 0.9f)
        for (wholeWordP in testProgresses) {
            val qUnsplit = SyllableAnimator.getSpringLetterProgress(wholeWordP, durationMs, leadMs)
            val activeIndexUnsplit = SyllableAnimator.getSpringActiveLetterIndex(qUnsplit, totalChars)

            val activeIndexRecovered: Int = if (wholeWordP < 0.5f) {
                val part1Linear = (wholeWordP - part1StartProp) / (part1EndProp - part1StartProp)
                val recoveredP = part1StartProp + part1Linear * (part1EndProp - part1StartProp)
                val qPart1 = SyllableAnimator.getSpringLetterProgress(recoveredP, durationMs, leadMs)
                SyllableAnimator.getSpringActiveLetterIndex(qPart1, totalChars)
            } else {
                val part2Linear = (wholeWordP - part2StartProp) / (part2EndProp - part2StartProp)
                val recoveredP = part2StartProp + part2Linear * (part2EndProp - part2StartProp)
                val qPart2 = SyllableAnimator.getSpringLetterProgress(recoveredP, durationMs, leadMs)
                SyllableAnimator.getSpringActiveLetterIndex(qPart2, totalChars)
            }

            assertEquals(
                "Active letter must match between split and unsplit word at p=$wholeWordP",
                activeIndexUnsplit,
                activeIndexRecovered
            )
        }
    }

    @Test
    fun testTargetsAheadAtRestBehindFollowFalloffAfterWordTargetSung() {
        val n = 5
        val falloffPower = 3f

        val scalePeak = 1.20f
        val scaleSung = 1.04f
        val liftPeak = 0.055f
        val liftSung = 0.02f
        val textSize = 100f

        val q = 0.45f
        val activeIndex = SyllableAnimator.getSpringActiveLetterIndex(q, n)
        assertEquals(2, activeIndex)
        val t = SyllableAnimator.getSpringActiveLetterProgress(q, n)

        val activeTargetScale = SyllableAnimator.getSpringLetterTargetScale(
            q = q,
            codePointIndex = 2,
            codePointCount = n,
            scalePeak = scalePeak,
            scaleSung = scaleSung,
            falloffPower = falloffPower
        )
        val expectedActiveScale = SyllableAnimator.getSpringLetterScaleCurve(t, scalePeak, scaleSung)
        assertEquals(expectedActiveScale, activeTargetScale, 0.0001f)

        for (k in 3 until n) {
            val scaleAhead = SyllableAnimator.getSpringLetterTargetScale(
                q = q,
                codePointIndex = k,
                codePointCount = n,
                scalePeak = scalePeak,
                scaleSung = scaleSung,
                falloffPower = falloffPower
            )
            val liftAhead = SyllableAnimator.getSpringLetterTargetLift(
                q = q,
                codePointIndex = k,
                codePointCount = n,
                textSize = textSize,
                liftPeak = liftPeak,
                liftSung = liftSung,
                falloffPower = falloffPower
            )
            assertEquals("Letter ahead $k must have target scale 1.0", 1.0f, scaleAhead, 0.0001f)
            assertEquals("Letter ahead $k must have target lift 0.0", 0.0f, liftAhead, 0.0001f)
        }

        for (k in 0 until 2) {
            val d = 2 - k
            val falloff = SyllableAnimator.getRippleFalloff(d.toFloat(), falloffPower)
            val expectedBehindScale = 1.0f + (expectedActiveScale - 1.0f) * falloff

            val scaleBehind = SyllableAnimator.getSpringLetterTargetScale(
                q = q,
                codePointIndex = k,
                codePointCount = n,
                scalePeak = scalePeak,
                scaleSung = scaleSung,
                falloffPower = falloffPower
            )
            assertEquals("Letter behind $k must follow falloff", expectedBehindScale, scaleBehind, 0.0001f)
        }

        val qSung = 1.0f
        for (k in 0 until n) {
            val scalePost = SyllableAnimator.getSpringLetterTargetScale(
                q = qSung,
                codePointIndex = k,
                codePointCount = n,
                scalePeak = scalePeak,
                scaleSung = scaleSung,
                falloffPower = falloffPower
            )
            val liftPost = SyllableAnimator.getSpringLetterTargetLift(
                q = qSung,
                codePointIndex = k,
                codePointCount = n,
                textSize = textSize,
                liftPeak = liftPeak,
                liftSung = liftSung,
                falloffPower = falloffPower
            )
            assertEquals("Letter $k after sung must target scaleSung", scaleSung, scalePost, 0.0001f)
            assertEquals("Letter $k after sung must target liftSung", liftSung * textSize, liftPost, 0.0001f)
        }
    }

    @Test
    fun testSpringStepPhysicsRegimesAndClamping() {
        val out = FloatArray(2)

        val initialPos = 1.0f
        val initialVel = 0.5f
        SyllableAnimator.stepSpring(
            currentPos = initialPos,
            currentVel = initialVel,
            targetPos = 2.0f,
            frequencyHz = 1.0f,
            dampingRatio = 0.7f,
            dtSeconds = 0f,
            outState = out,
            offset = 0
        )
        assertEquals(initialPos, out[0], 0.0001f)
        assertEquals(initialVel, out[1], 0.0001f)

        val outNormal = FloatArray(2)
        val outLarge = FloatArray(2)
        SyllableAnimator.stepSpring(
            currentPos = 0f,
            currentVel = 0f,
            targetPos = 1f,
            frequencyHz = 1.5f,
            dampingRatio = 0.6f,
            dtSeconds = 1f / 30f,
            outState = outNormal,
            offset = 0
        )
        SyllableAnimator.stepSpring(
            currentPos = 0f,
            currentVel = 0f,
            targetPos = 1f,
            frequencyHz = 1.5f,
            dampingRatio = 0.6f,
            dtSeconds = 10.0f, // 10 seconds should clamp to 1/30s
            outState = outLarge,
            offset = 0
        )
        assertEquals(outNormal[0], outLarge[0], 0.0001f)
        assertEquals(outNormal[1], outLarge[1], 0.0001f)

        var posUnder = 0f
        var velUnder = 0f
        var didOvershoot = false
        val dtStep = 0.01f
        for (step in 0 until 500) {
            SyllableAnimator.stepSpring(
                currentPos = posUnder,
                currentVel = velUnder,
                targetPos = 1.0f,
                frequencyHz = 1.5f,
                dampingRatio = 0.40f,
                dtSeconds = dtStep,
                outState = out,
                offset = 0
            )
            posUnder = out[0]
            velUnder = out[1]
            if (posUnder > 1.001f) {
                didOvershoot = true
            }
        }
        assertTrue("Underdamped spring must overshoot target", didOvershoot)
        assertEquals("Underdamped spring must converge to target", 1.0f, posUnder, 0.001f)
        assertEquals("Underdamped spring velocity must converge to 0", 0.0f, velUnder, 0.001f)

        var posCritical = 0f
        var velCritical = 0f
        var criticalMax = 0f
        for (step in 0 until 500) {
            SyllableAnimator.stepSpring(
                currentPos = posCritical,
                currentVel = velCritical,
                targetPos = 1.0f,
                frequencyHz = 1.5f,
                dampingRatio = 1.0f,
                dtSeconds = dtStep,
                outState = out,
                offset = 0
            )
            posCritical = out[0]
            velCritical = out[1]
            if (posCritical > criticalMax) criticalMax = posCritical
        }
        assertTrue("Critically damped spring must not overshoot target", criticalMax <= 1.0001f)
        assertEquals("Critically damped spring must converge to target", 1.0f, posCritical, 0.001f)

        var posOver = 0f
        var velOver = 0f
        var overMax = 0f
        for (step in 0 until 500) {
            SyllableAnimator.stepSpring(
                currentPos = posOver,
                currentVel = velOver,
                targetPos = 1.0f,
                frequencyHz = 1.5f,
                dampingRatio = 1.5f,
                dtSeconds = dtStep,
                outState = out,
                offset = 0
            )
            posOver = out[0]
            velOver = out[1]
            if (posOver > overMax) overMax = posOver
        }
        assertTrue("Overdamped spring must not overshoot target", overMax <= 1.0001f)
        assertEquals("Overdamped spring must converge to target", 1.0f, posOver, 0.001f)
    }

    @Test
    fun testCurveKeysExactValues() {
        val scalePeak = 1.20f
        val scaleSung = 1.04f

        assertEquals(1.00f, SyllableAnimator.getSpringLetterScaleCurve(0f, scalePeak, scaleSung), 0.0001f)
        assertEquals(scalePeak, SyllableAnimator.getSpringLetterScaleCurve(0.7f, scalePeak, scaleSung), 0.0001f)
        assertEquals(scaleSung, SyllableAnimator.getSpringLetterScaleCurve(1.0f, scalePeak, scaleSung), 0.0001f)

        val liftPeak = 0.055f
        val liftSung = 0.02f

        assertEquals(0.00f, SyllableAnimator.getSpringLetterLiftCurve(0f, liftPeak, liftSung), 0.0001f)
        assertEquals(liftPeak, SyllableAnimator.getSpringLetterLiftCurve(0.9f, liftPeak, liftSung), 0.0001f)
        assertEquals(liftSung, SyllableAnimator.getSpringLetterLiftCurve(1.0f, liftPeak, liftSung), 0.0001f)
    }

    @Test
    fun testHeldWordThresholdDefaultIs600() {
        assertEquals(600f, Tuning.HELD_WORD_MIN_DURATION_MS.defaultValue, 0.0001f)
        assertEquals(600L, Tuning.heldWordMinDurationMs)
        assertEquals(600L, SyllableAnimator.HELD_WORD_MIN_DURATION_MS)
    }

    @Test
    fun testMode2NeverRequestsGlowDraws() {
        assertEquals(1, WordMotionSpan.computeBlurredDrawCount(hasGlow = true, isHeld = true, codePointCount = 5, letterAnimation = 0))
        assertEquals(1, WordMotionSpan.computeBlurredDrawCount(hasGlow = true, isHeld = true, codePointCount = 5, letterAnimation = 1))
        assertEquals(0, WordMotionSpan.computeBlurredDrawCount(hasGlow = true, isHeld = true, codePointCount = 5, letterAnimation = 2))
    }

    @Test
    fun testNewTunablesDefaultsAndBounds() {
        assertEquals(2f, Tuning.LETTER_ANIMATION.defaultValue, 0.0001f)
        assertEquals(2, Tuning.letterAnimation)
        assertEquals(2f, Tuning.LETTER_ANIMATION.max, 0.0001f)
        assertFalse("isSequentialLetterAnimation must be false when letterAnimation == 2", Tuning.isSequentialLetterAnimation)

        assertEquals(250f, Tuning.LETTER_END_LEAD_MS.defaultValue, 0.0001f)
        assertEquals(250L, Tuning.letterEndLeadMs)
        assertEquals(0f, Tuning.LETTER_END_LEAD_MS.min, 0.0001f)
        assertEquals(500f, Tuning.LETTER_END_LEAD_MS.max, 0.0001f)
        assertTrue(Tuning.LETTER_END_LEAD_MS.isInteger)

        assertEquals(1.20f, Tuning.LETTER_SCALE_PEAK.defaultValue, 0.0001f)
        assertEquals(1.20f, Tuning.letterScalePeak, 0.0001f)
        assertEquals(1.00f, Tuning.LETTER_SCALE_PEAK.min, 0.0001f)
        assertEquals(1.40f, Tuning.LETTER_SCALE_PEAK.max, 0.0001f)

        assertEquals(1.00f, Tuning.LETTER_SCALE_SUNG.defaultValue, 0.0001f)
        assertEquals(1.00f, Tuning.letterScaleSung, 0.0001f)
        assertEquals(1.00f, Tuning.LETTER_SCALE_SUNG.min, 0.0001f)
        assertEquals(1.15f, Tuning.LETTER_SCALE_SUNG.max, 0.0001f)

        assertEquals(0.055f, Tuning.LETTER_LIFT_PEAK.defaultValue, 0.0001f)
        assertEquals(0.055f, Tuning.letterLiftPeak, 0.0001f)
        assertEquals(0.00f, Tuning.LETTER_LIFT_PEAK.min, 0.0001f)
        assertEquals(0.15f, Tuning.LETTER_LIFT_PEAK.max, 0.0001f)

        assertEquals(0.00f, Tuning.LETTER_LIFT_SUNG.defaultValue, 0.0001f)
        assertEquals(0.00f, Tuning.letterLiftSung, 0.0001f)
        assertEquals(0.00f, Tuning.LETTER_LIFT_SUNG.min, 0.0001f)
        assertEquals(0.08f, Tuning.LETTER_LIFT_SUNG.max, 0.0001f)

        assertEquals(0.9f, Tuning.LETTER_SCALE_SPRING_HZ.defaultValue, 0.0001f)
        assertEquals(0.9f, Tuning.letterScaleSpringHz, 0.0001f)
        assertEquals(0.3f, Tuning.LETTER_SCALE_SPRING_HZ.min, 0.0001f)
        assertEquals(3.0f, Tuning.LETTER_SCALE_SPRING_HZ.max, 0.0001f)

        assertEquals(0.65f, Tuning.LETTER_SCALE_DAMPING.defaultValue, 0.0001f)
        assertEquals(0.65f, Tuning.letterScaleDamping, 0.0001f)
        assertEquals(0.2f, Tuning.LETTER_SCALE_DAMPING.min, 0.0001f)
        assertEquals(1.5f, Tuning.LETTER_SCALE_DAMPING.max, 0.0001f)

        assertEquals(1.45f, Tuning.LETTER_LIFT_SPRING_HZ.defaultValue, 0.0001f)
        assertEquals(1.45f, Tuning.letterLiftSpringHz, 0.0001f)
        assertEquals(0.3f, Tuning.LETTER_LIFT_SPRING_HZ.min, 0.0001f)
        assertEquals(3.0f, Tuning.LETTER_LIFT_SPRING_HZ.max, 0.0001f)

        assertEquals(0.40f, Tuning.LETTER_LIFT_DAMPING.defaultValue, 0.0001f)
        assertEquals(0.40f, Tuning.letterLiftDamping, 0.0001f)
        assertEquals(0.2f, Tuning.LETTER_LIFT_DAMPING.min, 0.0001f)
        assertEquals(1.5f, Tuning.LETTER_LIFT_DAMPING.max, 0.0001f)
    }

    @Test
    fun testSungTargetsEqualRestForEveryLetterByDefault() {
        val n = 5
        val textSize = 100f
        val qSung = 1.0f

        assertEquals(1.00f, Tuning.LETTER_SCALE_SUNG.defaultValue, 0.0001f)
        assertEquals(0.00f, Tuning.LETTER_LIFT_SUNG.defaultValue, 0.0001f)

        for (k in 0 until n) {
            val scaleTarget = SyllableAnimator.getSpringLetterTargetScale(
                q = qSung,
                codePointIndex = k,
                codePointCount = n
            )
            val liftTarget = SyllableAnimator.getSpringLetterTargetLift(
                q = qSung,
                codePointIndex = k,
                codePointCount = n,
                textSize = textSize
            )
            assertEquals("Letter $k default sung scale target must equal 1.0", 1.00f, scaleTarget, 0.0001f)
            assertEquals("Letter $k default sung lift target must equal 0.0", 0.00f, liftTarget, 0.0001f)
        }
    }

    @Test
    fun testSpringPathWordScaleAtEndMatchesFlatSettledScale() {
        val motionWindows = longArrayOf(480L, 740L, 977L, 1900L)
        val exitFades = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        val amplitudes = floatArrayOf(0.4f, 0.7f, 1.0f)

        for (motionWindowMs in motionWindows) {
            for (exitFade in exitFades) {
                for (amplitude in amplitudes) {
                    val effectivePeak = Tuning.wordScaleSettle + (Tuning.wordScalePeak - Tuning.wordScaleSettle) * amplitude
                    val absoluteScale = SyllableAnimator.getWordMotionScale(
                        linearProgress = 1.0f,
                        motionWindowMs = motionWindowMs,
                        riseDurationMs = Tuning.wordRiseDurationMs,
                        startScale = Tuning.wordScaleStart,
                        peakScale = effectivePeak,
                        peakPosition = Tuning.wordScalePeakPosition,
                        easeInFraction = Tuning.wordScaleEaseInFraction,
                        settleDurationMs = Tuning.wordSettleDurationMs,
                        settleScale = Tuning.wordScaleSettle
                    )
                    val springWordScale = SyllableAnimator.toLineRelativeScale(absoluteScale, Tuning.wordScaleStart, exitFade)
                    val flatSettledScale = SyllableAnimator.toLineRelativeScale(Tuning.wordScaleSettle, Tuning.wordScaleStart, exitFade)

                    assertEquals(
                        "Spring path word scale at progress=1 must match flat settled scale",
                        flatSettledScale,
                        springWordScale,
                        0.0001f
                    )
                }
            }
        }

        val codePointCount = 6
        val textSize = 80f
        for (k in 0 until codePointCount) {
            val scaleTarget = SyllableAnimator.getSpringLetterTargetScale(1.0f, k, codePointCount)
            val liftTarget = SyllableAnimator.getSpringLetterTargetLift(1.0f, k, codePointCount, textSize)
            assertEquals("Settled scale target must be 1.0", 1.0f, scaleTarget, 0.0001f)
            assertEquals("Settled lift target must be 0.0", 0.0f, liftTarget, 0.0001f)
        }
    }

    @Test
    fun testSettlePredicateReturnsFalseMidBounceAndTrueOnceConverged() {
        val targetPos = 0.0f
        val posEpsilon = SyllableAnimator.SPRING_SETTLE_LIFT_POS_EPSILON
        val velEpsilon = SyllableAnimator.SPRING_SETTLE_LIFT_VEL_EPSILON

        val midBouncePos = 3.0f
        val midBounceVel = 10.0f
        assertFalse(
            "Mid bounce state must not be reported as settled",
            SyllableAnimator.isSpringSettled(midBouncePos, midBounceVel, targetPos, posEpsilon, velEpsilon)
        )

        var currentPos = 3.0f
        var currentVel = 10.0f
        val frequencyHz = Tuning.letterLiftSpringHz
        val dampingRatio = Tuning.letterLiftDamping
        val dt = 0.016f
        val outState = FloatArray(2)

        var settledTimeMs = -1L
        val maxSimTimeMs = 2000L
        val maxSteps = (maxSimTimeMs / (dt * 1000f)).toInt()

        var consecutiveSettledSteps = 0
        for (step in 1..maxSteps) {
            SyllableAnimator.stepSpring(
                currentPos = currentPos,
                currentVel = currentVel,
                targetPos = targetPos,
                frequencyHz = frequencyHz,
                dampingRatio = dampingRatio,
                dtSeconds = dt,
                outState = outState,
                offset = 0
            )
            currentPos = outState[0]
            currentVel = outState[1]

            val isSettled = SyllableAnimator.isSpringSettled(
                currentPos,
                currentVel,
                targetPos,
                posEpsilon,
                velEpsilon
            )

            if (isSettled) {
                consecutiveSettledSteps++
                if (consecutiveSettledSteps >= 3 && settledTimeMs < 0L) {
                    settledTimeMs = (step * dt * 1000f).toLong()
                }
            } else {
                consecutiveSettledSteps = 0
            }
        }

        assertTrue("Spring must report settled once converged", consecutiveSettledSteps > 0)
        assertTrue(
            "Lift spring must settle within bounded time 1200 ms (measured $settledTimeMs ms)",
            settledTimeMs in 1L..1200L
        )
    }
}
