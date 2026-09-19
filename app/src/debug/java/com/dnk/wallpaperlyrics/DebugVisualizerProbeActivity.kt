package com.dnk.wallpaperlyrics

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.AudioManager
import android.media.audiofx.Visualizer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class DebugVisualizerProbeActivity : Activity() {

    private companion object {
        private const val REQUEST_RECORD_AUDIO = 1001
        private const val UI_REFRESH_INTERVAL_MS = 100L
        private const val FFT_BAR_COUNT = 128
        private const val TAG = "VizProbe"
    }

    private var visualizer: Visualizer? = null
    private var audioManager: AudioManager? = null

    private val waveformWindow = VisualizerProbeStats.TimestampWindow()
    private val fftWindow = VisualizerProbeStats.TimestampWindow()

    private val measurementPeakRms = Visualizer.MeasurementPeakRms()
    private val fftMagnitudesBuffer = FloatArray(FFT_BAR_COUNT)
    private val fftMagnitudesUi = FloatArray(FFT_BAR_COUNT)

    @Volatile private var statusText: String = "INITIALIZING"
    @Volatile private var currentCsvPath: String = ""
    @Volatile private var actualCaptureSize: Int = 0
    @Volatile private var actualMaxCaptureRateMilliHz: Int = 0
    @Volatile private var actualSamplingRateMilliHz: Int = 0

    @Volatile private var latestPeakMb: Int = -9600
    @Volatile private var latestRmsMb: Int = -9600
    @Volatile private var latestWaveMin: Int = 128
    @Volatile private var latestWaveMax: Int = 128
    @Volatile private var latestSilentFraction: Float = 1.0f
    @Volatile private var latestVocalPercentage: Float = 0.0f
    private val consecutiveSilentCaptures = AtomicInteger(0)

    private var csvExecutor: ExecutorService? = null
    private var csvWriter: BufferedWriter? = null

    private val uiHandler = Handler(Looper.getMainLooper())
    private val uiRefreshRunnable = object : Runnable {
        override fun run() {
            updateUi()
            uiHandler.postDelayed(this, UI_REFRESH_INTERVAL_MS)
        }
    }

    private lateinit var statsTextView: TextView
    private lateinit var fftBarsView: FftBarsView

    private val captureListener = object : Visualizer.OnDataCaptureListener {
        override fun onWaveFormDataCapture(
            viz: Visualizer?,
            waveform: ByteArray?,
            samplingRate: Int
        ) {
            if (waveform == null) return
            val nowNanos = SystemClock.elapsedRealtimeNanos()
            waveformWindow.record(nowNanos)

            var peakMb = -9600
            var rmsMb = -9600
            val v = visualizer
            if (v != null) {
                try {
                    if (v.getMeasurementPeakRms(measurementPeakRms) == Visualizer.SUCCESS) {
                        peakMb = measurementPeakRms.mPeak
                        rmsMb = measurementPeakRms.mRms
                    }
                } catch (_: Throwable) {
                }
            }

            val minMax = VisualizerProbeStats.unsignedMinMax(waveform)
            val silentFrac = VisualizerProbeStats.silentFraction(waveform)
            val isSilent = silentFrac >= 1.0f

            if (isSilent) {
                consecutiveSilentCaptures.incrementAndGet()
            } else {
                consecutiveSilentCaptures.set(0)
            }

            latestPeakMb = peakMb
            latestRmsMb = rmsMb
            latestWaveMin = minMax.min
            latestWaveMax = minMax.max
            latestSilentFraction = silentFrac
            if (samplingRate > 0) {
                actualSamplingRateMilliHz = samplingRate
            }

            logCapture(nowNanos, "wave", peakMb, rmsMb, isSilent, latestVocalPercentage)
        }

        override fun onFftDataCapture(
            viz: Visualizer?,
            fft: ByteArray?,
            samplingRate: Int
        ) {
            if (fft == null) return
            val nowNanos = SystemClock.elapsedRealtimeNanos()
            fftWindow.record(nowNanos)

            if (samplingRate > 0) {
                actualSamplingRateMilliHz = samplingRate
            }
            val vocalFrac = VisualizerProbeStats.vocalBandFraction(fft, actualSamplingRateMilliHz)
            val vocalPct = vocalFrac * 100.0f
            latestVocalPercentage = vocalPct

            val numBins = minOf(FFT_BAR_COUNT, fft.size / 2)
            synchronized(fftMagnitudesBuffer) {
                for (k in 0 until numBins) {
                    val r = fft[2 * k].toDouble()
                    val i = fft[2 * k + 1].toDouble()
                    val mag = Math.hypot(r, i)
                    // 181 is approximately sqrt(128^2 + 128^2), the maximum magnitude of two signed bytes
                    fftMagnitudesBuffer[k] = (mag / 181.0).toFloat().coerceIn(0f, 1f)
                }
                for (k in numBins until FFT_BAR_COUNT) {
                    fftMagnitudesBuffer[k] = 0f
                }
            }

            val isSilent = consecutiveSilentCaptures.get() > 0
            logCapture(nowNanos, "fft", latestPeakMb, latestRmsMb, isSilent, vocalPct)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        buildViewHierarchy()

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            statusText = "Requesting RECORD_AUDIO permission"
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
        }
    }

    override fun onResume() {
        super.onResume()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVisualizer()
        } else {
            statusText = "Permission denied: RECORD_AUDIO required"
        }
        uiHandler.removeCallbacks(uiRefreshRunnable)
        uiHandler.post(uiRefreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(uiRefreshRunnable)
        stopVisualizer()
        stopCsvWriter()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                statusText = "Permission granted, starting probe"
                startVisualizer()
            } else {
                statusText = "Permission denied: RECORD_AUDIO required"
                stopVisualizer()
            }
            updateUi()
        }
    }

    private fun buildViewHierarchy() {
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val titleView = TextView(this).apply {
            text = "Visualizer Probe (Session 0)"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, (8 * density).toInt())
        }

        statsTextView = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#E0E0E0"))
            setPadding(0, 0, 0, (12 * density).toInt())
        }

        val fftTitleView = TextView(this).apply {
            text = "FFT Magnitude (First 128 Bins):"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#AAAAAA"))
            setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
        }

        fftBarsView = FftBarsView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (100 * density).toInt()
            ).apply {
                bottomMargin = (16 * density).toInt()
            }
        }

        val copyButton = Button(this).apply {
            text = "Copy summary"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2A2A2A"))
            setOnClickListener {
                copySummaryToClipboard()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        container.addView(titleView)
        container.addView(statsTextView)
        container.addView(fftTitleView)
        container.addView(fftBarsView)
        container.addView(copyButton)

        scrollView.addView(container)
        setContentView(scrollView)
    }

    private fun startVisualizer() {
        stopVisualizer()
        startCsvWriter()

        try {
            val viz = Visualizer(0)
            visualizer = viz

            val captureRange = Visualizer.getCaptureSizeRange()
            val targetCaptureSize = captureRange[1]
            val setCaptureSizeRet = viz.setCaptureSize(targetCaptureSize)
            if (setCaptureSizeRet != Visualizer.SUCCESS) {
                statusText = "setCaptureSize failed: code $setCaptureSizeRet"
                return
            }
            actualCaptureSize = viz.captureSize

            val scalingRet = viz.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)
            if (scalingRet != Visualizer.SUCCESS) {
                statusText = "setScalingMode failed: code $scalingRet"
                return
            }

            val measurementRet = viz.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS)
            if (measurementRet != Visualizer.SUCCESS) {
                statusText = "setMeasurementMode failed: code $measurementRet"
                return
            }

            val maxRate = Visualizer.getMaxCaptureRate()
            actualMaxCaptureRateMilliHz = maxRate
            actualSamplingRateMilliHz = viz.samplingRate

            val setListenerRet = viz.setDataCaptureListener(captureListener, maxRate, true, true)
            if (setListenerRet != Visualizer.SUCCESS) {
                statusText = "setDataCaptureListener failed: code $setListenerRet"
                return
            }

            val setEnabledRet = viz.setEnabled(true)
            if (setEnabledRet != Visualizer.SUCCESS) {
                statusText = "setEnabled failed: code $setEnabledRet"
                return
            }

            statusText = "RUNNING"
        } catch (t: Throwable) {
            statusText = "${t.javaClass.name}: ${t.message}"
            stopVisualizer()
        }
    }

    private fun stopVisualizer() {
        visualizer?.let { viz ->
            try {
                viz.enabled = false
            } catch (_: Throwable) {
            }
            try {
                viz.release()
            } catch (_: Throwable) {
            }
        }
        visualizer = null
    }

    private fun startCsvWriter() {
        stopCsvWriter()
        val dir = getExternalFilesDir(null)
        if (dir != null) {
            val file = File(dir, "vizprobe-${System.currentTimeMillis()}.csv")
            currentCsvPath = file.absolutePath
            val executor = Executors.newSingleThreadExecutor()
            csvExecutor = executor
            executor.execute {
                try {
                    val writer = BufferedWriter(FileWriter(file, true))
                    writer.write("elapsedRealtimeNanos,kind,peak_mB,rms_mB,silent,vocal_percentage\n")
                    writer.flush()
                    csvWriter = writer
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize CSV writer: ${e.message}")
                }
            }
        }
    }

    private fun stopCsvWriter() {
        val executor = csvExecutor
        csvExecutor = null
        if (executor != null) {
            executor.execute {
                try {
                    csvWriter?.flush()
                    csvWriter?.close()
                } catch (_: Exception) {
                } finally {
                    csvWriter = null
                }
            }
            executor.shutdown()
        }
    }

    private fun logCapture(
        nowNanos: Long,
        kind: String,
        peakMb: Int,
        rmsMb: Int,
        isSilent: Boolean,
        vocalPct: Float
    ) {
        val vocalFormatted = String.format(Locale.US, "%.1f", vocalPct)
        Log.d(
            TAG,
            "time=$nowNanos kind=$kind peak=${peakMb}mB rms=${rmsMb}mB silent=$isSilent vocal=${vocalFormatted}%"
        )
        val line = "$nowNanos,$kind,$peakMb,$rmsMb,$isSilent,$vocalFormatted\n"
        csvExecutor?.execute {
            try {
                csvWriter?.write(line)
            } catch (_: Exception) {
            }
        }
    }

    private fun updateUi() {
        val summary = buildSummaryText()
        statsTextView.text = summary

        synchronized(fftMagnitudesBuffer) {
            System.arraycopy(fftMagnitudesBuffer, 0, fftMagnitudesUi, 0, FFT_BAR_COUNT)
        }
        fftBarsView.updateMagnitudes(fftMagnitudesUi)
    }

    private fun buildSummaryText(): String {
        val nowNs = SystemClock.elapsedRealtimeNanos()
        val waveStats = waveformWindow.stats(nowNs)
        val fftStats = fftWindow.stats(nowNs)
        val musicActive = audioManager?.isMusicActive ?: false

        val peakDb = latestPeakMb / 100.0
        val rmsDb = latestRmsMb / 100.0
        val peakStr = if (latestPeakMb <= -9600) "-96.00 dB (silence)" else String.format(Locale.US, "%.2f dB (%d mB)", peakDb, latestPeakMb)
        val rmsStr = if (latestRmsMb <= -9600) "-96.00 dB (silence)" else String.format(Locale.US, "%.2f dB (%d mB)", rmsDb, latestRmsMb)

        val maxRateHz = actualMaxCaptureRateMilliHz / 1000.0
        val samplingRateHz = actualSamplingRateMilliHz / 1000.0
        val silentPct = latestSilentFraction * 100.0f

        return buildString {
            append("Status: ").append(statusText).append("\n")
            append("AudioManager.isMusicActive: ").append(musicActive).append("\n")
            append("CSV: ").append(if (currentCsvPath.isNotEmpty()) currentCsvPath else "pending").append("\n\n")

            append("Capture size: ").append(actualCaptureSize).append("\n")
            append(String.format(Locale.US, "Max capture rate: %.2f Hz (%d milliHz)\n", maxRateHz, actualMaxCaptureRateMilliHz))
            append(String.format(Locale.US, "Sampling rate: %.2f Hz (%d milliHz)\n\n", samplingRateHz, actualSamplingRateMilliHz))

            append("Waveform callbacks/sec: ").append(waveStats.count).append("\n")
            append("FFT callbacks/sec: ").append(fftStats.count).append("\n")
            append(String.format(
                Locale.US,
                "Callback interval (last sec): min %.1f ms, mean %.1f ms, max %.1f ms\n\n",
                waveStats.minIntervalMs,
                waveStats.meanIntervalMs,
                waveStats.maxIntervalMs
            ))

            append("Peak: ").append(peakStr).append("\n")
            append("RMS: ").append(rmsStr).append("\n\n")

            append(String.format(
                Locale.US,
                "Waveform: min byte %d, max byte %d, silence %.1f%%\n",
                latestWaveMin,
                latestWaveMax,
                silentPct
            ))
            append("Consecutive silent captures: ").append(consecutiveSilentCaptures.get()).append("\n\n")

            append(String.format(Locale.US, "FFT vocal band (300-3400 Hz): %.1f%%", latestVocalPercentage))
        }
    }

    private fun copySummaryToClipboard() {
        val summary = buildSummaryText()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Visualizer Probe Summary", summary)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Summary copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private class FftBarsView(context: Context) : View(context) {
        private val barPaint = Paint().apply {
            color = Color.parseColor("#4CAF50")
            style = Paint.Style.FILL
        }
        private val backgroundPaint = Paint().apply {
            color = Color.parseColor("#1E1E1E")
            style = Paint.Style.FILL
        }
        private val magnitudes = FloatArray(FFT_BAR_COUNT)

        fun updateMagnitudes(source: FloatArray) {
            val count = minOf(FFT_BAR_COUNT, source.size)
            System.arraycopy(source, 0, magnitudes, 0, count)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            canvas.drawRect(0f, 0f, w, h, backgroundPaint)

            val slotWidth = w / FFT_BAR_COUNT.toFloat()
            val barWidth = maxOf(1f, slotWidth - 1f)

            for (i in 0 until FFT_BAR_COUNT) {
                val mag = magnitudes[i].coerceIn(0f, 1f)
                val barHeight = mag * h
                val left = i * slotWidth
                val top = h - barHeight
                val right = left + barWidth
                val bottom = h
                canvas.drawRect(left, top, right, bottom, barPaint)
            }
        }
    }
}
