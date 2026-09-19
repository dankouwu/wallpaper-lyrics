package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class VisualizerProbeStatsTest {

    @Test
    fun silentFraction_all128_returnsOne() {
        val wave = ByteArray(1024) { 128.toByte() }
        assertEquals(1.0f, VisualizerProbeStats.silentFraction(wave), 0.0001f)
    }

    @Test
    fun silentFraction_mixed_returnsCorrectFraction() {
        val wave = ByteArray(100) { if (it < 40) 128.toByte() else 0.toByte() }
        assertEquals(0.4f, VisualizerProbeStats.silentFraction(wave), 0.0001f)
    }

    @Test
    fun silentFraction_empty_returnsOne() {
        assertEquals(1.0f, VisualizerProbeStats.silentFraction(ByteArray(0)), 0.0001f)
    }

    @Test
    fun unsignedMinMax_normalInput() {
        val wave = byteArrayOf(0.toByte(), 128.toByte(), (-1).toByte(), 50.toByte())
        val minMax = VisualizerProbeStats.unsignedMinMax(wave)
        assertEquals(0, minMax.min)
        assertEquals(255, minMax.max)
    }

    @Test
    fun unsignedMinMax_all128() {
        val wave = ByteArray(10) { 128.toByte() }
        val minMax = VisualizerProbeStats.unsignedMinMax(wave)
        assertEquals(128, minMax.min)
        assertEquals(128, minMax.max)
    }

    @Test
    fun unsignedMinMax_empty() {
        val minMax = VisualizerProbeStats.unsignedMinMax(ByteArray(0))
        assertEquals(128, minMax.min)
        assertEquals(128, minMax.max)
    }

    @Test
    fun vocalBandFraction_empty_returnsZero() {
        assertEquals(0f, VisualizerProbeStats.vocalBandFraction(ByteArray(0), 44_100_000), 0.0001f)
    }

    @Test
    fun vocalBandFraction_allZero_returnsZero() {
        val fft = ByteArray(1024) { 0 }
        assertEquals(0f, VisualizerProbeStats.vocalBandFraction(fft, 44_100_000), 0.0001f)
    }

    @Test
    fun vocalBandFraction_singleInBandBin_returnsOne() {
        // At 44.1 kHz with 1024 FFT size, bin 23 is ~990.5 Hz (within 300 to 3400 Hz).
        val fft = ByteArray(1024) { 0 }
        fft[2 * 23] = 40
        fft[2 * 23 + 1] = 30
        assertEquals(1.0f, VisualizerProbeStats.vocalBandFraction(fft, 44_100_000), 0.0001f)
    }

    @Test
    fun vocalBandFraction_singleOutOfBandBin_returnsZero() {
        // Bin 2 is ~86.1 Hz (< 300 Hz).
        val fft = ByteArray(1024) { 0 }
        fft[2 * 2] = 50
        assertEquals(0.0f, VisualizerProbeStats.vocalBandFraction(fft, 44_100_000), 0.0001f)
    }

    @Test
    fun vocalBandFraction_mixedBins_returnsProportionalFraction() {
        // In-band bin 23 has magSq = 30^2 + 40^2 = 2500.
        // Out-of-band bin 2 has magSq = 50^2 = 2500.
        // Total = 5000, Vocal = 2500, ratio = 0.5.
        val fft = ByteArray(1024) { 0 }
        fft[2 * 23] = 30
        fft[2 * 23 + 1] = 40
        fft[2 * 2] = 50
        assertEquals(0.5f, VisualizerProbeStats.vocalBandFraction(fft, 44_100_000), 0.0001f)
    }

    @Test
    fun timestampWindow_dropsOlderThanOneSecond_andReportsCorrectIntervals() {
        val window = VisualizerProbeStats.TimestampWindow(capacity = 16, windowDurationNs = 1_000_000_000L)
        window.record(1_000_000_000L)
        window.record(1_200_000_000L)
        window.record(1_500_000_000L)
        window.record(2_100_000_000L)

        val stats = window.stats()
        assertEquals(3, stats.count)
        assertEquals(300.0, stats.minIntervalMs, 0.001)
        assertEquals(450.0, stats.meanIntervalMs, 0.001)
        assertEquals(600.0, stats.maxIntervalMs, 0.001)
    }

    @Test
    fun timestampWindow_singleOrZeroCallbacks_handlesGracefully() {
        val window = VisualizerProbeStats.TimestampWindow(capacity = 16)
        val emptyStats = window.stats()
        assertEquals(0, emptyStats.count)
        assertEquals(0.0, emptyStats.minIntervalMs, 0.001)
        assertEquals(0.0, emptyStats.meanIntervalMs, 0.001)
        assertEquals(0.0, emptyStats.maxIntervalMs, 0.001)

        window.record(1_000_000_000L)
        val singleStats = window.stats()
        assertEquals(1, singleStats.count)
        assertEquals(0.0, singleStats.minIntervalMs, 0.001)
        assertEquals(0.0, singleStats.meanIntervalMs, 0.001)
        assertEquals(0.0, singleStats.maxIntervalMs, 0.001)
    }

    @Test
    fun timestampWindow_ringBufferWrapping_whenFull() {
        val window = VisualizerProbeStats.TimestampWindow(capacity = 3, windowDurationNs = 1_000_000_000L)
        window.record(1_000_000_000L)
        window.record(1_100_000_000L)
        window.record(1_200_000_000L)
        window.record(1_300_000_000L)

        val stats = window.stats()
        assertEquals(3, stats.count)
        assertEquals(100.0, stats.minIntervalMs, 0.001)
        assertEquals(100.0, stats.meanIntervalMs, 0.001)
        assertEquals(100.0, stats.maxIntervalMs, 0.001)
    }
}
