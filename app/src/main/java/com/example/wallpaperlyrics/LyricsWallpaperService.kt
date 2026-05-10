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
import android.graphics.text.LineBreaker

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
        private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        
        private var visible = false
        private var startTime = System.currentTimeMillis()

        @Volatile
        private var currentLyrics: List<LyricLine>? = null
        private var lyricLayouts: List<StaticLayout>? = null
        private var lineOffsets: FloatArray? = null 
        
        private var currentTitle: String? = null
        private var currentArtist: String? = null
        private var albumArt: Bitmap? = null
        private var isPlaying = false
        private var titleLayout: StaticLayout? = null
        private var artistLayout: StaticLayout? = null
        private var metadataTitleLayout: StaticLayout? = null
        private var metadataArtistLayout: StaticLayout? = null
        private var songStartTime = 0L
        private var lastWatchdogCheck = 0L

        private var viewAlpha = 1.0f
        private var targetViewAlpha = 1.0f

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
            typeface = ResourcesCompat.getFont(this@LyricsWallpaperService, R.font.inter_black)
            isAntiAlias = true
            letterSpacing = -0.02f // Match active tracking
            alpha = (255 * 0.35f).toInt()
            style = Paint.Style.FILL
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

        private var currentArtUri: String? = null
        private var hasArtForCurrentTrack = false

        private fun onMetadataChanged(metadata: MediaMetadata?) {
            try {
                val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()
                val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim()
                val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)?.trim()
                
                val albumArtUri = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI) ?: 
                                 metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)

                val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: 
                          metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

                Log.d("Wallpaper", "Metadata: $title - $artist ($album), Uri: $albumArtUri, ArtBitmap: ${art != null}")

                if (title.isNullOrBlank()) return

                val isNewTrack = (title != currentTitle || artist != currentArtist)
                if (isNewTrack) {
                    currentTitle = title
                    currentArtist = artist
                    currentArtUri = null 
                    hasArtForCurrentTrack = false
                    songStartTime = System.currentTimeMillis()

                    // Reset lyrics/layouts
                    currentLyrics = null
                    lyricLayouts = null
                    lineOffsets = null
                    titleLayout = null
                    artistLayout = null
                    metadataTitleLayout = null
                    metadataArtistLayout = null
                    
                    showToast("Fetching lyrics...")
                    lyricsManager.fetchLyrics(title, artist ?: "") { lines ->
                        if (currentTitle == title) {
                            currentLyrics = lines
                            showToast(if (lines != null) "Lyrics synced!" else "Lyrics unavailable")
                        }
                    }
                }

                // ART UPDATE LOGIC
                // 1. If we have a URI and it's new for this track (or session), fetch it
                if (!albumArtUri.isNullOrBlank() && albumArtUri != currentArtUri) {
                    currentArtUri = albumArtUri
                    lyricsManager.fetchBitmap(albumArtUri) { bitmap ->
                        mainHandler.post {
                            if (bitmap != null) {
                                updateAlbumArt(bitmap)
                                hasArtForCurrentTrack = true
                            } else if (art != null) {
                                // If high-res fetch failed, fallback to the bitmap provided in metadata
                                updateAlbumArt(art)
                                hasArtForCurrentTrack = true
                            }
                        }
                    }
                } 
                // 2. If we don't have a URI (or it hasn't changed), but we have a bitmap AND 
                // we haven't successfully set any art for this specific track yet, use it.
                else if (art != null && !hasArtForCurrentTrack) {
                    updateAlbumArt(art)
                    hasArtForCurrentTrack = true
                }
                
                // Special case: if it's a new track but we have NO new art yet, 
                // clear the old art after a short delay if it still hasn't arrived.
                if (isNewTrack && albumArtUri == null && art == null) {
                    mainHandler.postDelayed({
                        if (currentTitle == title && !hasArtForCurrentTrack) {
                             // Still no art for the new track, clear the old one
                             albumArt = null
                        }
                    }, 500)
                }

            } catch (e: Exception) {
                Log.e("Wallpaper", "Metadata error", e)
            }
        }

        private fun updateAlbumArt(bitmap: Bitmap) {
            Log.d("Wallpaper", "Album Art Resolution: ${bitmap.width}x${bitmap.height}")
            albumArt = bitmap
            Palette.from(bitmap).maximumColorCount(24).generate { palette ->
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

        private fun onPlaybackStateChanged(state: PlaybackState?) {
            isPlaying = state?.state == PlaybackState.STATE_PLAYING
        }

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
            val maxTextWidth = (width * 0.85f).toInt()
            val centerX = width / 2
            val centerY = height / 2

            val lines = currentLyrics

            // Determine target state
            val isMetadataState = !isPlaying || lines.isNullOrEmpty() || (System.currentTimeMillis() - songStartTime < 3000)
            targetViewAlpha = if (isMetadataState) 1.0f else 0.0f

            // Snappier interpolation (dt * 8.0f / 12.0f for a very responsive feel)
            if (viewAlpha != targetViewAlpha) {
                val speed = if (targetViewAlpha > viewAlpha) 8.0f else 12.0f // Higher values = snappier
                viewAlpha += (targetViewAlpha - viewAlpha) * (dt * speed).coerceAtMost(1.0f)
                if (Math.abs(viewAlpha - targetViewAlpha) < 0.005f) viewAlpha = targetViewAlpha
            }

            if (lines != null && (lyricLayouts == null || lineOffsets == null)) {
                val layouts = mutableListOf<StaticLayout>()

                lines.forEach { line ->
                    val isInstrumental = line.content == "♪"

                    // Template paint for this specific line
                    val linePaint = TextPaint(activePaint).apply {
                        if (isInstrumental) textSize = 120f
                    }

                    val layout = StaticLayout.Builder.obtain(line.content, 0, line.content.length, linePaint, maxTextWidth)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setLineSpacing(0f, 1.15f)
                        .build()
                    layouts.add(layout)
                }

                lyricLayouts = layouts

                var currentY = 0f
                val offsets = FloatArray(lines.size)
                for (i in lines.indices) {
                    val h = lyricLayouts!![i].height
                    offsets[i] = currentY + h / 2f
                    currentY += h + 26f 
                }
                lineOffsets = offsets
            }

            // Watchdog logic...
            val now = System.currentTimeMillis()
            if (currentLyrics == null && !currentTitle.isNullOrBlank() && (now - songStartTime > 4000)) {
                if (now - lastWatchdogCheck > 5000) { 
                    lastWatchdogCheck = now
                    currentTitle?.let { title ->
                        showToast("Retrying lyrics...")
                        lyricsManager.fetchLyrics(title, currentArtist ?: "") { l ->
                            if (currentTitle == title) {
                                currentLyrics = l
                                if (l != null) showToast("Lyrics synced!")
                            }
                        }
                    }
                }
            }

            // Draw Lyrics View if visible
            if (viewAlpha < 1.0f && !lines.isNullOrEmpty()) {
                val layouts = lyricLayouts ?: return
                val offsets = lineOffsets ?: return

                var currentIndex = lines.indexOfLast { it.startTime <= position }
                if (currentIndex == -1) currentIndex = 0

                targetScrollY = offsets[currentIndex]
                // Ease-out scroll
                scrollY += (targetScrollY - scrollY) * (dt * 12.0f).coerceAtMost(1.0f)

                // Use saveLayer for group alpha (Metadata vs Lyrics transition)
                val lyricsAlpha = ((1.0f - viewAlpha) * 255).toInt()
                val layerPaint = Paint().apply { alpha = lyricsAlpha }

                canvas.saveLayer(null, layerPaint)
                canvas.translate(0f, centerY - scrollY)
                val visibleRange = 12 
                val transitionDuration = 200f 

                for (i in (currentIndex - visibleRange)..(currentIndex + visibleRange)) {
                    if (i in layouts.indices) {
                        val line = lines[i]
                        val layout = layouts[i]

                        // Calculate linear factors for entry and exit events
                        val entryLinear = ((position - line.startTime) / transitionDuration).coerceIn(0f, 1f)
                        val exitLinear = if (i < lines.size - 1) {
                            ((position - lines[i+1].startTime) / transitionDuration).coerceIn(0f, 1f)
                        } else 0f

                        // Apply Quadratic Ease-Out to both (1 - (1-t)^2)
                        val easedEntry = 1f - (1f - entryLinear) * (1f - entryLinear)
                        val easedExit = 1f - (1f - exitLinear) * (1f - exitLinear)

                        // The final active factor is the combined transition
                        val easedFactor = (easedEntry - easedExit).coerceIn(0f, 1f)

                        canvas.save()

                        val lineCenterY = offsets[i]

                        // 1. Dynamic Scaling
                        val scale = 0.95f + (0.05f * easedFactor)
                        canvas.scale(scale, scale, centerX, lineCenterY)

                        // 2. Dynamic Opacity & Shadow (interpolating between 35% and 90%)
                        val p = layout.paint
                        val targetAlpha = (0.35f + (0.90f - 0.35f) * easedFactor) * 255
                        p.alpha = targetAlpha.toInt()

                        // Animate shadow intensity
                        val shadowAlpha = (80 * easedFactor).toInt()
                        p.setShadowLayer(10f, 0f, 0f, Color.argb(shadowAlpha, 0, 0, 0))

                        // 3. Render
                        canvas.translate(centerX - layout.width / 2f, lineCenterY - (layout.height / 2f))
                        layout.draw(canvas)

                        // Add instrumental progress animation if active
                        if (line.isInstrumental && position in line.startTime..line.endTime) {
                            val progress = (position - line.startTime).toFloat() / (line.endTime - line.startTime)
                            drawInstrumentalProgress(canvas, layout, progress, position, line)
                        }

                        canvas.restore()
                    }
                }
                canvas.restore()

                drawFadeGradients(canvas, width, height, (1.0f - viewAlpha))
            }


            // Draw Metadata View if visible
            if (viewAlpha > 0.0f) {
                val layerPaint = Paint().apply { alpha = (viewAlpha * 255).toInt() }
                canvas.saveLayer(null, layerPaint)
                drawMetadataWithAlbumArt(canvas, width, height)
                canvas.restore()
            }
        }

        private fun drawFadeGradients(canvas: Canvas, width: Float, height: Float, alpha: Float) {
            val fadeHeight = height * 0.25f
            val fadePaint = Paint()
            val colorAlpha = (alpha * 255).toInt()
            val black = Color.argb(colorAlpha, 0, 0, 0)
            val transparent = Color.argb(0, 0, 0, 0)

            // Top fade
            fadePaint.shader = LinearGradient(0f, 0f, 0f, fadeHeight, 
                intArrayOf(black, transparent), 
                null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, width, fadeHeight, fadePaint)

            // Bottom fade
            fadePaint.shader = LinearGradient(0f, height - fadeHeight, 0f, height, 
                intArrayOf(transparent, black), 
                null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, height - fadeHeight, width, height, fadePaint)
        }

        private fun drawMetadataWithAlbumArt(canvas: Canvas, width: Float, height: Float) {
            val centerX = width / 2f
            val centerY = height / 2f
            val metadataMaxTextWidth = (width * 0.75f).toInt() 
            
            if (metadataTitleLayout == null || metadataArtistLayout == null || metadataTitleLayout?.width != metadataMaxTextWidth) {
                // Create dedicated paints so we don't interfere with the main lyrics paints
                val titlePaint = TextPaint(activePaint).apply {
                    textSize = 90f 
                    typeface = ResourcesCompat.getFont(this@LyricsWallpaperService, R.font.inter_black) // Bolder
                    letterSpacing = -0.02f
                }
                
                val artistPaintForMetadata = TextPaint(artistPaint).apply {
                    textSize = 60f 
                    typeface = ResourcesCompat.getFont(this@LyricsWallpaperService, R.font.inter_semibold)
                    alpha = (255 * 0.6f).toInt()
                }

                val title = currentTitle ?: "No Music Playing"
                metadataTitleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, metadataMaxTextWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED)
                    .build()
                
                val artist = currentArtist ?: ""
                metadataArtistLayout = StaticLayout.Builder.obtain(artist, 0, artist.length, artistPaintForMetadata, metadataMaxTextWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED)
                    .build()
            }

            val tLayout = metadataTitleLayout!!
            val aLayout = metadataArtistLayout!!
            
            // Apply scale effect
            val scale = 0.96f + (0.04f * viewAlpha)
            canvas.save()
            canvas.scale(scale, scale, centerX, centerY)

            val albumSize = width * 0.70f
            val albumTextGap = width * 0.04f 
            val metadataGap = 5.0f
            
            val totalHeight = albumSize + albumTextGap + tLayout.height + metadataGap + aLayout.height
            var currentY = centerY - (totalHeight / 2f)
            
            // Draw Album Art
            albumArt?.let { art ->
                val rect = RectF(centerX - albumSize / 2f, currentY, centerX + albumSize / 2f, currentY + albumSize)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
                
                canvas.save()
                val path = Path().apply { addRoundRect(rect, 48f, 48f, Path.Direction.CW) }
                canvas.clipPath(path)
                canvas.drawBitmap(art, Rect(0, 0, art.width, art.height), rect, paint)
                canvas.restore()
            }
            
            currentY += albumSize + albumTextGap
            
            // Draw Title
            drawSimpleLayout(canvas, tLayout, centerX, currentY + tLayout.height / 2f)
            currentY += tLayout.height + metadataGap
            
            // Draw Artist
            drawSimpleLayout(canvas, aLayout, centerX, currentY + aLayout.height / 2f)
            
            canvas.restore()
        }

        private fun drawSimpleLayout(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
            canvas.save()
            canvas.translate(x - layout.width / 2f, y - (layout.height / 2f))
            layout.draw(canvas)
            canvas.restore()
        }

        private fun drawInstrumentalProgress(canvas: Canvas, layout: StaticLayout, progress: Float, position: Long, line: LyricLine) {
            val dotCount = 3
            val dotSpacing = 36f
            val dotRadius = 7f
            val totalWidth = (dotCount - 1) * dotSpacing
            val startX = layout.width / 2f - totalWidth / 2f
            val dotY = layout.height - 10f // Way tighter gap, pulling dots into the character's descent area
            
            // Snappy entry/exit fade (300ms)
            val entryAlpha = ((position - line.startTime) / 300f).coerceIn(0f, 1f)
            val exitAlpha = ((line.endTime - position) / 300f).coerceIn(0f, 1f)
            val groupAlpha = Math.min(entryAlpha, exitAlpha)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            
            for (i in 0 until dotCount) {
                // Each dot has a specific point in time where it's fully "active"
                val centerProgress = (i + 1).toFloat() / (dotCount + 1)
                
                // Calculate distance from this dot's peak focus (0.0 to 1.0)
                val dist = Math.abs(progress - centerProgress) * (dotCount + 1)
                val focus = (1.0f - dist).coerceIn(0.0f, 1.0f)
                
                // Smoothly interpolate alpha and scale
                val alpha = (100 + (155 * focus)).toInt()
                val scale = 1.0f + (0.4f * focus)
                
                paint.alpha = (alpha * groupAlpha).toInt()
                canvas.drawCircle(startX + i * dotSpacing, dotY, dotRadius * scale, paint)
            }
        }

        private fun showToast(message: String) {
            mainHandler.post {
                android.widget.Toast.makeText(this@LyricsWallpaperService, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
