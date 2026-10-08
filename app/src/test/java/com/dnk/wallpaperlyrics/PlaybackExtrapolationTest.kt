package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackExtrapolationTest {

    @Test
    fun `extrapolatePlaybackPosition evenly spaced clock inputs give evenly spaced positions`() {
        val basePosition = 15_000L
        val baseUpdateTime = 1_000L
        val speed = 1.0f
        val durationMs = 200_000L

        val clockInputs = listOf(1_000L, 1_020L, 1_040L, 1_060L, 1_080L, 1_100L)
        val positions = clockInputs.map { clockMs ->
            extrapolatePlaybackPosition(
                basePosition = basePosition,
                baseUpdateTime = baseUpdateTime,
                speed = speed,
                clockMs = clockMs,
                durationMs = durationMs
            )
        }

        assertEquals(listOf(15_000L, 15_020L, 15_040L, 15_060L, 15_080L, 15_100L), positions)

        val intervals = positions.zipWithNext { a, b -> b - a }
        assertTrue(intervals.all { it == 20L })
    }

    @Test
    fun `extrapolatePlaybackPosition honors playback speed`() {
        val basePosition = 10_000L
        val baseUpdateTime = 1_000L
        val durationMs = 200_000L
        val clockMs = 1_200L

        val posFast = extrapolatePlaybackPosition(
            basePosition = basePosition,
            baseUpdateTime = baseUpdateTime,
            speed = 1.5f,
            clockMs = clockMs,
            durationMs = durationMs
        )
        assertEquals(10_300L, posFast)

        val posSlow = extrapolatePlaybackPosition(
            basePosition = basePosition,
            baseUpdateTime = baseUpdateTime,
            speed = 0.5f,
            clockMs = clockMs,
            durationMs = durationMs
        )
        assertEquals(10_100L, posSlow)

        val posDouble = extrapolatePlaybackPosition(
            basePosition = basePosition,
            baseUpdateTime = baseUpdateTime,
            speed = 2.0f,
            clockMs = clockMs,
            durationMs = durationMs
        )
        assertEquals(10_400L, posDouble)

        val posZero = extrapolatePlaybackPosition(
            basePosition = basePosition,
            baseUpdateTime = baseUpdateTime,
            speed = 0.0f,
            clockMs = clockMs,
            durationMs = durationMs
        )
        assertEquals(10_200L, posZero)

        val posNegative = extrapolatePlaybackPosition(
            basePosition = basePosition,
            baseUpdateTime = baseUpdateTime,
            speed = -1.0f,
            clockMs = clockMs,
            durationMs = durationMs
        )
        assertEquals(10_200L, posNegative)
    }

    @Test
    fun `extrapolatePlaybackPosition clamps result to duration when positive`() {
        val basePosition = 179_000L
        val baseUpdateTime = 1_000L
        val durationMs = 180_000L

        val clamped = extrapolatePlaybackPosition(
            basePosition = basePosition,
            baseUpdateTime = baseUpdateTime,
            speed = 1.0f,
            clockMs = 3_000L,
            durationMs = durationMs
        )
        assertEquals(180_000L, clamped)

        val unclampedZero = extrapolatePlaybackPosition(
            basePosition = basePosition,
            baseUpdateTime = baseUpdateTime,
            speed = 1.0f,
            clockMs = 3_000L,
            durationMs = 0L
        )
        assertEquals(181_000L, unclampedZero)

        val unclampedNegative = extrapolatePlaybackPosition(
            basePosition = basePosition,
            baseUpdateTime = baseUpdateTime,
            speed = 1.0f,
            clockMs = 3_000L,
            durationMs = -1L
        )
        assertEquals(181_000L, unclampedNegative)
    }

    @Test
    fun `frameTimeToElapsedRealtimeMs converts Choreographer nanoTime to elapsedRealtime ms`() {
        val elapsedRealtimeNanos = 50_000_000_000L
        val nanoTime = 120_000_000_000L

        val frameTimeNanos = 119_990_000_000L
        val resultMs = frameTimeToElapsedRealtimeMs(
            frameTimeNanos = frameTimeNanos,
            elapsedRealtimeNanos = elapsedRealtimeNanos,
            nanoTime = nanoTime
        )
        assertEquals(49_990L, resultMs)
    }

    @Test
    fun `frameTimeToElapsedRealtimeMs evenly spaced vsync timestamps yield steady intervals`() {
        val elapsedRealtimeNanos = 100_000_000_000L
        val nanoTime = 100_000_000_000L
        val frameDurationNanos = 16_666_667L

        val frames = (0 until 5).map { i ->
            frameTimeToElapsedRealtimeMs(
                frameTimeNanos = 90_000_000_000L + i * frameDurationNanos,
                elapsedRealtimeNanos = elapsedRealtimeNanos,
                nanoTime = nanoTime
            )
        }

        val deltas = frames.zipWithNext { a, b -> b - a }
        assertTrue(deltas.all { it in 16L..17L })
    }
}
