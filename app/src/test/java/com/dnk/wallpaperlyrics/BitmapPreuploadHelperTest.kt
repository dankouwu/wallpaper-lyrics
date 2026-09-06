package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BitmapPreuploadHelperTest {

    @Test
    fun computePreuploadRanges_normalMidList() {
        val ranges = BitmapPreuploadHelper.computePreuploadRanges(
            currentIndex = 10,
            totalLines = 50,
            visibleRange = 7,
            lookahead = 3
        )
        assertEquals(listOf(0..2, 18..20), ranges)
    }

    @Test
    fun computePreuploadRanges_clampingAtStart() {
        val ranges = BitmapPreuploadHelper.computePreuploadRanges(
            currentIndex = 2,
            totalLines = 50,
            visibleRange = 7,
            lookahead = 3
        )
        assertEquals(listOf(10..12), ranges)
    }

    @Test
    fun computePreuploadRanges_clampingAtEnd() {
        val ranges = BitmapPreuploadHelper.computePreuploadRanges(
            currentIndex = 45,
            totalLines = 50,
            visibleRange = 7,
            lookahead = 3
        )
        assertEquals(listOf(35..37), ranges)
    }

    @Test
    fun computePreuploadRanges_emptyList() {
        val ranges = BitmapPreuploadHelper.computePreuploadRanges(
            currentIndex = 0,
            totalLines = 0,
            visibleRange = 7,
            lookahead = 3
        )
        assertTrue(ranges.isEmpty())
    }

    @Test
    fun computePreuploadRanges_listShorterThanWindow() {
        val ranges = BitmapPreuploadHelper.computePreuploadRanges(
            currentIndex = 2,
            totalLines = 5,
            visibleRange = 7,
            lookahead = 3
        )
        assertTrue(ranges.isEmpty())
    }

    @Test
    fun computeInitialPreuploadRange_normalStart() {
        val range = BitmapPreuploadHelper.computeInitialPreuploadRange(
            initialIndex = 0,
            totalLines = 50,
            visibleRange = 7,
            lookahead = 3
        )
        assertEquals(0..10, range)
    }

    @Test
    fun computeInitialPreuploadRange_midList() {
        val range = BitmapPreuploadHelper.computeInitialPreuploadRange(
            initialIndex = 15,
            totalLines = 50,
            visibleRange = 7,
            lookahead = 3
        )
        assertEquals(8..25, range)
    }

    @Test
    fun computeInitialPreuploadRange_shortList() {
        val range = BitmapPreuploadHelper.computeInitialPreuploadRange(
            initialIndex = 0,
            totalLines = 5,
            visibleRange = 7,
            lookahead = 3
        )
        assertEquals(0..4, range)
    }

    @Test
    fun computeInitialPreuploadRange_emptyList() {
        val range = BitmapPreuploadHelper.computeInitialPreuploadRange(
            initialIndex = 0,
            totalLines = 0,
            visibleRange = 7,
            lookahead = 3
        )
        assertTrue(range.isEmpty())
    }
}
