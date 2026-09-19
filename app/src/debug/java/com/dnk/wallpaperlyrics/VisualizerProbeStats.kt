package com.dnk.wallpaperlyrics

/**
 * Pure statistical and mathematical functions for the visualizer probe.
 * No Android framework dependencies so this logic can be unit-tested on the JVM.
 */
object VisualizerProbeStats {

    data class MinMax(val min: Int, val max: Int)

    data class IntervalStats(
        val count: Int,
        val minIntervalMs: Double,
        val meanIntervalMs: Double,
        val maxIntervalMs: Double
    )

    fun silentFraction(waveform: ByteArray): Float {
        if (waveform.isEmpty()) return 1.0f
        var silentCount = 0
        for (b in waveform) {
            if ((b.toInt() and 0xFF) == 128) {
                silentCount++
            }
        }
        return silentCount.toFloat() / waveform.size.toFloat()
    }

    fun unsignedMinMax(waveform: ByteArray): MinMax {
        if (waveform.isEmpty()) return MinMax(128, 128)
        var min = 255
        var max = 0
        for (b in waveform) {
            val u = b.toInt() and 0xFF
            if (u < min) min = u
            if (u > max) max = u
        }
        return MinMax(min, max)
    }

    fun vocalBandFraction(
        fft: ByteArray,
        samplingRateMilliHz: Int,
        lowHz: Int = 300,
        highHz: Int = 3400
    ): Float {
        val n = fft.size
        if (n < 2 || samplingRateMilliHz <= 0) return 0f
        val numBins = n / 2
        var vocalEnergy = 0.0
        var totalEnergy = 0.0
        for (k in 0 until numBins) {
            val r = fft[2 * k].toDouble()
            val i = fft[2 * k + 1].toDouble()
            val magSq = r * r + i * i
            val freqHz = (k.toDouble() * samplingRateMilliHz.toDouble()) / (n.toDouble() * 1000.0)
            if (freqHz in lowHz.toDouble()..highHz.toDouble()) {
                vocalEnergy += magSq
            }
            totalEnergy += magSq
        }
        if (totalEnergy <= 0.0) return 0f
        return (vocalEnergy / totalEnergy).toFloat()
    }

    /**
     * Fixed-size ring buffer for callback timestamps over a rolling duration.
     * Records callback arrival without per-callback object allocation.
     */
    class TimestampWindow(
        private val capacity: Int = 256,
        private val windowDurationNs: Long = 1_000_000_000L
    ) {
        private val timestamps = LongArray(capacity)
        private var head = 0
        private var size = 0

        @Synchronized
        fun record(timestampNs: Long) {
            prune(timestampNs)
            if (size == capacity) {
                head = (head + 1) % capacity
                size--
            }
            val tail = (head + size) % capacity
            timestamps[tail] = timestampNs
            size++
        }

        @Synchronized
        fun stats(nowNs: Long = -1L): IntervalStats {
            val effectiveNow = if (nowNs >= 0L) {
                nowNs
            } else if (size > 0) {
                timestamps[(head + size - 1) % capacity]
            } else {
                0L
            }
            prune(effectiveNow)
            if (size == 0) {
                return IntervalStats(0, 0.0, 0.0, 0.0)
            }
            if (size == 1) {
                return IntervalStats(1, 0.0, 0.0, 0.0)
            }

            var minNs = Long.MAX_VALUE
            var maxNs = Long.MIN_VALUE
            for (i in 0 until size - 1) {
                val t1 = timestamps[(head + i) % capacity]
                val t2 = timestamps[(head + i + 1) % capacity]
                val diff = t2 - t1
                if (diff < minNs) minNs = diff
                if (diff > maxNs) maxNs = diff
            }

            val totalNs = timestamps[(head + size - 1) % capacity] - timestamps[head]
            val meanNs = totalNs.toDouble() / (size - 1).toDouble()

            return IntervalStats(
                count = size,
                minIntervalMs = minNs / 1_000_000.0,
                meanIntervalMs = meanNs / 1_000_000.0,
                maxIntervalMs = maxNs / 1_000_000.0
            )
        }

        @Synchronized
        fun clear() {
            head = 0
            size = 0
        }

        private fun prune(cutoffNowNs: Long) {
            while (size > 0 && (cutoffNowNs - timestamps[head]) > windowDurationNs) {
                head = (head + 1) % capacity
                size--
            }
        }
    }
}
