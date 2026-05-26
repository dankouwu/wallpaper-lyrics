package com.example.wallpaperlyrics

import android.graphics.*
import android.graphics.RenderEffect
import android.graphics.Paint
import android.app.WallpaperColors
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
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
import kotlinx.coroutines.*
import kotlin.math.sqrt

data class AuroraPalette(
    val accent: Int,
    val base: Int,
    val mid: Int,
    val highlight: Int
)

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
            uniform shader unused;
            uniform float2 uRes;
            uniform float uTime;
            uniform float uWarpIntensity;
            uniform half4 uColor1;
            uniform half4 uColor2;
            uniform half4 uColor3;
            uniform half4 uColor4;

            float random(float2 st) {
                return fract(sin(dot(st.xy, float2(12.9898, 78.233))) * 43758.5453123);
            }

            float noise(float2 st) {
                float2 i = floor(st);
                float2 f = fract(st);
                float a = random(i);
                float b = random(i + float2(1.0, 0.0));
                float c = random(i + float2(0.0, 1.0));
                float d = random(i + float2(1.0, 1.0));
                float2 u = f * f * (3.0 - 2.0 * f);
                return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
            }

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / uRes;
                float t = uTime * 0.03;

                // 1. Base Anti-Symmetry to ensure rounded, non-linear movement
                float2 p = uv;
                p.x += sin(p.y * 3.0 + t) * 0.15;
                p.y += cos(p.x * 3.0 + t) * 0.15;

                // 2. Recursive Domain Warping (Recursive Noise)
                // This creates the 'liquid' feel without the sharp streaks
                float2 q = float2(
                    noise(p + t * 0.5),
                    noise(p + float2(2.4, 4.8) + t * 0.3)
                );
                
                float2 r = float2(
                    noise(p + q * 1.2 + float2(1.7, 9.2) + t * 0.2),
                    noise(p + q * 1.2 + float2(8.3, 2.8) + t * 0.1)
                );
                
                // Final noise value for color mixing
                float mixFactor = noise(p + r);

                // 3. Ultra-Smooth 4-Stop Interpolation with wider ranges
                // Wide smoothsteps eliminate 'pointy' segments and sharp edges
                float m1 = smoothstep(0.0, 0.6, mixFactor);
                float m2 = smoothstep(0.2, 0.8, mixFactor);
                float m3 = smoothstep(0.4, 1.0, mixFactor);
                
                half4 color = uColor1;
                color = mix(color, uColor2, m1);
                color = mix(color, uColor3, m2);
                color = mix(color, uColor4, m3);

                // 4. Post-Processing
                float luma = dot(color.rgb, float3(0.299, 0.587, 0.114));
                color.rgb = mix(float3(luma), color.rgb, 2.3); // Saturate
                color.rgb *= 0.6; // Darken for readability

                return color;
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
        private val engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        
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

        // Cached performance objects
        private val fadePaint = Paint()
        private var topFadeShader: LinearGradient? = null
        private var bottomFadeShader: LinearGradient? = null
        private var lastFadeWidth = 0f
        private var lastFadeHeight = 0f

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
        private var targetColors = intArrayOf(
            0xFF1DB954.toInt(), // Accent (Spotify Green)
            0xFF191414.toInt(), // Base (Dark)
            0xFF6B7C96.toInt(), // Mid
            0xFFEBF2FA.toInt()  // Highlight
        )
        private var currentColors = targetColors.copyOf()

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

        private var isScreenOff = false
        private var lastWakeTime = 0L

        private val screenStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        isScreenOff = true
                        // Snap to metadata view immediately so it's ready on wake
                        viewAlpha = 1.0f
                        targetViewAlpha = 1.0f
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        isScreenOff = false
                        lastWakeTime = System.currentTimeMillis()
                    }
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            mediaObserver.start()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            registerReceiver(screenStateReceiver, filter)
        }

        override fun onDestroy() {
            super.onDestroy()
            mediaObserver.stop()
            choreographer.removeFrameCallback(this)
            engineScope.cancel()
            unregisterReceiver(screenStateReceiver)
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
                    
                    // Force a transition to metadata view even if paused
                    targetViewAlpha = 1.0f 

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

        private fun updateAlbumArt(sourceBitmap: Bitmap) {
            albumArt = sourceBitmap
            engineScope.launch {
                val palette = withContext(Dispatchers.Default) {
                    extractPalette(sourceBitmap)
                }
                
                targetColors = intArrayOf(
                    palette.accent,
                    palette.base,
                    palette.mid,
                    palette.highlight
                )

                val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
                val isEnabled = prefs.getBoolean("dynamic_theming", false)

                if (isEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    notifyColorsChanged()
                }
            }
        }

        private fun extractPalette(sourceBitmap: Bitmap): AuroraPalette {
            // FIX 1: Turn ON filtering (true) for better bilinear interpolation to stop color fringing
            val scaledBitmap = Bitmap.createScaledBitmap(sourceBitmap, 128, 128, true)
            val p = Palette.from(scaledBitmap).generate()

            // 1. Vibrant Accent
            val vibrant = p.vibrantSwatch ?: p.dominantSwatch
            var accent = vibrant?.rgb ?: 0xFF1A1A1A.toInt()

            // 2. Deep Base
            val dark = p.darkMutedSwatch ?: p.darkVibrantSwatch
            var base = dark?.rgb ?: 0xFF0A0A0A.toInt()

            // 3. Atmospheric Mid
            val muted = p.mutedSwatch ?: p.dominantSwatch
            var mid = muted?.rgb ?: 0xFFF4F4F2.toInt()

            // 4. Highlight
            val light = p.lightVibrantSwatch ?: p.lightMutedSwatch
            var highlight = light?.rgb ?: 0xFFFFFFFF.toInt()

            // FIX 2: Check if the image is essentially monochromatic
            if (isMonochromatic(accent, base, mid, highlight)) {
                accent = 0xFF1A1A1A.toInt()    // Crisp text black
                base = 0xFF0A0A0A.toInt()      // Deep dark
                mid = 0xFFEAEAEA.toInt()       // Soft paper gray
                highlight = 0xFFFFFFFF.toInt() // Pure white flare
            } else {
                // Edge-case prevention: If accent and base are too similar, force visual separation
                if (calculateColorDistance(accent, base) < 60.0f) {
                    accent = shiftHue(base, 180f)
                }
            }

            return AuroraPalette(accent, base, mid, highlight)
        }

        private fun isMonochromatic(vararg colors: Int): Boolean {
            val hsv = FloatArray(3)
            var totalSaturation = 0f
            for (color in colors) {
                Color.colorToHSV(color, hsv)
                totalSaturation += hsv[1]
            }
            return (totalSaturation / colors.size) < 0.08f
        }

        private fun calculateColorDistance(c1: Int, c2: Int): Float {
            val rDiff = (Color.red(c1) - Color.red(c2)) / 255f
            val gDiff = (Color.green(c1) - Color.green(c2)) / 255f
            val bDiff = (Color.blue(c1) - Color.blue(c2)) / 255f
            return sqrt(rDiff * rDiff + gDiff * gDiff + bDiff * bDiff) * 255f
        }

        private fun shiftHue(color: Int, degrees: Float): Int {
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            hsv[0] = (hsv[0] + degrees) % 360f // Rotate around the color wheel
            hsv[1] = hsv[1].coerceAtLeast(0.7f) // Guarantee saturation
            return Color.HSVToColor(hsv)
        }

        override fun onComputeColors(): WallpaperColors? {
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            if (prefs.getBoolean("dynamic_theming", false) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                val primary = Color.valueOf(targetColors[0])
                val secondary = Color.valueOf(targetColors[1])
                val tertiary = Color.valueOf(targetColors[2])
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
            if (currentColors.size != targets.size) {
                currentColors = IntArray(targets.size) { Color.BLACK }
            }
            for (i in currentColors.indices) {
                currentColors[i] = interpolateColor(currentColors[i], targets[i], lerpFactor)
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
            
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            val speedMult = prefs.getFloat("bg_speed", 1.0f)
            val time = ((System.currentTimeMillis() - startTime) / 1000f) * speedMult
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && runtimeShader != null) {
                runtimeShader?.let { shader ->
                    shader.setFloatUniform("uRes", width, height)
                    shader.setFloatUniform("uTime", time)
                    shader.setFloatUniform("uWarpIntensity", 1.4f)
                    
                    // Set 4 dominant colors
                    currentColors.forEachIndexed { i, color ->
                        val r = Color.red(color) / 255f
                        val g = Color.green(color) / 255f
                        val b = Color.blue(color) / 255f
                        shader.setFloatUniform("uColor${i + 1}", r, g, b, 1f)
                    }
                    
                    shaderPaint.shader = shader

                    // Apply Kawase Blur effect (200f radius) via reflection
                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        try {
                            val base = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "unused")
                            val blur = android.graphics.RenderEffect.createBlurEffect(200f, 200f, base, android.graphics.Shader.TileMode.CLAMP)
                            val method = shaderPaint.javaClass.getMethod("setRenderEffect", android.graphics.RenderEffect::class.java)
                            method.invoke(shaderPaint, blur)
                        } catch (e: Exception) {
                            Log.e("Wallpaper", "Reflection blur error", e)
                        }
                    }
                    
                    canvas.drawRect(0f, 0f, width, height, shaderPaint)
                }
            } else {
                // Fallback for API < 33: Spicy Lyrics Mesh Gradient (Radial Blobs)
                canvas.drawColor(Color.BLACK)

                for (index in currentColors.indices) {
                    val paint = auroraPaints.getOrNull(index) ?: break
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
            canvas.save() // SAVE here to balance the restores below

            // Determine target state
            val isMetadataState = isScreenOff || (System.currentTimeMillis() - lastWakeTime < 2000) || !isPlaying || lines.isNullOrEmpty() || (System.currentTimeMillis() - songStartTime < 3000)
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

                val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
                val userOffset = prefs.getInt("sync_offset", 0).toLong()
                val leadTime = 50L // -50ms proactive lead time
                val adjustedPos = position - userOffset + leadTime

                var currentIndex = lines.indexOfLast { it.startTime <= adjustedPos }
                if (currentIndex == -1) currentIndex = 0

                val transitionDuration = 200f 

                // Synchronized Glide
                val currentOffset = offsets[currentIndex]
                val prevOffset = if (currentIndex > 0) offsets[currentIndex - 1] else currentOffset
                
                val entryProgress = ((adjustedPos - lines[currentIndex].startTime) / transitionDuration).coerceIn(0f, 1f)
                val easedGlide = 1f - (1f - entryProgress) * (1f - entryProgress)
                
                targetScrollY = prevOffset + (currentOffset - prevOffset) * easedGlide
                
                // Ease-out scroll
                scrollY += (targetScrollY - scrollY) * (dt * 12.0f).coerceAtMost(1.0f)

                // Use saveLayer only during Metadata vs Lyrics transitions
                val lyricsAlpha = ((1.0f - viewAlpha) * 255).toInt()
                val needsLayer = lyricsAlpha < 255

                if (needsLayer) {
                    val layerPaint = Paint().apply { alpha = lyricsAlpha }
                    canvas.saveLayer(null, layerPaint)
                }

                canvas.translate(0f, centerY - scrollY)
                val visibleRange = 8 
                
                for (i in (currentIndex - visibleRange)..(currentIndex + visibleRange)) {
                    if (i in layouts.indices) {
                        val line = lines[i]
                        val layout = layouts[i]
                        
                        val entryLinear = ((adjustedPos - line.startTime) / transitionDuration).coerceIn(0f, 1f)
                        val exitLinear = if (i < lines.size - 1) {
                            ((adjustedPos - lines[i+1].startTime) / transitionDuration).coerceIn(0f, 1f)
                        } else 0f
                        
                        val easedEntry = 1f - (1f - entryLinear) * (1f - entryLinear)
                        val easedExit = 1f - (1f - exitLinear) * (1f - exitLinear)
                        val easedFactor = (easedEntry - easedExit).coerceIn(0f, 1f)

                        canvas.save()
                        val lineCenterY = offsets[i]

                        val scale = 0.95f + (0.05f * easedFactor)
                        canvas.scale(scale, scale, centerX, lineCenterY)

                        val p = layout.paint
                        val targetAlpha = (0.35f + (0.55f * easedFactor)) * 255
                        p.alpha = targetAlpha.toInt()

                        // Performance optimization: Only update shadow if it actually changed significantly
                        // This prevents expensive text re-uploads to the GPU
                        val shadowAlpha = (80 * easedFactor).toInt()
                        if (shadowAlpha > 5) {
                            p.setShadowLayer(10f, 0f, 0f, Color.argb(shadowAlpha, 0, 0, 0))
                        } else {
                            p.clearShadowLayer()
                        }

                        canvas.translate(centerX - layout.width / 2f, lineCenterY - (layout.height / 2f))
                        layout.draw(canvas)

                        if (line.isInstrumental && position in line.startTime..line.endTime) {
                            val progress = (position - line.startTime).toFloat() / (line.endTime - line.startTime)
                            drawInstrumentalProgress(canvas, layout, progress, position, line)
                        }

                        canvas.restore()
                    }
                }
                if (needsLayer) canvas.restore()
            }



            // Draw Metadata View if visible
            if (viewAlpha > 0.0f) {
                val layerPaint = Paint().apply { alpha = (viewAlpha * 255).toInt() }
                canvas.saveLayer(null, layerPaint)
                drawMetadataWithAlbumArt(canvas, width, height)
                canvas.restore()
            }

            canvas.restore() // Restore to absolute screen coordinates

            // Draw fade-out gradients (Top and Bottom) at the very end to keep them static
            drawFadeGradients(canvas, width, height, (1.0f - viewAlpha))
        }

        private fun drawFadeGradients(canvas: Canvas, width: Float, height: Float, alpha: Float) {
            if (alpha <= 0f) return
            
            val fadeHeight = height * 0.25f
            
            // Re-create shaders only if dimensions changed
            if (width != lastFadeWidth || height != lastFadeHeight) {
                lastFadeWidth = width
                lastFadeHeight = height
                
                topFadeShader = LinearGradient(0f, 0f, 0f, fadeHeight, 
                    intArrayOf(Color.BLACK, Color.TRANSPARENT), 
                    null, Shader.TileMode.CLAMP)
                    
                bottomFadeShader = LinearGradient(0f, height - fadeHeight, 0f, height, 
                    intArrayOf(Color.TRANSPARENT, Color.BLACK), 
                    null, Shader.TileMode.CLAMP)
            }

            fadePaint.alpha = (alpha * 255).toInt()

            // Top fade
            fadePaint.shader = topFadeShader
            canvas.drawRect(0f, 0f, width, fadeHeight, fadePaint)

            // Bottom fade
            fadePaint.shader = bottomFadeShader
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
