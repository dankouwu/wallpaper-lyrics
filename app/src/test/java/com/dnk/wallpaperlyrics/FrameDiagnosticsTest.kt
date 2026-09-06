package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameDiagnosticsTest {

    @Test
    fun computePercentile_empty() {
        val result = FrameDiagnostics.computePercentile(LongArray(0), 0, 50.0)
        assertEquals(0L, result)
    }

    @Test
    fun computePercentile_singleElement() {
        val array = longArrayOf(15_000_000L)
        assertEquals(15_000_000L, FrameDiagnostics.computePercentile(array, 1, 50.0))
        assertEquals(15_000_000L, FrameDiagnostics.computePercentile(array, 1, 90.0))
        assertEquals(15_000_000L, FrameDiagnostics.computePercentile(array, 1, 99.0))
    }

    @Test
    fun computePercentile_knownDistribution() {
        val array = LongArray(100) { (it + 1).toLong() }
        assertEquals(50L, FrameDiagnostics.computePercentile(array, 100, 50.0))
        assertEquals(90L, FrameDiagnostics.computePercentile(array, 100, 90.0))
        assertEquals(99L, FrameDiagnostics.computePercentile(array, 100, 99.0))
    }

    @Test
    fun computeFrameStats_empty() {
        val stats = FrameDiagnostics.computeFrameStats(LongArray(0), 0)
        assertEquals(0, stats.count)
        assertEquals(0L, stats.medianNs)
        assertEquals(0L, stats.p90Ns)
        assertEquals(0L, stats.p99Ns)
        assertEquals(0L, stats.maxNs)
        assertEquals(0, stats.over16msCount)
        assertEquals(0, stats.over33msCount)
    }

    @Test
    fun computeFrameStats_singleElement() {
        val array = longArrayOf(20_000_000L)
        val stats = FrameDiagnostics.computeFrameStats(array, 1)
        assertEquals(1, stats.count)
        assertEquals(20_000_000L, stats.medianNs)
        assertEquals(20_000_000L, stats.p90Ns)
        assertEquals(20_000_000L, stats.p99Ns)
        assertEquals(20_000_000L, stats.maxNs)
        assertEquals(1, stats.over16msCount)
        assertEquals(0, stats.over33msCount)
    }

    @Test
    fun computeFrameStats_thresholdCounts() {
        val array = longArrayOf(
            5_000_000L,   // < 16.7ms
            10_000_000L,  // < 16.7ms
            20_000_000L,  // > 16.7ms, < 33.3ms
            40_000_000L   // > 33.3ms
        )
        val stats = FrameDiagnostics.computeFrameStats(array, 4)
        assertEquals(4, stats.count)
        assertEquals(40_000_000L, stats.maxNs)
        assertEquals(2, stats.over16msCount)
        assertEquals(1, stats.over33msCount)
    }

    @Test
    fun formatMs_correctness() {
        assertEquals("1.50", FrameDiagnostics.formatMs(1_500_000L))
        assertEquals("16.67", FrameDiagnostics.formatMs(16_666_667L))
        assertEquals("0.00", FrameDiagnostics.formatMs(0L))
    }

    @Test
    fun recordFrame_ringBufferWrapping() {
        val diagnostics = FrameDiagnostics(
            capacity = 10,
            reportInterval = 100,
            jankThresholdNs = 100_000_000L,
            logger = { _, _ -> },
            warnLogger = { _, _ -> }
        )

        var timeNs = 1_000_000_000L
        for (i in 1..25) {
            val start = timeNs
            val end = start + 5_000_000L
            diagnostics.recordFrame(start, end, isLineChange = i % 2 == 0)
            timeNs += 16_666_666L
        }

        assertEquals(10, diagnostics.recordedCount)
        assertEquals(25L, diagnostics.totalFramesRecorded)
    }

    @Test
    fun recordFrame_periodicReportEmission() {
        val logs = mutableListOf<String>()
        val diagnostics = FrameDiagnostics(
            capacity = 10,
            reportInterval = 5,
            jankThresholdNs = 100_000_000L,
            logger = { _, msg -> logs.add(msg) },
            warnLogger = { _, _ -> }
        )

        var timeNs = 1_000_000_000L
        for (i in 1..10) {
            val start = timeNs
            val end = start + 5_000_000L
            diagnostics.recordFrame(start, end, isLineChange = i % 2 == 0)
            timeNs += 16_666_666L
        }

        assertEquals(2, logs.size)
        assertTrue(logs[0].contains("Frame Timing Summary"))
        assertTrue(logs[0].contains("Steady-state"))
        assertTrue(logs[0].contains("Line-change"))
    }

    @Test
    fun recordFrame_jankWarning() {
        val warnings = mutableListOf<String>()
        val diagnostics = FrameDiagnostics(
            capacity = 10,
            reportInterval = 100,
            jankThresholdNs = 33_333_333L,
            logger = { _, _ -> },
            warnLogger = { _, msg -> warnings.add(msg) }
        )

        // Normal frame
        diagnostics.recordFrame(1_000_000_000L, 1_005_000_000L, isLineChange = false)
        assertEquals(0, warnings.size)

        // Jank frame (40ms draw)
        diagnostics.recordFrame(1_016_666_666L, 1_056_666_666L, isLineChange = true)
        assertEquals(1, warnings.size)
        assertTrue(warnings[0].contains("Jank frame"))
        assertTrue(warnings[0].contains("draw=40.00ms"))
        assertTrue(warnings[0].contains("lineChange=true"))
    }
}
