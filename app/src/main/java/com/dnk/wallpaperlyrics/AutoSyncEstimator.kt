package com.dnk.wallpaperlyrics

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Estimates lyric timing offset by cross-correlating vocal band energy with expected lyric activity.
 * Callers must not invoke estimate concurrently with add; estimate takes a synchronized snapshot first.
 */
class AutoSyncEstimator(capacity: Int = DEFAULT_CAPACITY) {

    companion object {
        const val DEFAULT_CAPACITY = 4096
        const val MAX_LAG_MS = 3000
        const val LAG_STEP_MS = 10
        const val DETREND_WINDOW_MS = 3000L
        const val MIN_SAMPLES = 400
        const val MIN_VOCAL_LINES = 6
        const val MIN_PEAK_CORRELATION = 0.15f
        const val MIN_PEAK_MARGIN = 0.05f
        const val EXCLUSION_MS = 250
    }

    sealed class Result {
        data class Confident(val offsetMs: Int, val peakCorrelation: Float, val margin: Float, val samples: Int) : Result()
        data class NotEnoughData(val samples: Int, val vocalLines: Int) : Result()
        data class Ambiguous(val bestOffsetMs: Int, val peakCorrelation: Float, val margin: Float, val samples: Int) : Result()
    }

    private class Interval(val start: Long, var end: Long)

    private class SampleSnapshot(val positions: LongArray, val features: FloatArray, val count: Int)

    private val bufferPositions = LongArray(capacity)
    private val bufferFeatures = FloatArray(capacity)
    private var bufferCount = 0

    @Synchronized
    fun add(positionMs: Long, feature: Float) {
        if (!feature.isFinite() || bufferCount >= bufferPositions.size) {
            return
        }
        bufferPositions[bufferCount] = positionMs
        bufferFeatures[bufferCount] = feature
        bufferCount++
    }

    @Synchronized
    fun sampleCount(): Int = bufferCount

    @Synchronized
    fun clear() {
        bufferCount = 0
    }

    @Synchronized
    private fun createSnapshot(): SampleSnapshot {
        val count = bufferCount
        val positionsCopy = LongArray(count)
        val featuresCopy = FloatArray(count)
        System.arraycopy(bufferPositions, 0, positionsCopy, 0, count)
        System.arraycopy(bufferFeatures, 0, featuresCopy, 0, count)
        return SampleSnapshot(positionsCopy, featuresCopy, count)
    }

    fun estimate(lines: List<LyricLine>): Result {
        val snapshot = createSnapshot()
        val rawCount = snapshot.count
        if (rawCount < MIN_SAMPLES) {
            val vocalLines = countCoveredVocalLines(lines, snapshot.positions, rawCount)
            return Result.NotEnoughData(samples = rawCount, vocalLines = vocalLines)
        }

        val positions = snapshot.positions
        val features = snapshot.features
        quickSort(positions, features, 0, rawCount - 1)

        val coveredVocalLines = countCoveredVocalLines(lines, positions, rawCount)
        if (coveredVocalLines < MIN_VOCAL_LINES) {
            return Result.NotEnoughData(samples = rawCount, vocalLines = coveredVocalLines)
        }

        val mergedIntervals = buildMergedExpectedIntervals(lines)
        if (mergedIntervals.isEmpty()) {
            return Result.NotEnoughData(samples = rawCount, vocalLines = 0)
        }

        val normFeatures = detrendAndNormalizeFeatures(positions, features, rawCount)

        val halfWindow = DETREND_WINDOW_MS / 2
        val yRaw = FloatArray(rawCount)
        val yDetrended = FloatArray(rawCount)

        val numLags = (MAX_LAG_MS - (-MAX_LAG_MS)) / LAG_STEP_MS + 1
        val correlations = FloatArray(numLags)
        val lags = IntArray(numLags)

        var peakCorr = Float.NEGATIVE_INFINITY
        var peakLagIndex = 0

        for (l in 0 until numLags) {
            val d = -MAX_LAG_MS + l * LAG_STEP_MS
            lags[l] = d

            // Evaluate expected vocal activity E(position - d) using a monotonic pointer over intervals.
            var intervalIdx = 0
            val numIntervals = mergedIntervals.size
            for (i in 0 until rawCount) {
                val t = positions[i] - d
                while (intervalIdx < numIntervals && mergedIntervals[intervalIdx].end <= t) {
                    intervalIdx++
                }
                yRaw[i] = if (intervalIdx < numIntervals && t >= mergedIntervals[intervalIdx].start) 1.0f else 0.0f
            }

            // Detrend y with the same centered moving average window over sample positions.
            var left = 0
            var right = 0
            var sumY = 0.0
            var sumDetrendedY = 0.0
            for (i in 0 until rawCount) {
                val maxPos = positions[i] + halfWindow
                while (right < rawCount && positions[right] <= maxPos) {
                    sumY += yRaw[right]
                    right++
                }
                val minPos = positions[i] - halfWindow
                while (left < rawCount && positions[left] < minPos) {
                    sumY -= yRaw[left]
                    left++
                }
                val windowCount = right - left
                val ma = (sumY / windowCount).toFloat()
                val detrended = yRaw[i] - ma
                yDetrended[i] = detrended
                sumDetrendedY += detrended
            }

            // Pearson correlation against pre-normalized features.
            val meanY = sumDetrendedY / rawCount
            var varY = 0.0
            var cov = 0.0
            for (i in 0 until rawCount) {
                val diffY = yDetrended[i] - meanY
                varY += diffY * diffY
                cov += normFeatures[i] * diffY
            }

            val corr = if (varY > 1e-12) {
                (cov / sqrt(varY)).toFloat()
            } else {
                0.0f
            }

            correlations[l] = corr
            if (corr > peakCorr) {
                peakCorr = corr
                peakLagIndex = l
            }
        }

        val peakLag = lags[peakLagIndex]
        var maxOutsideCorr = Float.NEGATIVE_INFINITY
        for (l in 0 until numLags) {
            if (abs(lags[l] - peakLag) > EXCLUSION_MS) {
                if (correlations[l] > maxOutsideCorr) {
                    maxOutsideCorr = correlations[l]
                }
            }
        }

        val margin = if (maxOutsideCorr == Float.NEGATIVE_INFINITY) 0.0f else peakCorr - maxOutsideCorr

        return if (peakCorr >= MIN_PEAK_CORRELATION && margin >= MIN_PEAK_MARGIN) {
            Result.Confident(offsetMs = peakLag, peakCorrelation = peakCorr, margin = margin, samples = rawCount)
        } else {
            Result.Ambiguous(bestOffsetMs = peakLag, peakCorrelation = peakCorr, margin = margin, samples = rawCount)
        }
    }

    private fun buildMergedExpectedIntervals(lines: List<LyricLine>): List<Interval> {
        val raw = mutableListOf<Interval>()
        for (i in lines.indices) {
            val line = lines[i]
            if (line.isInstrumental || line.content.isBlank()) continue
            if (!line.words.isNullOrEmpty()) {
                for (word in line.words) {
                    if (word.endTime > word.startTime) {
                        raw.add(Interval(word.startTime, word.endTime))
                    }
                }
            } else {
                val nextLine = lines.getOrNull(i + 1)
                val lineEnd = if (nextLine != null) minOf(line.endTime, nextLine.startTime) else line.endTime
                if (lineEnd > line.startTime) {
                    raw.add(Interval(line.startTime, lineEnd))
                }
            }
        }

        if (raw.isEmpty()) return emptyList()
        raw.sortBy { it.start }

        val merged = mutableListOf<Interval>()
        for (interval in raw) {
            if (merged.isEmpty() || interval.start > merged.last().end) {
                merged.add(Interval(interval.start, interval.end))
            } else {
                merged.last().end = maxOf(merged.last().end, interval.end)
            }
        }
        return merged
    }

    private fun countCoveredVocalLines(lines: List<LyricLine>, positions: LongArray, count: Int): Int {
        if (count == 0 || lines.isEmpty()) return 0
        var coveredCount = 0
        for (i in lines.indices) {
            val line = lines[i]
            if (line.isInstrumental || line.content.isBlank()) continue

            var lineCovered = false
            if (!line.words.isNullOrEmpty()) {
                for (word in line.words) {
                    if (word.endTime > word.startTime && hasSampleInInterval(positions, count, word.startTime, word.endTime)) {
                        lineCovered = true
                        break
                    }
                }
            } else {
                val nextLine = lines.getOrNull(i + 1)
                val lineEnd = if (nextLine != null) minOf(line.endTime, nextLine.startTime) else line.endTime
                if (lineEnd > line.startTime && hasSampleInInterval(positions, count, line.startTime, lineEnd)) {
                    lineCovered = true
                }
            }

            if (lineCovered) {
                coveredCount++
            }
        }
        return coveredCount
    }

    private fun hasSampleInInterval(positions: LongArray, count: Int, start: Long, end: Long): Boolean {
        for (i in 0 until count) {
            val pos = positions[i]
            if (pos in start until end) {
                return true
            }
        }
        return false
    }

    private fun detrendAndNormalizeFeatures(positions: LongArray, features: FloatArray, count: Int): FloatArray {
        val halfWindow = DETREND_WINDOW_MS / 2
        val detrended = FloatArray(count)

        var left = 0
        var right = 0
        var windowSum = 0.0
        var detrendedSum = 0.0

        for (i in 0 until count) {
            val maxPos = positions[i] + halfWindow
            while (right < count && positions[right] <= maxPos) {
                windowSum += features[right]
                right++
            }
            val minPos = positions[i] - halfWindow
            while (left < count && positions[left] < minPos) {
                windowSum -= features[left]
                left++
            }
            val windowCount = right - left
            val ma = (windowSum / windowCount).toFloat()
            val value = features[i] - ma
            detrended[i] = value
            detrendedSum += value
        }

        val mean = detrendedSum / count
        var varSum = 0.0
        for (i in 0 until count) {
            val diff = detrended[i] - mean
            varSum += diff * diff
        }

        val norm = FloatArray(count)
        if (varSum > 1e-12) {
            val invStd = (1.0 / sqrt(varSum)).toFloat()
            for (i in 0 until count) {
                norm[i] = ((detrended[i] - mean) * invStd).toFloat()
            }
        }
        return norm
    }

    private fun quickSort(positions: LongArray, features: FloatArray, low: Int, high: Int) {
        if (low < high) {
            val pivot = positions[(low + high) ushr 1]
            var i = low
            var j = high
            while (i <= j) {
                while (positions[i] < pivot) i++
                while (positions[j] > pivot) j--
                if (i <= j) {
                    val tempPos = positions[i]
                    positions[i] = positions[j]
                    positions[j] = tempPos

                    val tempFeat = features[i]
                    features[i] = features[j]
                    features[j] = tempFeat

                    i++
                    j--
                }
            }
            if (low < j) quickSort(positions, features, low, j)
            if (i < high) quickSort(positions, features, i, high)
        }
    }
}
