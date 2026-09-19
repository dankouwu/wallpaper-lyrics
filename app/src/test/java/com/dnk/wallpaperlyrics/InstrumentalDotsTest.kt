package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstrumentalDotsTest {

    @Test
    fun testDotsDoNotOverlapAcrossFineSweep() {
        val dotCount = 3
        val steps = 10000
        val threshold = 0.0001f

        for (step in 0..steps) {
            val progress = step.toFloat() / steps.toFloat()
            for (i in 0 until dotCount) {
                val focusI = LyricsRenderer.getInstrumentalDotFocus(progress, i, dotCount, 0f)
                if (focusI > threshold) {
                    for (j in 0 until dotCount) {
                        if (i != j) {
                            val focusJ = LyricsRenderer.getInstrumentalDotFocus(progress, j, dotCount, 0f)
                            assertTrue(
                                "Dots $i and $j must not overlap at progress $progress (focusI=$focusI, focusJ=$focusJ)",
                                focusJ <= threshold
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testDotsAreSequential() {
        val dotCount = 3
        val steps = 5000
        val threshold = 0.0001f

        for (i in 0 until dotCount - 1) {
            var dotIPeakProgress = -1.0f
            var dotINextLeavesRestProgress = -1.0f

            for (step in 0..steps) {
                val progress = step.toFloat() / steps.toFloat()
                val focusI = LyricsRenderer.getInstrumentalDotFocus(progress, i, dotCount, 0f)
                val focusNext = LyricsRenderer.getInstrumentalDotFocus(progress, i + 1, dotCount, 0f)

                if (Math.abs(focusI - 1.0f) < 0.005f && dotIPeakProgress < 0f) {
                    dotIPeakProgress = progress
                }
                if (focusNext > threshold && dotINextLeavesRestProgress < 0f) {
                    dotINextLeavesRestProgress = progress
                }
            }

            assertTrue("Dot $i must reach peak", dotIPeakProgress >= 0f)
            assertTrue("Dot ${i + 1} must leave rest", dotINextLeavesRestProgress >= 0f)
            assertTrue(
                "Dot $i peak ($dotIPeakProgress) must occur before dot ${i + 1} leaves rest ($dotINextLeavesRestProgress)",
                dotIPeakProgress < dotINextLeavesRestProgress
            )
        }
    }

    @Test
    fun testDotsRestAtWindowEdges() {
        val dotCount = 3
        for (i in 0 until dotCount) {
            val wStart = i.toFloat() / dotCount.toFloat()
            val wEnd = (i + 1).toFloat() / dotCount.toFloat()

            val focusStart = LyricsRenderer.getInstrumentalDotFocus(wStart, i, dotCount, 0f)
            val focusEnd = LyricsRenderer.getInstrumentalDotFocus(wEnd, i, dotCount, 0f)

            assertEquals("Dot $i must be at rest at window start $wStart", 0.0f, focusStart, 0.0001f)
            assertEquals("Dot $i must be at rest at window end $wEnd", 0.0f, focusEnd, 0.0001f)
        }
    }

    @Test
    fun testEmphasisIsSubtle() {
        val peakScale = Tuning.DOT_SCALE_PEAK.defaultValue
        val liftFraction = Tuning.DOT_LIFT_FRACTION.defaultValue

        assertTrue("Dot peak scale ($peakScale) must be <= 1.20f", peakScale <= 1.20f)
        assertTrue("Dot peak scale ($peakScale) must be >= 1.00f", peakScale >= 1.00f)
        assertTrue("Dot lift fraction ($liftFraction) must be <= 0.40f", liftFraction <= 0.40f)
        assertTrue("Dot lift fraction ($liftFraction) must be >= 0.00f", liftFraction >= 0.00f)
    }

    @Test
    fun testDifferentDotCountsDoNotOverlap() {
        for (dotCount in 1..8) {
            val steps = 2000
            val threshold = 0.0001f
            for (step in 0..steps) {
                val progress = step.toFloat() / steps.toFloat()
                var activeCount = 0
                for (i in 0 until dotCount) {
                    val focus = LyricsRenderer.getInstrumentalDotFocus(progress, i, dotCount, 0f)
                    if (focus > threshold) {
                        activeCount++
                    }
                }
                assertTrue(
                    "At progress $progress with dotCount $dotCount, at most 1 dot can be active, found $activeCount",
                    activeCount <= 1
                )
            }
        }
    }

    @Test
    fun testOverlapMonotonicallyIncreasesWithSetting() {
        val dotCount = 3
        val baseWindow = 1.0f / dotCount.toFloat()
        val steps = 20000
        val threshold = 0.0f

        fun measureOverlap(overlapSetting: Float): Float {
            var dot0End = 0.0f
            var dot1Start = 1.0f
            for (step in 0..steps) {
                val p = step.toFloat() / steps.toFloat()
                val f0 = LyricsRenderer.getInstrumentalDotFocus(p, 0, dotCount, overlapSetting)
                val f1 = LyricsRenderer.getInstrumentalDotFocus(p, 1, dotCount, overlapSetting)
                if (f0 > threshold) {
                    if (p > dot0End) dot0End = p
                }
                if (f1 > threshold) {
                    if (p < dot1Start) dot1Start = p
                }
            }
            return if (dot0End > dot1Start) dot0End - dot1Start else 0.0f
        }

        val overlap0 = measureOverlap(0f)
        val overlap25 = measureOverlap(25f)
        val overlap50 = measureOverlap(50f)
        val overlap75 = measureOverlap(75f)
        val overlap100 = measureOverlap(100f)

        assertEquals("Overlap at 0 must be 0", 0.0f, overlap0, 0.001f)
        assertTrue("Overlap must increase from 0 to 25", overlap25 > overlap0)
        assertTrue("Overlap must increase from 25 to 50", overlap50 > overlap25)
        assertTrue("Overlap must increase from 50 to 75", overlap75 > overlap50)
        assertTrue("Overlap must increase from 75 to 100", overlap100 > overlap75)

        assertEquals("Overlap at 50 must be half base window", baseWindow * 0.5f, overlap50, 0.002f)
        assertEquals("Overlap at 100 must equal one full base window", baseWindow, overlap100, 0.002f)
    }

    @Test
    fun testCentresDoNotMoveAtAnyOverlap() {
        val overlaps = listOf(0f, 25f, 50f, 75f, 100f)
        for (dotCount in 1..5) {
            for (i in 0 until dotCount) {
                val expectedCentre = (i.toFloat() + 0.5f) / dotCount.toFloat()
                for (overlap in overlaps) {
                    val focusAtCentre = LyricsRenderer.getInstrumentalDotFocus(expectedCentre, i, dotCount, overlap)
                    assertEquals(
                        "Dot $i with dotCount $dotCount at overlap $overlap must reach peak 1.0 at centre $expectedCentre",
                        1.0f,
                        focusAtCentre,
                        0.0001f
                    )

                    val epsilon = 0.005f
                    val leftFocus = LyricsRenderer.getInstrumentalDotFocus(expectedCentre - epsilon, i, dotCount, overlap)
                    val rightFocus = LyricsRenderer.getInstrumentalDotFocus(expectedCentre + epsilon, i, dotCount, overlap)
                    assertTrue("Left of centre must have lower focus than peak", leftFocus < 1.0f)
                    assertTrue("Right of centre must have lower focus than peak", rightFocus < 1.0f)
                }
            }
        }
    }

    @Test
    fun testRestAtWindowEdgesHoldsAtEveryOverlap() {
        val overlaps = listOf(0f, 15f, 50f, 85f, 100f)
        for (dotCount in 1..5) {
            val countF = dotCount.toFloat()
            for (overlap in overlaps) {
                val halfWidth = (1f + overlap / 100f) / (2f * countF)
                for (i in 0 until dotCount) {
                    val centre = (i.toFloat() + 0.5f) / countF
                    val wStart = Math.max(0f, centre - halfWidth)
                    val wEnd = Math.min(1f, centre + halfWidth)

                    val focusStart = LyricsRenderer.getInstrumentalDotFocus(wStart, i, dotCount, overlap)
                    val focusEnd = LyricsRenderer.getInstrumentalDotFocus(wEnd, i, dotCount, overlap)

                    assertEquals(
                        "Dot $i (count $dotCount, overlap $overlap) must rest at window start $wStart",
                        0.0f,
                        focusStart,
                        0.0001f
                    )
                    assertEquals(
                        "Dot $i (count $dotCount, overlap $overlap) must rest at window end $wEnd",
                        0.0f,
                        focusEnd,
                        0.0001f
                    )

                    val focusBefore = LyricsRenderer.getInstrumentalDotFocus(wStart - 0.01f, i, dotCount, overlap)
                    val focusAfter = LyricsRenderer.getInstrumentalDotFocus(wEnd + 0.01f, i, dotCount, overlap)
                    assertEquals(0.0f, focusBefore, 0.0001f)
                    assertEquals(0.0f, focusAfter, 0.0001f)
                }
            }
        }
    }

    @Test
    fun testEdgeDotsRestAtZeroAndOneAtEveryOverlapNoClipping() {
        val overlaps = listOf(0f, 10f, 25f, 50f, 75f, 90f, 100f)
        for (dotCount in 1..8) {
            for (overlap in overlaps) {
                // All dots must be at rest at progress 0.0
                for (i in 0 until dotCount) {
                    val focusAt0 = LyricsRenderer.getInstrumentalDotFocus(0.0f, i, dotCount, overlap)
                    assertEquals(
                        "Dot $i (count $dotCount, overlap $overlap) must be 0.0 at progress 0.0",
                        0.0f,
                        focusAt0,
                        0.0001f
                    )
                }

                // All dots must be at rest at progress 1.0
                for (i in 0 until dotCount) {
                    val focusAt1 = LyricsRenderer.getInstrumentalDotFocus(1.0f, i, dotCount, overlap)
                    assertEquals(
                        "Dot $i (count $dotCount, overlap $overlap) must be 0.0 at progress 1.0",
                        0.0f,
                        focusAt1,
                        0.0001f
                    )
                }

                // Smooth startup for first dot near 0 (no abrupt jump or clipping)
                val focusNearZero = LyricsRenderer.getInstrumentalDotFocus(0.001f, 0, dotCount, overlap)
                assertTrue("Focus near zero must stay small: $focusNearZero", focusNearZero in 0.0f..0.02f)

                // Smooth finish for last dot near 1 (no abrupt jump or clipping)
                val focusNearOne = LyricsRenderer.getInstrumentalDotFocus(0.999f, dotCount - 1, dotCount, overlap)
                assertTrue("Focus near one must stay small: $focusNearOne", focusNearOne in 0.0f..0.02f)
            }
        }
    }

    @Test
    fun testDotCountOneToEightAcrossOverlapRange() {
        for (dotCount in 1..8) {
            for (overlap in listOf(0f, 100f)) {
                for (i in 0 until dotCount) {
                    val centre = (i.toFloat() + 0.5f) / dotCount.toFloat()
                    val peakFocus = LyricsRenderer.getInstrumentalDotFocus(centre, i, dotCount, overlap)
                    assertEquals(
                        "Dot $i (count $dotCount, overlap $overlap) must peak at centre",
                        1.0f,
                        peakFocus,
                        0.0001f
                    )
                }
            }
        }
    }
}
