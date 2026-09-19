package com.dnk.wallpaperlyrics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Choreographer
import android.view.View
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Compact live preview of the wallpaper background and album art card.
 * Reproduces the engine rendering path at 128x128 resolution to keep frame rendering cheap.
 */
class BackgroundPreviewView(context: Context) : View(context) {

    private val cornerRadiusPx = LyricsSettings.dpToPx(context, 18f).toFloat()

    private val shaderPaint = Paint()
    private val auroraPaints = List(5) {
        Paint().apply {
            isAntiAlias = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
    }

    private val runtimeShader: RuntimeShader? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        try {
            RuntimeShader(LyricsWallpaperService.AURORA_SHADER)
        } catch (e: Exception) {
            null
        }
    } else {
        null
    }

    private val albumArtPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // Pre-allocated geometry objects to prevent GC churn during animation frames.
    private val clipPath = Path()
    private val clipRect = RectF()
    private val albumArtPath = Path()
    private val cardRect = RectF()
    private val albumArtSrcRect = Rect()

    private val hintTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb((255 * 0.85f).toInt(), 255, 255, 255)
        textSize = LyricsSettings.dpToPx(context, 14f).toFloat()
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private var hintLayout: StaticLayout? = null
    private var previewMode: PreviewMode = PreviewMode.IDLE_ACCESS_NEEDED
    private var isIdleForced: Boolean = false
    private var hasNotificationAccess: Boolean = false
    private var hasSong: Boolean = false
    private var hasCover: Boolean = false
    private var isShowingSongBackground: Boolean = false

    private var albumArtBitmap: Bitmap? = null
    private var backgroundBitmap: Bitmap? = null
    private val currentIdleColors = IntArray(4)
    private val currentSongColors = IntArray(4)
    // Key derived from song metadata to avoid reprocessing the currently shown or in-flight track.
    private var currentSongKey: String? = null

    private var currentCornerRadius: Float = 48f
    private var radiusScale: Float = 1.0f
    private var bgSpeed: Float = 1.0f
    private var bgSaturationExponent: Float = AuroraRenderer.DEFAULT_CHROMA_EXPONENT
    private var staticBg: Boolean = false
    private var accumulatedTime: Float = 0f
    private var lastFrameTimeNs: Long = 0L

    private var isAttached: Boolean = false
    private var isResumed: Boolean = false
    private var isRunning: Boolean = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var rebuildJob: Job? = null
    private var saturationJob: Job? = null
    private var songJob: Job? = null
    private val lyricsManager by lazy { LyricsManager(context.applicationContext) }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return
            if (lastFrameTimeNs != 0L) {
                val dt = (frameTimeNanos - lastFrameTimeNs) / 1_000_000_000f
                val clampedDt = dt.coerceIn(0f, 0.1f)
                if (!staticBg) {
                    accumulatedTime += clampedDt * bgSpeed
                }
            }
            lastFrameTimeNs = frameTimeNanos
            invalidate()
            if (isRunning && !staticBg) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    init {
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#333333"))
            cornerRadius = cornerRadiusPx
        }
        clipToOutline = true
        isClickable = false
        isFocusable = false
    }

    fun initSettings(
        cornerRadius: Float,
        speed: Float,
        isStatic: Boolean,
        accent: Int,
        base: Int,
        mid: Int,
        highlight: Int,
        saturationExponent: Float = AuroraRenderer.DEFAULT_CHROMA_EXPONENT
    ) {
        currentCornerRadius = cornerRadius
        bgSpeed = speed
        staticBg = isStatic
        bgSaturationExponent = saturationExponent
        setIdleColors(accent, base, mid, highlight)
    }

    fun setCornerRadius(radius: Float) {
        if (currentCornerRadius == radius) return
        currentCornerRadius = radius
        invalidate()
    }

    fun setSpeed(speed: Float) {
        bgSpeed = speed
    }

    fun setSaturationExponent(exponent: Float) {
        if (bgSaturationExponent == exponent) return
        bgSaturationExponent = exponent
        val card = albumArtBitmap
        if (previewMode == PreviewMode.SONG && card != null && !card.isRecycled) {
            rebuildSongBackground(card)
        }
    }

    private fun rebuildSongBackground(card: Bitmap) {
        saturationJob?.cancel()
        saturationJob = scope.launch {
            var blurredResult: Bitmap? = null
            try {
                blurredResult = withContext(Dispatchers.Default) {
                    val palette = AuroraRenderer.extractPalette(card)
                    val preprocessed = AuroraRenderer.preprocessArt(card, palette.accent, 0.15f)
                    val scaledWork = Bitmap.createScaledBitmap(preprocessed, 128, 128, true)
                    preprocessed.recycle()

                    val pass1 = AuroraRenderer.blurBitmap(scaledWork, 20)
                    scaledWork.recycle()

                    val pass2 = AuroraRenderer.blurBitmap(pass1, 20)
                    pass1.recycle()
                    AuroraRenderer.boostChroma(pass2, bgSaturationExponent)
                    AuroraRenderer.capLightness(pass2)
                    pass2
                }
                val oldBg = backgroundBitmap
                backgroundBitmap = blurredResult
                oldBg?.recycle()
                invalidate()
            } catch (e: CancellationException) {
                blurredResult?.recycle()
            } catch (e: Exception) {
                blurredResult?.recycle()
            }
        }
    }

    fun setStaticBg(isStatic: Boolean) {
        if (staticBg == isStatic) return
        staticBg = isStatic
        updateRunningState()
        invalidate()
    }

    fun forceIdleMode() {
        if (isIdleForced) return
        isIdleForced = true
        currentSongKey = null
        updateMode()
        songJob?.cancel()
        val oldArt = albumArtBitmap
        albumArtBitmap = null
        oldArt?.recycle()
        isShowingSongBackground = false
        rebuildIdleBackground()
        invalidate()
    }

    fun setIdleColors(accent: Int, base: Int, mid: Int, highlight: Int) {
        val colorsChanged = currentIdleColors[0] != accent ||
            currentIdleColors[1] != base ||
            currentIdleColors[2] != mid ||
            currentIdleColors[3] != highlight

        currentIdleColors[0] = accent
        currentIdleColors[1] = base
        currentIdleColors[2] = mid
        currentIdleColors[3] = highlight

        if (!colorsChanged && backgroundBitmap != null && !backgroundBitmap!!.isRecycled && !isShowingSongBackground) {
            return
        }

        if (previewMode != PreviewMode.SONG) {
            isShowingSongBackground = false
            rebuildIdleBackground()
        }
    }

    private fun updateMode() {
        val newMode = decidePreviewMode(
            hasNotificationAccess = hasNotificationAccess,
            hasSong = hasSong,
            hasCover = hasCover,
            idleForced = isIdleForced
        )
        if (previewMode != newMode) {
            previewMode = newMode
            updateHintLayout()
        }
    }

    private fun updateHintLayout() {
        val text = when (previewMode) {
            PreviewMode.IDLE_PLAY_HINT -> "Play a song to preview it with its cover"
            PreviewMode.IDLE_ACCESS_NEEDED -> "Allow notification access to preview your song"
            PreviewMode.IDLE_FORCED, PreviewMode.SONG -> null
        }
        if (text.isNullOrEmpty() || width <= 0) {
            hintLayout = null
            return
        }
        val paddingPx = LyricsSettings.dpToPx(context, 48f)
        val textWidth = (width - paddingPx).coerceAtLeast(1)
        hintLayout = StaticLayout.Builder.obtain(text, 0, text.length, hintTextPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()
    }

    private fun rebuildIdleBackground() {
        val targetColors = currentIdleColors.copyOf()
        rebuildJob?.cancel()
        rebuildJob = scope.launch {
            var blurredResult: Bitmap? = null
            try {
                blurredResult = withContext(Dispatchers.Default) {
                    val idleMesh = AuroraRenderer.createIdleMesh(targetColors)
                    val preprocessed = AuroraRenderer.preprocessArt(idleMesh, Color.BLACK, 0f)
                    idleMesh.recycle()

                    // Downscaling to 128x128 reduces blur operations 64x while matching the visual
                    // softness of the engine radius 80 at 512x512 with radius 20 here.
                    val scaled = Bitmap.createScaledBitmap(preprocessed, 128, 128, true)
                    preprocessed.recycle()

                    val firstPass = AuroraRenderer.blurBitmap(scaled, 20)
                    scaled.recycle()

                    val secondPass = AuroraRenderer.blurBitmap(firstPass, 20)
                    firstPass.recycle()

                    secondPass
                }

                val oldBitmap = backgroundBitmap
                backgroundBitmap = blurredResult
                oldBitmap?.recycle()
                invalidate()
            } catch (e: CancellationException) {
                blurredResult?.recycle()
            } catch (e: Exception) {
                blurredResult?.recycle()
            }
        }
    }

    private suspend fun fetchBitmapSuspend(uriStr: String): Bitmap? =
        suspendCancellableCoroutine { continuation ->
            lyricsManager.fetchBitmap(uriStr) { bitmap ->
                if (continuation.isActive) {
                    continuation.resume(bitmap)
                } else {
                    bitmap?.recycle()
                }
            }
        }

    fun updateSong(hasNotificationAccess: Boolean, metadata: MediaMetadata?) {
        this.hasNotificationAccess = hasNotificationAccess
        val meta = metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val songAvailable = !title.isNullOrBlank()
        this.hasSong = songAvailable

        if (isIdleForced) {
            currentSongKey = null
            updateMode()
            return
        }

        if (!hasNotificationAccess || !songAvailable || meta == null) {
            currentSongKey = null
            this.hasCover = false
            songJob?.cancel()
            val oldArt = albumArtBitmap
            albumArtBitmap = null
            oldArt?.recycle()
            updateMode()
            if (isShowingSongBackground || backgroundBitmap == null || backgroundBitmap?.isRecycled == true) {
                isShowingSongBackground = false
                rebuildIdleBackground()
            }
            invalidate()
            return
        }

        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val album = meta.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val artUri = meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ART_URI)
        val candidateKey = buildSongPreviewKey(title, artist, album, artUri)
        if (isSameSongPreview(currentSongKey, candidateKey)) {
            return
        }
        currentSongKey = candidateKey

        val jobKey = candidateKey
        songJob?.cancel()
        songJob = scope.launch {
            var isFetchedBitmap = false
            var rawBitmap: Bitmap? = null
            try {
                rawBitmap = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)

                if (rawBitmap == null) {
                    val uriStr = meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                        ?: meta.getString(MediaMetadata.METADATA_KEY_ART_URI)
                    if (!uriStr.isNullOrBlank()) {
                        rawBitmap = fetchBitmapSuspend(uriStr)
                        isFetchedBitmap = (rawBitmap != null)
                    }
                }

                if (rawBitmap == null) {
                    // Avoid clearing a newer song key if this job was superseded.
                    if (currentSongKey == jobKey) {
                        currentSongKey = null
                    }
                    hasCover = false
                    val oldArt = albumArtBitmap
                    albumArtBitmap = null
                    oldArt?.recycle()
                    updateMode()
                    if (isShowingSongBackground || backgroundBitmap == null || backgroundBitmap?.isRecycled == true) {
                        isShowingSongBackground = false
                        rebuildIdleBackground()
                    }
                    invalidate()
                    return@launch
                }

                val processResult: CoverProcessResult = withContext(Dispatchers.Default) {
                    var cardCover: Bitmap? = null
                    var blurredBg: Bitmap? = null
                    try {
                        val w = rawBitmap.width
                        val h = rawBitmap.height
                        val side = minOf(w, h)
                        val x = (w - side) / 2
                        val y = (h - side) / 2
                        val cropped = Bitmap.createBitmap(rawBitmap, x, y, side, side)
                        // Copy the bitmap when cropping returns the source so cardCover is owned exclusively by this view.
                        val square = if (cropped === rawBitmap) {
                            cropped.copy(cropped.config, false)
                        } else {
                            cropped
                        }
                        val scaledCard = if (square.width > 512) {
                            val scaled = Bitmap.createScaledBitmap(square, 512, 512, true)
                            if (scaled !== square) square.recycle()
                            scaled
                        } else {
                            square
                        }
                        cardCover = scaledCard

                        val palette = AuroraRenderer.extractPalette(scaledCard)
                        val preprocessed = AuroraRenderer.preprocessArt(scaledCard, palette.accent, 0.15f)
                        val scaledWork = Bitmap.createScaledBitmap(preprocessed, 128, 128, true)
                        preprocessed.recycle()

                        val pass1 = AuroraRenderer.blurBitmap(scaledWork, 20)
                        scaledWork.recycle()

                        val pass2 = AuroraRenderer.blurBitmap(pass1, 20)
                        pass1.recycle()
                        AuroraRenderer.boostChroma(pass2, bgSaturationExponent)
                        AuroraRenderer.capLightness(pass2)

                        blurredBg = pass2
                        CoverProcessResult(scaledCard, blurredBg, palette)
                    } catch (e: Throwable) {
                        cardCover?.recycle()
                        blurredBg?.recycle()
                        throw e
                    } finally {
                        // Bitmaps from fetchBitmapSuspend are owned by this view, while MediaMetadata bitmaps belong to external processes.
                        if (isFetchedBitmap && rawBitmap !== cardCover && !rawBitmap.isRecycled) {
                            rawBitmap.recycle()
                        }
                    }
                }

                hasCover = true
                updateMode()
                if (previewMode == PreviewMode.SONG) {
                    val oldArt = albumArtBitmap
                    albumArtBitmap = processResult.cardCover
                    oldArt?.recycle()

                    val oldBg = backgroundBitmap
                    backgroundBitmap = processResult.blurredBg
                    oldBg?.recycle()

                    currentSongColors[0] = processResult.palette.accent
                    currentSongColors[1] = processResult.palette.base
                    currentSongColors[2] = processResult.palette.mid
                    currentSongColors[3] = processResult.palette.highlight
                    isShowingSongBackground = true
                    invalidate()
                } else {
                    if (currentSongKey == jobKey) {
                        currentSongKey = null
                    }
                    processResult.cardCover.recycle()
                    processResult.blurredBg.recycle()
                    val oldArt = albumArtBitmap
                    albumArtBitmap = null
                    oldArt?.recycle()
                    if (isShowingSongBackground || backgroundBitmap == null || backgroundBitmap?.isRecycled == true) {
                        isShowingSongBackground = false
                        rebuildIdleBackground()
                    }
                    invalidate()
                }
            } catch (e: CancellationException) {
                if (isFetchedBitmap && rawBitmap?.isRecycled == false) {
                    rawBitmap.recycle()
                }
                // Avoid clearing a newer song key if this job was superseded.
                if (currentSongKey == jobKey) {
                    currentSongKey = null
                }
                return@launch
            } catch (e: Exception) {
                if (isFetchedBitmap && rawBitmap?.isRecycled == false) {
                    rawBitmap.recycle()
                }
                // Avoid clearing a newer song key if this job was superseded.
                if (currentSongKey == jobKey) {
                    currentSongKey = null
                }
                hasCover = false
                val oldArt = albumArtBitmap
                albumArtBitmap = null
                oldArt?.recycle()
                updateMode()
                if (isShowingSongBackground || backgroundBitmap == null || backgroundBitmap?.isRecycled == true) {
                    isShowingSongBackground = false
                    rebuildIdleBackground()
                }
                invalidate()
                return@launch
            }
        }
    }

    fun onResume() {
        isResumed = true
        updateRunningState()
        if (backgroundBitmap == null || backgroundBitmap?.isRecycled == true) {
            if (previewMode != PreviewMode.SONG) {
                isShowingSongBackground = false
                rebuildIdleBackground()
            }
        }
    }

    fun onPause() {
        isResumed = false
        updateRunningState()
        songJob?.cancel()
        rebuildJob?.cancel()
        currentSongKey = null
    }

    fun release() {
        isResumed = false
        updateRunningState()
        rebuildJob?.cancel()
        songJob?.cancel()
        scope.cancel()
        currentSongKey = null

        val bg = backgroundBitmap
        backgroundBitmap = null
        bg?.recycle()

        val art = albumArtBitmap
        albumArtBitmap = null
        art?.recycle()

        hintLayout = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isAttached = true
        updateRunningState()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isAttached = false
        updateRunningState()
        release()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val displayWidthPx = resources.displayMetrics.widthPixels.toFloat()
        val engineCardWidth = MetadataArtLayout.WIDTH_FRACTION * displayWidthPx
        val previewCardWidth = h * 0.58f
        radiusScale = if (engineCardWidth > 0f) previewCardWidth / engineCardWidth else 1.0f
        updateHintLayout()
    }

    private fun updateRunningState() {
        val shouldRun = isAttached && isResumed && !staticBg
        if (shouldRun == isRunning) return
        isRunning = shouldRun
        if (shouldRun) {
            lastFrameTimeNs = 0L
            Choreographer.getInstance().postFrameCallback(frameCallback)
        } else {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        canvas.save()
        clipPath.reset()
        clipRect.set(0f, 0f, width.toFloat(), height.toFloat())
        clipPath.addRoundRect(clipRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
        canvas.clipPath(clipPath)

        val bg = backgroundBitmap
        val colors = if (previewMode == PreviewMode.SONG) currentSongColors else currentIdleColors
        if (bg != null && !bg.isRecycled) {
            AuroraRenderer.drawAurora(
                canvas,
                runtimeShader,
                shaderPaint,
                bg,
                null,
                0f,
                false,
                accumulatedTime,
                0f,
                0f,
                0f,
                0f,
                0f,
                colors,
                auroraPaints,
                staticBg
            )
        } else {
            val baseColor = colors.getOrElse(1) { 0xFF0A0B1A.toInt() }
            canvas.drawColor(AuroraRenderer.tintBlack(baseColor))
        }

        if (previewMode == PreviewMode.SONG) {
            val art = albumArtBitmap
            if (art != null && !art.isRecycled) {
                val cardSize = height * 0.58f
                val centerX = width / 2f
                val centerY = height / 2f
                val half = cardSize / 2f
                cardRect.set(centerX - half, centerY - half, centerX + half, centerY + half)
                val previewRadius = currentCornerRadius * radiusScale
                val clampedRadius = previewRadius.coerceIn(0f, half)
                albumArtPath.reset()
                albumArtPath.addRoundRect(cardRect, clampedRadius, clampedRadius, Path.Direction.CW)
                canvas.save()
                canvas.clipPath(albumArtPath)
                albumArtSrcRect.set(0, 0, art.width, art.height)
                canvas.drawBitmap(art, albumArtSrcRect, cardRect, albumArtPaint)
                canvas.restore()
            }
        } else {
            val layout = hintLayout
            if (layout != null) {
                canvas.save()
                val textX = (width - layout.width) / 2f
                val textY = (height - layout.height) / 2f
                canvas.translate(textX, textY)
                layout.draw(canvas)
                canvas.restore()
            }
        }

        canvas.restore()
    }

    private class CoverProcessResult(
        val cardCover: Bitmap,
        val blurredBg: Bitmap,
        val palette: AuroraPalette
    )
}
