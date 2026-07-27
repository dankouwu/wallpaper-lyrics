package com.dnk.wallpaperlyrics

import android.graphics.*
import android.app.WallpaperColors
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.content.res.ResourcesCompat
import android.util.Log
import android.text.StaticLayout
import android.text.Layout
import android.text.TextPaint
import android.graphics.text.LineBreaker
import android.view.Choreographer
import android.graphics.RuntimeShader
import kotlinx.coroutines.*

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
            uniform shader u_texture;
            uniform shader u_texture_next;
            uniform float u_blend;
            uniform float2 u_resolution;
            uniform float u_time;
            uniform float u_time_next;
            uniform float u_intensity;
            uniform float u_saturation;
            uniform float u_dithering;
            uniform float u_scale;
            uniform float2 u_seed;
            uniform float2 u_seed_next;
            uniform float u_static_bg;

            float hash(float3 p) {
                float3 p3 = fract(p * 0.1031);
                p3 += dot(p3, p3.zyx + 31.32);
                return fract((p3.x + p3.y) * p3.z);
            }

            float3 mod289(float3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
            float2 mod289(float2 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
            float3 permute(float3 x) { return mod289(((x * 34.0) + 1.0) * x); }

            float snoise(float2 v) {
                const float4 C = float4(0.211324865405187, 0.366025403784439,
                                    -0.577350269189626, 0.024390243902439);
                float2 i  = floor(v + dot(v, C.yy));
                float2 x0 = v - i + dot(i, C.xx);
                float2 i1 = (x0.x > x0.y) ? float2(1.0, 0.0) : float2(0.0, 1.0);
                float4 x12 = x0.xyxy + C.xxzz;
                x12.xy -= i1;
                i = mod289(i);
                float3 p = permute(permute(i.y + float3(0.0, i1.y, 1.0)) + i.x + float3(0.0, i1.x, 1.0));
                float3 m = max(0.5 - float3(dot(x0,x0), dot(x12.xy,x12.xy), dot(x12.zw,x12.zw)), 0.0);
                m = m * m; m = m * m;
                float3 x = 2.0 * fract(p * C.www) - 1.0;
                float3 h = abs(x) - 0.5;
                float3 ox = floor(x + 0.5);
                float3 a0 = x - ox;
                m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);
                float3 g;
                g.x = a0.x * x0.x + h.x * x0.y;
                g.yz = a0.yz * x12.xz + h.yz * x12.yw;
                return 130.0 * dot(m, g);
            }

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / u_resolution;
                
                uv = (uv - 0.5) / u_scale + 0.5;
                uv = clamp(uv, 0.0, 1.0);
                
                float2 center = uv - 0.5;
                float centerWeight = 1.0 - smoothstep(0.0, 0.7, length(center));

                float2 warp = float2(0.0, 0.0);
                if (u_static_bg == 0.0) {
                    // Eval warp for current texture
                    float t = u_time * 0.05;
                    float2 uvSeeded = uv + u_seed;
                    float n1 = snoise(uvSeeded * 0.22 + float2(t, t * 0.7));
                    float n2 = snoise(uvSeeded * 0.22 + float2(-t * 0.8, t * 0.5) + float2(50.0, 50.0));
                    float n3 = snoise(uvSeeded * 0.25 + float2(t * 1.2, -t) + float2(100.0, 0.0));
                    float n4 = snoise(uvSeeded * 0.25 + float2(-t, t * 1.1) + float2(0.0, 100.0));
                    warp = float2(
                        n1 * 0.82 + n3 * 0.18,
                        n2 * 0.82 + n4 * 0.18
                    ) * centerWeight;
                }
                float2 warpedUV = clamp(uv + warp * u_intensity, 0.0, 1.0);
                half4 colorCurrent = u_texture.eval(warpedUV * u_resolution);
 
                float2 warpNext = float2(0.0, 0.0);
                if (u_static_bg == 0.0) {
                    // Eval warp for next texture
                    float tNext = u_time_next * 0.05;
                    float2 uvSeededNext = uv + u_seed_next;
                    float nn1 = snoise(uvSeededNext * 0.22 + float2(tNext, tNext * 0.7));
                    float nn2 = snoise(uvSeededNext * 0.22 + float2(-tNext * 0.8, tNext * 0.5) + float2(50.0, 50.0));
                    float nn3 = snoise(uvSeededNext * 0.25 + float2(tNext * 1.2, -tNext) + float2(100.0, 0.0));
                    float nn4 = snoise(uvSeededNext * 0.25 + float2(-tNext, tNext * 1.1) + float2(0.0, 100.0));
                    warpNext = float2(
                        nn1 * 0.82 + nn3 * 0.18,
                        nn2 * 0.82 + nn4 * 0.18
                    ) * centerWeight;
                }
                float2 warpedUVNext = clamp(uv + warpNext * u_intensity, 0.0, 1.0);
                half4 colorNext = u_texture_next.eval(warpedUVNext * u_resolution);

                half4 color = mix(colorCurrent, colorNext, u_blend);

                float vignette = 1.0 - dot(center, center) * 0.3;
                color.rgb *= vignette;

                // Saturation and Warmth
                float gray = dot(color.rgb, float3(0.299, 0.587, 0.114));
                color.rgb = mix(float3(gray), color.rgb, u_saturation);

                // Warmth adjustment: keep cool/neutral colors neutral, make warm colors warmer
                float warmth = max(0.0, color.r - color.b);
                float3 warmColor = color.rgb * float3(1.22, 1.05, 0.80);
                color.rgb = clamp(mix(color.rgb, warmColor, warmth), 0.0, 1.0);

                float noiseVal = hash(float3(fragCoord, floor((u_static_bg == 1.0 ? 0.0 : u_time) * 60.0)));
                color.rgb += (noiseVal - 0.5) * u_dithering;

                color.rgb *= 0.75; // Sightly less aggressive dimming for better vibrancy

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

        private var prefDynamicTheming = false
        private var prefBgSpeed = 1.0f
        private var prefSyncOffset = 0
        private var songSyncOffset = 0L
        private var prefAlbumCornerRadius = 48f
        private var prefMetadataOnlyMode = false
        private var prefStaticBg = false
        private var prefPersistentNotification = false

        private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                "dynamic_theming" -> prefDynamicTheming = prefs.getBoolean("dynamic_theming", false)
                "bg_speed" -> prefBgSpeed = prefs.getFloat("bg_speed", 1.0f)
                "sync_offset" -> prefSyncOffset = prefs.getInt("sync_offset", 0)
                "album_corner_radius" -> prefAlbumCornerRadius = prefs.getFloat("album_corner_radius", 48f)
                "preferred_media_player" -> mediaObserver.refresh()
                "metadata_only_mode" -> {
                    prefMetadataOnlyMode = prefs.getBoolean("metadata_only_mode", false)
                    if (prefMetadataOnlyMode) {
                        currentLyrics = null
                        lyricBitmaps?.forEach { it.recycle() }
                        lyricBitmaps = null
                        lyricLayouts = null
                        lineOffsets = null
                    } else {
                        val title = currentTitle
                        val artist = currentArtist
                        if (!title.isNullOrBlank()) {
                            engineScope.launch {
                                lyricsSearchExhausted = false
                                currentLyrics = null
                                lyricBitmaps?.forEach { it.recycle() }
                                lyricBitmaps = null
                                lyricLayouts = null
                                lineOffsets = null
                                lyricsManager.fetchLyrics(title, artist ?: "", currentDurationMs) { lines, definitive ->
                                    if (currentTitle == title) {
                                        currentLyrics = lines
                                        if (lines == null && definitive) lyricsSearchExhausted = true
                                        if (lines != null) showToast("Lyrics synced!")
                                        else if (definitive) showToast("Lyrics unavailable")
                                    }
                                }
                            }
                        }
                    }
                }
                "static_bg" -> prefStaticBg = prefs.getBoolean("static_bg", false)
                "persistent_notification" -> {
                    prefPersistentNotification = prefs.getBoolean("persistent_notification", false)
                    if (prefPersistentNotification) {
                        showOrUpdateNotification(currentTitle, currentArtist)
                    } else {
                        cancelNotification()
                    }
                }
                else -> {
                    if (key != null && key.startsWith("song_delay_")) {
                        updateSongSpecificDelay(prefs)
                    }
                }
            }
        }

        private fun loadPreferences(prefs: SharedPreferences) {
            prefDynamicTheming = prefs.getBoolean("dynamic_theming", false)
            prefBgSpeed = prefs.getFloat("bg_speed", 1.0f)
            prefSyncOffset = prefs.getInt("sync_offset", 0)
            prefAlbumCornerRadius = prefs.getFloat("album_corner_radius", 48f)
            prefMetadataOnlyMode = prefs.getBoolean("metadata_only_mode", false)
            prefStaticBg = prefs.getBoolean("static_bg", false)
            prefPersistentNotification = prefs.getBoolean("persistent_notification", false)
            updateSongSpecificDelay(prefs)
        }

        private fun updateSongSpecificDelay(prefs: SharedPreferences) {
            val title = currentTitle
            val artist = currentArtist
            songSyncOffset = if (!title.isNullOrBlank()) {
                prefs.getInt("song_delay_${title}_${artist}", 0).toLong()
            } else {
                0L
            }
        }

        @Volatile
        private var currentBgArt: Bitmap? = null
        @Volatile
        private var nextBgArt: Bitmap? = null
        private var blendProgress = 0f
        private var isTransitioning = false
        private var accumulatedTime = 0f
        private var nextAccumulatedTime = 0f
        private var currentSeedX = 0f
        private var currentSeedY = 0f
        private var nextSeedX = 0f
        private var nextSeedY = 0f
        private var currentAnimationSpeed = 1.0f

        @Volatile
        private var detectedBluetoothLatency = 0L

        private val audioDeviceCallback = object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
                updateBluetoothLatency()
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) {
                updateBluetoothLatency()
            }
        }
        
        private var visible = false
        private var startTime = System.currentTimeMillis()

        @Volatile
        private var currentLyrics: List<LyricLine>? = null
        private var lyricLayouts: List<StaticLayout>? = null
        private var lyricBitmaps: List<Bitmap>? = null
        private var lineOffsets: FloatArray? = null 
        
        private var currentTitle: String? = null
        private var currentArtist: String? = null
        private var currentDurationMs = 0L
        // Written from OkHttp threads, read in the draw loop — volatile like currentLyrics.
        @Volatile
        private var lyricsSearchExhausted = false
        private var albumArt: Bitmap? = null
        private var isPlaying = false
        private var titleLayout: StaticLayout? = null
        private var artistLayout: StaticLayout? = null
        private var metadataTitleLayout: StaticLayout? = null
        private var metadataArtistLayout: StaticLayout? = null
        private var songStartTime = 0L

        private var prevAlbumArt: Bitmap? = null
        private var prevTitleLayout: StaticLayout? = null
        private var prevArtistLayout: StaticLayout? = null
        private var metadataTransitionProgress = 1.0f

        private fun startMetadataTransition() {
            if (metadataTitleLayout != null) {
                prevAlbumArt = albumArt
                prevTitleLayout = metadataTitleLayout
                prevArtistLayout = metadataArtistLayout
                metadataTransitionProgress = 0.0f
            }
        }
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
            0xFFFF0055.toInt(), // Accent (Neon Crimson Red)
            0xFF0A0B1A.toInt(), // Base (Midnight Indigo Blue)
            0xFF7A22FF.toInt(), // Mid (Deep Electric Purple)
            0xFFD6C7FF.toInt()  // Highlight (Luminous Soft Lavender)
        )
        private var currentColors = targetColors.copyOf()

        private val activePaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 96f
            typeface = ResourcesCompat.getFont(this@LyricsWallpaperService, R.font.inter_black)
            isAntiAlias = true
            letterSpacing = -0.02f
            alpha = 230
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

        private var isScreenOff = !(this@LyricsWallpaperService.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isInteractive
        private var lastWakeTime = 0L

        private var lastKnownPlaybackPosition = 0L
        private var lastUpdateTime = 0L
        private var lastStateSyncTime = 0L

        private fun getExtrapolatedPosition(): Long {
            if (!isPlaying) {
                return lastKnownPlaybackPosition
            }
            val timeDiff = SystemClock.elapsedRealtime() - lastUpdateTime
            val pos = lastKnownPlaybackPosition + timeDiff
            return if (currentDurationMs > 0) {
                pos.coerceAtMost(currentDurationMs)
            } else {
                pos
            }
        }

        private fun syncPlaybackState() {
            val state = mediaObserver.getPlaybackState()
            isPlaying = state?.state == PlaybackState.STATE_PLAYING
            if (state != null) {
                lastKnownPlaybackPosition = state.position
                lastUpdateTime = if (state.lastPositionUpdateTime > 0L) state.lastPositionUpdateTime else SystemClock.elapsedRealtime()
            }
        }

        private fun schedulePlaybackStateSync() {
            engineScope.launch(Dispatchers.IO) {
                val state = mediaObserver.getPlaybackState()
                withContext(Dispatchers.Main) {
                    isPlaying = state?.state == PlaybackState.STATE_PLAYING
                    if (state != null) {
                        val prevPos = lastKnownPlaybackPosition
                        lastKnownPlaybackPosition = state.position
                        lastUpdateTime = if (state.lastPositionUpdateTime > 0L) state.lastPositionUpdateTime else SystemClock.elapsedRealtime()
                        
                        if (Math.abs(lastKnownPlaybackPosition - prevPos) > 2000L) {
                            snapScrollToPosition()
                        }
                    }
                }
            }
        }

        private fun snapScrollToPosition() {
            val lines = currentLyrics ?: return
            val offsets = lineOffsets ?: return
            val position = getExtrapolatedPosition()
            val userOffset = prefSyncOffset.toLong() + songSyncOffset
            val totalOffset = userOffset + detectedBluetoothLatency
            val leadTime = 50L
            val adjustedPos = position - totalOffset + leadTime

            var currentIndex = lines.indexOfLast { it.startTime <= adjustedPos }
            if (currentIndex == -1) currentIndex = 0

            val transitionDuration = 200f
            val currentOffset = offsets[currentIndex]
            val prevOffset = if (currentIndex > 0) offsets[currentIndex - 1] else currentOffset
            
            val entryProgress = ((adjustedPos - lines[currentIndex].startTime) / transitionDuration).coerceIn(0f, 1f)
            val easedGlide = 1f - (1f - entryProgress) * (1f - entryProgress)
            
            val target = prevOffset + (currentOffset - prevOffset) * easedGlide
            scrollY = target
            targetScrollY = target
        }

        private val screenStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        isScreenOff = true
                        viewAlpha = 0.0f
                        targetViewAlpha = 0.0f
                        snapScrollToPosition()
                        drawFrame(0f)
                        drawFrame(0f)
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        val wasOff = isScreenOff
                        isScreenOff = false
                        if (wasOff) {
                            lastWakeTime = System.currentTimeMillis()
                        }
                        snapScrollToPosition()
                        drawFrame(0f)
                        drawFrame(0f)
                    }
                }
            }
        }

        private val forceReloadLyricsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action
                if (action == "com.dnk.wallpaperlyrics.FORCE_RELOAD_LYRICS" || action == "com.dnk.wallpaperlyrics.RELOAD_LYRICS") {
                    val title = currentTitle
                    val artist = currentArtist
                    if (!title.isNullOrBlank()) {
                        engineScope.launch {
                            if (action == "com.dnk.wallpaperlyrics.FORCE_RELOAD_LYRICS") {
                                withContext(Dispatchers.IO) {
                                    lyricsManager.deleteCacheFor(title, artist ?: "")
                                }
                                showToast("Forcing re-fetch of lyrics...")
                            } else {
                                showToast("Reloading lyrics...")
                            }
                            lyricsSearchExhausted = false
                            currentLyrics = null
                            lyricBitmaps?.forEach { it.recycle() }
                            lyricBitmaps = null
                            lyricLayouts = null
                            lineOffsets = null
                            titleLayout = null
                            artistLayout = null
                            metadataTitleLayout = null
                            metadataArtistLayout = null
                            
                            lyricsManager.fetchLyrics(title, artist ?: "", currentDurationMs) { lines, definitive ->
                                if (currentTitle == title) {
                                    currentLyrics = lines
                                    if (lines == null && definitive) lyricsSearchExhausted = true
                                    if (lines != null) showToast("Lyrics re-fetched successfully!")
                                    else if (definitive) showToast("Lyrics unavailable")
                                }
                            }
                        }
                    }
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            loadPreferences(prefs)
            prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
            mediaObserver.start()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            registerReceiver(screenStateReceiver, filter)

            val lyricsFilter = IntentFilter().apply {
                addAction("com.dnk.wallpaperlyrics.FORCE_RELOAD_LYRICS")
                addAction("com.dnk.wallpaperlyrics.RELOAD_LYRICS")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(forceReloadLyricsReceiver, lyricsFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(forceReloadLyricsReceiver, lyricsFilter)
            }

            try {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
            } catch (e: Exception) {
                Log.e("Wallpaper", "Failed to register audio device callback", e)
            }
            updateBluetoothLatency()

            // Initialize background with the default idle mesh (Very Blurred - 2 passes)
            val idleMesh = AuroraRenderer.createIdleMesh(targetColors)
            val preprocessed = AuroraRenderer.preprocessArt(idleMesh, Color.BLACK, 0f)
            val firstPass = AuroraRenderer.blurBitmap(preprocessed, 20)
            currentBgArt = AuroraRenderer.blurBitmap(firstPass, 20)
            idleMesh.recycle()
            preprocessed.recycle()
            firstPass.recycle()

            if (isPreview) {
                resetToIdleState()
            } else {
                showOrUpdateNotification(currentTitle, currentArtist)
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            cancelNotification()
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
            mediaObserver.stop()
            choreographer.removeFrameCallback(this)
            engineScope.cancel()
            unregisterReceiver(screenStateReceiver)
            try {
                unregisterReceiver(forceReloadLyricsReceiver)
            } catch (e: Exception) {}

            try {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
            } catch (e: Exception) {}

            currentBgArt?.recycle()
            currentBgArt = null
            nextBgArt?.recycle()
            nextBgArt = null
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                lastFrameTimeNanos = 0
                val wasOff = isScreenOff
                isScreenOff = false
                if (wasOff) {
                    lastWakeTime = System.currentTimeMillis()
                }
                
                // Snap viewAlpha to the correct target immediately to prevent transitions/fading on app return
                val now = System.currentTimeMillis()
                val timeSinceWake = now - lastWakeTime
                var lines = currentLyrics
                val isMetadataState = (!isScreenOff && timeSinceWake in 1000..3000) || !isPlaying || lines.isNullOrEmpty() || (now - songStartTime < 3000)
                targetViewAlpha = if (isMetadataState) 1.0f else 0.0f
                viewAlpha = targetViewAlpha

                syncPlaybackState()
                snapScrollToPosition()
                drawFrame(0f)
                drawFrame(0f)
                mediaObserver.refresh()
                choreographer.postFrameCallback(this)
            } else {
                choreographer.removeFrameCallback(this)
            }
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            val wasOff = isScreenOff
            isScreenOff = false
            if (wasOff) {
                lastWakeTime = System.currentTimeMillis()
            }
            
            val now = System.currentTimeMillis()
            val timeSinceWake = now - lastWakeTime
            val lines = currentLyrics
            val isMetadataState = (!isScreenOff && timeSinceWake in 1000..3000) || !isPlaying || lines.isNullOrEmpty() || (now - songStartTime < 3000)
            targetViewAlpha = if (isMetadataState) 1.0f else 0.0f
            viewAlpha = targetViewAlpha

            syncPlaybackState()
            snapScrollToPosition()
            drawFrame(0f)
            drawFrame(0f)
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

                if (title.isNullOrBlank()) {
                    resetToIdleState()
                    return
                }

                val durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

                val isNewTrack = (title != currentTitle || artist != currentArtist)
                if (durationMs > 0) currentDurationMs = durationMs
                if (isNewTrack) {
                    startMetadataTransition()
                    currentTitle = title
                    currentArtist = artist
                    val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
                    updateSongSpecificDelay(prefs)
                    showOrUpdateNotification(title, artist)
                    currentDurationMs = durationMs
                    lyricsSearchExhausted = false
                    currentArtUri = null
                    hasArtForCurrentTrack = false
                    songStartTime = System.currentTimeMillis()
                    
                    // Reset extrapolation variables
                    lastKnownPlaybackPosition = 0L
                    lastUpdateTime = SystemClock.elapsedRealtime()

                    // Force a transition to metadata view even if paused
                    targetViewAlpha = 1.0f 

                    // Update latency for the new song
                    updateBluetoothLatency()
                    // Reset lyrics/layouts
                    currentLyrics = null
                    lyricBitmaps?.forEach { it.recycle() }
                    lyricBitmaps = null
                    lyricLayouts = null
                    lineOffsets = null
                    titleLayout = null
                    artistLayout = null
                    metadataTitleLayout = null
                    metadataArtistLayout = null

                    // Immediately transition background to fallback art if available so it is fluidified from start
                    if (art != null) {
                        updateAlbumArt(art)
                        hasArtForCurrentTrack = true
                    }
                    
                    if (!prefMetadataOnlyMode) {
                        showToast("Fetching lyrics...")
                        lyricsManager.fetchLyrics(title, artist ?: "", durationMs) { lines, definitive ->
                            if (currentTitle == title) {
                                currentLyrics = lines
                                if (lines == null && definitive) lyricsSearchExhausted = true
                                if (lines != null) showToast("Lyrics synced!")
                                else if (definitive) showToast("Lyrics unavailable")
                                // Transient failure stays quiet; the watchdog retries shortly.
                            }
                        }
                    } else {
                        currentLyrics = null
                        lyricsSearchExhausted = true
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

        private fun updateBluetoothLatency() {
            detectedBluetoothLatency = 0L
        }

        private fun updateAlbumArt(sourceBitmap: Bitmap) {
            albumArt = sourceBitmap
            engineScope.launch {
                val palette = withContext(Dispatchers.Default) {
                    AuroraRenderer.extractPalette(sourceBitmap)
                }

                // pre-process and blur the album cover for the dynamic background (Very Blurred - 2 passes)
                val blurred = withContext(Dispatchers.Default) {
                    val preprocessed = AuroraRenderer.preprocessArt(sourceBitmap, palette.accent, 0.15f)
                    val firstPass = AuroraRenderer.blurBitmap(preprocessed, 20)
                    val secondPass = AuroraRenderer.blurBitmap(firstPass, 20)
                    preprocessed.recycle()
                    firstPass.recycle()
                    secondPass
                }
                
                withContext(Dispatchers.Main) {
                    targetColors = intArrayOf(
                        palette.accent,
                        palette.base,
                        palette.mid,
                        palette.highlight
                    )
                    
                    triggerBgTransition(blurred)

                    if (prefDynamicTheming && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        notifyColorsChanged()
                    }
                }
            }
        }



        override fun onComputeColors(): WallpaperColors? {
            if (prefDynamicTheming && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                val primary = Color.valueOf(targetColors[0])
                val secondary = Color.valueOf(targetColors[1])
                val tertiary = Color.valueOf(targetColors[2])
                return WallpaperColors(primary, secondary, tertiary)
            }
            return null
        }

        private fun onPlaybackStateChanged(state: PlaybackState?) {
            isPlaying = state?.state == PlaybackState.STATE_PLAYING
            if (state != null) {
                lastKnownPlaybackPosition = state.position
                lastUpdateTime = if (state.lastPositionUpdateTime > 0L) state.lastPositionUpdateTime else SystemClock.elapsedRealtime()
            }
        }

        private fun createPreviewAlbumArt(): Bitmap {
            val size = 256
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            
            // Draw a beautiful dark round rect matching settings theme
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1C1C1E")
            }
            val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
            canvas.drawRoundRect(rect, 32f, 32f, bgPaint)
            
            // Draw a subtle border
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#3A3A3C")
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            canvas.drawRoundRect(rect, 32f, 32f, borderPaint)
            
            // Draw "WL" initials centered
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 96f
                typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            
            // Center text vertically
            val fontMetrics = textPaint.fontMetrics
            val yHeight = (size / 2f) - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText("WL", size / 2f, yHeight, textPaint)
            
            return bmp
        }

        private fun resetToIdleState() {
            if (isPreview) {
                currentTitle = "Wallpaper Lyrics"
                currentArtist = "dankouwu & riveerxd"
                currentDurationMs = 180000L
                lyricsSearchExhausted = false
                albumArt = createPreviewAlbumArt()
                currentLyrics = listOf(
                    LyricLine(0L, 4000L, "Welcome to Wallpaper Lyrics"),
                    LyricLine(4000L, 8000L, "A high-performance live wallpaper"),
                    LyricLine(8000L, 12000L, "Synced with your favorite music players"),
                    LyricLine(12000L, 16000L, "Featuring smooth animations and scroll"),
                    LyricLine(16000L, 20000L, "And dynamic warped aurora backgrounds"),
                    LyricLine(20000L, 24000L, "Supporting Spotify and Tidal out of the box"),
                    LyricLine(24000L, 28000L, "Enjoy your premium music experience")
                )
                hasArtForCurrentTrack = true
                
                metadataTitleLayout = null
                metadataArtistLayout = null
                titleLayout = null
                artistLayout = null
                lyricBitmaps?.forEach { it.recycle() }
                lyricBitmaps = null
                lyricLayouts = null
                lineOffsets = null

                targetColors = intArrayOf(
                    0xFF0A84FF.toInt(), // Vibrant Electric Blue
                    0xFF0A0B1A.toInt(), // Midnight Indigo Base
                    0xFFBF5AF2.toInt(), // Gorgeous Purple
                    0xFFFF375F.toInt()  // Soft Neon Pink Highlight
                )
                currentColors = targetColors.copyOf()
                isPlaying = true
            } else {
                currentTitle = null
                currentArtist = null
                currentDurationMs = 0L
                lyricsSearchExhausted = false
                albumArt = null
                currentLyrics = null
                currentArtUri = null
                hasArtForCurrentTrack = false

                metadataTitleLayout = null
                metadataArtistLayout = null
                titleLayout = null
                artistLayout = null
                lyricBitmaps?.forEach { it.recycle() }
                lyricBitmaps = null
                lyricLayouts = null
                lineOffsets = null

                targetColors = intArrayOf(
                    0xFFFF0055.toInt(), // Accent (Neon Crimson Red)
                    0xFF0A0B1A.toInt(), // Base (Midnight Indigo Blue)
                    0xFF7A22FF.toInt(), // Mid (Deep Electric Purple)
                    0xFFD6C7FF.toInt()  // Highlight (Luminous Soft Lavender)
                )
                currentColors = targetColors.copyOf()
            }
            
            engineScope.launch {
                val idleMesh = AuroraRenderer.createIdleMesh(targetColors)
                val preprocessed = AuroraRenderer.preprocessArt(idleMesh, Color.BLACK, 0f)
                val firstPass = AuroraRenderer.blurBitmap(preprocessed, 20)
                val blurred = AuroraRenderer.blurBitmap(firstPass, 20)
                idleMesh.recycle()
                preprocessed.recycle()
                firstPass.recycle()
                
                withContext(Dispatchers.Main) {
                    triggerBgTransition(blurred)
                    
                    if (prefDynamicTheming && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        notifyColorsChanged()
                    }
                }
            }
            showOrUpdateNotification(currentTitle, currentArtist)
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
                    if (isTransitioning) {
                        blendProgress += dt * 1.0f // 1 second crossfade duration
                        if (blendProgress >= 1.0f) {
                            blendProgress = 1.0f
                            isTransitioning = false
                            val old = currentBgArt
                            currentBgArt = nextBgArt
                            nextBgArt = null
                            old?.recycle()
                            accumulatedTime = nextAccumulatedTime
                            currentSeedX = nextSeedX
                            currentSeedY = nextSeedY
                        }
                    }

                    val nowMs = SystemClock.elapsedRealtime()
                    if (nowMs - lastStateSyncTime >= 1000L) {
                        lastStateSyncTime = nowMs
                        schedulePlaybackStateSync()
                    }

                    val speedMult = prefBgSpeed
                    val targetSpeed = if (isPlaying) speedMult else 0.0f
                    
                    // Smoothly interpolate current speed towards target speed
                    val lerpFactor = (dt * 3.0f).coerceAtMost(1.0f)
                    currentAnimationSpeed += (targetSpeed - currentAnimationSpeed) * lerpFactor
                    accumulatedTime += dt * currentAnimationSpeed
                    if (isTransitioning) {
                        nextAccumulatedTime += dt * currentAnimationSpeed
                    }

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
                currentColors[i] = AuroraRenderer.interpolateColor(currentColors[i], targets[i], lerpFactor)
            }
        }

        private fun drawAurora(canvas: Canvas) {
            AuroraRenderer.drawAurora(
                canvas,
                runtimeShader,
                shaderPaint,
                currentBgArt,
                nextBgArt,
                blendProgress,
                isTransitioning,
                accumulatedTime,
                nextAccumulatedTime,
                currentSeedX,
                currentSeedY,
                nextSeedX,
                nextSeedY,
                currentColors,
                auroraPaints,
                prefStaticBg
            )
        }

        private fun drawLyrics(canvas: Canvas, dt: Float) {
            val width = canvas.width.toFloat()
            val height = canvas.height.toFloat()
            
            // Auto-advance sample lyrics position for active live wallpaper preview
            val position = if (isPreview && currentTitle == "Wallpaper Lyrics") {
                (System.currentTimeMillis() % 28000L) // Loop 28 seconds preview sequence
            } else {
                getExtrapolatedPosition()
            }
            val maxTextWidth = (width * 0.85f).toInt()
            val centerX = width / 2
            val centerY = height / 2

            var lines = currentLyrics
            val now = System.currentTimeMillis()

            // If we don't have lyrics and are still searching/fetching, keep resetting songStartTime
            // so the 3-second metadata view grace period only starts counting AFTER lyrics are ready or exhausted!
            if (lines == null && !lyricsSearchExhausted && !currentTitle.isNullOrBlank()) {
                songStartTime = now
            }

            canvas.save() // SAVE here to balance the restores below

            // Determine target state
            val timeSinceWake = now - lastWakeTime
            val isMetadataState = prefMetadataOnlyMode || (!isScreenOff && timeSinceWake in 1000..3000) || !isPlaying || lines.isNullOrEmpty() || (now - songStartTime < 3000)
            targetViewAlpha = if (isMetadataState) 1.0f else 0.0f

            // State change transition (slowed by 1.5x to last longer)
            if (viewAlpha != targetViewAlpha) {
                val speed = if (targetViewAlpha > viewAlpha) 5.33f else 8.0f
                viewAlpha += (targetViewAlpha - viewAlpha) * (dt * speed).coerceAtMost(1.0f)
                if (Math.abs(viewAlpha - targetViewAlpha) < 0.005f) viewAlpha = targetViewAlpha
            }

            if (lines != null && (lyricLayouts == null || lyricBitmaps == null || lineOffsets == null)) {
                val (layouts, bitmaps, linesWithMeasuredWords) = LyricsRenderer.buildLyricLayouts(
                    lines,
                    activePaint,
                    maxTextWidth
                )

                currentLyrics = linesWithMeasuredWords
                lines = linesWithMeasuredWords

                lyricLayouts = layouts
                lyricBitmaps = bitmaps

                var currentY = 0f
                val offsets = FloatArray(lines.size)
                for (i in lines.indices) {
                    val h = lyricLayouts!![i].height
                    offsets[i] = currentY + h / 2f
                    currentY += h + 26f 
                }
                lineOffsets = offsets

                // Initialize scrollY to the active line position instantly to prevent rapid snap-scrolling on load!
                val initialPos = position - (prefSyncOffset.toLong() + songSyncOffset + detectedBluetoothLatency) + 50L
                var initialIndex = lines.indexOfLast { it.startTime <= initialPos }
                if (initialIndex == -1) initialIndex = 0
                scrollY = offsets[initialIndex]
            }

            // Watchdog logic... (only retry layer; disarmed once a miss is definitive)
            if (!prefMetadataOnlyMode && currentLyrics == null && !lyricsSearchExhausted && !currentTitle.isNullOrBlank() && (now - songStartTime > 4000)) {
                if (now - lastWatchdogCheck > 5000) { 
                    lastWatchdogCheck = now
                    currentTitle?.let { title ->
                        showToast("Retrying lyrics...")
                        lyricsManager.fetchLyrics(title, currentArtist ?: "", currentDurationMs) { l, definitive ->
                            if (currentTitle == title) {
                                currentLyrics = l
                                if (l == null && definitive) lyricsSearchExhausted = true
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

                val userOffset = prefSyncOffset.toLong() + songSyncOffset
                val totalOffset = userOffset + detectedBluetoothLatency
                val leadTime = 50L // -50ms proactive lead time
                val adjustedPos = position - totalOffset + leadTime

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
                val visibleRange = 7
                val bitmaps = lyricBitmaps ?: return
                
                val clipTop = scrollY - centerY - 300f 
                val clipBottom = scrollY - centerY + height + 300f
                
                // Use a shared Paint for drawing bitmaps
                val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                
                for (i in (currentIndex - visibleRange)..(currentIndex + visibleRange)) {
                    if (i in layouts.indices) {
                        val line = lines[i]
                        val layout = layouts[i]
                        val bmp = bitmaps[i]
                        val lineCenterY = offsets[i]
                        
                        // Culling: Skip lines that are outside the viewport
                        if (lineCenterY + bmp.height / 2f < clipTop || lineCenterY - bmp.height / 2f > clipBottom) continue
                        
                        val entryLinear = ((adjustedPos - line.startTime) / transitionDuration).coerceIn(0f, 1f)
                        val exitLinear = if (i < lines.size - 1) {
                            ((adjustedPos - lines[i+1].startTime) / transitionDuration).coerceIn(0f, 1f)
                        } else 0f
                        
                        val easedEntry = 1f - (1f - entryLinear) * (1f - entryLinear)
                        val easedExit = 1f - (1f - exitLinear) * (1f - exitLinear)
                        val easedFactor = (easedEntry - easedExit).coerceIn(0f, 1f)

                        canvas.save()

                        val scale = 0.95f + (0.05f * easedFactor)
                        canvas.scale(scale, scale, centerX, lineCenterY)

                        // 230 was the original max alpha of the active text
                        val targetAlpha = (0.35f + (0.65f * easedFactor)) * 230
                        val isActive = i == currentIndex
                        val isFadingOut = i < currentIndex && exitLinear < 1f
                        
                        if ((isActive || isFadingOut) && line.words != null && line.words.isNotEmpty()) {
                            if (isActive) {
                                // 1. Update progress of pre-created spans with artificial smoothing
                                for (word in line.words) {
                                    val span = word.spanRef as? WordGradientSpan ?: continue
                                    
                                    val startT = if (word.fullStartTime == 0L) word.startTime else word.fullStartTime
                                    val endT = if (word.fullEndTime == 0L) word.endTime else word.fullEndTime
                                    
                                    val fullWordLinearProgress = when {
                                        adjustedPos >= endT -> 1f
                                        adjustedPos <= startT -> 0f
                                        else -> {
                                            ((adjustedPos - startT).toFloat() / (endT - startT).toFloat()).coerceIn(0f, 1f)
                                        }
                                    }
                                    
                                    val fullWordEasedProgress = SyllableAnimator.getEasedProgress(fullWordLinearProgress, word.text)
                                    
                                    val startProp = word.partStartProp
                                    val endProp = if (word.partEndProp == 0f) 1f else word.partEndProp
                                    
                                    val targetProgress = if (endProp > startProp) {
                                        ((fullWordEasedProgress - startProp) / (endProp - startProp)).coerceIn(0f, 1f)
                                    } else {
                                        fullWordEasedProgress
                                    }
                                    
                                    span.progress = targetProgress
                                    
                                    span.activeAlpha = 230
                                    span.inactiveAlpha = 80
                                }
                            } else {
                                // 2. Previously active line fading out!
                                // All words are completed (progress = 1f), fade activeAlpha to 80 (gray)
                                val fadeProgress = exitLinear.coerceIn(0f, 1f)
                                val currentAlpha = (230 - (230 - 80) * fadeProgress).toInt()
                                
                                for (word in line.words) {
                                    val span = word.spanRef as? WordGradientSpan ?: continue
                                    span.progress = 1f
                                    span.activeAlpha = currentAlpha
                                    span.inactiveAlpha = 80
                                }
                            }
                            
                            // Configure default paint parameters (NO SHADOW!)
                            layout.paint.shader = null
                            layout.paint.color = Color.argb(80, 255, 255, 255)
                            layout.paint.alpha = 255

                            // Draw layout exactly once
                            canvas.translate(centerX - layout.width / 2f, lineCenterY - (layout.height / 2f))
                            layout.draw(canvas)
                        } else {
                            // Inactive line, draw pre-rendered bitmap
                            bmpPaint.alpha = targetAlpha.toInt()
                            canvas.translate(centerX - bmp.width / 2f, lineCenterY - (bmp.height / 2f))
                            canvas.drawBitmap(bmp, 0f, 0f, bmpPaint)
                        }

                        if (line.isInstrumental && position in line.startTime..line.endTime && i < lines.size - 1) {
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
                drawMetadataWithAlbumArt(canvas, width, height, dt)
                canvas.restore()
            }

            canvas.restore() // Restore to absolute screen coordinates

            // Draw fade-out gradients (Top and Bottom) at the very end to keep them static
            drawFadeGradients(canvas, width, height, (1.0f - viewAlpha))
        }

        private fun drawFadeGradients(canvas: Canvas, width: Float, height: Float, alpha: Float) {
            if (alpha <= 0f) return
            if (width != lastFadeWidth || height != lastFadeHeight) {
                lastFadeWidth = width
                lastFadeHeight = height
                val shaders = LyricsRenderer.createFadeShaders(height)
                topFadeShader = shaders.first
                bottomFadeShader = shaders.second
            }
            LyricsRenderer.drawFadeGradients(
                canvas, width, height, alpha,
                fadePaint, topFadeShader, bottomFadeShader
            )
        }

        private fun drawMetadataLayouts(
            canvas: Canvas,
            width: Float,
            height: Float,
            tLayout: StaticLayout,
            aLayout: StaticLayout,
            art: Bitmap?
        ) {
            val centerX = width / 2f
            val centerY = height / 2f
            
            // Apply scale effect
            val scale = 0.96f + (0.04f * viewAlpha)
            canvas.save()
            canvas.scale(scale, scale, centerX, centerY)

            val albumSize = if (art != null) width * 0.70f else 0f
            val albumTextGap = if (art != null) width * 0.04f else 0f
            val metadataGap = 5.0f
            
            val totalHeight = albumSize + albumTextGap + tLayout.height + metadataGap + aLayout.height
            var currentY = centerY - (totalHeight / 2f)
            
            // Draw Album Art
            art?.let { bmp ->
                val rect = RectF(centerX - albumSize / 2f, currentY, centerX + albumSize / 2f, currentY + albumSize)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
                
                val cornerRadius = prefAlbumCornerRadius
                
                canvas.save()
                val path = Path().apply { addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW) }
                canvas.clipPath(path)
                canvas.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), rect, paint)
                canvas.restore()
                currentY += albumSize + albumTextGap
            }
            
            // Draw Title
            drawSimpleLayout(canvas, tLayout, centerX, currentY + tLayout.height / 2f)
            currentY += tLayout.height + metadataGap
            
            // Draw Artist
            drawSimpleLayout(canvas, aLayout, centerX, currentY + aLayout.height / 2f)
            
            canvas.restore()
        }

        private fun cleanTitle(title: String): String {
            return LyricsRenderer.cleanTitle(title)
        }

        private fun drawMetadataWithAlbumArt(canvas: Canvas, width: Float, height: Float, dt: Float) {
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

                val rawTitle = currentTitle ?: "No Music Playing"
                val title = cleanTitle(rawTitle)
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

            // Update transition progress (speed: 2.0f for 500ms duration)
            if (metadataTransitionProgress < 1.0f) {
                metadataTransitionProgress = (metadataTransitionProgress + dt * 2.0f).coerceAtMost(1.0f)
            }

            if (metadataTransitionProgress < 1.0f && prevTitleLayout != null && prevArtistLayout != null) {
                // Draw previous metadata fading out (opacity = 1 - progress)
                val prevAlpha = (1.0f - metadataTransitionProgress)
                canvas.saveLayer(null, Paint().apply { alpha = (prevAlpha * 255).toInt() })
                drawMetadataLayouts(canvas, width, height, prevTitleLayout!!, prevArtistLayout!!, prevAlbumArt)
                canvas.restore()

                // Draw new metadata fading in (opacity = progress)
                canvas.saveLayer(null, Paint().apply { alpha = (metadataTransitionProgress * 255).toInt() })
                drawMetadataLayouts(canvas, width, height, metadataTitleLayout!!, metadataArtistLayout!!, albumArt)
                canvas.restore()
            } else {
                if (prevTitleLayout != null) {
                    prevAlbumArt = null
                    prevTitleLayout = null
                    prevArtistLayout = null
                }
                drawMetadataLayouts(canvas, width, height, metadataTitleLayout!!, metadataArtistLayout!!, albumArt)
            }
        }

        private fun drawSimpleLayout(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
            LyricsRenderer.drawSimpleLayout(canvas, layout, x, y)
        }

        private fun drawInstrumentalProgress(canvas: Canvas, layout: StaticLayout, progress: Float, position: Long, line: LyricLine) {
            LyricsRenderer.drawInstrumentalProgress(canvas, layout, progress, position, line)
        }

        private fun showToast(message: String) {
            mainHandler.post {
                android.widget.Toast.makeText(this@LyricsWallpaperService, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        private fun triggerBgTransition(newBlurred: Bitmap) {
            val isIdle = currentTitle.isNullOrBlank()
            val timeOffset = if (isIdle) 0f else 5f + (Math.random() * 10000f).toFloat()
            val seedX = if (isIdle) 0f else (Math.random() * 1000f).toFloat()
            val seedY = if (isIdle) 0f else (Math.random() * 1000f).toFloat()
 
            if (currentBgArt == null) {
                currentBgArt = newBlurred
                nextBgArt = null
                blendProgress = 0f
                isTransitioning = false
                accumulatedTime = timeOffset
                currentSeedX = seedX
                currentSeedY = seedY
            } else {
                if (isTransitioning) {
                    currentBgArt?.recycle()
                    currentBgArt = nextBgArt
                    currentSeedX = nextSeedX
                    currentSeedY = nextSeedY
                }
                nextBgArt = newBlurred
                blendProgress = 0f
                isTransitioning = true
                nextAccumulatedTime = timeOffset
                nextSeedX = seedX
                nextSeedY = seedY
            }
        }

        private fun showOrUpdateNotification(title: String?, artist: String?) {
            if (isPreview) return
            try {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        "wallpaper_lyrics_control",
                        "Wallpaper Lyrics",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Playback status and control for lyrics refresh"
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                val openAppIntent = Intent(this@LyricsWallpaperService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val openAppPendingIntent = PendingIntent.getActivity(
                    this@LyricsWallpaperService,
                    0,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val refreshIntent = Intent("com.dnk.wallpaperlyrics.FORCE_RELOAD_LYRICS").apply {
                    setPackage(packageName)
                }
                val refreshPendingIntent = PendingIntent.getBroadcast(
                    this@LyricsWallpaperService,
                    1,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val contentText = if (!title.isNullOrBlank()) {
                    if (!artist.isNullOrBlank()) "$title — $artist" else title
                } else {
                    "No active song playing"
                }

                val notification = NotificationCompat.Builder(this@LyricsWallpaperService, "wallpaper_lyrics_control")
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Wallpaper Lyrics")
                    .setContentText(contentText)
                    .setOngoing(true)
                    .setContentIntent(openAppPendingIntent)
                    .addAction(
                        android.R.drawable.ic_menu_rotate,
                        "Refresh Lyrics",
                        refreshPendingIntent
                    )
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()

                notificationManager.notify(1001, notification)
            } catch (e: Exception) {
                Log.e("Wallpaper", "Failed to update notification", e)
            }
        }

        private fun cancelNotification() {
            try {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(1001)
            } catch (e: Exception) {
                Log.e("Wallpaper", "Failed to cancel notification", e)
            }
        }

    }
}
