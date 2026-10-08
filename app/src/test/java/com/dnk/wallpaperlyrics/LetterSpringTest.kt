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
    fun testSplitWordGivesSameActiveLetterAsUnsplitWord() {
        val totalChars = 8
        val testProgresses = floatArrayOf(0.1f, 0.25f, 0.4f, 0.55f, 0.7f, 0.9f)
        for (wholeWordP in testProgresses) {
            val qUnsplit = SyllableAnimator.getSpringLetterProgress(wholeWordP)
            val activeIndexUnsplit = SyllableAnimator.getSpringActiveLetterIndex(qUnsplit, totalChars)

            val part1StartProp = 0f
            val part1EndProp = 0.5f
            val part2StartProp = 0.5f
            val part2EndProp = 1.0f

            val activeIndexRecovered: Int = if (wholeWordP < 0.5f) {
                val part1Linear = (wholeWordP - part1StartProp) / (part1EndProp - part1StartProp)
                val recoveredP = part1StartProp + part1Linear * (part1EndProp - part1StartProp)
                val qPart1 = SyllableAnimator.getSpringLetterProgress(recoveredP)
                SyllableAnimator.getSpringActiveLetterIndex(qPart1, totalChars)
            } else {
                val part2Linear = (wholeWordP - part2StartProp) / (part2EndProp - part2StartProp)
                val recoveredP = part2StartProp + part2Linear * (part2EndProp - part2StartProp)
                val qPart2 = SyllableAnimator.getSpringLetterProgress(recoveredP)
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
    fun testLetterAnimationTunable() {
        assertEquals(2f, Tuning.LETTER_ANIMATION.defaultValue, 0.0001f)
        assertEquals(2, Tuning.letterAnimation)
        assertEquals(0f, Tuning.LETTER_ANIMATION.min, 0.0001f)
        assertEquals(2f, Tuning.LETTER_ANIMATION.max, 0.0001f)
        assertTrue(Tuning.LETTER_ANIMATION.isInteger)
        assertFalse("isSequentialLetterAnimation must be false when letterAnimation == 2", Tuning.isSequentialLetterAnimation)
        assertTrue("isSpringLetterAnimation must be true when letterAnimation == 2", Tuning.isSpringLetterAnimation)
    }

    @Test
    fun testWordScaleAtEndMatchesFlatSettledScale() {
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
                        "Word scale at progress=1 must match flat settled scale",
                        flatSettledScale,
                        springWordScale,
                        0.0001f
                    )
                }
            }
        }
    }

    enum class LifecyclePath {
        UNSTARTED,
        LETTER_MOTION,
        FLAT_SETTLED,
        EXIT,
        BAKED
    }

    data class FrameRecord(
        val frameIndex: Int,
        val timeMs: Long,
        val path: LifecyclePath,
        val wordScale: Float,
        val letterScales: FloatArray,
        val letterLifts: FloatArray,
        val baselineYs: FloatArray,
        val topYs: FloatArray,
        val centerXs: FloatArray,
        val letterProgresses: FloatArray = FloatArray(0)
    )

    data class SimulationResult(
        val frames: List<FrameRecord>,
        val maxBaselineJump: Float,
        val maxBaselineJumpFrame: Int,
        val maxBaselineJumpLetter: Int,
        val maxBaselineJumpPathSwitch: String,
        val maxTopJump: Float,
        val maxTopJumpFrame: Int,
        val maxTopJumpLetter: Int,
        val maxTopJumpPathSwitch: String,
        val maxCenterJump: Float,
        val maxCenterJumpFrame: Int,
        val maxCenterJumpLetter: Int,
        val maxCenterJumpPathSwitch: String,
        val peakDrawnLifts: FloatArray,
        val maxPeakDrawnLift: Float
    )

    fun runLifecycleSimulation(
        wordDurationMs: Long,
        letterCount: Int,
        textSize: Float = 60f,
        resyncMidWord: Boolean = false,
        resyncOffsetMs: Long = 30L,
        earlyNextLineMs: Long = 0L,
        dtMs: Float = 16.6667f,
        wordText: String = "test",
        partStartProp: Float = 0f,
        partEndProp: Float = 1f
    ): SimulationResult {
        val ascent = 0.75f * textSize
        val glyphWidth = 20f
        val advance = letterCount * glyphWidth
        val wordCenterX = 100f + advance / 2f
        val lineStartTime = 1000L
        val wordStartT = 1000L
        val wordEndT = wordStartT + wordDurationMs
        val transitionDuration = 200f
        val holdMax = Tuning.lineHoldMaxMs
        val wordOverlapMs = Tuning.wordOverlapMs
        val wordMinAnimationMs = Tuning.wordMinAnimationMs
        val wordMotionTrailFraction = Tuning.wordMotionTrailFraction
        val wordMotionTrailMinMs = Tuning.wordMotionTrailMinMs
        val wordMotionTrailMaxMs = Tuning.wordMotionTrailMaxMs
        val wordMotionDurationFloorMs = Tuning.wordMotionDurationFloorMs
        val wordLeadInMs = Tuning.wordLeadInMs
        val wordRiseDurationMs = Tuning.wordRiseDurationMs
        val wordSettleDurationMs = Tuning.wordSettleDurationMs

        val effectiveFloorMs = SyllableAnimator.getEffectiveMotionFloor(
            wordMotionDurationFloorMs,
            wordLeadInMs,
            wordRiseDurationMs,
            wordSettleDurationMs
        )

        val nextLineStartTime = if (earlyNextLineMs > 0L) wordEndT - earlyNextLineMs else wordEndT + 500L
        val lineEndTime = nextLineStartTime
        val releaseTime = SyllableAnimator.getLineReleaseTime(
            listOf(LyricWord(wordStartT, wordEndT, "test", 0, 4)),
            lineEndTime,
            nextLineStartTime,
            Long.MAX_VALUE,
            holdMax,
            wordMotionTrailFraction,
            wordMotionTrailMinMs,
            wordMotionTrailMaxMs,
            wordOverlapMs,
            wordMinAnimationMs,
            effectiveFloorMs
        )
        val totalSimEndMs = releaseTime + transitionDuration.toLong() + 300L
        val motionStartT = SyllableAnimator.getMotionWordStart(
            wordStartT,
            lineStartTime,
            lineStartTime,
            wordLeadInMs
        )
        val effectiveEndT = SyllableAnimator.getExtendedWordEnd(
            wordStartT,
            wordEndT,
            lineEndTime,
            wordOverlapMs,
            wordMinAnimationMs
        )
        val motionEndT = SyllableAnimator.getMotionWordEnd(
            wordStartT,
            wordEndT,
            lineEndTime,
            wordMotionTrailFraction,
            wordMotionTrailMinMs,
            wordMotionTrailMaxMs,
            wordOverlapMs,
            wordMinAnimationMs,
            effectiveFloorMs,
            maxOverrunMs = holdMax + transitionDuration.toLong()
        )
        val motionWindowMs = Math.max(1L, motionEndT - motionStartT)
        val sweepStartOffsetMs = wordStartT - motionStartT
        val sweepDurationMs = effectiveEndT - wordStartT

        val frames = mutableListOf<FrameRecord>()
        var frameIndex = 0
        var currentSimTime = 800f
        val resyncTimeMs = wordStartT + wordDurationMs / 2

        val peakDrawnLifts = FloatArray(letterCount)

        while (currentSimTime <= totalSimEndMs.toFloat()) {
            val wallTimeMs = currentSimTime.toLong()
            val adjustedPos = if (resyncMidWord && wallTimeMs >= resyncTimeMs) {
                wallTimeMs - resyncOffsetMs
            } else {
                wallTimeMs
            }

            val path: LifecyclePath
            val wordScale: Float
            val letterScales = FloatArray(letterCount)
            val letterLifts = FloatArray(letterCount)
            val baselineYs = FloatArray(letterCount)
            val topYs = FloatArray(letterCount)
            val centerXs = FloatArray(letterCount)
            val letterProgresses = FloatArray(letterCount)

            val amp = SyllableAnimator.getMotionAmplitude(wordDurationMs)
            val effectivePeak = Tuning.wordScaleSettle + (Tuning.wordScalePeak - Tuning.wordScaleSettle) * amp

            if (wallTimeMs < lineStartTime) {
                path = LifecyclePath.BAKED
                wordScale = 1.0f
                for (i in 0 until letterCount) {
                    val lCenterX = 100f + (i + 0.5f) * glyphWidth
                    letterScales[i] = 1.0f
                    letterLifts[i] = 0.0f
                    baselineYs[i] = 0.0f
                    topYs[i] = -ascent
                    centerXs[i] = lCenterX
                }
            } else if (wallTimeMs < nextLineStartTime) {
                val lineRampFraction = LyricsWallpaperService.getLineRampFraction(wallTimeMs - lineStartTime)
                val rawGate = lineStartTime + ((adjustedPos - lineStartTime) * lineRampFraction).toLong()
                val wordGatePos = if (Math.abs(adjustedPos - rawGate) > 3000L) adjustedPos else rawGate

                val motionLinearProgress = when {
                    wordGatePos >= motionEndT -> 1f
                    wordGatePos <= motionStartT -> 0f
                    else -> ((wordGatePos - motionStartT).toFloat() / motionWindowMs.toFloat()).coerceIn(0f, 1f)
                }

                val progress = motionLinearProgress

                if (progress <= 0f) {
                    path = LifecyclePath.UNSTARTED
                    wordScale = 1.0f
                    for (i in 0 until letterCount) {
                        val lCenterX = 100f + (i + 0.5f) * glyphWidth
                        letterScales[i] = 1.0f
                        letterLifts[i] = 0.0f
                        baselineYs[i] = 0.0f
                        topYs[i] = -ascent
                        centerXs[i] = lCenterX
                    }
                } else if (progress >= 1f) {
                    path = LifecyclePath.FLAT_SETTLED
                    val settled = SyllableAnimator.toLineRelativeScale(Tuning.wordScaleSettle, Tuning.wordScaleStart, 0f)
                    wordScale = settled
                    for (i in 0 until letterCount) {
                        val lCenterX = 100f + (i + 0.5f) * glyphWidth
                        letterScales[i] = settled
                        letterLifts[i] = 0.0f
                        letterProgresses[i] = 1.0f
                        baselineYs[i] = 0.0f
                        topYs[i] = -settled * ascent
                        centerXs[i] = wordCenterX + (lCenterX - wordCenterX) * settled
                    }
                } else {
                    path = LifecyclePath.LETTER_MOTION
                    wordScale = 1.0f
                    val elapsedMs = progress * motionWindowMs.toFloat()
                    val propRange = (partEndProp - partStartProp).coerceAtLeast(0f)
                    for (i in 0 until letterCount) {
                        val lCenterX = 100f + (i + 0.5f) * glyphWidth
                        val fLeft = (partStartProp + (i.toFloat() / letterCount.toFloat()) * propRange).coerceIn(0f, 1f)
                        val fRight = (partStartProp + ((i + 1).toFloat() / letterCount.toFloat()) * propRange).coerceIn(0f, 1f)
                        val letterProgress = SyllableAnimator.getLetterMotionProgress(
                            timeMs = elapsedMs,
                            motionWindowMs = motionWindowMs,
                            sweepStartOffsetMs = sweepStartOffsetMs,
                            sweepDurationMs = sweepDurationMs,
                            fractionLeft = fLeft,
                            fractionRight = fRight,
                            wordText = wordText,
                            overlapMs = Tuning.heldWordLetterOverlapMs,
                            peakPosition = Tuning.wordLiftPeakPosition
                        )
                        val letterAbsScale = SyllableAnimator.getWordMotionScale(
                            letterProgress,
                            startScale = Tuning.wordScaleStart,
                            peakScale = effectivePeak,
                            peakPosition = Tuning.wordScalePeakPosition,
                            easeInFraction = Tuning.wordScaleEaseInFraction,
                            settleScale = Tuning.wordScaleSettle
                        )
                        val lScale = SyllableAnimator.toLineRelativeScale(letterAbsScale, Tuning.wordScaleStart, 0f)
                        val lLift = SyllableAnimator.getWordLift(
                            letterProgress,
                            textSize,
                            Tuning.heldWordLetterLiftFraction,
                            Tuning.wordLiftPeakPosition
                        ) * amp

                        letterScales[i] = lScale
                        letterLifts[i] = lLift
                        letterProgresses[i] = letterProgress
                        baselineYs[i] = -lLift
                        topYs[i] = -lLift - lScale * ascent
                        centerXs[i] = wordCenterX + (lCenterX - wordCenterX) * lScale

                        if (lLift > peakDrawnLifts[i]) {
                            peakDrawnLifts[i] = lLift
                        }
                    }
                }
            } else {
                val exitLinear = ((adjustedPos - releaseTime).toFloat() / transitionDuration).coerceIn(0f, 1f)
                val easedExit = 1f - (1f - exitLinear) * (1f - exitLinear)

                if (exitLinear >= 1f) {
                    path = LifecyclePath.BAKED
                    wordScale = 1.0f
                    for (i in 0 until letterCount) {
                        val lCenterX = 100f + (i + 0.5f) * glyphWidth
                        letterScales[i] = 1.0f
                        letterLifts[i] = 0.0f
                        letterProgresses[i] = 1.0f
                        baselineYs[i] = 0.0f
                        topYs[i] = -ascent
                        centerXs[i] = lCenterX
                    }
                } else {
                    val progress = ((adjustedPos - motionStartT).toFloat() / motionWindowMs.toFloat()).coerceIn(0f, 1f)

                    if (progress >= 1f) {
                        path = LifecyclePath.EXIT
                        val settled = SyllableAnimator.toLineRelativeScale(Tuning.wordScaleSettle, Tuning.wordScaleStart, easedExit)
                        wordScale = settled
                        for (i in 0 until letterCount) {
                            val lCenterX = 100f + (i + 0.5f) * glyphWidth
                            letterScales[i] = settled
                            letterLifts[i] = 0.0f
                            letterProgresses[i] = 1.0f
                            baselineYs[i] = 0.0f
                            topYs[i] = -settled * ascent
                            centerXs[i] = wordCenterX + (lCenterX - wordCenterX) * settled
                        }
                    } else {
                        path = LifecyclePath.LETTER_MOTION
                        wordScale = 1.0f
                        val elapsedMs = progress * motionWindowMs.toFloat()
                        val propRange = (partEndProp - partStartProp).coerceAtLeast(0f)
                        for (i in 0 until letterCount) {
                            val lCenterX = 100f + (i + 0.5f) * glyphWidth
                            val fLeft = (partStartProp + (i.toFloat() / letterCount.toFloat()) * propRange).coerceIn(0f, 1f)
                            val fRight = (partStartProp + ((i + 1).toFloat() / letterCount.toFloat()) * propRange).coerceIn(0f, 1f)
                            val letterProgress = SyllableAnimator.getLetterMotionProgress(
                                timeMs = elapsedMs,
                                motionWindowMs = motionWindowMs,
                                sweepStartOffsetMs = sweepStartOffsetMs,
                                sweepDurationMs = sweepDurationMs,
                                fractionLeft = fLeft,
                                fractionRight = fRight,
                                wordText = wordText,
                                overlapMs = Tuning.heldWordLetterOverlapMs,
                                peakPosition = Tuning.wordLiftPeakPosition
                            )
                            val letterAbsScale = SyllableAnimator.getWordMotionScale(
                                letterProgress,
                                startScale = Tuning.wordScaleStart,
                                peakScale = effectivePeak,
                                peakPosition = Tuning.wordScalePeakPosition,
                                easeInFraction = Tuning.wordScaleEaseInFraction,
                                settleScale = Tuning.wordScaleSettle
                            )
                            val lScale = SyllableAnimator.toLineRelativeScale(letterAbsScale, Tuning.wordScaleStart, easedExit)
                            val lLift = SyllableAnimator.getWordLift(
                                letterProgress,
                                textSize,
                                Tuning.heldWordLetterLiftFraction,
                                Tuning.wordLiftPeakPosition
                            ) * amp

                            letterScales[i] = lScale
                            letterLifts[i] = lLift
                            letterProgresses[i] = letterProgress
                            baselineYs[i] = -lLift
                            topYs[i] = -lLift - lScale * ascent
                            centerXs[i] = wordCenterX + (lCenterX - wordCenterX) * lScale

                            if (lLift > peakDrawnLifts[i]) {
                                peakDrawnLifts[i] = lLift
                            }
                        }
                    }
                }
            }

            frames.add(
                FrameRecord(
                    frameIndex = frameIndex,
                    timeMs = wallTimeMs,
                    path = path,
                    wordScale = wordScale,
                    letterScales = letterScales,
                    letterLifts = letterLifts,
                    baselineYs = baselineYs,
                    topYs = topYs,
                    centerXs = centerXs,
                    letterProgresses = letterProgresses
                )
            )

            frameIndex++
            currentSimTime += dtMs
        }

        var maxBaselineJump = 0f
        var maxBaselineJumpFrame = -1
        var maxBaselineJumpLetter = -1
        var maxBaselineJumpPathSwitch = ""

        var maxTopJump = 0f
        var maxTopJumpFrame = -1
        var maxTopJumpLetter = -1
        var maxTopJumpPathSwitch = ""

        var maxCenterJump = 0f
        var maxCenterJumpFrame = -1
        var maxCenterJumpLetter = -1
        var maxCenterJumpPathSwitch = ""

        for (f in 1 until frames.size) {
            val prev = frames[f - 1]
            val curr = frames[f]
            val pathSwitch = if (prev.path != curr.path) "${prev.path} -> ${curr.path}" else curr.path.name

            for (i in 0 until letterCount) {
                val bJump = Math.abs(curr.baselineYs[i] - prev.baselineYs[i])
                if (bJump > maxBaselineJump) {
                    maxBaselineJump = bJump
                    maxBaselineJumpFrame = curr.frameIndex
                    maxBaselineJumpLetter = i
                    maxBaselineJumpPathSwitch = pathSwitch
                }

                val tJump = Math.abs(curr.topYs[i] - prev.topYs[i])
                if (tJump > maxTopJump) {
                    maxTopJump = tJump
                    maxTopJumpFrame = curr.frameIndex
                    maxTopJumpLetter = i
                    maxTopJumpPathSwitch = pathSwitch
                }

                val cJump = Math.abs(curr.centerXs[i] - prev.centerXs[i])
                if (cJump > maxCenterJump) {
                    maxCenterJump = cJump
                    maxCenterJumpFrame = curr.frameIndex
                    maxCenterJumpLetter = i
                    maxCenterJumpPathSwitch = pathSwitch
                }
            }
        }

        val maxPeakLift = peakDrawnLifts.maxOrNull() ?: 0f

        return SimulationResult(
            frames = frames,
            maxBaselineJump = maxBaselineJump,
            maxBaselineJumpFrame = maxBaselineJumpFrame,
            maxBaselineJumpLetter = maxBaselineJumpLetter,
            maxBaselineJumpPathSwitch = maxBaselineJumpPathSwitch,
            maxTopJump = maxTopJump,
            maxTopJumpFrame = maxTopJumpFrame,
            maxTopJumpLetter = maxTopJumpLetter,
            maxTopJumpPathSwitch = maxTopJumpPathSwitch,
            maxCenterJump = maxCenterJump,
            maxCenterJumpFrame = maxCenterJumpFrame,
            maxCenterJumpLetter = maxCenterJumpLetter,
            maxCenterJumpPathSwitch = maxCenterJumpPathSwitch,
            peakDrawnLifts = peakDrawnLifts,
            maxPeakDrawnLift = maxPeakLift
        )
    }

    data class SimCase(
        val durationMs: Long,
        val letterCount: Int,
        val resync: Boolean = false,
        val earlyNextLineMs: Long = 0L,
        val label: String
    )

    @Test
    fun testLifecycleTraceReportNumbers() {
        val cases = listOf(
            SimCase(600L, 3, label = "600ms (3 letters)"),
            SimCase(700L, 4, label = "700ms (4 letters)"),
            SimCase(800L, 5, label = "800ms (5 letters)"),
            SimCase(1200L, 5, label = "1200ms (5 letters)"),
            SimCase(2400L, 2, label = "2400ms (2 letters)"),
            SimCase(2500L, 8, label = "2500ms (8 letters)"),
            SimCase(1200L, 5, resync = true, label = "1200ms resync (30ms)"),
            SimCase(1200L, 5, earlyNextLineMs = 300L, label = "1200ms next line 300ms early")
        )

        println("=== LIFECYCLE TRACE REPORT ===")
        for (c in cases) {
            val res = runLifecycleSimulation(
                c.durationMs,
                c.letterCount,
                resyncMidWord = c.resync,
                earlyNextLineMs = c.earlyNextLineMs
            )
            val normalPeakLift = Tuning.wordLiftPeakFraction * 60f * SyllableAnimator.getMotionAmplitude(c.durationMs)
            val liftPercent = (res.maxPeakDrawnLift / normalPeakLift) * 100f

            println("${c.label}: maxTopJump=${String.format("%.3f", res.maxTopJump)}px (frame ${res.maxTopJumpFrame}, letter ${res.maxTopJumpLetter}, ${res.maxTopJumpPathSwitch}), maxBaselineJump=${String.format("%.3f", res.maxBaselineJump)}px (frame ${res.maxBaselineJumpFrame}, letter ${res.maxBaselineJumpLetter}, ${res.maxBaselineJumpPathSwitch}), maxCenterJump=${String.format("%.3f", res.maxCenterJump)}px (frame ${res.maxCenterJumpFrame}, letter ${res.maxCenterJumpLetter}, ${res.maxCenterJumpPathSwitch}), peakLift=${String.format("%.3f", res.maxPeakDrawnLift)}px / ${String.format("%.3f", normalPeakLift)}px (${String.format("%.1f", liftPercent)}%)")
            val letterTopJumps = FloatArray(c.letterCount)
            for (f in 1 until res.frames.size) {
                val prev = res.frames[f - 1]
                val curr = res.frames[f]
                for (i in 0 until c.letterCount) {
                    val tj = Math.abs(curr.topYs[i] - prev.topYs[i])
                    if (tj > letterTopJumps[i]) letterTopJumps[i] = tj
                }
            }
            val jumpsStr = letterTopJumps.mapIndexed { idx, j -> "L$idx=${String.format("%.3f", j)}px" }.joinToString(", ")
            println("   Per-letter max top jump: $jumpsStr")
            if (c.durationMs == 2500L) {
                println("--- Detailed frames for 2500ms (8 letters) L1 ---")
                for (f in 20..26) {
                    val rec = res.frames[f]
                    val prev = res.frames[f - 1]
                    val topJump = rec.topYs[1] - prev.topYs[1]
                    val baseJump = rec.baselineYs[1] - prev.baselineYs[1]
                    val cJump = rec.centerXs[1] - prev.centerXs[1]
                    println("frame $f: time=${rec.timeMs}ms, p=${rec.letterProgresses[1]}, scale=${rec.letterScales[1]}, lift=${rec.letterLifts[1]}, topY=${rec.topYs[1]} (dTop=${topJump}), dBase=${baseJump}, dCenter=${cJump}")
                }
            }
        }
    }

    @Test
    fun testPathHandoffContinuity() {
        val cases = listOf(
            runLifecycleSimulation(600L, 3),
            runLifecycleSimulation(700L, 4),
            runLifecycleSimulation(800L, 5),
            runLifecycleSimulation(1200L, 5),
            runLifecycleSimulation(2400L, 2),
            runLifecycleSimulation(2500L, 8),
            runLifecycleSimulation(1200L, 5, resyncMidWord = true),
            runLifecycleSimulation(1200L, 5, earlyNextLineMs = 300L)
        )
        for (case in cases) {
            val frames = case.frames
            for (f in 1 until frames.size) {
                val prev = frames[f - 1]
                val curr = frames[f]
                if (prev.path != curr.path) {
                    for (i in prev.topYs.indices) {
                        val topJump = Math.abs(curr.topYs[i] - prev.topYs[i])
                        val baseJump = Math.abs(curr.baselineYs[i] - prev.baselineYs[i])
                        val centerJump = Math.abs(curr.centerXs[i] - prev.centerXs[i])
                        assertTrue(
                            "Path handoff ${prev.path} -> ${curr.path} letter $i top jump must be <= 1.0 px (was $topJump px)",
                            topJump <= 1.0f
                        )
                        assertTrue(
                            "Path handoff ${prev.path} -> ${curr.path} letter $i baseline jump must be <= 1.0 px (was $baseJump px)",
                            baseJump <= 1.0f
                        )
                        assertTrue(
                            "Path handoff ${prev.path} -> ${curr.path} letter $i center jump must be <= 1.0 px (was $centerJump px)",
                            centerJump <= 1.0f
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testLifecycleTraceSmoothnessAndPeakLift() {
        val cases = listOf(
            Triple(600L, 3, 0L),
            Triple(700L, 4, 0L),
            Triple(800L, 5, 0L),
            Triple(1200L, 5, 0L),
            Triple(2400L, 2, 0L),
            Triple(2500L, 8, 0L),
            Triple(1200L, 5, 300L)
        )

        for ((durationMs, letterCount, earlyMs) in cases) {
            val res = runLifecycleSimulation(durationMs, letterCount, earlyNextLineMs = earlyMs)
            val normalPeakLift = Tuning.wordLiftPeakFraction * 60f * SyllableAnimator.getMotionAmplitude(durationMs)

            assertTrue("$durationMs ms max top jump must be <= 1.0 px (was ${res.maxTopJump} px)", res.maxTopJump <= 1.0f)
            assertTrue("$durationMs ms max baseline jump must be <= 0.5 px (was ${res.maxBaselineJump} px)", res.maxBaselineJump <= 0.5f)
            assertTrue("$durationMs ms max center jump must be <= 1.3 px (was ${res.maxCenterJump} px)", res.maxCenterJump <= 1.3f)

            val ratio = res.maxPeakDrawnLift / normalPeakLift
            assertTrue(
                "$durationMs ms peak lift (${res.maxPeakDrawnLift} px) must match normal word peak lift ($normalPeakLift px) within 5% (ratio was $ratio)",
                Math.abs(ratio - 1.0f) <= 0.05f
            )
        }

        val resync = runLifecycleSimulation(1200L, 5, resyncMidWord = true)
        val normalPeak1200 = Tuning.wordLiftPeakFraction * 60f * SyllableAnimator.getMotionAmplitude(1200L)
        assertTrue("1200ms resync max top jump must be <= 1.0 px (was ${resync.maxTopJump} px)", resync.maxTopJump <= 1.0f)
        assertTrue("1200ms resync max baseline jump must be <= 0.5 px (was ${resync.maxBaselineJump} px)", resync.maxBaselineJump <= 0.5f)
        assertTrue("1200ms resync max center jump must be <= 1.3 px (was ${resync.maxCenterJump} px)", resync.maxCenterJump <= 1.3f)
        assertTrue(
            "1200ms resync peak lift (${resync.maxPeakDrawnLift} px) must match normal word ($normalPeak1200 px) within 5%",
            Math.abs(resync.maxPeakDrawnLift / normalPeak1200 - 1.0f) <= 0.05f
        )
    }

    @Test
    fun testLetterPeakAlignmentWithDisplayedSweep() {
        val cases = listOf(
            Triple(2400L, 2, "We"),
            Triple(600L, 3, "test")
        )

        for ((wordDurationMs, letterCount, wordText) in cases) {
            val res = runLifecycleSimulation(wordDurationMs, letterCount, wordText = wordText)
            val sweepStartOffsetMs = 0L
            val sweepDurationMs = wordDurationMs + 50L
            val wordStartMs = 1000L

            for (i in 0 until letterCount) {
                val fLeft = i.toFloat() / letterCount.toFloat()
                val fRight = (i + 1).toFloat() / letterCount.toFloat()
                val fCenter = (fLeft + fRight) * 0.5f

                val tCenterLinear = SyllableAnimator.invertEasedProgress(fCenter, wordText)
                val tCenterMs = sweepStartOffsetMs + tCenterLinear * sweepDurationMs.toFloat()
                val sweepPassWallTimeMs = wordStartMs + tCenterMs

                var maxLift = -1f
                var peakFrameTime = -1L
                for (f in res.frames) {
                    if (f.letterLifts[i] > maxLift) {
                        maxLift = f.letterLifts[i]
                        peakFrameTime = f.timeMs
                    }
                }

                val isFirst = i == 0
                val isLast = i == letterCount - 1
                val minRise = Tuning.wordRiseDurationMs.toFloat()
                val minSettle = Tuning.wordSettleDurationMs.toFloat()
                val motionWindowF = Math.max(1L, SyllableAnimator.getMotionWordEnd(
                    wordStartMs, wordStartMs + wordDurationMs, wordStartMs + wordDurationMs + 500L,
                    Tuning.wordMotionTrailFraction, Tuning.wordMotionTrailMinMs, Tuning.wordMotionTrailMaxMs,
                    Tuning.wordOverlapMs, Tuning.wordMinAnimationMs,
                    SyllableAnimator.getEffectiveMotionFloor(Tuning.wordMotionDurationFloorMs, Tuning.wordLeadInMs, Tuning.wordRiseDurationMs, Tuning.wordSettleDurationMs),
                    maxOverrunMs = Tuning.lineHoldMaxMs + 200L
                ) - wordStartMs).toFloat()

                val expectedPeakT = when {
                    isFirst && tCenterMs < minRise -> minRise
                    isLast && motionWindowF - tCenterMs < minSettle -> motionWindowF - minSettle
                    else -> tCenterMs
                }
                val documentedShiftMs = Math.abs(expectedPeakT - tCenterMs)
                val errorMs = Math.abs(peakFrameTime - sweepPassWallTimeMs)
                val allowedErrorMs = 17f + documentedShiftMs

                assertTrue(
                    "Word $wordDurationMs ms Letter $i lift peak frame ($peakFrameTime ms) must be within allowed shift ($allowedErrorMs ms, documented shift $documentedShiftMs ms) of sweep pass ($sweepPassWallTimeMs ms), error was $errorMs ms",
                    errorMs <= allowedErrorMs
                )
                if (!isFirst && !isLast) {
                    assertEquals(
                        "Interior letter $i must have zero documented shift from sweep pass",
                        0f,
                        documentedShiftMs,
                        0.0001f
                    )
                }
            }
        }
    }

    @Test
    fun testLetterOverlapTunable() {
        val wordDurationMs = 2400L
        val wordText = "We"

        Tuning.heldWordLetterOverlapMs = 200L
        val res200 = runLifecycleSimulation(wordDurationMs, 2, wordText = wordText)
        val wEndTime200 = res200.frames.first { it.letterProgresses[0] >= 1f }.timeMs
        val eStartTime200 = res200.frames.first { it.letterProgresses[1] > 0f }.timeMs
        val overlap200 = wEndTime200 - eStartTime200
        assertTrue(
            "With 200ms overlap, e must start ~200ms before W ends (was $overlap200 ms)",
            Math.abs(overlap200 - 200L) <= 17L
        )

        Tuning.heldWordLetterOverlapMs = 0L
        val res0 = runLifecycleSimulation(wordDurationMs, 2, wordText = wordText)
        val wEndTime0 = res0.frames.first { it.letterProgresses[0] >= 1f }.timeMs
        val eStartTime0 = res0.frames.first { it.letterProgresses[1] > 0f }.timeMs
        val overlap0 = wEndTime0 - eStartTime0
        assertTrue(
            "With 0ms overlap, e must start within one frame of W ending (was $overlap0 ms)",
            Math.abs(overlap0 - 0L) <= 17L
        )

        Tuning.heldWordLetterOverlapMs = 200L
    }

    @Test
    fun testWrappedHeldWordPeakWhenSweepPasses() {
        val wordDurationMs = 2000L
        val wordText = "together"
        val part2Letters = 4
        val partStartProp = 0.5f
        val partEndProp = 1.0f

        val res = runLifecycleSimulation(
            wordDurationMs = wordDurationMs,
            letterCount = part2Letters,
            wordText = wordText,
            partStartProp = partStartProp,
            partEndProp = partEndProp
        )
        val sweepStartOffsetMs = 0L
        val sweepDurationMs = wordDurationMs + 50L
        val wordStartMs = 1000L

        val propRange = partEndProp - partStartProp
        for (i in 0 until part2Letters) {
            val fLeft = partStartProp + (i.toFloat() / part2Letters.toFloat()) * propRange
            val fRight = partStartProp + ((i + 1).toFloat() / part2Letters.toFloat()) * propRange
            val fCenter = (fLeft + fRight) * 0.5f

            val tCenterLinear = SyllableAnimator.invertEasedProgress(fCenter, wordText)
            val tCenterMs = sweepStartOffsetMs + tCenterLinear * sweepDurationMs.toFloat()
            val sweepPassWallTimeMs = wordStartMs + tCenterMs

            var maxLift = -1f
            var peakFrameTime = -1L
            for (f in res.frames) {
                if (f.letterLifts[i] > maxLift) {
                    maxLift = f.letterLifts[i]
                    peakFrameTime = f.timeMs
                }
            }

            val errorMs = Math.abs(peakFrameTime - sweepPassWallTimeMs)
            assertTrue(
                "Wrapped part 2 letter $i lift peak frame ($peakFrameTime ms) must be within one frame (17ms) of sweep pass ($sweepPassWallTimeMs ms), error was $errorMs ms",
                errorMs <= 17f
            )
        }
    }

    @Test
    fun testAllLettersReachProgressOneByMotionEndAndMatchSettledPath() {
        val cases = listOf(
            Pair(600L, 3),
            Pair(700L, 4),
            Pair(800L, 5),
            Pair(1200L, 5),
            Pair(2400L, 2),
            Pair(2500L, 8)
        )
        val wordStartT = 1000L

        for ((durationMs, letterCount) in cases) {
            val res = runLifecycleSimulation(durationMs, letterCount)
            val wordEndT = wordStartT + durationMs
            val motionEndT = SyllableAnimator.getMotionWordEnd(
                wordStartT,
                wordEndT,
                wordEndT + 500L,
                Tuning.wordMotionTrailFraction,
                Tuning.wordMotionTrailMinMs,
                Tuning.wordMotionTrailMaxMs,
                Tuning.wordOverlapMs,
                Tuning.wordMinAnimationMs,
                SyllableAnimator.getEffectiveMotionFloor(
                    Tuning.wordMotionDurationFloorMs,
                    Tuning.wordLeadInMs,
                    Tuning.wordRiseDurationMs,
                    Tuning.wordSettleDurationMs
                ),
                maxOverrunMs = Tuning.lineHoldMaxMs + 200L
            )

            val atEndFrames = res.frames.filter { it.timeMs >= motionEndT }
            assertTrue("Must have frames at or after motionEndT for $durationMs ms", atEndFrames.isNotEmpty())
            val frame = atEndFrames.first()
            for (i in 0 until letterCount) {
                assertEquals(
                    "Letter $i in $durationMs ms must reach progress 1.0 by motionEndT (was ${frame.letterProgresses[i]})",
                    1.0f,
                    frame.letterProgresses[i],
                    0.0001f
                )
                assertEquals(
                    "Letter $i in $durationMs ms lift must be 0 at end (was ${frame.letterLifts[i]})",
                    0.0f,
                    frame.letterLifts[i],
                    0.0001f
                )
            }
        }
    }


    @Test
    fun testLetterMotionEndStateEqualsFlatPathMathLevel() {
        val advance = 320f
        val wordCenterX = 100f + advance / 2f
        val letterCount = 5
        val textSize = 60f
        val ascent = 0.75f * textSize
        val glyphWidth = advance / letterCount

        val restScale = Tuning.wordScaleStart
        val settleScale = Tuning.wordScaleSettle
        val exitFade = 0f
        val settledLineRelative = SyllableAnimator.toLineRelativeScale(settleScale, restScale, exitFade)

        for (i in 0 until letterCount) {
            val letterLeft = 100f + i * glyphWidth
            val letterRight = letterLeft + glyphWidth
            val letterCenterX = (letterLeft + letterRight) * 0.5f

            val letterProgress = 1.0f
            val absScale = SyllableAnimator.getWordMotionScale(
                linearProgress = letterProgress,
                startScale = restScale,
                peakScale = Tuning.wordScalePeak,
                peakPosition = Tuning.wordScalePeakPosition,
                easeInFraction = Tuning.wordScaleEaseInFraction,
                settleScale = settleScale
            )
            val letterScale = SyllableAnimator.toLineRelativeScale(absScale, restScale, exitFade)
            val letterLift = SyllableAnimator.getWordLift(letterProgress, textSize)

            val perLetterCenter = wordCenterX + (letterCenterX - wordCenterX) * letterScale
            val perLetterBaselineY = -letterScale * letterLift
            val perLetterTopY = perLetterBaselineY - letterScale * ascent

            val flatCenter = wordCenterX + (letterCenterX - wordCenterX) * settledLineRelative
            val flatBaselineY = 0f
            val flatTopY = -settledLineRelative * ascent

            assertEquals("Letter $i scale must equal settled scale", settledLineRelative, letterScale, 0.0001f)
            assertEquals("Letter $i lift must be 0", 0f, letterLift, 0.0001f)
            assertEquals("Letter $i center must equal flat path center", flatCenter, perLetterCenter, 0.0001f)
            assertEquals("Letter $i baseline must equal flat path baseline", flatBaselineY, perLetterBaselineY, 0.0001f)
            assertEquals("Letter $i top must equal flat path top", flatTopY, perLetterTopY, 0.0001f)
        }
    }

    @Test
    fun testLetterMotionScaleNoDipBelowSettle() {
        val windowMs = 700L
        val riseMs = 240L
        val settleMs = 460L
        val scaleStart = 0.95f
        val scalePeak = 1.00f
        val scaleSettle = 0.98f
        val peakPos = 0.60f
        val easeIn = 0.20f

        val pPeak = (riseMs.toFloat() / windowMs.toFloat()).coerceIn(0.01f, 0.99f)

        for (step in 0..1000) {
            val p = step / 1000f
            val s = SyllableAnimator.getWordMotionScale(
                linearProgress = p,
                motionWindowMs = windowMs,
                riseDurationMs = riseMs,
                startScale = scaleStart,
                peakScale = scalePeak,
                peakPosition = peakPos,
                easeInFraction = easeIn,
                settleDurationMs = settleMs,
                settleScale = scaleSettle
            )
            if (p >= pPeak) {
                assertTrue("Scale at progress $p after peak must not dip below settle scale $scaleSettle (was $s)", s >= scaleSettle - 0.0001f)
            }
        }
        val finalScale = SyllableAnimator.getWordMotionScale(
            linearProgress = 1.0f,
            motionWindowMs = windowMs,
            riseDurationMs = riseMs,
            startScale = scaleStart,
            peakScale = scalePeak,
            peakPosition = peakPos,
            easeInFraction = easeIn,
            settleDurationMs = settleMs,
            settleScale = scaleSettle
        )
        assertEquals(scaleSettle, finalScale, 0.0001f)
    }

    @Test
    fun testLetterMotionPeakLiftMatchesNormalWordWithinFivePercent() {
        val textSize = 60f
        val wordDurationMs = 1200L
        val amp = SyllableAnimator.getMotionAmplitude(wordDurationMs)
        val normalPeakLift = Tuning.wordLiftPeakFraction * textSize * amp

        var letterMaxLift = 0f
        for (step in 0..1000) {
            val p = step / 1000f
            val lift = SyllableAnimator.getWordLift(p, textSize, Tuning.heldWordLetterLiftFraction, Tuning.wordLiftPeakPosition) * amp
            if (lift > letterMaxLift) {
                letterMaxLift = lift
            }
        }
        val ratio = letterMaxLift / normalPeakLift
        assertTrue("Letter peak lift ($letterMaxLift px) must equal normal word peak lift ($normalPeakLift px) within 5% (ratio was $ratio)", Math.abs(ratio - 1.0f) <= 0.05f)
    }

    @Test
    fun testLetterMotionPeakOrder() {
        val motionWindowMs = 1500L
        val n = 5
        val peakTimes = FloatArray(n)

        for (k in 0 until n) {
            var maxLift = -1f
            var timeAtMaxLift = -1f
            for (step in 0..1500) {
                val tMs = step.toFloat()
                val pLetter = SyllableAnimator.getLetterMotionProgress(
                    timeMs = tMs,
                    motionWindowMs = motionWindowMs,
                    sweepStartOffsetMs = 0L,
                    sweepDurationMs = motionWindowMs,
                    codePointIndex = k,
                    codePointCount = n,
                    wordText = "abcde"
                )
                val lift = SyllableAnimator.getWordLift(pLetter, 60f)
                if (lift > maxLift) {
                    maxLift = lift
                    timeAtMaxLift = tMs
                }
            }
            peakTimes[k] = timeAtMaxLift
        }

        for (k in 0 until n - 1) {
            assertTrue("Letter $k peak (${peakTimes[k]} ms) must occur before letter ${k+1} peak (${peakTimes[k+1]} ms)", peakTimes[k] < peakTimes[k+1])
        }
    }

    @Test
    fun testMode2HeldWordLetterLiftSeparatedFromNormalWordLift() {
        val textSize = 60f
        val wordDurationMs = 1200L
        val amp = SyllableAnimator.getMotionAmplitude(wordDurationMs)

        assertEquals(0.05f, Tuning.wordLiftPeakFraction, 0.0001f)
        assertEquals(0.05f, Tuning.heldWordLetterLiftFraction, 0.0001f)

        val defaultNormalPeakLift = Tuning.wordLiftPeakFraction * textSize * amp
        val defaultRes = runLifecycleSimulation(wordDurationMs, 5, textSize = textSize)
        assertTrue(
            "At defaults, mode 2 peak letter lift matches normal word peak lift within 5%",
            Math.abs(defaultRes.maxPeakDrawnLift / defaultNormalPeakLift - 1.0f) <= 0.05f
        )

        Tuning.heldWordLetterLiftFraction = 0.15f
        Tuning.wordLiftPeakFraction = 0.05f

        val normalPeakLift1 = Tuning.wordLiftPeakFraction * textSize * amp
        assertEquals("Normal word lift must not change when heldWordLetterLiftFraction changes", defaultNormalPeakLift, normalPeakLift1, 0.0001f)

        val res1 = runLifecycleSimulation(wordDurationMs, 5, textSize = textSize)
        val expectedHeldPeakLift1 = 0.15f * textSize * amp
        assertTrue(
            "Mode 2 peak letter lift must scale with heldWordLetterLiftFraction within 5%",
            Math.abs(res1.maxPeakDrawnLift / expectedHeldPeakLift1 - 1.0f) <= 0.05f
        )

        Tuning.heldWordLetterLiftFraction = 0.05f
        Tuning.wordLiftPeakFraction = 0.10f

        val normalPeakLift2 = Tuning.wordLiftPeakFraction * textSize * amp
        assertEquals(0.10f * textSize * amp, normalPeakLift2, 0.0001f)

        val res2 = runLifecycleSimulation(wordDurationMs, 5, textSize = textSize)
        val expectedHeldPeakLift2 = 0.05f * textSize * amp
        assertTrue(
            "Mode 2 peak letter lift must stay at heldWordLetterLiftFraction when wordLiftPeakFraction changes",
            Math.abs(res2.maxPeakDrawnLift / expectedHeldPeakLift2 - 1.0f) <= 0.05f
        )
    }

    @Test
    fun testHeldWeNoIdleGap() {
        val res = runLifecycleSimulation(2400L, 2, wordText = "We")
        val sweepStartMs = 1000L
        val sweepEndMs = 1000L + 2400L + 50L
        var gapFrames = 0
        for (f in res.frames) {
            if (f.timeMs > sweepStartMs && f.timeMs < sweepEndMs) {
                val hasMoving = f.letterProgresses.any { it > 0f && it < 1f }
                if (!hasMoving) {
                    gapFrames++
                }
            }
        }
        assertEquals("There must be no idle gap frames during sweep", 0, gapFrames)
    }



    @Test
    fun testLineStartWordGateRampSpeed() {
        val dt = 16.67f
        val rampDurationMs = 200f
        var maxGateSpeed = 0f

        var t = 0f
        while (t < rampDurationMs) {
            val gateNow = t * LyricsWallpaperService.getLineRampFraction(t, rampDurationMs)
            val gateNext = (t + dt) * LyricsWallpaperService.getLineRampFraction(t + dt, rampDurationMs)
            val speed = (gateNext - gateNow) / dt
            if (speed > maxGateSpeed) {
                maxGateSpeed = speed
            }
            t += dt
        }

        assertTrue("Gate speed during ramp must never exceed 1.4x (was $maxGateSpeed)", maxGateSpeed <= 1.4f)

        val gateAtEnd = rampDurationMs * LyricsWallpaperService.getLineRampFraction(rampDurationMs, rampDurationMs)
        val gateAfterEnd = (rampDurationMs + dt) * LyricsWallpaperService.getLineRampFraction(rampDurationMs + dt, rampDurationMs)
        val speedAtEnd = (gateAfterEnd - gateAtEnd) / dt
        println("testLineStartWordGateRampSpeed: maxGateSpeed = ${String.format("%.4f", maxGateSpeed)}x, speedAtEnd = ${String.format("%.4f", speedAtEnd)}x")
        assertTrue(
            "Gate speed at end of ramp must be within 0.02 of 1.0 (was $speedAtEnd)",
            Math.abs(speedAtEnd - 1.0f) <= 0.02f
        )
    }

    @Test
    fun testComputeWordEdgeMatchesApplyDrawStateFormula() {
        val wordLeft = 100f
        val wordWidth = 200f
        val progress = 0.5f

        val edge = computeWordEdge(wordLeft, wordWidth, progress)

        val transitionWidth = (wordWidth * 0.3f).coerceAtLeast(40f)
        val xTransition = wordLeft + (wordWidth + transitionWidth) * progress
        val expectedP1 = (xTransition - transitionWidth - wordLeft) / wordWidth
        val expectedP2 = (xTransition - wordLeft) / wordWidth

        assertEquals(transitionWidth, edge.transitionWidth, 0.0001f)
        assertEquals(xTransition - transitionWidth, edge.edgeLeft, 0.0001f)
        assertEquals(xTransition, edge.edgeRight, 0.0001f)

        val actualP1 = (edge.edgeLeft - wordLeft) / wordWidth
        val actualP2 = (edge.edgeRight - wordLeft) / wordWidth
        assertEquals(expectedP1, actualP1, 0.0001f)
        assertEquals(expectedP2, actualP2, 0.0001f)
    }

    @Test
    fun testComputeWordEdgeMinimum40pxFloor() {
        val wordLeft = 50f
        val wordWidth = 80f
        val progress = 0.25f

        val edge = computeWordEdge(wordLeft, wordWidth, progress)

        assertEquals(40f, edge.transitionWidth, 0.0001f)
        assertEquals(40f, edge.edgeLeft, 0.0001f)
        assertEquals(80f, edge.edgeRight, 0.0001f)

        val actualP1 = (edge.edgeLeft - wordLeft) / wordWidth
        val actualP2 = (edge.edgeRight - wordLeft) / wordWidth
        val xTransition = wordLeft + (wordWidth + 40f) * progress
        val expectedP1 = (xTransition - 40f - wordLeft) / wordWidth
        val expectedP2 = (xTransition - wordLeft) / wordWidth
        assertEquals(expectedP1, actualP1, 0.0001f)
        assertEquals(expectedP2, actualP2, 0.0001f)
    }

    @Test
    fun testComputeWordEdgeBoundaries() {
        val wordLeft = 20f
        val wordWidth = 150f

        val edge0 = computeWordEdge(wordLeft, wordWidth, 0f)
        assertEquals(wordLeft, edge0.edgeRight, 0.0001f)
        assertEquals(wordLeft - edge0.transitionWidth, edge0.edgeLeft, 0.0001f)

        val edge1 = computeWordEdge(wordLeft, wordWidth, 1f)
        assertEquals(wordLeft + wordWidth, edge1.edgeLeft, 0.0001f)
        assertEquals(wordLeft + wordWidth + edge1.transitionWidth, edge1.edgeRight, 0.0001f)
    }

    @Test
    fun testComputeWordEdgeZeroAllocationInPlace() {
        val reusable = WordEdge()
        val result = computeWordEdge(10f, 100f, 0.3f, reusable)
        assertTrue("computeWordEdge must mutate and return the passed out instance", result === reusable)
        assertEquals(40f, reusable.transitionWidth, 0.0001f)
        assertEquals(10f + (100f + 40f) * 0.3f, reusable.edgeRight, 0.0001f)
        assertEquals(reusable.edgeRight - 40f, reusable.edgeLeft, 0.0001f)
    }

    @Test
    fun testMode2LettersSpanningTransitionEdge() {
        val wordLeft = 0f
        val wordWidth = 120f
        val sweepProg = 0.5f

        val edge = computeWordEdge(wordLeft, wordWidth, sweepProg)
        assertEquals(40f, edge.transitionWidth, 0.0001f)
        assertEquals(40f, edge.edgeLeft, 0.0001f)
        assertEquals(80f, edge.edgeRight, 0.0001f)

        val letterLefts = floatArrayOf(0f, 30f, 60f, 90f)
        val letterRights = floatArrayOf(30f, 60f, 90f, 120f)

        fun classify(left: Float, right: Float): String = when {
            right <= edge.edgeLeft -> "active"
            left >= edge.edgeRight -> "inactive"
            else -> "under_edge"
        }

        assertEquals("active", classify(letterLefts[0], letterRights[0]))
        assertEquals("under_edge", classify(letterLefts[1], letterRights[1]))
        assertEquals("under_edge", classify(letterLefts[2], letterRights[2]))
        assertEquals("inactive", classify(letterLefts[3], letterRights[3]))
    }

    @Test
    fun testMode2EdgeStationaryUnderLetterMotion() {
        val wordLeft = 100f
        val wordWidth = 200f
        val edge = computeWordEdge(wordLeft, wordWidth, 0.5f)

        val letterLeft = 140f
        val letterRight = 180f
        val letterCenterX = (letterLeft + letterRight) * 0.5f
        val letterScale = 1.15f
        val letterLift = 12f
        val targetCenterX = letterCenterX + 8f
        val y = 200f

        val halfWidth = (letterRight - letterLeft) * 0.5f * letterScale
        val transformedLeft = targetCenterX - halfWidth
        val transformedRight = targetCenterX + halfWidth

        assertTrue("Letter must be under edge", transformedRight > edge.edgeLeft && transformedLeft < edge.edgeRight)

        for (u in floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val xWord = edge.edgeLeft + u * edge.transitionWidth
            val yWord = y

            val xLocal = letterCenterX + (xWord - targetCenterX) / letterScale
            val yLocal = y + (yWord - (y - letterLift)) / letterScale

            val xCanvas = targetCenterX + (xLocal - letterCenterX) * letterScale
            val yCanvas = (y - letterLift) + (yLocal - y) * letterScale

            assertEquals(xWord, xCanvas, 0.0001f)
            assertEquals(yWord, yCanvas, 0.0001f)
        }
    }
}


