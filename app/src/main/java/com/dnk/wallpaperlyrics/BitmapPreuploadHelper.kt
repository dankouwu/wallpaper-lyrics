package com.dnk.wallpaperlyrics

import kotlin.math.max
import kotlin.math.min

/**
 * Works out which lyric lines to upload to the GPU before they scroll into view.
 * Index arithmetic only, so the off-by-one cases are testable without a device.
 */
object BitmapPreuploadHelper {

    /**
     * The ranges just outside the visible window, warmed on every line change.
     * Backward as well as forward, because a seek can scroll either way.
     */
    fun computePreuploadRanges(
        currentIndex: Int,
        totalLines: Int,
        visibleRange: Int = 7,
        lookahead: Int = 3
    ): List<IntRange> {
        if (totalLines <= 0 || lookahead <= 0) return emptyList()

        val result = ArrayList<IntRange>(2)

        // Backward lookahead range (for upward scrolls/seeks)
        val backwardStart = currentIndex - visibleRange - lookahead
        val backwardEnd = currentIndex - visibleRange - 1
        val clampedBackward = clampRange(backwardStart, backwardEnd, totalLines)
        if (!clampedBackward.isEmpty()) {
            result.add(clampedBackward)
        }

        // Forward lookahead range (for normal downward scrolling)
        val forwardStart = currentIndex + visibleRange + 1
        val forwardEnd = currentIndex + visibleRange + lookahead
        val clampedForward = clampRange(forwardStart, forwardEnd, totalLines)
        if (!clampedForward.isEmpty()) {
            result.add(clampedForward)
        }

        return result
    }

    /**
     * Computes the initial index range to pre-upload immediately when lyric layouts and bitmaps are built.
     * Covers from the top of the initial visible window down through the lookahead boundary.
     */
    fun computeInitialPreuploadRange(
        initialIndex: Int,
        totalLines: Int,
        visibleRange: Int = 7,
        lookahead: Int = 3
    ): IntRange {
        if (totalLines <= 0) return IntRange.EMPTY

        val start = max(0, initialIndex - visibleRange)
        val end = min(totalLines - 1, initialIndex + visibleRange + lookahead)
        return if (start <= end) start..end else IntRange.EMPTY
    }

    private fun clampRange(start: Int, end: Int, totalLines: Int): IntRange {
        if (totalLines <= 0 || start > end) return IntRange.EMPTY
        val clampedStart = start.coerceIn(0, totalLines)
        val clampedEnd = end.coerceIn(-1, totalLines - 1)
        return if (clampedStart <= clampedEnd) clampedStart..clampedEnd else IntRange.EMPTY
    }
}
