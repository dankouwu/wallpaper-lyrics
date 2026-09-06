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
        private const val NOTIFICATION_CHANNEL_ID = "wallpaper_lyrics_control"
        private const val NOTIFICATION_ID = 1001

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
            uniform float u_tex_scale;
            uniform float2 u_tex_offset;

            float ign(float2 p) {
                return fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715))));
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

            // Bilinear hardware filtering is C0 continuous with slope breaks at texel boundaries.
            // Warping coordinates with a C2 continuous quintic curve within each texel aligns
            // derivatives across boundaries and eliminates Mach banding contour lines.
            float2 quinticTexelCoord(float2 p) {
                if (u_tex_scale <= 0.0) {
                    return p;
                }
                float2 t = (p - u_tex_offset) / u_tex_scale + 0.5;
                float2 i = floor(t);
                float2 f = t - i;
                f = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
                return (i + f - 0.5) * u_tex_scale + u_tex_offset;
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
                half4 colorCurrent = u_texture.eval(quinticTexelCoord(warpedUV * u_resolution));
 
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
                half4 colorNext = u_texture_next.eval(quinticTexelCoord(warpedUVNext * u_resolution));

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

                color.rgb *= 0.75; // Sightly less aggressive dimming for better vibrancy

                // u_time starts at a random offset up to 1000 and grows without bound, so an
                // unwrapped frame index pushes ign past float precision and the dither collapses
                // to a constant. 64 phases is ample and keeps the coordinate small forever.
                float frame = u_static_bg == 1.0 ? 0.0 : mod(floor(u_time * 60.0), 64.0);
                float2 frameOffset = frame * float2(1.6180339887, 2.6180339887);
                float2 dp = fragCoord + frameOffset;
                float dr = ign(dp) - ign(dp + float2(5.588238, 3.45367));
                float dg = ign(dp + float2(17.234100, 9.812300)) - ign(dp + float2(23.117700, 41.556900));
                float db = ign(dp + float2(31.905400, 55.221100)) - ign(dp + float2(47.663200, 13.774500));
                color.rgb += float3(dr, dg, db) * (u_dithering * 0.5);

                return color;
            }
        """
    }

    private val persistentNotificationLifecycle = PersistentNotificationLifecycle()
    private var notificationTitle: String? = null
    private var notificationArtist: String? = null

    private fun updatePersistentNotificationPreference(enabled: Boolean) {
        applyNotificationAction(persistentNotificationLifecycle.onPreferenceChanged(enabled))
    }

    private fun registerNotificationEngine(engine: Any, isPreview: Boolean) {
        applyNotificationAction(persistentNotificationLifecycle.onEngineCreated(engine, isPreview))
    }

    private fun unregisterNotificationEngine(engine: Any, isPreview: Boolean) {
        applyNotificationAction(persistentNotificationLifecycle.onEngineDestroyed(engine, isPreview))
    }

    private fun updatePersistentNotificationMetadata(
        engine: Any,
        isPreview: Boolean,
        title: String?,
        artist: String?
    ) {
        val action = persistentNotificationLifecycle.onMetadataChanged(engine, isPreview) ?: return
        notificationTitle = title
        notificationArtist = artist
        applyNotificationAction(action)
    }

    private fun applyNotificationAction(action: NotificationAction) {
        when (action) {
            NotificationAction.NONE -> Unit
            NotificationAction.SHOW_OR_UPDATE -> showOrUpdateNotification()
            NotificationAction.CANCEL -> cancelNotification()
        }
    }

    private fun showOrUpdateNotification() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Wallpaper Lyrics",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Playback status and control for lyrics refresh"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val openAppIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val refreshIntent = Intent("com.dnk.wallpaperlyrics.FORCE_RELOAD_LYRICS").apply {
                setPackage(packageName)
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                this,
                1,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val contentText = if (!notificationTitle.isNullOrBlank()) {
                if (!notificationArtist.isNullOrBlank()) {
                    "$notificationTitle · $notificationArtist"
                } else {
                    notificationTitle
                }
            } else {
                "No active song playing"
            }

            val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
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

            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e("Wallpaper", "Failed to update notification", e)
        }
    }

    private fun cancelNotification() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e("Wallpaper", "Failed to cancel notification", e)
        }
    }

    private data class PendingTrack(
        val title: String,
        val artist: String?,
        val art: Bitmap?,
        val albumArtUri: String?,
        val durationMs: Long
    )

    override fun onCreateEngine(): Engine {
        return LyricsEngine()
    }

    inner class LyricsEngine : Engine(), Choreographer.FrameCallback {
        private val mediaObserver = MediaObserver(this@LyricsWallpaperService, ::onMetadataChanged, ::onPlaybackStateChanged)
        private val lyricsManager = LyricsManager(this@LyricsWallpaperService)
        private val choreographer = Choreographer.getInstance()
        private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        private val engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        private val frameDiagnostics = if (BuildConfig.DEBUG) FrameDiagnostics() else null

        private var prefDynamicTheming = false
        private var prefBgSpeed = 1.0f
        private var prefSyncOffset = 0
        private var songSyncOffset = 0L
        private var prefAlbumCornerRadius = 48f
        private var prefMetadataOnlyMode = false
        private var prefStaticBg = false
        private var prefPersistentNotification = false
        private var prefIdleTitle = IdleScreenSettings.DEFAULT_IDLE_TITLE
        private var prefIdleAccent = IdleScreenSettings.DEFAULT_ACCENT
        private var prefIdleBase = IdleScreenSettings.DEFAULT_BASE
        private var prefIdleMid = IdleScreenSettings.DEFAULT_MID
        private var prefIdleHighlight = IdleScreenSettings.DEFAULT_HIGHLIGHT

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
                    updatePersistentNotificationPreference(prefPersistentNotification)
                }
                IdleScreenSettings.KEY_IDLE_TITLE -> {
                    prefIdleTitle = IdleScreenSettings.resolveIdleTitle(prefs.getString(IdleScreenSettings.KEY_IDLE_TITLE, null))
                    metadataTitleLayout = null
                    metadataArtistLayout = null
                }
                IdleScreenSettings.KEY_IDLE_ACCENT,
                IdleScreenSettings.KEY_IDLE_BASE,
                IdleScreenSettings.KEY_IDLE_MID,
                IdleScreenSettings.KEY_IDLE_HIGHLIGHT -> {
                    prefIdleAccent = prefs.getInt(IdleScreenSettings.KEY_IDLE_ACCENT, IdleScreenSettings.DEFAULT_ACCENT)
                    prefIdleBase = prefs.getInt(IdleScreenSettings.KEY_IDLE_BASE, IdleScreenSettings.DEFAULT_BASE)
                    prefIdleMid = prefs.getInt(IdleScreenSettings.KEY_IDLE_MID, IdleScreenSettings.DEFAULT_MID)
                    prefIdleHighlight = prefs.getInt(IdleScreenSettings.KEY_IDLE_HIGHLIGHT, IdleScreenSettings.DEFAULT_HIGHLIGHT)
                    targetColors = intArrayOf(
                        prefIdleAccent,
                        prefIdleBase,
                        prefIdleMid,
                        prefIdleHighlight
                    )
                    currentColors = targetColors.copyOf()
                    if (currentTitle.isNullOrBlank()) {
                        applyIdleBackground()
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
            prefIdleTitle = IdleScreenSettings.resolveIdleTitle(prefs.getString(IdleScreenSettings.KEY_IDLE_TITLE, null))
            prefIdleAccent = prefs.getInt(IdleScreenSettings.KEY_IDLE_ACCENT, IdleScreenSettings.DEFAULT_ACCENT)
            prefIdleBase = prefs.getInt(IdleScreenSettings.KEY_IDLE_BASE, IdleScreenSettings.DEFAULT_BASE)
            prefIdleMid = prefs.getInt(IdleScreenSettings.KEY_IDLE_MID, IdleScreenSettings.DEFAULT_MID)
            prefIdleHighlight = prefs.getInt(IdleScreenSettings.KEY_IDLE_HIGHLIGHT, IdleScreenSettings.DEFAULT_HIGHLIGHT)
            targetColors = intArrayOf(
                prefIdleAccent,
                prefIdleBase,
                prefIdleMid,
                prefIdleHighlight
            )
            currentColors = targetColors.copyOf()
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
        // Written from OkHttp threads, read in the draw loop, so volatile like currentLyrics.
        @Volatile
        private var lyricsSearchExhausted = false
        private var albumArt: Bitmap? = null
        private var albumArtAspect = 1.0f
        private var isPlaying = false
        private var titleLayout: StaticLayout? = null
        private var artistLayout: StaticLayout? = null
        private var metadataTitleLayout: StaticLayout? = null
        private var metadataArtistLayout: StaticLayout? = null
        private var songStartTime = 0L

        private var prevAlbumArt: Bitmap? = null
        private var prevAlbumArtAspect = 1.0f
        private var prevTitleLayout: StaticLayout? = null
        private var prevArtistLayout: StaticLayout? = null
        private var metadataTransitionProgress = 1.0f

        private var pendingTrack: PendingTrack? = null
        private var pendingCommitRunnable: Runnable? = null
        private var songTransitionStartMs = 0L

        private fun cancelPendingCommit() {
            pendingCommitRunnable?.let { mainHandler.removeCallbacks(it) }
            pendingCommitRunnable = null
            pendingTrack = null
            songTransitionStartMs = 0L
        }

        private fun isMetadataState(now: Long, timeSinceWake: Long, lines: List<LyricLine>?): Boolean {
            val inSongTransition = songTransitionStartMs > 0L && SongTransition.isActive(now - songTransitionStartMs)
            return prefMetadataOnlyMode ||
                   inSongTransition ||
                   (!isScreenOff && timeSinceWake in 1000..3000) ||
                   !isPlaying ||
                   lines.isNullOrEmpty() ||
                   (now - songStartTime < 3000)
        }

        private fun startMetadataTransition() {
            if (metadataTitleLayout != null) {
                prevAlbumArt = albumArt
                prevAlbumArtAspect = albumArtAspect
                prevTitleLayout = metadataTitleLayout
                prevArtistLayout = metadataArtistLayout
                metadataTransitionProgress = 0.0f
            }
        }
        private var lastWatchdogCheck = 0L
        private var lastToastTime = 0L
        private val TOAST_COOLDOWN_MS = 30_000L

        private var viewAlpha = 1.0f
        private var targetViewAlpha = 1.0f

        // Cached performance objects
        private val fadePaint = Paint()
        private var topFadeShader: LinearGradient? = null
        private var bottomFadeShader: LinearGradient? = null
        private var lastFadeWidth = 0f
        private var lastFadeHeight = 0f

        // Hoisted draw-loop paints: avoids 7+ heap allocations per frame at 60 FPS
        private val lyricsLayerPaint = Paint()
        private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val metadataLayerPaint = Paint()
        private val albumArtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        private val albumArtPath = Path()
        private val prevMetaLayerPaint = Paint()
        private val nextMetaLayerPaint = Paint()
        private val activeLineLayerPaint = Paint()
        private val activeLineLayerBounds = RectF()
        private val metadataArtistLayerPaint = Paint().apply { alpha = (255 * 0.6f).toInt() }
        private val metadataArtistBounds = RectF()

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
            IdleScreenSettings.DEFAULT_ACCENT,
            IdleScreenSettings.DEFAULT_BASE,
            IdleScreenSettings.DEFAULT_MID,
            IdleScreenSettings.DEFAULT_HIGHLIGHT
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

        // Line-change ramp: tracks when currentIndex last changed so word progress
        // ramps from 0 over 200ms of wall-clock time rather than jumping instantly.
        // Prevents 1-second position-sync updates from pre-completing words 1-2 on every
        // line transition (when the position jumps 100-300ms ahead in a single frame).
        private var prevCurrentIndex: Int = -1
        private var lineChangeElapsedMs: Long = 0L

        private var isScreenOff = !(this@LyricsWallpaperService.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isInteractive
        private var lastWakeTime = 0L

        private var lastKnownPlaybackPosition = 0L
        private var lastUpdateTime = 0L
        private var lastKnownPlaybackSpeed = 1.0f
        private var lastStateSyncTime = 0L
        private var debugDemoStartRealtime = 0L

        private var lastDiagLogTime = 0L

        private fun isDebugBuild() = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

        private fun isDebugDemoActive() = isDebugBuild() && debugDemoStartRealtime != 0L

        private fun getExtrapolatedPosition(): Long {
            if (isDebugDemoActive()) {
                return SystemClock.elapsedRealtime() - debugDemoStartRealtime
            }
            if (!isPlaying) {
                return lastKnownPlaybackPosition
            }

            // Primary: compute directly from the live PlaybackState (no stale cache)
            val directPos = mediaObserver.getCurrentPosition()

            // Fallback: use our cached extrapolation variables
            val now = SystemClock.elapsedRealtime()
            val timeDiff = now - lastUpdateTime
            val speed = if (lastKnownPlaybackSpeed > 0f) lastKnownPlaybackSpeed else 1.0f
            val cachedPos = lastKnownPlaybackPosition + (timeDiff * speed).toLong()

            // Use the direct position if available (non-zero means controller is active)
            val pos = if (directPos > 0L) directPos else cachedPos

            // Periodic diagnostic log (~every 5s) to trace drift
            if (now - lastDiagLogTime >= 5000L) {
                lastDiagLogTime = now
                Log.d("WP-Drift", "pos: direct=${directPos}ms, cached=${cachedPos}ms, " +
                    "delta=${directPos - cachedPos}ms, using=${pos}ms, " +
                    "base=${lastKnownPlaybackPosition}ms, timeDiff=${timeDiff}ms, " +
                    "speed=${lastKnownPlaybackSpeed}")
            }

            return if (currentDurationMs > 0) {
                pos.coerceAtMost(currentDurationMs)
            } else {
                pos
            }
        }

        private fun syncPlaybackState() {
            if (isDebugDemoActive()) return
            val state = mediaObserver.getPlaybackState()
            isPlaying = state?.state == PlaybackState.STATE_PLAYING
            if (state != null) {
                if (state.lastPositionUpdateTime > 0L) {
                    lastKnownPlaybackPosition = state.position
                    lastUpdateTime = state.lastPositionUpdateTime
                    lastKnownPlaybackSpeed = state.playbackSpeed
                } else if (isPlaying) {
                    if (state.position != lastKnownPlaybackPosition) {
                        lastKnownPlaybackPosition = state.position
                        lastUpdateTime = SystemClock.elapsedRealtime()
                        lastKnownPlaybackSpeed = state.playbackSpeed
                    }
                } else {
                    lastKnownPlaybackPosition = state.position
                }
            }
        }

        private var lastResyncLogTime = 0L

        private fun schedulePlaybackStateSync() {
            if (isDebugDemoActive()) return
            engineScope.launch(Dispatchers.IO) {
                val state = mediaObserver.getPlaybackState()
                withContext(Dispatchers.Main) {
                    if (state == null) return@withContext
                    isPlaying = state.state == PlaybackState.STATE_PLAYING

                    val prevPos = lastKnownPlaybackPosition
                    val now = SystemClock.elapsedRealtime()

                    if (state.lastPositionUpdateTime > 0L) {
                        lastKnownPlaybackPosition = state.position
                        lastUpdateTime = state.lastPositionUpdateTime
                        lastKnownPlaybackSpeed = state.playbackSpeed
                    } else if (isPlaying) {
                        if (state.position != lastKnownPlaybackPosition) {
                            lastKnownPlaybackPosition = state.position
                            lastUpdateTime = now
                            lastKnownPlaybackSpeed = state.playbackSpeed
                        }
                    } else {
                        lastKnownPlaybackPosition = state.position
                    }

                    // Diagnostic log (~every 5s) to trace resync behavior
                    if (now - lastResyncLogTime >= 5000L) {
                        lastResyncLogTime = now
                        Log.d("WP-Drift", "resync: state.pos=${state.position}ms, " +
                            "state.lastPosUpdateTime=${state.lastPositionUpdateTime}, " +
                            "state.speed=${state.playbackSpeed}, " +
                            "state.state=${state.state}, " +
                            "applied.pos=${lastKnownPlaybackPosition}ms, " +
                            "applied.updateTime=${lastUpdateTime}")
                    }

                    if (Math.abs(lastKnownPlaybackPosition - prevPos) > 2000L) {
                        snapScrollToPosition()
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

            val bsIdx = lines.binarySearch { it.startTime.compareTo(adjustedPos) }
            var currentIndex = if (bsIdx >= 0) bsIdx else (-bsIdx - 2).coerceAtLeast(0)

            val currentOffset = offsets[currentIndex]
            val prevOffset = if (currentIndex > 0) offsets[currentIndex - 1] else currentOffset
            val glideDistance = Math.abs(currentOffset - prevOffset)
            val glideDuration = SyllableAnimator.glideDurationMs(glideDistance)
            
            val entryProgress = ((adjustedPos - lines[currentIndex].startTime) / glideDuration).coerceIn(0f, 1f)
            val easedGlide = SyllableAnimator.easeOutGlide(entryProgress)
            
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

        private val debugDemoReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (!isDebugBuild()) return
                when (intent?.action) {
                    "com.dnk.wallpaperlyrics.DEBUG_START_EFFORTLESS" -> startDebugEffortlessDemo()
                    "com.dnk.wallpaperlyrics.DEBUG_PLAY_EFFORTLESS" -> playDebugEffortlessDemo()
                    "com.dnk.wallpaperlyrics.DEBUG_END_EFFORTLESS" -> endDebugEffortlessDemo()
                    "com.dnk.wallpaperlyrics.DEBUG_METADATA_BEAT" -> {
                        if (isDebugDemoActive()) lastWakeTime = System.currentTimeMillis() - 1000L
                    }
                }
            }
        }

        private fun startDebugEffortlessDemo() {
            if (!isDebugBuild() || isPreview) return

            val art = try {
                assets.open("effortless.jpg").use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                Log.e("WallpaperDemo", "Unable to load supplied Effortless artwork", e)
                return
            } ?: return

            // Clear any prior demo track so repeated captures always begin with a real
            // metadata transition and a fresh lyrics fetch.
            currentTitle = null
            currentArtist = null
            currentLyrics = null
            albumArt = null
            albumArtAspect = 1.0f
            hasArtForCurrentTrack = false
            currentArtUri = null
            lyricsSearchExhausted = false
            lyricBitmaps?.forEach { it.recycle() }
            lyricBitmaps = null
            lyricLayouts = null
            lineOffsets = null
            metadataTitleLayout = null
            metadataArtistLayout = null

            debugDemoStartRealtime = SystemClock.elapsedRealtime()
            onMetadataChanged(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "Effortless")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "Josh Woodward")
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, "Not Quite Connected")
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, 229_000L)
                    .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
                    .build()
            )
            // Hold on the album-cover metadata view until the explicit PLAY action.
            isPlaying = false
            songStartTime = System.currentTimeMillis()
            lastWakeTime = 0L

            mainHandler.postDelayed({
                if (!isDebugDemoActive()) return@postDelayed
                val providerLyrics = currentLyrics
                if (providerLyrics?.any { line -> line.words?.any { !it.isEstimated } == true } == true) {
                    Log.i("WallpaperDemo", "Effortless provider result: word-level sync")
                    return@postDelayed
                }

                val suppliedLines = try {
                    assets.open("effortless.lrc").bufferedReader().use { LyricsManager.parseLrcText(it.readText(), 229_000L) }
                } catch (e: Exception) {
                    Log.e("WallpaperDemo", "Unable to parse supplied Effortless LRC", e)
                    null
                }
                val enhancedLines = try {
                    assets.open("effortless-opening.elrc").bufferedReader().use { LyricsManager.parseLrcText(it.readText(), 229_000L) }
                } catch (e: Exception) {
                    Log.e("WallpaperDemo", "Unable to parse manual Effortless timing", e)
                    null
                }
                if (suppliedLines == null || enhancedLines == null) return@postDelayed

                lyricBitmaps?.forEach { it.recycle() }
                lyricBitmaps = null
                lyricLayouts = null
                lineOffsets = null
                currentLyrics = enhancedLines
                lyricsSearchExhausted = true
                debugDemoStartRealtime = SystemClock.elapsedRealtime() - 16_570L
                Log.i("WallpaperDemo", "Effortless provider result: line-level or unavailable; using manual word-level timing for supplied opening passage")
            }, 12_000L)
        }

        private fun playDebugEffortlessDemo() {
            if (!isDebugDemoActive() || currentTitle != "Effortless") return
            val nowRealtime = SystemClock.elapsedRealtime()
            // The supplied opening marker is at 14.57s and the first lyric at 16.57s.
            debugDemoStartRealtime = nowRealtime - 14_570L
            songStartTime = System.currentTimeMillis() - 4_000L
            lastWakeTime = 0L
            isPlaying = true
        }

        private fun endDebugEffortlessDemo() {
            if (!isDebugDemoActive() || currentTitle != "Effortless") return
            isPlaying = false
            lastWakeTime = 0L
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            loadPreferences(prefs)
            prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
            updatePersistentNotificationPreference(prefPersistentNotification)
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
            if (isDebugBuild()) {
                val debugFilter = IntentFilter().apply {
                    addAction("com.dnk.wallpaperlyrics.DEBUG_START_EFFORTLESS")
                    addAction("com.dnk.wallpaperlyrics.DEBUG_PLAY_EFFORTLESS")
                    addAction("com.dnk.wallpaperlyrics.DEBUG_END_EFFORTLESS")
                    addAction("com.dnk.wallpaperlyrics.DEBUG_METADATA_BEAT")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(debugDemoReceiver, debugFilter, Context.RECEIVER_EXPORTED)
                } else {
                    registerReceiver(debugDemoReceiver, debugFilter)
                }
            }

            try {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
            } catch (e: Exception) {
                Log.e("Wallpaper", "Failed to register audio device callback", e)
            }
            updateBluetoothLatency()

            applyIdleBackground()

            registerNotificationEngine(this, isPreview)
            mediaObserver.start()
            if (isPreview) {
                resetToIdleState()
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            cancelPendingCommit()
            unregisterNotificationEngine(this, isPreview)
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
            mediaObserver.stop()
            choreographer.removeFrameCallback(this)
            engineScope.cancel()
            unregisterReceiver(screenStateReceiver)
            try {
                unregisterReceiver(forceReloadLyricsReceiver)
            } catch (e: Exception) {}
            if (isDebugBuild()) {
                try {
                    unregisterReceiver(debugDemoReceiver)
                } catch (e: Exception) {}
            }

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
                val isMetadataState = isMetadataState(now, timeSinceWake, lines)
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
            val isMetadataState = isMetadataState(now, timeSinceWake, lines)
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
        private var trackArtGeneration = 0

        private fun onMetadataChanged(metadata: MediaMetadata?) {
            if (metadata == null) {
                resetToIdleState()
                return
            }
            try {
                val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()
                val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim()
                val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)?.trim()

                val albumArtUri = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI) ?:
                                 metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)

                val art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?:
                          metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)

                Log.d("Wallpaper", "Metadata: $title - $artist ($album), Uri: $albumArtUri, ArtBitmap: ${art != null}")

                if (title.isNullOrBlank()) {
                    resetToIdleState()
                    return
                }

                val durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)

                val isNewTrack = (title != currentTitle || artist != currentArtist)
                if (isNewTrack) {
                    val isFromValidSong = !currentTitle.isNullOrBlank()
                    if (isFromValidSong) {
                        // Defer the entire track change commit so the finished song's metadata is held on screen and lyrics fade out before the new track state is applied.
                        // Replace pending values without restarting the timer so rapid skips complete on schedule without indefinite postponement.
                        pendingTrack = PendingTrack(title, artist, art, albumArtUri, durationMs)
                        if (pendingCommitRunnable == null) {
                            songTransitionStartMs = System.currentTimeMillis()
                            targetViewAlpha = 1.0f
                            val runnable = Runnable {
                                val track = pendingTrack
                                pendingCommitRunnable = null
                                pendingTrack = null
                                if (track != null) {
                                    commitTrackChange(track.title, track.artist, track.art, track.albumArtUri, track.durationMs)
                                }
                            }
                            pendingCommitRunnable = runnable
                            mainHandler.postDelayed(runnable, SongTransition.HOLD_OLD_MS)
                        }
                    } else {
                        // Initial song or returning from idle commits immediately to avoid a 1 second startup delay on an empty screen.
                        cancelPendingCommit()
                        commitTrackChange(title, artist, art, albumArtUri, durationMs)
                    }
                } else {
                    if (pendingTrack != null) {
                        // If the user skipped back to the active song before the scheduled commit fired, drop the pending transition.
                        cancelPendingCommit()
                    } else {
                        if (durationMs > 0) currentDurationMs = durationMs
                        // Prefer the high resolution URI. The metadata bitmap is the fallback.
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
                    }
                }

            } catch (e: Exception) {
                Log.e("Wallpaper", "Metadata error", e)
            }
        }

        private fun commitTrackChange(
            title: String,
            artist: String?,
            art: Bitmap?,
            albumArtUri: String?,
            durationMs: Long
        ) {
            trackArtGeneration++
            startMetadataTransition()
            currentTitle = title
            currentArtist = artist
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            updateSongSpecificDelay(prefs)
            updatePersistentNotificationMetadata(this, isPreview, title, artist)
            currentDurationMs = durationMs
            lyricsSearchExhausted = false
            currentArtUri = null
            hasArtForCurrentTrack = false
            songStartTime = System.currentTimeMillis()

            lastKnownPlaybackPosition = 0L
            lastUpdateTime = SystemClock.elapsedRealtime()

            // Force a transition to metadata view even if paused
            targetViewAlpha = 1.0f

            // Output route can change with the track, so re-measure before the first line.
            updateBluetoothLatency()

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

            // Prefer the high resolution URI. The metadata bitmap is the fallback.
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
            if (albumArtUri == null && art == null) {
                val gen = trackArtGeneration
                mainHandler.postDelayed({
                    if (trackArtGeneration == gen && !hasArtForCurrentTrack) {
                         albumArt = null
                         albumArtAspect = 1.0f
                    }
                }, 500)
            }
        }

        private fun updateBluetoothLatency() {
            detectedBluetoothLatency = 0L
        }

        private fun updateAlbumArt(sourceBitmap: Bitmap) {
            albumArt = sourceBitmap
            albumArtAspect = MetadataArtLayout.aspectFor(
                MetadataArtLayout.allowsNativeAspect(mediaObserver.getActivePackageName()),
                sourceBitmap.width,
                sourceBitmap.height
            )
            engineScope.launch {
                val palette = withContext(Dispatchers.Default) {
                    AuroraRenderer.extractPalette(sourceBitmap)
                }

                // pre-process and blur the album cover for the dynamic background (Very Blurred - 2 passes)
                val blurred = withContext(Dispatchers.Default) {
                    val preprocessed = AuroraRenderer.preprocessArt(sourceBitmap, palette.accent, 0.15f)
                    // Radius 80 scales radius 20 linearly with resolution (512 / 128) to preserve visual softness.
                    val firstPass = AuroraRenderer.blurBitmap(preprocessed, 80)
                    val secondPass = AuroraRenderer.blurBitmap(firstPass, 80)
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
                        try { notifyColorsChanged() } catch (e: Exception) { /* wallpaper may not be set */ }
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
            if (isDebugDemoActive()) return
            isPlaying = state?.state == PlaybackState.STATE_PLAYING
            if (state != null) {
                val prevPos = lastKnownPlaybackPosition

                if (state.lastPositionUpdateTime > 0L) {
                    lastKnownPlaybackPosition = state.position
                    lastUpdateTime = state.lastPositionUpdateTime
                    lastKnownPlaybackSpeed = state.playbackSpeed
                } else if (isPlaying) {
                    if (state.position != lastKnownPlaybackPosition) {
                        lastKnownPlaybackPosition = state.position
                        lastUpdateTime = SystemClock.elapsedRealtime()
                        lastKnownPlaybackSpeed = state.playbackSpeed
                    }
                } else {
                    lastKnownPlaybackPosition = state.position
                }

                // On a seek, snap the scroll cursor to the real position instead of
                // extrapolating from a stale one.
                if (Math.abs(lastKnownPlaybackPosition - prevPos) > 1000L) {
                    snapScrollToPosition()
                }
            }
        }

        private fun resetToIdleState() {
            cancelPendingCommit()
            currentTitle = null
            currentArtist = null
            currentDurationMs = 0L
            lyricsSearchExhausted = false
            albumArt = null
            albumArtAspect = 1.0f
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
                prefIdleAccent,
                prefIdleBase,
                prefIdleMid,
                prefIdleHighlight
            )
            currentColors = targetColors.copyOf()
            applyIdleBackground()
            updatePersistentNotificationMetadata(this, isPreview, currentTitle, currentArtist)
        }

        private fun applyIdleBackground() {
            engineScope.launch(Dispatchers.Default) {
                val idleMesh = AuroraRenderer.createIdleMesh(targetColors)
                val preprocessed = AuroraRenderer.preprocessArt(idleMesh, Color.BLACK, 0f)
                // Radius 80 scales radius 20 linearly with resolution (512 / 128) to preserve visual softness.
                val firstPass = AuroraRenderer.blurBitmap(preprocessed, 80)
                val blurred = AuroraRenderer.blurBitmap(firstPass, 80)
                idleMesh.recycle()
                preprocessed.recycle()
                firstPass.recycle()
                
                withContext(Dispatchers.Main) {
                    triggerBgTransition(blurred)
                    
                    if (prefDynamicTheming && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        try { notifyColorsChanged() } catch (e: Exception) { /* wallpaper may not be set */ }
                    }
                }
            }
        }

        private fun drawFrame(dt: Float) {
            val frameStartNs = if (BuildConfig.DEBUG) System.nanoTime() else 0L
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
                if (BuildConfig.DEBUG) {
                    val frameEndNs = System.nanoTime()
                    val isLineChange = (SystemClock.elapsedRealtime() - lineChangeElapsedMs) <= 200L
                    frameDiagnostics?.recordFrame(frameStartNs, frameEndNs, isLineChange)
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
            
            val position = getExtrapolatedPosition()
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

            // Lyrics, inside its own save/restore.
            val timeSinceWake = now - lastWakeTime
            val isMetadataState = isMetadataState(now, timeSinceWake, lines)
            targetViewAlpha = if (isMetadataState) 1.0f else 0.0f

            // State change transition (symmetric speeds)
            if (viewAlpha != targetViewAlpha) {
                val speed = 6.0f
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

                preuploadInitialBitmaps(initialIndex, bitmaps)
            }

            // Watchdog logic... (only retry layer; disarmed once a miss is definitive)
            if (!prefMetadataOnlyMode && currentLyrics == null && !lyricsSearchExhausted && !currentTitle.isNullOrBlank() && (now - songStartTime > 4000)) {
                if (now - lastWatchdogCheck > 5000) { 
                    lastWatchdogCheck = now
                    currentTitle?.let { title ->
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
                val leadTime = 50L
                val adjustedPos = position - totalOffset + leadTime

                val bsIdx = lines.binarySearch { it.startTime.compareTo(adjustedPos) }
                var currentIndex = if (bsIdx >= 0) bsIdx else (-bsIdx - 2).coerceAtLeast(0)

                val transitionDuration = 200f

                // Synchronized Glide
                val currentOffset = offsets[currentIndex]
                val prevOffset = if (currentIndex > 0) offsets[currentIndex - 1] else currentOffset
                val glideDistance = Math.abs(currentOffset - prevOffset)
                val glideDuration = SyllableAnimator.glideDurationMs(glideDistance)

                val entryProgress = ((adjustedPos - lines[currentIndex].startTime) / glideDuration).coerceIn(0f, 1f)
                val easedGlide = SyllableAnimator.easeOutGlide(entryProgress)

                targetScrollY = prevOffset + (currentOffset - prevOffset) * easedGlide

                // Smooth scroll toward target. A spring (k=3600) was tried but is numerically
                // unstable at 60fps (sqrt(k)*dt≈0.96 > 0.618 stability bound), causing scroll
                // to diverge on large jumps. Simple lerp at dt*16 is unconditionally stable,
                // gives ~62ms lag, and caps dt to avoid spikes on the first frame after wake.
                val safeDt = dt.coerceAtMost(0.033f) // never extrapolate more than one 30fps frame
                scrollY += (targetScrollY - scrollY) * (safeDt * 16f).coerceAtMost(1f)


                // Use saveLayer only during Metadata vs Lyrics transitions
                val lyricsAlpha = ((1.0f - viewAlpha) * 255).toInt()
                val needsLayer = lyricsAlpha < 255

                if (needsLayer) {
                    lyricsLayerPaint.alpha = lyricsAlpha
                    canvas.saveLayer(null, lyricsLayerPaint)
                }

                canvas.save() // Bounds the translate so metadata section is unaffected
                canvas.translate(0f, centerY - scrollY)
                val visibleRange = 7
                val bitmaps = lyricBitmaps ?: return

                val clipTop = scrollY - centerY - 300f
                val clipBottom = scrollY - centerY + height + 300f


                // Line-change detection: when the active line changes, start a 200ms wall-clock
                // ramp so word progress opens from 0 → actual, regardless of how far adjustedPos
                // has jumped ahead due to the 1-second position resync.
                if (currentIndex != prevCurrentIndex) {
                    prevCurrentIndex = currentIndex
                    lineChangeElapsedMs = android.os.SystemClock.elapsedRealtime()
                    preuploadUpcomingBitmaps(currentIndex, bitmaps)
                }
                val lineRampFraction = ((android.os.SystemClock.elapsedRealtime() - lineChangeElapsedMs)
                    .toFloat() / 200f).coerceIn(0f, 1f)

                for (i in (currentIndex - visibleRange)..(currentIndex + visibleRange)) {
                    if (i in layouts.indices) {
                        val line = lines[i]
                        val layout = layouts[i]
                        val bmp = bitmaps[i]
                        val lineCenterY = offsets[i]

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

                        val targetAlpha = (0.35f + (0.65f * easedFactor)) * 230
                        val isActive = i == currentIndex
                        val isFadingOut = i < currentIndex && exitLinear < 1f

                        if ((isActive || isFadingOut) && line.words != null && line.words.isNotEmpty()) {
                            if (isActive) {
                                // Word-gate: clamp the effective position used for word progress
                                // to prevent position-sync jumps from instantly pre-completing
                                // words when the line first appears. Ramps from line.startTime
                                // to adjustedPos over 200ms of wall-clock time.
                                // Snap immediately for seeks (>3s jump) so they aren't delayed.
                                val rawGate = line.startTime + ((adjustedPos - line.startTime) * lineRampFraction).toLong()
                                val wordGatePos = if (Math.abs(adjustedPos - rawGate) > 3000L) adjustedPos else rawGate

                                // Detect pre-roll phase: Wav2Vec2 may detect the first word
                                // onset 200-500ms after the LRC line timestamp. During this gap
                                // all spans have progress=0 which makes the active line look dim
                                // (80 alpha). Smoothly brighten from 80→80+50=130 instead.
                                val firstWordOnset = line.words.minOfOrNull {
                                    if (it.fullStartTime != 0L) it.fullStartTime else it.startTime
                                } ?: wordGatePos
                                val isPreRollPhase = wordGatePos < firstWordOnset &&
                                    firstWordOnset > line.startTime + 100L

                                if (isPreRollPhase) {
                                    val preRollDuration = (firstWordOnset - line.startTime).toFloat().coerceAtLeast(1f)
                                    val preRollProgress = ((wordGatePos - line.startTime) / preRollDuration).coerceIn(0f, 1f)
                                    val preRollAlpha = (80 + (50 * preRollProgress)).toInt()
                                    for (word in line.words) {
                                        val span = word.spanRef as? WordGradientSpan ?: continue
                                        span.progress = 0f
                                        span.motionProgress = 0f
                                        span.activeAlpha = preRollAlpha
                                        span.inactiveAlpha = preRollAlpha
                                    }
                                } else {
                                    for (word in line.words) {
                                        val span = word.spanRef as? WordGradientSpan ?: continue

                                        val startT = if (word.fullStartTime == 0L) word.startTime else word.fullStartTime
                                        val endT = if (word.fullEndTime == 0L) word.endTime else word.fullEndTime
                                        val effectiveEndT = SyllableAnimator.getExtendedWordEnd(startT, endT, line.endTime)

                                        val fullWordLinearProgress = when {
                                            wordGatePos >= effectiveEndT -> 1f
                                            wordGatePos <= startT -> 0f
                                            else -> {
                                                ((wordGatePos - startT).toFloat() / (effectiveEndT - startT).toFloat()).coerceIn(0f, 1f)
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
                                        span.motionProgress = if (fullWordLinearProgress > 0f && fullWordLinearProgress < 1f) {
                                            fullWordLinearProgress
                                        } else {
                                            0f
                                        }
                                        span.activeAlpha = 230
                                        span.inactiveAlpha = 80
                                    }
                                }
                            } else {
                                val fadeProgress = exitLinear.coerceIn(0f, 1f)
                                val currentAlpha = (230 - (230 - 80) * fadeProgress).toInt()

                                for (word in line.words) {
                                    val span = word.spanRef as? WordGradientSpan ?: continue
                                    span.progress = 1f
                                    span.motionProgress = 0f
                                    span.activeAlpha = currentAlpha
                                    span.inactiveAlpha = 80
                                }
                            }

                            layout.paint.shader = null
                            layout.paint.color = Color.WHITE
                            layout.paint.alpha = 255

                            canvas.translate(centerX - layout.width / 2f, lineCenterY - (layout.height / 2f))
                            val hasSpans = line.words.any { it.spanRef != null }
                            if (!hasSpans) {
                                activeLineLayerBounds.set(0f, 0f, layout.width.toFloat(), layout.height.toFloat())
                                activeLineLayerPaint.alpha = 80
                                canvas.saveLayer(activeLineLayerBounds, activeLineLayerPaint)
                                layout.draw(canvas)
                                canvas.restore()
                            } else {
                                layout.draw(canvas)
                            }
                        } else {
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
                canvas.restore() // Unwind the translate

                if (needsLayer) canvas.restore()
            }

            // Metadata, inside its own save/restore.
            if (viewAlpha > 0.0f) {
                if (viewAlpha < 1.0f) {
                    // Only use an offscreen compositing layer during transitions.
                    // In steady-state (viewAlpha==1.0) saveLayer forces an unnecessary
                    // GPU offscreen framebuffer allocation every frame.
                    metadataLayerPaint.alpha = (viewAlpha * 255).toInt()
                    canvas.saveLayer(null, metadataLayerPaint)
                    drawMetadataWithAlbumArt(canvas, width, height, dt)
                    canvas.restore()
                } else {
                    drawMetadataWithAlbumArt(canvas, width, height, dt)
                }
            }

            // --- FADE GRADIENTS (always in screen coordinates) ---
            drawFadeGradients(canvas, width, height, (1.0f - viewAlpha))
        }

        private fun drawFadeGradients(canvas: Canvas, width: Float, height: Float, alpha: Float) {
            if (alpha <= 0f) return
            if (width != lastFadeWidth || height != lastFadeHeight) {
                lastFadeWidth = width
                lastFadeHeight = height
                topFadeShader = null
                bottomFadeShader = null
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
            art: Bitmap?,
            aspect: Float
        ) {
            val centerX = width / 2f
            val centerY = height / 2f
            
            // Apply scale effect
            val scale = 0.96f + (0.04f * viewAlpha)
            canvas.save()
            canvas.scale(scale, scale, centerX, centerY)

            val albumW = if (art != null) MetadataArtLayout.fittedWidth(width, height, aspect) else 0f
            val albumH = if (art != null) MetadataArtLayout.fittedHeight(width, height, aspect) else 0f
            val hasTitle = tLayout.text.isNotEmpty()
            val hasArtist = aLayout.text.isNotEmpty()
            val titleHeight = if (hasTitle) tLayout.height else 0
            val artistHeight = if (hasArtist) aLayout.height else 0
            val albumTextGap = if (art != null && (hasTitle || hasArtist)) width * 0.04f else 0f
            val metadataGap = if (hasTitle && hasArtist) 5.0f else 0f
            
            val totalHeight = albumH + albumTextGap + titleHeight + metadataGap + artistHeight
            var currentY = centerY - (totalHeight / 2f)
            
            // Draw Album Art
            art?.let { bmp ->
                val rect = RectF(centerX - albumW / 2f, currentY, centerX + albumW / 2f, currentY + albumH)
                val cornerRadius = prefAlbumCornerRadius

                canvas.save()
                albumArtPath.reset()
                albumArtPath.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
                canvas.clipPath(albumArtPath)
                canvas.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), rect, albumArtPaint)
                canvas.restore()
                currentY += albumH + albumTextGap
            }
            
            // Draw Title
            if (hasTitle) {
                drawSimpleLayout(canvas, tLayout, centerX, currentY + tLayout.height / 2f)
                currentY += tLayout.height + metadataGap
            }
            
            // Draw Artist
            if (hasArtist) {
                val aWidth = aLayout.width.toFloat()
                val aHeight = aLayout.height.toFloat()
                metadataArtistBounds.set(
                    centerX - aWidth / 2f,
                    currentY,
                    centerX + aWidth / 2f,
                    currentY + aHeight
                )
                canvas.saveLayer(metadataArtistBounds, metadataArtistLayerPaint)
                drawSimpleLayout(canvas, aLayout, centerX, currentY + aHeight / 2f)
                canvas.restore()
            }
            
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
                    alpha = 255
                }

                val rawTitle = currentTitle ?: prefIdleTitle
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
                prevMetaLayerPaint.alpha = (prevAlpha * 255).toInt()
                canvas.saveLayer(null, prevMetaLayerPaint)
                drawMetadataLayouts(canvas, width, height, prevTitleLayout!!, prevArtistLayout!!, prevAlbumArt, prevAlbumArtAspect)
                canvas.restore()

                // Draw new metadata fading in (opacity = progress)
                nextMetaLayerPaint.alpha = (metadataTransitionProgress * 255).toInt()
                canvas.saveLayer(null, nextMetaLayerPaint)
                drawMetadataLayouts(canvas, width, height, metadataTitleLayout!!, metadataArtistLayout!!, albumArt, albumArtAspect)
                canvas.restore()
            } else {
                if (prevTitleLayout != null) {
                    prevAlbumArt = null
                    prevTitleLayout = null
                    prevArtistLayout = null
                }
                drawMetadataLayouts(canvas, width, height, metadataTitleLayout!!, metadataArtistLayout!!, albumArt, albumArtAspect)
            }
        }

        private fun drawSimpleLayout(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
            LyricsRenderer.drawSimpleLayout(canvas, layout, x, y)
        }

        private fun drawInstrumentalProgress(canvas: Canvas, layout: StaticLayout, progress: Float, position: Long, line: LyricLine) {
            LyricsRenderer.drawInstrumentalProgress(canvas, layout, progress, position, line)
        }

        private fun showToast(message: String) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastToastTime < TOAST_COOLDOWN_MS) return
            lastToastTime = now
            mainHandler.post {
                android.widget.Toast.makeText(this@LyricsWallpaperService, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        private fun triggerBgTransition(newBlurred: Bitmap) {
            val isIdle = currentTitle.isNullOrBlank()
            val isDebugDemo = isDebugDemoActive()
            val timeOffset = when {
                isIdle -> 0f
                isDebugDemo -> 42f
                else -> 5f + (Math.random() * 10000f).toFloat()
            }
            val seedX = when {
                isIdle -> 0f
                isDebugDemo -> 137f
                else -> (Math.random() * 1000f).toFloat()
            }
            val seedY = when {
                isIdle -> 0f
                isDebugDemo -> 271f
                else -> (Math.random() * 1000f).toFloat()
            }

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

        private fun preuploadUpcomingBitmaps(currentIndex: Int, bitmaps: List<Bitmap>) {
            val ranges = BitmapPreuploadHelper.computePreuploadRanges(
                currentIndex = currentIndex,
                totalLines = bitmaps.size,
                visibleRange = 7,
                lookahead = 3
            )
            for (r in ranges) {
                for (i in r) {
                    val bmp = bitmaps.getOrNull(i)
                    if (bmp != null && !bmp.isRecycled) {
                        bmp.prepareToDraw()
                    }
                }
            }
        }

        private fun preuploadInitialBitmaps(initialIndex: Int, bitmaps: List<Bitmap>) {
            val range = BitmapPreuploadHelper.computeInitialPreuploadRange(
                initialIndex = initialIndex,
                totalLines = bitmaps.size,
                visibleRange = 7,
                lookahead = 3
            )
            for (i in range) {
                val bmp = bitmaps.getOrNull(i)
                if (bmp != null && !bmp.isRecycled) {
                    bmp.prepareToDraw()
                }
            }
        }

    }
}
