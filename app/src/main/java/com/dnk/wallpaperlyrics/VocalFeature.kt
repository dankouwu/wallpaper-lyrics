package com.dnk.wallpaperlyrics

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln

/**
 * Audio feature extractor operating directly on Visualizer FFT byte captures.
 * Evaluates band energy without object allocation or per-bin division.
 */
object VocalFeature {
    const val LOW_HZ = 250
    const val HIGH_HZ = 3500

    /**
     * Returns the natural log of summed magnitude squared over bins whose centre frequency is in [lowHz, highHz],
     * or Float.NaN when that energy is 0 (silence, volume 0, or blocked capture).
     */
    fun vocalBandLogEnergy(
        fft: ByteArray,
        samplingRateMilliHz: Int,
        lowHz: Int = LOW_HZ,
        highHz: Int = HIGH_HZ
    ): Float {
        val captureSize = fft.size
        if (captureSize < 2 || samplingRateMilliHz <= 0 || lowHz > highHz) {
            return Float.NaN
        }

        val totalBins = captureSize / 2
        val binWidthHz = (samplingRateMilliHz.toDouble() / 1000.0) / captureSize.toDouble()
        if (binWidthHz <= 0.0) {
            return Float.NaN
        }

        // Small epsilon absorbs floating point rounding so boundary frequencies stay inclusive.
        val minK = ceil(lowHz.toDouble() / binWidthHz - 1e-9).toInt().coerceAtLeast(0)
        val maxK = floor(highHz.toDouble() / binWidthHz + 1e-9).toInt().coerceAtMost(totalBins - 1)
        if (minK > maxK) {
            return Float.NaN
        }

        var sumMagSq = 0.0
        for (k in minK..maxK) {
            val r = fft[2 * k].toDouble()
            val i = fft[2 * k + 1].toDouble()
            sumMagSq += r * r + i * i
        }

        if (sumMagSq <= 0.0) {
            return Float.NaN
        }
        return ln(sumMagSq).toFloat()
    }
}
