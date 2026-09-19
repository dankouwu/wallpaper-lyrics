package com.dnk.wallpaperlyrics

import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages the Android Visualizer lifecycle on a dedicated HandlerThread.
 * Feeds vocal band energy features directly into AutoSyncEstimator without main thread contention.
 */
class AutoSyncCapture(
    private val estimator: AutoSyncEstimator,
    private val snapshotProvider: () -> PlaybackSnapshot,
    private val onFailure: (reason: String) -> Unit
) {

    companion object {
        private const val TAG = "AutoSync"
        private const val INIT_TIMEOUT_MS = 1000L
        private const val STOP_TIMEOUT_MS = 500L
        private const val PERIODIC_LOG_INTERVAL_MS = 5000L

        private val isProcessCaptureActive = AtomicBoolean(false)

        fun isProcessCaptureRunning(): Boolean = isProcessCaptureActive.get()
    }

    @Volatile
    private var isRunning = false

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var visualizer: Visualizer? = null
    private var actualSamplingRateMilliHz: Int = 0

    private var nanCountInWindow = 0
    private var totalCountInWindow = 0

    private val periodicLogRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            val nan = nanCountInWindow
            val total = totalCountInWindow
            nanCountInWindow = 0
            totalCountInWindow = 0
            val count = estimator.sampleCount()
            Log.d(TAG, "Capturing: samples=$count, NaN callbacks=$nan/$total in last 5s")
            handler?.postDelayed(this, PERIODIC_LOG_INTERVAL_MS)
        }
    }

    private val captureListener = object : Visualizer.OnDataCaptureListener {
        override fun onWaveFormDataCapture(viz: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
            // Waveform capture is disabled; only FFT callbacks are processed.
        }

        override fun onFftDataCapture(viz: Visualizer?, fft: ByteArray?, samplingRate: Int) {
            if (!isRunning || fft == null) return
            val snapshot = snapshotProvider()
            if (!snapshot.isPlaying) return

            val rate = if (samplingRate > 0) samplingRate else actualSamplingRateMilliHz
            if (rate <= 0) return

            val feature = VocalFeature.vocalBandLogEnergy(fft, rate)
            totalCountInWindow++
            if (feature.isNaN()) {
                nanCountInWindow++
            } else {
                val now = SystemClock.elapsedRealtime()
                val positionMs = snapshot.currentPosition(now)
                estimator.add(positionMs, feature)
            }
        }
    }

    fun isCapturing(): Boolean = isRunning

    @Synchronized
    fun start(): Boolean {
        if (isRunning) return true

        if (!isProcessCaptureActive.compareAndSet(false, true)) {
            Log.w(TAG, "Cannot start capture: another capture is already active in process")
            return false
        }

        val thread = HandlerThread("AutoSyncCaptureThread", Process.THREAD_PRIORITY_AUDIO)
        thread.start()
        val localHandler = Handler(thread.looper)
        handlerThread = thread
        handler = localHandler

        val initLatch = CountDownLatch(1)
        var initSuccess = false
        var initError: String? = null

        localHandler.post {
            try {
                val viz = Visualizer(0)
                visualizer = viz

                val captureRange = Visualizer.getCaptureSizeRange()
                val targetCaptureSize = captureRange[1]
                val setCaptureSizeRet = viz.setCaptureSize(targetCaptureSize)
                if (setCaptureSizeRet != Visualizer.SUCCESS) {
                    val reason = "setCaptureSize failed with code $setCaptureSizeRet"
                    Log.e(TAG, reason)
                    initError = reason
                    return@post
                }

                val scalingRet = viz.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)
                if (scalingRet != Visualizer.SUCCESS) {
                    val reason = "setScalingMode failed with code $scalingRet"
                    Log.e(TAG, reason)
                    initError = reason
                    return@post
                }

                actualSamplingRateMilliHz = viz.samplingRate
                val maxRate = Visualizer.getMaxCaptureRate()

                val setListenerRet = viz.setDataCaptureListener(captureListener, maxRate, false, true)
                if (setListenerRet != Visualizer.SUCCESS) {
                    val reason = "setDataCaptureListener failed with code $setListenerRet"
                    Log.e(TAG, reason)
                    initError = reason
                    return@post
                }

                val setEnabledRet = viz.setEnabled(true)
                if (setEnabledRet != Visualizer.SUCCESS) {
                    val reason = "setEnabled failed with code $setEnabledRet"
                    Log.e(TAG, reason)
                    initError = reason
                    return@post
                }

                isRunning = true
                nanCountInWindow = 0
                totalCountInWindow = 0
                localHandler.postDelayed(periodicLogRunnable, PERIODIC_LOG_INTERVAL_MS)
                initSuccess = true
                Log.i(TAG, "Audio capture started (rate=${maxRate}mHz, size=$targetCaptureSize)")
            } catch (t: Throwable) {
                val reason = "${t.javaClass.simpleName}: ${t.message}"
                Log.e(TAG, "Exception starting Visualizer: $reason", t)
                initError = reason
            } finally {
                initLatch.countDown()
            }
        }

        try {
            initLatch.await(INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            initError = "Initialization timed out"
        }

        if (!initSuccess) {
            val reason = initError ?: "Unknown initialization failure"
            stop()
            onFailure(reason)
            return false
        }

        return true
    }

    @Synchronized
    fun stop() {
        val wasRunning = isRunning
        isRunning = false

        val thread = handlerThread
        val localHandler = handler
        val viz = visualizer

        visualizer = null
        handler = null
        handlerThread = null

        if (thread != null) {
            localHandler?.removeCallbacks(periodicLogRunnable)
            val stopLatch = CountDownLatch(1)
            localHandler?.post {
                try {
                    viz?.enabled = false
                } catch (t: Throwable) {
                    Log.w(TAG, "Error disabling Visualizer", t)
                }
                try {
                    viz?.release()
                } catch (t: Throwable) {
                    Log.w(TAG, "Error releasing Visualizer", t)
                }
                stopLatch.countDown()
            }
            try {
                stopLatch.await(STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {}
            thread.quitSafely()
        }

        if (wasRunning || isProcessCaptureActive.get()) {
            isProcessCaptureActive.set(false)
            Log.i(TAG, "Audio capture stopped")
        }
    }
}
