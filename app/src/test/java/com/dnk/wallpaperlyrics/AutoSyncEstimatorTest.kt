package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.ln
import kotlin.math.sin

class AutoSyncEstimatorTest {

    @Test
    fun constantsMatchSpecification() {
        assertEquals(4096, AutoSyncEstimator.DEFAULT_CAPACITY)
        assertEquals(3000, AutoSyncEstimator.MAX_LAG_MS)
        assertEquals(10, AutoSyncEstimator.LAG_STEP_MS)
        assertEquals(3000L, AutoSyncEstimator.DETREND_WINDOW_MS)
        assertEquals(400, AutoSyncEstimator.MIN_SAMPLES)
        assertEquals(6, AutoSyncEstimator.MIN_VOCAL_LINES)
        assertEquals(0.15f, AutoSyncEstimator.MIN_PEAK_CORRELATION, 1e-6f)
        assertEquals(0.05f, AutoSyncEstimator.MIN_PEAK_MARGIN, 1e-6f)
        assertEquals(250, AutoSyncEstimator.EXCLUSION_MS)
    }

    @Test
    fun nanFeaturesAreIgnored() {
        val estimator = AutoSyncEstimator(100)
        estimator.add(1000L, Float.NaN)
        assertEquals(0, estimator.sampleCount())

        estimator.add(1050L, 2.5f)
        estimator.add(1100L, Float.NaN)
        estimator.add(1150L, 3.1f)
        assertEquals(2, estimator.sampleCount())
    }

    @Test
    fun addBeyondCapacityDoesNotThrowAndStaysAtCapacity() {
        val capacity = 20
        val estimator = AutoSyncEstimator(capacity)
        for (i in 0 until 50) {
            estimator.add(i * 50L, 1.0f)
        }
        assertEquals(capacity, estimator.sampleCount())
    }

    @Test
    fun clearResetsSampleCount() {
        val estimator = AutoSyncEstimator(50)
        estimator.add(100L, 1.0f)
        estimator.add(200L, 2.0f)
        assertEquals(2, estimator.sampleCount())
        estimator.clear()
        assertEquals(0, estimator.sampleCount())
    }

    @Test
    fun tooFewSamplesReturnsNotEnoughData() {
        val estimator = AutoSyncEstimator(1000)
        val (lines, _) = createSyntheticSong(Random(42))
        for (i in 0 until 200) { // MIN_SAMPLES is 400
            estimator.add(i * 51L, 1.5f)
        }
        val result = estimator.estimate(lines)
        assertTrue("Expected NotEnoughData when samples < MIN_SAMPLES", result is AutoSyncEstimator.Result.NotEnoughData)
        val notEnough = result as AutoSyncEstimator.Result.NotEnoughData
        assertEquals(200, notEnough.samples)
    }

    @Test
    fun fewerThanMinVocalLinesReturnsNotEnoughData() {
        val estimator = AutoSyncEstimator(1000)
        // Song with only 3 vocal lines
        val lines = listOf(
            LyricLine(startTime = 1000L, endTime = 3000L, content = "One"),
            LyricLine(startTime = 4000L, endTime = 6000L, content = "Two"),
            LyricLine(startTime = 7000L, endTime = 9000L, content = "Three")
        )
        // Add 500 samples within the range of these lines
        for (i in 0 until 500) {
            estimator.add(1000L + i * 15L, 2.0f)
        }
        val result = estimator.estimate(lines)
        assertTrue("Expected NotEnoughData when vocal lines < 6", result is AutoSyncEstimator.Result.NotEnoughData)
        val notEnough = result as AutoSyncEstimator.Result.NotEnoughData
        assertEquals(3, notEnough.vocalLines)
        assertEquals(500, notEnough.samples)
    }

    @Test
    fun pureNoiseFeaturesGiveAmbiguousOrNotEnoughDataNeverConfident() {
        val estimator = AutoSyncEstimator(2000)
        val (lines, totalDuration) = createSyntheticSong(Random(1234))
        val random = Random(5678)
        var pos = 0L
        while (pos < totalDuration) {
            estimator.add(pos, random.nextFloat() * 10f)
            pos += 51L
        }
        val result = estimator.estimate(lines)
        assertFalse("Pure noise features must never yield Confident result", result is AutoSyncEstimator.Result.Confident)
        assertTrue(result is AutoSyncEstimator.Result.Ambiguous || result is AutoSyncEstimator.Result.NotEnoughData)
    }

    @Test
    fun signCheckVocalsDelayedBy500msYieldOffsetNearPlus500() {
        val (lines, totalDuration) = createSyntheticSong(Random(101))
        val estimator = AutoSyncEstimator()
        val trueOffset = 500
        populateSyntheticSamples(estimator, lines, totalDuration, trueOffset = trueOffset, seed = 202)

        val result = estimator.estimate(lines)
        assertTrue("Expected Confident result for clean +500ms offset", result is AutoSyncEstimator.Result.Confident)
        val confident = result as AutoSyncEstimator.Result.Confident
        assertEquals(500.0, confident.offsetMs.toDouble(), 30.0)
    }

    @Test
    fun seekDiscontinuousSamplesStillYieldConfidentResultAtTrueOffset() {
        val (lines, _) = createSyntheticSong(Random(303))
        val estimator = AutoSyncEstimator()
        val trueOffset = 300
        val random = Random(404)

        // Section 1: 60s to 90s
        var pos = 60_000L
        while (pos < 90_000L) {
            val jitter = (random.nextGaussian() * 12.0).toLong()
            val samplePos = maxOf(0L, pos + jitter)
            val feature = evaluateSyntheticFeature(samplePos, lines, trueOffset, random)
            estimator.add(samplePos, feature)
            pos += 51L
        }

        // Section 2: 20s to 40s (jump backwards in position)
        pos = 20_000L
        while (pos < 40_000L) {
            val jitter = (random.nextGaussian() * 12.0).toLong()
            val samplePos = maxOf(0L, pos + jitter)
            val feature = evaluateSyntheticFeature(samplePos, lines, trueOffset, random)
            estimator.add(samplePos, feature)
            pos += 51L
        }

        val result = estimator.estimate(lines)
        assertTrue("Seek samples should still result in Confident", result is AutoSyncEstimator.Result.Confident)
        val confident = result as AutoSyncEstimator.Result.Confident
        assertEquals(trueOffset.toDouble(), confident.offsetMs.toDouble(), 30.0)
    }

    @Test
    fun syntheticSongMultipleOffsetsYieldConfidentWithin30ms() {
        val trueOffsets = intArrayOf(-1200, -300, 0, 450, 2000)
        val seed = 777L
        for (trueOffset in trueOffsets) {
            val (lines, totalDuration) = createSyntheticSong(Random(seed))
            val estimator = AutoSyncEstimator()
            populateSyntheticSamples(estimator, lines, totalDuration, trueOffset = trueOffset, seed = seed + trueOffset)

            val result = estimator.estimate(lines)
            assertTrue("Expected Confident for offset $trueOffset", result is AutoSyncEstimator.Result.Confident)
            val confident = result as AutoSyncEstimator.Result.Confident
            println("True offset: $trueOffset ms -> Estimated: ${confident.offsetMs} ms (peakCorr=${confident.peakCorrelation}, margin=${confident.margin})")
            assertEquals("Offset for $trueOffset should be within 30ms", trueOffset.toDouble(), confident.offsetMs.toDouble(), 30.0)
            assertTrue("Peak correlation must meet threshold", confident.peakCorrelation >= AutoSyncEstimator.MIN_PEAK_CORRELATION)
            assertTrue("Margin must meet threshold", confident.margin >= AutoSyncEstimator.MIN_PEAK_MARGIN)
        }
    }

    @Test
    fun benchmarkPerformanceFor4096SamplesAnd60Lines() {
        val random = Random(999)
        val lines = mutableListOf<LyricLine>()
        var cur = 1000L
        for (i in 0 until 60) {
            val dur = 2500L
            lines.add(LyricLine(startTime = cur, endTime = cur + dur, content = "Benchmark line $i"))
            cur += dur + 1000L
        }
        val estimator = AutoSyncEstimator(4096)
        for (i in 0 until 4096) {
            estimator.add(i * 51L, (i % 10).toFloat())
        }

        // Warmup
        estimator.estimate(lines)

        // Measured run
        val startNs = System.nanoTime()
        val result = estimator.estimate(lines)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
        println("Benchmark: estimate() for 4096 samples and 60 lines took $elapsedMs ms; result=$result")
        assertTrue("Estimate should complete in reasonable time (< 200ms)", elapsedMs < 200.0)
    }

    private fun createSyntheticSong(random: Random): Pair<List<LyricLine>, Long> {
        val lines = mutableListOf<LyricLine>()
        var currentTime = 1000L
        for (i in 0 until 40) {
            val duration = 2000L + random.nextInt(2001) // 2000..4000 ms
            val endTime = currentTime + duration
            val isInstrumental = (i == 20) // one instrumental line
            val content = if (isInstrumental) "" else "Line $i lyrics"
            val words = if (!isInstrumental && i % 2 == 0) {
                val numWords = 4
                val wordDur = duration / numWords
                (0 until numWords).map { w ->
                    LyricWord(
                        startTime = currentTime + w * wordDur,
                        endTime = currentTime + (w + 1) * wordDur,
                        text = "w$w",
                        startIndex = 0,
                        endIndex = 2
                    )
                }
            } else {
                null
            }
            lines.add(
                LyricLine(
                    startTime = currentTime,
                    endTime = endTime,
                    content = content,
                    isInstrumental = isInstrumental,
                    words = words
                )
            )
            val gap = 500L + random.nextInt(2501) // 500..3000 ms
            currentTime = endTime + gap
        }
        return Pair(lines, currentTime)
    }

    private fun populateSyntheticSamples(
        estimator: AutoSyncEstimator,
        lines: List<LyricLine>,
        totalDuration: Long,
        trueOffset: Int,
        seed: Long
    ) {
        val random = Random(seed)
        var pos = 0L
        while (pos < totalDuration && estimator.sampleCount() < AutoSyncEstimator.DEFAULT_CAPACITY) {
            val jitter = (random.nextGaussian() * 12.0).toLong()
            val samplePos = maxOf(0L, pos + jitter)
            val feature = evaluateSyntheticFeature(samplePos, lines, trueOffset, random)
            estimator.add(samplePos, feature)
            pos += 51L
        }
    }

    private fun evaluateSyntheticFeature(
        samplePos: Long,
        lines: List<LyricLine>,
        trueOffset: Int,
        random: Random
    ): Float {
        val tLyric = samplePos - trueOffset
        var inVocal = false
        for (i in lines.indices) {
            val line = lines[i]
            if (line.isInstrumental || line.content.isBlank()) continue
            if (!line.words.isNullOrEmpty()) {
                for (word in line.words!!) {
                    if (tLyric in word.startTime until word.endTime) {
                        inVocal = true
                        break
                    }
                }
            } else {
                val nextLine = lines.getOrNull(i + 1)
                val lineEnd = if (nextLine != null) minOf(line.endTime, nextLine.startTime) else line.endTime
                if (tLyric in line.startTime until lineEnd) {
                    inVocal = true
                }
            }
            if (inVocal) break
        }

        val baseline = 10.0 + 4.0 * sin(2.0 * Math.PI * samplePos / 60000.0)
        val vocalGain = 16.0
        val noise = random.nextDouble() * 2.0
        val energy = baseline + (if (inVocal) vocalGain else 0.0) + noise
        return ln(energy).toFloat()
    }
}
