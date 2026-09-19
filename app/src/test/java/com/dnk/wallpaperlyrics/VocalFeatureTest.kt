package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

class VocalFeatureTest {

    private val sampleRate48kMilliHz = 48_000_000

    @Test
    fun constantsMatchSpecification() {
        assertEquals(250, VocalFeature.LOW_HZ)
        assertEquals(3500, VocalFeature.HIGH_HZ)
    }

    @Test
    fun allZeroInputReturnsNaN() {
        val fft = ByteArray(1024)
        val energy = VocalFeature.vocalBandLogEnergy(fft, sampleRate48kMilliHz)
        assertTrue("Energy for all-zero capture should be NaN", energy.isNaN())
    }

    @Test
    fun emptyOrInvalidInputReturnsNaN() {
        assertTrue(VocalFeature.vocalBandLogEnergy(ByteArray(0), sampleRate48kMilliHz).isNaN())
        assertTrue(VocalFeature.vocalBandLogEnergy(ByteArray(1), sampleRate48kMilliHz).isNaN())
        assertTrue(VocalFeature.vocalBandLogEnergy(ByteArray(1024), 0).isNaN())
        assertTrue(VocalFeature.vocalBandLogEnergy(ByteArray(1024), -48_000_000).isNaN())
    }

    @Test
    fun energyOnlyInsideBandIsFiniteAndMonotonicWithMagnitude() {
        val quietFft = ByteArray(1024)
        // Bin 20 is around 937.5 Hz (within 250..3500 Hz).
        quietFft[2 * 20] = 5
        quietFft[2 * 20 + 1] = 0

        val loudFft = ByteArray(1024)
        loudFft[2 * 20] = 20
        loudFft[2 * 20 + 1] = 0

        val quietEnergy = VocalFeature.vocalBandLogEnergy(quietFft, sampleRate48kMilliHz)
        val loudEnergy = VocalFeature.vocalBandLogEnergy(loudFft, sampleRate48kMilliHz)

        assertFalse(quietEnergy.isNaN())
        assertFalse(loudEnergy.isNaN())
        assertTrue("Louder in-band signal must have higher log energy", loudEnergy > quietEnergy)

        val expectedQuiet = ln(25.0).toFloat()
        assertEquals(expectedQuiet, quietEnergy, 1e-4f)

        val expectedLoud = ln(400.0).toFloat()
        assertEquals(expectedLoud, loudEnergy, 1e-4f)
    }

    @Test
    fun energyOnlyOutsideBandReturnsNaN() {
        val fft = ByteArray(1024)
        // Bin 2 is 93.75 Hz (< 250 Hz)
        fft[2 * 2] = 50
        fft[2 * 2 + 1] = 50
        // Bin 100 is 4687.5 Hz (> 3500 Hz)
        fft[2 * 100] = 50
        fft[2 * 100 + 1] = 50

        val energy = VocalFeature.vocalBandLogEnergy(fft, sampleRate48kMilliHz)
        assertTrue("Energy outside vocal band should be ignored resulting in NaN", energy.isNaN())
    }

    @Test
    fun bandEdgesAt48kHzAnd1024BinsSelectExpectedBins() {
        // At 48000 Hz and 1024 bins, bin width is 46.875 Hz.
        // Bin 5 is 234.375 Hz (outside [250, 3500]).
        // Bin 6 is 281.25 Hz (inside [250, 3500]).
        // Bin 74 is 3468.75 Hz (inside [250, 3500]).
        // Bin 75 is 3515.625 Hz (outside [250, 3500]).

        val bin5Fft = ByteArray(1024).apply { this[2 * 5] = 20 }
        assertTrue("Bin 5 is below 250 Hz and should yield NaN", VocalFeature.vocalBandLogEnergy(bin5Fft, sampleRate48kMilliHz).isNaN())

        val bin6Fft = ByteArray(1024).apply { this[2 * 6] = 20 }
        assertFalse("Bin 6 is above 250 Hz and should yield finite energy", VocalFeature.vocalBandLogEnergy(bin6Fft, sampleRate48kMilliHz).isNaN())

        val bin74Fft = ByteArray(1024).apply { this[2 * 74] = 20 }
        assertFalse("Bin 74 is below 3500 Hz and should yield finite energy", VocalFeature.vocalBandLogEnergy(bin74Fft, sampleRate48kMilliHz).isNaN())

        val bin75Fft = ByteArray(1024).apply { this[2 * 75] = 20 }
        assertTrue("Bin 75 is above 3500 Hz and should yield NaN", VocalFeature.vocalBandLogEnergy(bin75Fft, sampleRate48kMilliHz).isNaN())
    }

    @Test
    fun negativeSignedByteValuesContributePositiveMagnitudeSquared() {
        val fft = ByteArray(1024)
        // Bin 10 is 468.75 Hz
        fft[2 * 10] = (-30).toByte()
        fft[2 * 10 + 1] = (-40).toByte()

        val energy = VocalFeature.vocalBandLogEnergy(fft, sampleRate48kMilliHz)
        val expected = ln(30.0 * 30.0 + 40.0 * 40.0).toFloat()
        assertEquals(expected, energy, 1e-4f)
    }
}
