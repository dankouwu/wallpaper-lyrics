package com.dnk.wallpaperlyrics

import android.graphics.*
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.palette.graphics.Palette
import androidx.core.graphics.ColorUtils
import kotlin.math.sin
import android.util.Log
import android.text.StaticLayout
import android.text.Layout
import android.text.TextPaint
import android.view.Choreographer

class LyricsWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return LyricsEngine()
    }

    inner class LyricsEngine : Engine(), Choreographer.FrameCallback {
        private val mediaObserver = MediaObserver(this@LyricsWallpaperService, ::onMetadataChanged, ::onPlaybackStateChanged)
        private val lyricsManager = LyricsManager(this@LyricsWallpaperService)
        private val choreographer = Choreographer.getInstance()
        
        private var visible = false
        private var startTime = System.currentTimeMillis()

        @Volatile
        private var currentLyrics: List<LyricLine>? = null
        private var activeLayouts: List<StaticLayout>? = null
        private var inactiveLayouts: List<StaticLayout>? = null
        private var lineOffsets: FloatArray? = null 
        
        private var currentTitle: String? = null
        private var currentArtist: String? = null
        private var songStartTime = 0L
        
        private val backgroundPaint = Paint().apply { color = Color.BLACK }
        private val auroraPaints = List(5) { 
            Paint().apply { 
                isAntiAlias = true 
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            } 
        }
        
        @Volatile
        private var targetColors = IntArray(5) { Color.BLACK }
        private var currentColors = IntArray(5) { Color.BLACK }

        private val activePaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 96f
            typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Typeface.create(Typeface.SANS_SERIF, 900, false)
            } else {
                Typeface.DEFAULT_BOLD
            }
            isAntiAlias = true
            setShadowLayer(10f, 0f, 0f, Color.argb(80, 0, 0, 0))
        }

        private val inactivePaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 96f
            typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Typeface.create(Typeface.SANS_SERIF, 600, false)
            } else {
                Typeface.DEFAULT
            }
            isAntiAlias = true
            alpha = (255 * 0.35f).toInt()
        }
        
        private val artistPaint = TextPaint(inactivePaint).apply {
            textSize = 62f
            alpha = (255 * 0.5f).toInt()
        }

        private var scrollY = 0f
        private var targetScrollY = 0f
        private var lastFrameTimeNanos = 0L

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            mediaObserver.start()
        }

        override fun onDestroy() {
            super.onDestroy()
            mediaObserver.stop()
            choreographer.removeFrameCallback(this)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                lastFrameTimeNanos = 0
                mediaObserver.refresh()
                choreographer.postFrameCallback(this)
            } else {
                choreographer.removeFrameCallback(this)
            }
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!visible) return
            val dt = if (lastFrameTimeNanos == 0L) 0.016f else (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
            lastFrameTimeNanos = frameTimeNanos
            drawFrame(dt)
            if (visible) choreographer.postFrameCallback(this)
        }

        private fun onMetadataChanged(metadata: MediaMetadata?) {
            try {
                val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

                if (title != null && (title != currentTitle || artist != currentArtist)) {
                    currentTitle = title
                    currentArtist = artist
                    songStartTime = System.currentTimeMillis()
                    currentLyrics = null 
                    activeLayouts = null
                    inactiveLayouts = null
                    lineOffsets = null
                    lyricsManager.fetchLyrics(title, artist ?: "") { lines ->
                        currentLyrics = lines
                    }
                }

                art?.let {
                    Palette.from(it).maximumColorCount(24).generate { palette ->
                        palette?.let { p ->
                            val swatches = p.swatches
                                .sortedByDescending { it.population }
                                .take(10)
                                .sortedBy { swatch -> ColorUtils.calculateLuminance(swatch.rgb) }

                            val newColors = IntArray(5)
                            if (swatches.isNotEmpty()) {
                                for (i in 0 until 5) {
                                    val idx = (i * (swatches.size - 1) / 4).coerceIn(0, swatches.size - 1)
                                    var color = swatches[idx].rgb
                                    val hsv = FloatArray(3)
                                    Color.colorToHSV(color, hsv)
                                    hsv[1] = (hsv[1] * 1.15f).coerceIn(0.1f, 1.0f)
                                    hsv[2] = (hsv[2] * 1.05f).coerceIn(0.1f, 1.0f)
                                    newColors[i] = Color.HSVToColor(hsv)
                                }
                            } else {
                                newColors.fill(Color.BLACK)
                            }
                            targetColors = newColors
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Wallpaper", "Metadata error", e)
            }
        }

        private fun onPlaybackStateChanged(state: PlaybackState?) {}

        private fun drawFrame(dt: Float) {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    holder.lockHardwareCanvas()
                } else {
                    holder.lockCanvas()
                }
                if (canvas != null) {
                    updateColors(dt)
                    drawAurora(canvas)
                    drawLyrics(canvas, dt)
                }
            } catch (e: Exception) {
                Log.e("Wallpaper", "Draw error", e)
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas) } catch (e: Exception) {}
                }
            }
        }

        private fun updateColors(dt: Float) {
            val targets = targetColors
            val lerpFactor = (dt * 1.2f).coerceAtMost(1.0f)
            for (i in currentColors.indices) {
                if (i < targets.size) {
                    currentColors[i] = interpolateColor(currentColors[i], targets[i], lerpFactor)
                }
            }
        }

        private fun interpolateColor(from: Int, to: Int, fraction: Float): Int {
            val a = (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * fraction).toInt()
            val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * fraction).toInt()
            val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * fraction).toInt()
            val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * fraction).toInt()
            return Color.argb(a, r, g, b)
        }

        private fun drawAurora(canvas: Canvas) {
            val width = canvas.width.toFloat()
            val height = canvas.height.toFloat()
            val time = (System.currentTimeMillis() - startTime) / 1000f
            canvas.drawColor(Color.BLACK)
            auroraPaints.forEachIndexed { index, paint ->
                val x = width / 2 + (width / 2.2f) * sin(time * (0.08f + index * 0.02f) + index * 1.4f)
                val y = height / 2 + (height / 3.5f) * sin(time * (0.06f + index * 0.01f) + index * 2.1f)
                val radius = width * (1.5f + 0.2f * sin(time * 0.05f + index))
                paint.shader = RadialGradient(x, y, radius, intArrayOf(currentColors[index], Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
                canvas.drawCircle(x, y, radius, paint)
            }
            canvas.drawColor(Color.argb(100, 0, 0, 0))
        }

        private fun drawLyrics(canvas: Canvas, dt: Float) {
            val width = canvas.width.toFloat()
            val height = canvas.height.toFloat()
            val position = mediaObserver.getCurrentPosition()
            val timeSinceSongStart = System.currentTimeMillis() - songStartTime
            val maxTextWidth = (width * 0.85f).toInt()
            val centerX = width / 2
            val centerY = height / 2

            val lines = currentLyrics
            if (lines != null && (activeLayouts == null || lineOffsets == null)) {
                activeLayouts = lines.map { line ->
                    StaticLayout.Builder.obtain(line.content, 0, line.content.length, activePaint, maxTextWidth)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setLineSpacing(0f, 1.15f)
                        .build()
                }
                inactiveLayouts = lines.map { line ->
                    StaticLayout.Builder.obtain(line.content, 0, line.content.length, inactivePaint, maxTextWidth)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setLineSpacing(0f, 1.15f)
                        .build()
                }
                
                var currentY = 0f
                val offsets = FloatArray(lines.size)
                for (i in lines.indices) {
                    val h = activeLayouts!![i].height
                    offsets[i] = currentY + h / 2f
                    currentY += h + 80f
                }
                lineOffsets = offsets
            }

            if (timeSinceSongStart < 5000 || lines.isNullOrEmpty()) {
                val title = currentTitle ?: "No Music Playing"
                drawSimpleText(canvas, title, centerX, centerY - 80, maxTextWidth, activePaint)
                currentArtist?.let {
                    drawSimpleText(canvas, it, centerX, centerY + 110, maxTextWidth, artistPaint)
                }
                return
            }

            val aLayouts = activeLayouts ?: return
            val iLayouts = inactiveLayouts ?: return
            val offsets = lineOffsets ?: return
            
            var currentIndex = lines.indexOfLast { it.startTime <= position }
            if (currentIndex == -1) currentIndex = 0
            
            targetScrollY = offsets[currentIndex]
            scrollY += (targetScrollY - scrollY) * (dt * 8.0f).coerceAtMost(1.0f)

            canvas.save()
            canvas.translate(0f, centerY - scrollY)
            val visibleRange = 7
            for (i in (currentIndex - visibleRange)..(currentIndex + visibleRange)) {
                if (i in aLayouts.indices) {
                    val isCurrent = i == currentIndex
                    val layout = if (isCurrent) aLayouts[i] else iLayouts[i]
                    canvas.save()
                    val lineCenterY = offsets[i]
                    if (!isCurrent) canvas.scale(0.95f, 0.95f, centerX, lineCenterY)
                    canvas.translate(centerX - maxTextWidth / 2f, lineCenterY - (layout.height / 2f))
                    layout.draw(canvas)
                    canvas.restore()
                }
            }
            canvas.restore()
        }

        private fun drawSimpleText(canvas: Canvas, text: String, x: Float, y: Float, maxWidth: Int, paint: TextPaint) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()
            canvas.save()
            canvas.translate(x - maxWidth / 2f, y - (layout.height / 2f))
            layout.draw(canvas)
            canvas.restore()
        }
    }
}
