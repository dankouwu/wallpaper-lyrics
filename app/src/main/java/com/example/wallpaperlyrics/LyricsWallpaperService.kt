package com.example.wallpaperlyrics

import android.graphics.*
import android.graphics.RenderEffect
import android.graphics.Paint
import android.app.WallpaperColors
import android.content.Context
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.palette.graphics.Palette
import androidx.core.graphics.ColorUtils
import kotlin.math.sin
import kotlin.math.cos
import androidx.core.content.res.ResourcesCompat
import android.util.Log
import android.text.StaticLayout
import android.text.Layout
import android.text.TextPaint
import android.view.Choreographer
import android.graphics.RuntimeShader

class LyricsWallpaperService : WallpaperService() {

    companion object {
        private const val BLUR_SHADER = """
            uniform shader content;
            uniform float2 uRes;
            
            vec4 main(vec2 fragCoord) {
                vec2 uv = fragCoord / uRes;
                float dist = 0.0;
                
                // Calculate blur strength based on Y position (top 22% and bottom 22%)
                if (uv.y < 0.22) {
                    dist = (0.22 - uv.y) / 0.22;
                } else if (uv.y > 0.78) {
                    dist = (uv.y - 0.78) / 0.22;
                }
                
                if (dist <= 0.0) return content.eval(fragCoord);
                
                // Gaussian-ish blur sampling
                float blurSize = dist * 20.0; 
                vec4 col = vec4(0.0);
                float total = 0.0;
                
                for (float x = -2.0; x <= 2.0; x++) {
                    for (float y = -2.0; y <= 2.0; y++) {
                        float weight = 1.0 - (length(vec2(x, y)) / 3.0);
                        if (weight > 0.0) {
                            col += content.eval(fragCoord + vec2(x, y) * blurSize * 0.5) * weight;
                            total += weight;
                        }
                    }
                }
                return col / total;
            }
        """

        private const val AURORA_SHADER = """
            uniform float2 uRes;
            uniform float uTime;
            uniform vec3 uColor1;
            uniform vec3 uColor2;
            uniform vec3 uColor3;
            uniform vec3 uColor4;
            uniform vec3 uColor5;

            float getWeight(vec2 uv, vec2 p) {
                float d = distance(uv, p);
                
                // Restored to a more natural, balanced softness
                float blurRegion = 0.22;
                float softness = 0.15;
                if (uv.y < blurRegion) {
                    softness += (blurRegion - uv.y) * 3.0; 
                } else if (uv.y > (1.0 - blurRegion)) {
                    softness += (uv.y - (1.0 - blurRegion)) * 3.0;
                }
                
                return 1.0 / (d * d + softness);
            }

            vec4 main(vec2 fragCoord) {
                vec2 uv = fragCoord / uRes;
                float t = uTime * 0.12;
                
                // 5 anchor points with independent orbital movement
                vec2 p1 = vec2(0.5 + 0.45 * sin(t * 0.7 + 0.5), 0.5 + 0.35 * cos(t * 0.5 + 1.2));
                vec2 p2 = vec2(0.5 + 0.35 * sin(t * 0.6 + 2.1), 0.5 + 0.45 * cos(t * 0.8 + 0.3));
                vec2 p3 = vec2(0.5 + 0.45 * sin(t * 0.4 + 3.8), 0.5 + 0.35 * cos(t * 0.7 + 2.5));
                vec2 p4 = vec2(0.5 + 0.35 * sin(t * 0.9 + 5.2), 0.5 + 0.45 * cos(t * 0.4 + 4.1));
                vec2 p5 = vec2(0.5 + 0.45 * sin(t * 0.5 + 1.1), 0.5 + 0.35 * cos(t * 1.1 + 0.7));

                float w1 = getWeight(uv, p1);
                float w2 = getWeight(uv, p2);
                float w3 = getWeight(uv, p3);
                float w4 = getWeight(uv, p4);
                float w5 = getWeight(uv, p5);

                float totalW = w1 + w2 + w3 + w4 + w5;
                vec3 col = (uColor1 * w1 + uColor2 * w2 + uColor3 * w3 + uColor4 * w4 + uColor5 * w5) / totalW;

                // Subtle dithering to eliminate banding
                float dither = fract(sin(dot(uv, vec2(12.9898, 78.233))) * 43758.5453);
                col += (dither - 0.5) * 0.018;

                return vec4(col, 1.0);
            }
        """
    }

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
        private var titleLayout: StaticLayout? = null
        private var artistLayout: StaticLayout? = null
        private var songStartTime = 0L
        private var lastWatchdogCheck = 0L

        private val backgroundPaint = Paint().apply { color = Color.BLACK }
        private val auroraPaints = List(5) { 
            Paint().apply { 
                isAntiAlias = true 
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            } 
        }

        private var runtimeShader: RuntimeShader? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(AURORA_SHADER)
        } else {
            null
        }

        private var blurShader: RuntimeShader? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(BLUR_SHADER)
        } else {
            null
        }

        private val shaderPaint = Paint()
        
        @Volatile
        private var targetColors = IntArray(5) { Color.BLACK }
        private var currentColors = IntArray(5) { Color.BLACK }

        private val activePaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 96f
            typeface = ResourcesCompat.getFont(this@LyricsWallpaperService, R.font.inter_black)
            isAntiAlias = true
            letterSpacing = -0.02f
            alpha = 230
            setShadowLayer(10f, 0f, 0f, Color.argb(80, 0, 0, 0))
        }

        private val inactivePaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 96f
            typeface = ResourcesCompat.getFont(this@LyricsWallpaperService, R.font.inter_bold)
            isAntiAlias = true
            alpha = (255 * 0.35f).toInt()
            // Subtle stroke to simulate "ExtraBold" (between Bold 700 and Black 900)
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = 1.5f
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
                val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()
                val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim()
                val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

                if (!title.isNullOrBlank() && (title != currentTitle || artist != currentArtist)) {
                    currentTitle = title
                    currentArtist = artist
                    songStartTime = System.currentTimeMillis()

                    // Reset everything for the new track
                    currentLyrics = null
                    activeLayouts = null
                    inactiveLayouts = null
                    lineOffsets = null
                    titleLayout = null
                    artistLayout = null

                    lyricsManager.fetchLyrics(title, artist ?: "") { lines ->
                        // Only update if this response is still for the current track
                        if (currentTitle == title) {
                            currentLyrics = lines
                        }
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
                                    hsv[1] = (hsv[1] * 1.35f).coerceIn(0.1f, 1.0f)
                                    hsv[2] = (hsv[2] * 1.25f).coerceIn(0.1f, 1.0f)
                                    newColors[i] = Color.HSVToColor(hsv)
                                }
                            } else {
                                newColors.fill(Color.BLACK)
                            }
                            targetColors = newColors

                            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
                            val isEnabled = prefs.getBoolean("dynamic_theming", false)

                            if (isEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                notifyColorsChanged()
                            }
                            }
                            }
                            }            } catch (e: Exception) {
                Log.e("Wallpaper", "Metadata error", e)
            }
        }

        override fun onComputeColors(): WallpaperColors? {
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            if (prefs.getBoolean("dynamic_theming", false) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                // Use the primary, secondary, and tertiary colors from the sampled palette
                val primary = Color.valueOf(targetColors[0])
                val secondary = Color.valueOf(targetColors[2])
                val tertiary = Color.valueOf(targetColors[4])
                return WallpaperColors(primary, secondary, tertiary)
            }
            return null
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
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && runtimeShader != null) {
                runtimeShader?.let { shader ->
                    shader.setFloatUniform("uRes", width, height)
                    shader.setFloatUniform("uTime", time)
                    
                    // Set color uniforms (converting Int to RGB floats 0-1)
                    currentColors.forEachIndexed { i, color ->
                        val r = Color.red(color) / 255f
                        val g = Color.green(color) / 255f
                        val b = Color.blue(color) / 255f
                        shader.setFloatUniform("uColor${i + 1}", r, g, b)
                    }
                    
                    shaderPaint.shader = shader
                    canvas.drawRect(0f, 0f, width, height, shaderPaint)
                }
            } else {
                // Fallback for API < 33: Spicy Lyrics Mesh Gradient (Radial Blobs)
                canvas.drawColor(Color.BLACK)

                auroraPaints.forEachIndexed { index, paint ->
                    val t = time * 0.12f
                    // Orbital movement matching the shader logic
                    val phase = index * 1.5f
                    val x = width * (0.5f + 0.4f * sin(t * (0.6f + index * 0.1f) + phase).toFloat())
                    val y = height * (0.5f + 0.4f * cos(t * (0.5f + index * 0.15f) + phase + 1f).toFloat())
                    
                    // Large radius for deep, diffused blending
                    val radius = width * 1.6f
                    
                    val color = currentColors[index]
                    val transparentColor = Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))
                    
                    // Dynamic softness for fallback path: restored to natural level
                    var innerOffset = 0.15f
                    val uvY = y / height
                    val blurRegion = 0.22f
                    if (uvY < blurRegion) {
                        innerOffset += (blurRegion - uvY) * 0.8f 
                    } else if (uvY > (1.0f - blurRegion)) {
                        innerOffset += (uvY - (1.0f - blurRegion)) * 0.8f
                    }

                    val gradient = RadialGradient(
                        x, y, radius,
                        intArrayOf(color, transparentColor),
                        floatArrayOf(innerOffset.coerceAtMost(0.85f), 0.96f), 
                        Shader.TileMode.CLAMP
                    )
                    
                    paint.shader = gradient
                    canvas.drawCircle(x, y, radius, paint)
                }
            }
            
            // Subtle darkening overlay for text legibility
            canvas.drawColor(Color.argb(80, 0, 0, 0))
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
                val aLayouts = mutableListOf<StaticLayout>()
                val iLayouts = mutableListOf<StaticLayout>()
                
                lines.forEach { line ->
                    val isInstrumental = line.content == "♪"
                    
                    // Use larger font for the instrumental symbol
                    if (isInstrumental) {
                        activePaint.textSize = 120f
                        inactivePaint.textSize = 120f
                    } else {
                        activePaint.textSize = 96f
                        inactivePaint.textSize = 96f
                    }

                    // 1. Calculate the active layout (the "widest" one)
                    val activeLayout = StaticLayout.Builder.obtain(line.content, 0, line.content.length, activePaint, maxTextWidth)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setLineSpacing(0f, 1.15f)
                        .build()
                    aLayouts.add(activeLayout)

                    // 2. Extract the line breaks from the active layout to "lock" the inactive one
                    val lockedContent = StringBuilder()
                    for (i in 0 until activeLayout.lineCount) {
                        val start = activeLayout.getLineStart(i)
                        val end = activeLayout.getLineEnd(i)
                        var lineText = line.content.substring(start, end)
                        lockedContent.append(lineText)
                        // If the line doesn't already end with a newline and isn't the last line, add one
                        if (i < activeLayout.lineCount - 1 && !lineText.endsWith("\n")) {
                            lockedContent.append("\n")
                        }
                    }

                    // 3. Create the inactive layout using the locked content
                    val inactiveLayout = StaticLayout.Builder.obtain(lockedContent, 0, lockedContent.length, inactivePaint, maxTextWidth + 50)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setLineSpacing(0f, 1.15f)
                        .build()
                    iLayouts.add(inactiveLayout)
                }
                
                // Reset sizes to default after processing
                activePaint.textSize = 96f
                inactivePaint.textSize = 96f
                
                activeLayouts = aLayouts
                inactiveLayouts = iLayouts
                
                var currentY = 0f
                val offsets = FloatArray(lines.size)
                for (i in lines.indices) {
                    val h = activeLayouts!![i].height
                    offsets[i] = currentY + h / 2f
                    currentY += h + 26f 
                }
                lineOffsets = offsets
            }

            // Watchdog: If we've been on the metadata screen for >4s and still have no lyrics, 
            // but have a valid track, try fetching again in case the request was lost.
            val now = System.currentTimeMillis()
            if (currentLyrics == null && !currentTitle.isNullOrBlank() && (now - songStartTime > 4000)) {
                if (now - lastWatchdogCheck > 5000) { // Don't spam, check every 5s
                    lastWatchdogCheck = now
                    currentTitle?.let { title ->
                        lyricsManager.fetchLyrics(title, currentArtist ?: "") { lines ->
                            if (currentTitle == title && lines != null) {
                                currentLyrics = lines
                            }
                        }
                    }
                }
            }

            if (timeSinceSongStart < 3000 || lines.isNullOrEmpty()) {
                if (titleLayout == null || artistLayout == null) {
                    val title = currentTitle ?: "No Music Playing"
                    titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, activePaint, maxTextWidth)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .build()
                    
                    val artist = currentArtist ?: ""
                    artistLayout = StaticLayout.Builder.obtain(artist, 0, artist.length, artistPaint, maxTextWidth)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .build()
                }

                val tLayout = titleLayout!!
                val aLayout = artistLayout!!
                val metadataGap = 10f
                val totalMetadataHeight = tLayout.height + metadataGap + aLayout.height
                
                // Position title and artist based on their actual heights
                val titleCenterY = centerY - (totalMetadataHeight / 2f) + (tLayout.height / 2f)
                val artistCenterY = centerY + (totalMetadataHeight / 2f) - (aLayout.height / 2f)

                drawSimpleLayout(canvas, tLayout, centerX, titleCenterY, maxTextWidth)
                drawSimpleLayout(canvas, aLayout, centerX, artistCenterY, maxTextWidth)
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
            val visibleRange = 12 // Increased range as lines are closer
            for (i in (currentIndex - visibleRange)..(currentIndex + visibleRange)) {
                if (i in aLayouts.indices) {
                    val isCurrent = i == currentIndex
                    val layout = if (isCurrent) aLayouts[i] else iLayouts[i]
                    canvas.save()
                    val lineCenterY = offsets[i]
                    if (!isCurrent) canvas.scale(0.95f, 0.95f, centerX, lineCenterY)
                    // Use layout.width to ensure both active and inactive layouts are perfectly centered
                    canvas.translate(centerX - layout.width / 2f, lineCenterY - (layout.height / 2f))
                    layout.draw(canvas)
                    canvas.restore()
                }
            }
            canvas.restore()

            // Draw fade-out gradients (Top and Bottom)
            drawFadeGradients(canvas, width, height)
        }

        private fun drawFadeGradients(canvas: Canvas, width: Float, height: Float) {
            val fadeHeight = height * 0.25f
            val fadePaint = Paint()

            // Top fade
            fadePaint.shader = LinearGradient(0f, 0f, 0f, fadeHeight, 
                intArrayOf(Color.BLACK, Color.TRANSPARENT), 
                null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, width, fadeHeight, fadePaint)

            // Bottom fade
            fadePaint.shader = LinearGradient(0f, height - fadeHeight, 0f, height, 
                intArrayOf(Color.TRANSPARENT, Color.BLACK), 
                null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, height - fadeHeight, width, height, fadePaint)
        }

        private fun drawSimpleLayout(canvas: Canvas, layout: StaticLayout, x: Float, y: Float, maxWidth: Int) {
            canvas.save()
            canvas.translate(x - maxWidth / 2f, y - (layout.height / 2f))
            layout.draw(canvas)
            canvas.restore()
        }
    }
}
