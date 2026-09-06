package com.dnk.wallpaperlyrics

import android.util.Log
import java.util.Locale

/** One reporting window's worth of frame timings. */
data class FrameStats(
    val count: Int,
    val medianNs: Long,
    val p90Ns: Long,
    val p99Ns: Long,
    val maxNs: Long,
    val over16msCount: Int,
    val over33msCount: Int
)

/**
 * Records how long each frame took and how far apart frames landed.
 * Ring buffers of primitives, because anything that allocates here shows up as
 * the jank it is meant to be measuring.
 */
class FrameDiagnostics(
    private val capacity: Int = REPORT_INTERVAL_FRAMES,
    private val reportInterval: Int = REPORT_INTERVAL_FRAMES,
    private val jankThresholdNs: Long = NS_33_3_MS,
    private val logger: (tag: String, msg: String) -> Unit = { tag, msg -> Log.i(tag, msg) },
    private val warnLogger: (tag: String, msg: String) -> Unit = { tag, msg -> Log.w(tag, msg) }
) {
    companion object {
        const val TAG = "WallpaperFrameTiming"
        const val REPORT_INTERVAL_FRAMES = 300
        const val NS_16_7_MS = 16_666_667L
        const val NS_33_3_MS = 33_333_333L

        /** Nearest rank percentile. The array must already be sorted. */
        fun computePercentile(sortedValues: LongArray, count: Int, percentile: Double): Long {
            if (count <= 0) return 0L
            if (count == 1) return sortedValues[0]
            val rank = Math.ceil(percentile / 100.0 * count).toInt()
            val index = (rank - 1).coerceIn(0, count - 1)
            return sortedValues[index]
        }

        /** Values are nanoseconds. The array is sorted in place. */
        fun computeFrameStats(values: LongArray, count: Int): FrameStats {
            if (count <= 0) {
                return FrameStats(
                    count = 0,
                    medianNs = 0L,
                    p90Ns = 0L,
                    p99Ns = 0L,
                    maxNs = 0L,
                    over16msCount = 0,
                    over33msCount = 0
                )
            }

            val sorted = values.copyOf(count).apply { sort() }
            var over16 = 0
            var over33 = 0
            for (i in 0 until count) {
                val v = sorted[i]
                if (v > NS_16_7_MS) over16++
                if (v > NS_33_3_MS) over33++
            }

            val median = computePercentile(sorted, count, 50.0)
            val p90 = computePercentile(sorted, count, 90.0)
            val p99 = computePercentile(sorted, count, 99.0)
            val max = sorted[count - 1]

            return FrameStats(
                count = count,
                medianNs = median,
                p90Ns = p90,
                p99Ns = p99,
                maxNs = max,
                over16msCount = over16,
                over33msCount = over33
            )
        }


        fun formatMs(nanos: Long): String {
            val ms = nanos / 1_000_000.0
            return String.format(Locale.US, "%.2f", ms)
        }
    }

    private val drawDurations = LongArray(capacity)
    private val intervals = LongArray(capacity)
    private val isLineChangeFlags = BooleanArray(capacity)

    private var head = 0
    var recordedCount: Int = 0
        private set
    var totalFramesRecorded: Long = 0L
        private set
    private var lastFrameStartNs = 0L

    /** Called once per frame, so nothing in here may allocate. */
    fun recordFrame(frameStartNs: Long, frameEndNs: Long, isLineChange: Boolean) {
        val durationNs = frameEndNs - frameStartNs
        val intervalNs = if (lastFrameStartNs > 0L) frameStartNs - lastFrameStartNs else 0L
        lastFrameStartNs = frameStartNs

        val idx = head
        drawDurations[idx] = durationNs
        intervals[idx] = intervalNs
        isLineChangeFlags[idx] = isLineChange

        head = (idx + 1) % capacity
        if (recordedCount < capacity) {
            recordedCount++
        }
        totalFramesRecorded++

        // Immediate warning only when exceeding the jank threshold
        if (durationNs > jankThresholdNs || intervalNs > jankThresholdNs) {
            warnLogger(
                TAG,
                "Jank frame: draw=${formatMs(durationNs)}ms, interval=${formatMs(intervalNs)}ms, lineChange=$isLineChange"
            )
        }

        if (totalFramesRecorded % reportInterval == 0L) {
            emitPeriodicReport()
        }
    }

    /** Line changes are reported separately, since they are the expensive frames. */
    fun emitPeriodicReport() {
        val count = recordedCount
        if (count == 0) return

        var steadyCount = 0
        var lineChangeCount = 0
        for (i in 0 until count) {
            if (isLineChangeFlags[i]) lineChangeCount++ else steadyCount++
        }

        val steadyDraws = LongArray(steadyCount)
        val steadyIntervals = LongArray(steadyCount)
        val lineChangeDraws = LongArray(lineChangeCount)
        val lineChangeIntervals = LongArray(lineChangeCount)

        var sIdx = 0
        var lIdx = 0
        for (i in 0 until count) {
            if (isLineChangeFlags[i]) {
                lineChangeDraws[lIdx] = drawDurations[i]
                lineChangeIntervals[lIdx] = intervals[i]
                lIdx++
            } else {
                steadyDraws[sIdx] = drawDurations[i]
                steadyIntervals[sIdx] = intervals[i]
                sIdx++
            }
        }

        val steadyDrawStats = computeFrameStats(steadyDraws, steadyCount)
        val steadyIntervalStats = computeFrameStats(steadyIntervals, steadyCount)
        val lineChangeDrawStats = computeFrameStats(lineChangeDraws, lineChangeCount)
        val lineChangeIntervalStats = computeFrameStats(lineChangeIntervals, lineChangeCount)

        val report = buildString {
            append("Frame Timing Summary (last ").append(count).append(" frames):\n")
            append("  Steady-state (").append(steadyCount).append(" frames):\n")
            append("    Draw:     median=").append(formatMs(steadyDrawStats.medianNs))
                .append("ms, p90=").append(formatMs(steadyDrawStats.p90Ns))
                .append("ms, p99=").append(formatMs(steadyDrawStats.p99Ns))
                .append("ms, max=").append(formatMs(steadyDrawStats.maxNs))
                .append("ms | >16.7ms: ").append(steadyDrawStats.over16msCount)
                .append(", >33.3ms: ").append(steadyDrawStats.over33msCount).append("\n")
            append("    Interval: median=").append(formatMs(steadyIntervalStats.medianNs))
                .append("ms, p90=").append(formatMs(steadyIntervalStats.p90Ns))
                .append("ms, p99=").append(formatMs(steadyIntervalStats.p99Ns))
                .append("ms, max=").append(formatMs(steadyIntervalStats.maxNs))
                .append("ms | >16.7ms: ").append(steadyIntervalStats.over16msCount)
                .append(", >33.3ms: ").append(steadyIntervalStats.over33msCount).append("\n")
            append("  Line-change (").append(lineChangeCount).append(" frames):\n")
            append("    Draw:     median=").append(formatMs(lineChangeDrawStats.medianNs))
                .append("ms, p90=").append(formatMs(lineChangeDrawStats.p90Ns))
                .append("ms, p99=").append(formatMs(lineChangeDrawStats.p99Ns))
                .append("ms, max=").append(formatMs(lineChangeDrawStats.maxNs))
                .append("ms | >16.7ms: ").append(lineChangeDrawStats.over16msCount)
                .append(", >33.3ms: ").append(lineChangeDrawStats.over33msCount).append("\n")
            append("    Interval: median=").append(formatMs(lineChangeIntervalStats.medianNs))
                .append("ms, p90=").append(formatMs(lineChangeIntervalStats.p90Ns))
                .append("ms, p99=").append(formatMs(lineChangeIntervalStats.p99Ns))
                .append("ms, max=").append(formatMs(lineChangeIntervalStats.maxNs))
                .append("ms | >16.7ms: ").append(lineChangeIntervalStats.over16msCount)
                .append(", >33.3ms: ").append(lineChangeIntervalStats.over33msCount)
        }

        logger(TAG, report)
    }
}
