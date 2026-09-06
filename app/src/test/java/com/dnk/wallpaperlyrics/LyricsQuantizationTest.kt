package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsQuantizationTest {

    @Test
    fun quantizationDriftExceedsOnePixelOnMultiWordLine() {
        // Fractional advances from paint.measureText for words on a line
        val wordAdvances = floatArrayOf(
            52.4f, 48.4f, 61.4f, 45.4f, 55.4f, 50.4f
        )

        var exactSum = 0f
        var roundedPerWordSum = 0

        for (advance in wordAdvances) {
            exactSum += advance
            roundedPerWordSum += Math.round(advance)
        }

        val roundedExactSum = Math.round(exactSum)
        val drift = Math.abs(roundedPerWordSum - roundedExactSum)

        // 6 words each with 0.4 fractional advance lose 0.4 * 6 = 2.4px when rounded individually
        assertEquals(313, roundedExactSum)
        assertEquals(311, roundedPerWordSum)
        assertTrue("Accumulated quantization drift must exceed 1px", drift > 1)
        assertEquals(2, drift)
    }

    @Test
    fun quantizationDriftGrowsWithWordCount() {
        val fractionalAdvance = 40.4f
        var previousDrift = 0

        val wordCounts = listOf(2, 5, 8, 12)
        val observedDrifts = mutableListOf<Int>()

        for (count in wordCounts) {
            var exactSum = 0f
            var roundedPerWordSum = 0

            for (i in 0 until count) {
                exactSum += fractionalAdvance
                roundedPerWordSum += Math.round(fractionalAdvance)
            }

            val roundedExactSum = Math.round(exactSum)
            val drift = Math.abs(roundedPerWordSum - roundedExactSum)
            observedDrifts.add(drift)

            assertTrue("Drift must grow or remain non-decreasing with word count", drift >= previousDrift)
            previousDrift = drift
        }

        // At 2 words drift is 1, at 5 words drift is 2, at 8 words drift is 3, at 12 words drift is 5
        assertEquals(listOf(1, 2, 3, 5), observedDrifts)
        assertTrue("Drift at large word count must exceed initial drift", observedDrifts.last() > observedDrifts.first())
    }
}
