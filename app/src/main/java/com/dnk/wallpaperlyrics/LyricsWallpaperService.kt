package com.dnk.wallpaperlyrics

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.app.WallpaperColors
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.SharedPreferences
import android.provider.Settings
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
            uniform float u_edge_falloff;
            
            vec4 main(vec2 fragCoord) {
                vec2 uv = fragCoord / uRes;
                float dist = 0.0;
                
                // Calculate blur strength based on Y position (top and bottom falloff band)
                if (uv.y < u_edge_falloff) {
                    dist = (u_edge_falloff - uv.y) / u_edge_falloff;
                } else if (uv.y > (1.0 - u_edge_falloff)) {
                    dist = (uv.y - (1.0 - u_edge_falloff)) / u_edge_falloff;
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

        internal const val AURORA_SHADER = """
            uniform shader u_texture;
            uniform shader u_texture_next;
            uniform float u_blend;
            uniform float2 u_resolution;
            uniform float u_time;
            uniform float u_time_next;
            uniform float u_intensity;
            uniform float u_dithering;
            uniform float u_scale;
            uniform float2 u_seed;
            uniform float2 u_seed_next;
            uniform float u_static_bg;
            uniform float u_tex_scale;
            uniform float2 u_tex_offset;
            uniform float u_vignette;

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
                    float n1 = snoise(uvSeeded * 0.35 + float2(t, t * 0.7));
                    float n2 = snoise(uvSeeded * 0.35 + float2(-t * 0.8, t * 0.5) + float2(50.0, 50.0));
                    float n3 = snoise(uvSeeded * 0.9 + float2(t * 1.2, -t) + float2(100.0, 0.0));
                    float n4 = snoise(uvSeeded * 0.9 + float2(-t, t * 1.1) + float2(0.0, 100.0));
                    warp = float2(
                        n1 * 0.65 + n3 * 0.35,
                        n2 * 0.65 + n4 * 0.35
                    ) * centerWeight;
                }
                float2 warpedUV = clamp(uv + warp * u_intensity, 0.0, 1.0);
                half4 colorCurrent = u_texture.eval(quinticTexelCoord(warpedUV * u_resolution));
 
                float2 warpNext = float2(0.0, 0.0);
                if (u_static_bg == 0.0) {
                    // Eval warp for next texture
                    float tNext = u_time_next * 0.05;
                    float2 uvSeededNext = uv + u_seed_next;
                    float nn1 = snoise(uvSeededNext * 0.35 + float2(tNext, tNext * 0.7));
                    float nn2 = snoise(uvSeededNext * 0.35 + float2(-tNext * 0.8, tNext * 0.5) + float2(50.0, 50.0));
                    float nn3 = snoise(uvSeededNext * 0.9 + float2(tNext * 1.2, -tNext) + float2(100.0, 0.0));
                    float nn4 = snoise(uvSeededNext * 0.9 + float2(-tNext, tNext * 1.1) + float2(0.0, 100.0));
                    warpNext = float2(
                        nn1 * 0.65 + nn3 * 0.35,
                        nn2 * 0.65 + nn4 * 0.35
                    ) * centerWeight;
                }
                float2 warpedUVNext = clamp(uv + warpNext * u_intensity, 0.0, 1.0);
                half4 colorNext = u_texture_next.eval(quinticTexelCoord(warpedUVNext * u_resolution));

                half4 color = mix(colorCurrent, colorNext, u_blend);

                float vignette = 1.0 - dot(center, center) * u_vignette;
                color.rgb *= vignette;

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
        private val frameDiagnostics = if (BuildFlags.DEBUG) FrameDiagnostics() else null

        private var prefDynamicTheming = false
        private var prefBgSpeed = 1.0f
        private var prefBgSaturation = AuroraRenderer.DEFAULT_CHROMA_EXPONENT
        private var prefSyncOffset = 0
        private var songSyncOffset = 0L
        private var prefAlbumCornerRadius = 48f
        private var prefMetadataOnlyMode = false
        private var prefAodMode = AodMode.METADATA_AND_BACKGROUND
        private var prefStaticBg = false
        private var prefPersistentNotification = false
        private var prefStatusToasts = true
        private var prefIdleTitle = IdleScreenSettings.DEFAULT_IDLE_TITLE
        private var prefIdleAccent = IdleScreenSettings.DEFAULT_ACCENT
        private var prefIdleBase = IdleScreenSettings.DEFAULT_BASE
        private var prefIdleMid = IdleScreenSettings.DEFAULT_MID
        private var prefIdleHighlight = IdleScreenSettings.DEFAULT_HIGHLIGHT
        private var notificationAccessGranted = false

        private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                "dynamic_theming" -> prefDynamicTheming = prefs.getBoolean("dynamic_theming", false)
                "bg_speed" -> prefBgSpeed = prefs.getFloat("bg_speed", 1.0f)
                "bg_saturation" -> prefBgSaturation = prefs.getFloat("bg_saturation", Tuning.chromaExponent)
                "sync_offset" -> prefSyncOffset = prefs.getInt("sync_offset", 0)
                "album_corner_radius" -> prefAlbumCornerRadius = prefs.getFloat("album_corner_radius", 48f)
                "preferred_media_player" -> mediaObserver.refresh()
                AodMode.KEY -> prefAodMode = AodMode.fromPref(prefs.getString(AodMode.KEY, null))
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
                LyricsSettings.KEY_STATUS_TOASTS ->
                    prefStatusToasts = prefs.getBoolean(LyricsSettings.KEY_STATUS_TOASTS, true)
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
                    } else if (key != null && key.startsWith(DeviceOffsets.KEY_PREFIX)) {
                        updateBluetoothLatency()
                    }
                }
            }
        }

        private fun loadPreferences(prefs: SharedPreferences) {
            prefDynamicTheming = prefs.getBoolean("dynamic_theming", false)
            prefBgSpeed = prefs.getFloat("bg_speed", 1.0f)
            prefBgSaturation = prefs.getFloat("bg_saturation", Tuning.chromaExponent)
            prefSyncOffset = prefs.getInt("sync_offset", 0)
            prefAlbumCornerRadius = prefs.getFloat("album_corner_radius", 48f)
            prefMetadataOnlyMode = prefs.getBoolean("metadata_only_mode", false)
            prefAodMode = AodMode.fromPref(prefs.getString(AodMode.KEY, null))
            prefStaticBg = prefs.getBoolean("static_bg", false)
            prefPersistentNotification = prefs.getBoolean("persistent_notification", false)
            prefStatusToasts = prefs.getBoolean(LyricsSettings.KEY_STATUS_TOASTS, true)
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
                val manual = prefs.getInt("song_delay_${title}_${artist}", 0)
                manual.toLong()
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
        private var rememberedWordSpacing: Float = Tuning.wordSpacing
        
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
        private var metadataTransitionStartTime = 0L

        private var cardFadeProgress = 1.0f
        private var cardFadeStartTime = 0L

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
                metadataTransitionStartTime = SystemClock.elapsedRealtime()
                metadataTransitionProgress = 0.0f
                cardFadeProgress = 1.0f
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
        private val prevAlbumArtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        private val albumArtPath = Path()
        private val albumArtRect = RectF()
        private val albumArtSrcRect = Rect()
        private val prevAlbumArtRect = RectF()
        private val prevAlbumArtSrcRect = Rect()
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
        private var targetBgGeneration = -1

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
            alpha = 255
            style = Paint.Style.FILL
        }
        
        private val artistPaint = TextPaint(inactivePaint).apply {
            textSize = 62f
            alpha = (255 * 0.5f).toInt()
        }

        private var scrollY = 0f
        private var scrollVelocity = 0f
        private var scrollOmega = 0f
        private var lastFocusIndex = -1
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
            val currentIndex = if (bsIdx >= 0) bsIdx else (-bsIdx - 2).coerceAtLeast(0)

            scrollY = offsets[currentIndex]
            scrollVelocity = 0f
            lastFocusIndex = -1
        }

        private val screenStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        isScreenOff = true
                        viewAlpha = 1.0f
                        targetViewAlpha = 1.0f
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
                Log.i("WallpaperDemo", "debugDemoReceiver.onReceive action=${intent?.action} isDebugBuild=${isDebugBuild()}")
                if (!isDebugBuild()) return
                when (intent?.action) {
                    "com.dnk.wallpaperlyrics.DEBUG_START_EFFORTLESS" -> startDebugEffortlessDemo()
                    "com.dnk.wallpaperlyrics.DEBUG_PLAY_EFFORTLESS" -> playDebugEffortlessDemo()
                    "com.dnk.wallpaperlyrics.DEBUG_END_EFFORTLESS" -> endDebugEffortlessDemo()
                    "com.dnk.wallpaperlyrics.DEBUG_METADATA_BEAT" -> {
                        if (isDebugDemoActive()) lastWakeTime = System.currentTimeMillis() - 1000L
                    }
                    "com.dnk.wallpaperlyrics.DEBUG_START_PREVIEW" -> startDebugPreviewDemo(intent)
                    "com.dnk.wallpaperlyrics.DEBUG_PLAY_PREVIEW" -> playDebugPreviewDemo(intent.getLongExtra("offset", 0L))
                    "com.dnk.wallpaperlyrics.DEBUG_END_PREVIEW" -> endDebugPreviewDemo()
                    "com.dnk.wallpaperlyrics.DEBUG_PALETTE_CHANGED" -> {
                        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
                        prefIdleAccent = prefs.getInt(IdleScreenSettings.KEY_IDLE_ACCENT, IdleScreenSettings.DEFAULT_ACCENT)
                        prefIdleBase = prefs.getInt(IdleScreenSettings.KEY_IDLE_BASE, IdleScreenSettings.DEFAULT_BASE)
                        prefIdleMid = prefs.getInt(IdleScreenSettings.KEY_IDLE_MID, IdleScreenSettings.DEFAULT_MID)
                        prefIdleHighlight = prefs.getInt(IdleScreenSettings.KEY_IDLE_HIGHLIGHT, IdleScreenSettings.DEFAULT_HIGHLIGHT)
                        if (currentTitle.isNullOrBlank()) {
                            targetColors = intArrayOf(
                                prefIdleAccent,
                                prefIdleBase,
                                prefIdleMid,
                                prefIdleHighlight
                            )
                            currentColors = targetColors.copyOf()
                            applyIdleBackground()
                        }
                    }
                }
            }
        }

        // Debug-only capture harness: loads an arbitrary track (title/artist/album/art/lrc
        // supplied via broadcast extras) so store preview recordings can be made for songs
        // other than the bundled Effortless demo, without a real media session.
        private fun startDebugPreviewDemo(intent: Intent?) {
            Log.i("WallpaperDemo", "startDebugPreviewDemo isDebugBuild=${isDebugBuild()} isPreview=$isPreview intent=$intent")
            if (!isDebugBuild() || isPreview || intent == null) return
            val artAsset = intent.getStringExtra("art") ?: return
            val lrcAsset = intent.getStringExtra("lrc") ?: return
            val title = intent.getStringExtra("title") ?: "Preview"
            val artist = intent.getStringExtra("artist") ?: ""
            val album = intent.getStringExtra("album") ?: ""
            val durationMs = intent.getLongExtra("duration", 180_000L)

            val art = try {
                assets.open(artAsset).use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                Log.e("WallpaperDemo", "Unable to load preview artwork", e)
                return
            } ?: return

            val lines = try {
                assets.open(lrcAsset).bufferedReader().use { LyricsManager.parseLrcText(it.readText(), durationMs) }
            } catch (e: Exception) {
                Log.e("WallpaperDemo", "Unable to parse preview lrc", e)
                return
            } ?: return

            currentTitle = null
            currentArtist = null
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            TrackResolution.clearTrack(prefs, isPreview)
            currentLyrics = null
            albumArt = null
            albumArtAspect = 1.0f
            hasArtForCurrentTrack = false
            cancelPendingArtRetry()
            currentArtUri = null
            inFlightArtUri = null
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
                    .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, album)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
                    .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
                    .build()
            )
            isPlaying = false
            songStartTime = System.currentTimeMillis()
            lastWakeTime = 0L
            currentLyrics = lines
            lyricsSearchExhausted = true
            Log.i("WallpaperDemo", "startDebugPreviewDemo committed title=$currentTitle lines=${lines.size}")
        }

        private fun playDebugPreviewDemo(offsetMs: Long) {
            if (!isDebugDemoActive()) return
            val nowRealtime = SystemClock.elapsedRealtime()
            debugDemoStartRealtime = nowRealtime - offsetMs
            songStartTime = System.currentTimeMillis() - offsetMs
            lastWakeTime = 0L
            isPlaying = true
        }

        private fun endDebugPreviewDemo() {
            if (!isDebugDemoActive()) return
            isPlaying = false
            lastWakeTime = 0L
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
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            TrackResolution.clearTrack(prefs, isPreview)
            currentLyrics = null
            albumArt = null
            albumArtAspect = 1.0f
            hasArtForCurrentTrack = false
            cancelPendingArtRetry()
            currentArtUri = null
            inFlightArtUri = null
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
            // Seed permission state before the first frame builds metadata layouts.
            notificationAccessGranted = hasNotificationAccess()
            Tuning.load(this@LyricsWallpaperService)
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
                    addAction("com.dnk.wallpaperlyrics.DEBUG_START_PREVIEW")
                    addAction("com.dnk.wallpaperlyrics.DEBUG_PLAY_PREVIEW")
                    addAction("com.dnk.wallpaperlyrics.DEBUG_END_PREVIEW")
                    addAction("com.dnk.wallpaperlyrics.DEBUG_PALETTE_CHANGED")
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
            cancelPendingArtRetry()
            trackArtGeneration++
            unregisterNotificationEngine(this, isPreview)
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            TrackResolution.clearTrack(prefs, isPreview)
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
                val hasAccess = hasNotificationAccess()
                if (hasAccess != notificationAccessGranted) {
                    notificationAccessGranted = hasAccess
                    // Invalidate cached metadata layouts so idle text is rebuilt with the new permission state.
                    metadataTitleLayout = null
                    metadataArtistLayout = null
                }
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

                completeExpiredTransitions()

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

            completeExpiredTransitions()

            syncPlaybackState()
            snapScrollToPosition()
            drawFrame(0f)
            drawFrame(0f)
        }

        private fun completeExpiredTransitions() {
            val nowRealtime = SystemClock.elapsedRealtime()
            if (metadataTransitionStartTime > 0L && nowRealtime - metadataTransitionStartTime >= CardFade.DURATION_MS) {
                metadataTransitionProgress = 1.0f
                prevTitleLayout = null
                prevArtistLayout = null
            }
            if (cardFadeStartTime > 0L && nowRealtime - cardFadeStartTime >= CardFade.DURATION_MS) {
                cardFadeProgress = 1.0f
                prevAlbumArt = null
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
        private var inFlightArtUri: String? = null
        private var hasArtForCurrentTrack = false
        private var trackArtGeneration = 0
        private var pendingArtRetryRunnable: Runnable? = null

        private fun cancelPendingArtRetry() {
            pendingArtRetryRunnable?.let { mainHandler.removeCallbacks(it) }
            pendingArtRetryRunnable = null
        }

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
                        fetchAlbumArt(albumArtUri, art)
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
            cancelPendingArtRetry()
            trackArtGeneration++
            startMetadataTransition()
            currentTitle = title
            currentArtist = artist
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            TrackResolution.publishTrack(prefs, isPreview, title, artist)
            updateSongSpecificDelay(prefs)
            updatePersistentNotificationMetadata(this, isPreview, title, artist)
            currentDurationMs = durationMs
            lyricsSearchExhausted = false
            currentArtUri = null
            inFlightArtUri = null
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
                    if (currentTitle == title && !isDebugDemoActive()) {
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

            fetchAlbumArt(albumArtUri, art)

            // Special case: if it's a new track but we have NO new art yet,
            // clear the old art after a short delay if it still hasn't arrived.
            if (albumArtUri == null && art == null) {
                val gen = trackArtGeneration
                mainHandler.postDelayed({
                    if (trackArtGeneration == gen && !hasArtForCurrentTrack) {
                         albumArt = null
                         albumArtAspect = 1.0f
                         prevAlbumArt = null
                         cardFadeProgress = 1.0f
                    }
                }, 500)
            }

            if (isScreenOff) {
                drawFrame(0f)
            }
        }

        private fun fetchAlbumArt(albumArtUri: String?, fallbackArt: Bitmap?) {
            if (!albumArtUri.isNullOrBlank() && albumArtUri != currentArtUri && albumArtUri != inFlightArtUri) {
                cancelPendingArtRetry()
                // inFlightArtUri stays set across delays so incoming metadata events for the same URI do not launch redundant parallel fetches.
                inFlightArtUri = albumArtUri
                executeArtFetch(albumArtUri, fallbackArt, trackArtGeneration, attempt = 1)
            } else if (fallbackArt != null && !hasArtForCurrentTrack) {
                updateAlbumArt(fallbackArt)
                hasArtForCurrentTrack = true
            }
        }

        private fun executeArtFetch(albumArtUri: String, fallbackArt: Bitmap?, gen: Int, attempt: Int) {
            if (trackArtGeneration != gen) return
            lyricsManager.fetchBitmap(albumArtUri) { bitmap ->
                mainHandler.post {
                    if (trackArtGeneration != gen || inFlightArtUri != albumArtUri) return@post
                    if (bitmap != null) {
                        inFlightArtUri = null
                        pendingArtRetryRunnable = null
                        currentArtUri = albumArtUri
                        updateAlbumArt(bitmap)
                        hasArtForCurrentTrack = true
                    } else {
                        val retryDelay = ArtRetry.delayAfterFailure(attempt)
                        if (retryDelay != null) {
                            if (fallbackArt != null && !hasArtForCurrentTrack) {
                                updateAlbumArt(fallbackArt)
                                hasArtForCurrentTrack = true
                            }
                            val retryRunnable = Runnable {
                                pendingArtRetryRunnable = null
                                executeArtFetch(albumArtUri, fallbackArt, gen, attempt + 1)
                            }
                            pendingArtRetryRunnable = retryRunnable
                            mainHandler.postDelayed(retryRunnable, retryDelay)
                        } else {
                            inFlightArtUri = null
                            pendingArtRetryRunnable = null
                            if (fallbackArt != null && !hasArtForCurrentTrack) {
                                updateAlbumArt(fallbackArt)
                                hasArtForCurrentTrack = true
                            } else if (!hasArtForCurrentTrack) {
                                albumArt = null
                                albumArtAspect = 1.0f
                                prevAlbumArt = null
                                cardFadeProgress = 1.0f
                            }
                        }
                    }
                }
            }
        }

        private fun updateBluetoothLatency() {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                ) {
                    detectedBluetoothLatency = 0L
                    return
                }

                val audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                if (audioManager == null) {
                    detectedBluetoothLatency = 0L
                    return
                }

                val devices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                val btDevice = devices.firstOrNull { dev ->
                    dev.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    dev.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    dev.type == android.media.AudioDeviceInfo.TYPE_HEARING_AID ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
                        dev.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        dev.type == android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                        dev.type == android.media.AudioDeviceInfo.TYPE_BLE_BROADCAST
                    ))
                }

                val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) btDevice?.address else null
                if (address.isNullOrBlank()) {
                    detectedBluetoothLatency = 0L
                    return
                }

                val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
                val stored = mutableMapOf<String, Int>()
                for ((key, value) in prefs.all) {
                    if (key.startsWith(DeviceOffsets.KEY_PREFIX)) {
                        val offset = (value as? Number)?.toInt()
                        if (offset != null) {
                            stored[key.removePrefix(DeviceOffsets.KEY_PREFIX)] = offset
                        }
                    }
                }

                val resolved = DeviceOffsets.resolve(address, stored)
                detectedBluetoothLatency = resolved.toLong()
            } catch (e: Exception) {
                Log.e("Wallpaper", "Failed to update bluetooth latency", e)
                detectedBluetoothLatency = 0L
            }
        }

        /**
         * Cuts the black bars off a letterboxed thumbnail. The bars are wide enough to
         * show inside the rounded corners of the metadata view, and they drag the
         * extracted palette towards black, so they come off before anything reads the
         * bitmap.
         *
         * Sampling a handful of columns is enough: a bar spans the full width, so a row
         * only counts as one when every sample in it is dark.
         */
        private fun cropLetterbox(bitmap: Bitmap): Bitmap {
            val columnSamples = 9
            val width = bitmap.width
            val height = bitmap.height
            if (width < columnSamples || height <= 0) return bitmap

            val strips = Array(columnSamples) { IntArray(height) }
            for (i in 0 until columnSamples) {
                val x = (i + 1) * width / (columnSamples + 1)
                bitmap.getPixels(strips[i], 0, 1, x, 0, 1, height)
            }

            val rowIsDark = BooleanArray(height) { y ->
                strips.all { MetadataArtLayout.isLetterboxDark(it[y]) }
            }

            val rows = MetadataArtLayout.contentRows(rowIsDark)
            if (rows.first == 0 && rows.last == height - 1) return bitmap
            return Bitmap.createBitmap(bitmap, 0, rows.first, width, rows.last - rows.first + 1)
        }

        private fun updateAlbumArt(sourceBitmap: Bitmap) {
            val nativeAspect = MetadataArtLayout.allowsNativeAspect(mediaObserver.getActivePackageName())
            val art = if (nativeAspect) cropLetterbox(sourceBitmap) else sourceBitmap
            if (art === albumArt) return

            if (CardFade.shouldFade(albumArt, art)) {
                prevAlbumArt = albumArt
                prevAlbumArtAspect = albumArtAspect
                cardFadeProgress = 0.0f
                cardFadeStartTime = SystemClock.elapsedRealtime()
            }
            albumArt = art
            albumArtAspect = MetadataArtLayout.aspectFor(nativeAspect, art.width, art.height)
            val capturedGen = trackArtGeneration
            engineScope.launch {
                val palette = withContext(Dispatchers.Default) {
                    AuroraRenderer.extractPalette(art)
                }

                // pre-process and blur the album cover for the dynamic background (Very Blurred - 2 passes)
                val blurred = withContext(Dispatchers.Default) {
                    val preprocessed = AuroraRenderer.preprocessArt(art, palette.accent, 0.15f)
                    // Radius 80 scales radius 20 linearly with resolution (512 / 128) to preserve visual softness.
                    val firstPass = AuroraRenderer.blurBitmap(preprocessed, 80)
                    val secondPass = AuroraRenderer.blurBitmap(firstPass, 80)
                    preprocessed.recycle()
                    firstPass.recycle()
                    val exponent = prefBgSaturation
                    val gamutCap = Tuning.gamutCapFraction
                    val linearBoost = Tuning.linearBoost
                    val depth = Tuning.backgroundDepth
                    val depthGateLow = Tuning.depthGateLow
                    val depthGateHigh = Tuning.depthGateHigh
                    AuroraRenderer.boostChroma(
                        secondPass,
                        exponent,
                        gamutCap,
                        linearBoost,
                        depth,
                        depthGateLow,
                        depthGateHigh
                    )
                    val knee = Tuning.lightnessCapKnee
                    val ceiling = Tuning.lightnessCapCeiling
                    val strength = Tuning.lightnessCapStrength
                    AuroraRenderer.capLightness(secondPass, knee, ceiling, strength)
                    secondPass
                }
                
                withContext(Dispatchers.Main) {
                    targetColors = intArrayOf(
                        palette.accent,
                        palette.base,
                        palette.mid,
                        palette.highlight
                    )
                    
                    triggerBgTransition(blurred, capturedGen)

                    if (prefDynamicTheming && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        try { notifyColorsChanged() } catch (e: Exception) { /* wallpaper may not be set */ }
                    }
                }
            }
            if (isScreenOff) {
                drawFrame(0f)
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
            cancelPendingArtRetry()
            trackArtGeneration++
            currentTitle = null
            currentArtist = null
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            TrackResolution.clearTrack(prefs, isPreview)
            currentDurationMs = 0L
            lyricsSearchExhausted = false
            albumArt = null
            albumArtAspect = 1.0f
            prevAlbumArt = null
            prevAlbumArtAspect = 1.0f
            cardFadeProgress = 1.0f
            cardFadeStartTime = 0L
            metadataTransitionProgress = 1.0f
            metadataTransitionStartTime = 0L
            prevTitleLayout = null
            prevArtistLayout = null
            currentLyrics = null
            currentArtUri = null
            inFlightArtUri = null
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
            val capturedGen = trackArtGeneration
            engineScope.launch(Dispatchers.Default) {
                val idleMesh = AuroraRenderer.createIdleMesh(targetColors)
                val preprocessed = AuroraRenderer.preprocessArt(idleMesh, Color.BLACK, 0f)
                // Radius 80 scales radius 20 linearly with resolution (512 / 128) to preserve visual softness.
                val firstPass = AuroraRenderer.blurBitmap(preprocessed, 80)
                val blurred = AuroraRenderer.blurBitmap(firstPass, 80)
                idleMesh.recycle()
                preprocessed.recycle()
                firstPass.recycle()
                val exponent = prefBgSaturation
                val gamutCap = Tuning.gamutCapFraction
                val linearBoost = Tuning.linearBoost
                val depth = Tuning.backgroundDepth
                val depthGateLow = Tuning.depthGateLow
                val depthGateHigh = Tuning.depthGateHigh
                AuroraRenderer.boostChroma(
                    blurred,
                    exponent,
                    gamutCap,
                    linearBoost,
                    depth,
                    depthGateLow,
                    depthGateHigh
                )
                val knee = Tuning.lightnessCapKnee
                val ceiling = Tuning.lightnessCapCeiling
                val strength = Tuning.lightnessCapStrength
                AuroraRenderer.capLightness(blurred, knee, ceiling, strength)
                
                withContext(Dispatchers.Main) {
                    triggerBgTransition(blurred, capturedGen)
                    
                    if (prefDynamicTheming && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        try { notifyColorsChanged() } catch (e: Exception) { /* wallpaper may not be set */ }
                    }
                }
            }
        }

        private fun drawFrame(dt: Float) {
            val frameStartNs = if (BuildFlags.DEBUG) System.nanoTime() else 0L
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
                        val crossfadeRate = Tuning.crossfadeRate
                        blendProgress += dt * crossfadeRate
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
                    if (isScreenOff) {
                        when (prefAodMode) {
                            AodMode.OFF -> {
                                canvas.drawPaint(backgroundPaint)
                            }
                            AodMode.METADATA_ONLY -> {
                                canvas.drawPaint(backgroundPaint)
                                drawLyrics(canvas, dt)
                            }
                            AodMode.METADATA_AND_BACKGROUND -> {
                                drawAurora(canvas)
                                drawLyrics(canvas, dt)
                            }
                        }
                    } else {
                        drawAurora(canvas)
                        drawLyrics(canvas, dt)
                    }
                }
            } catch (e: Exception) {
                Log.e("Wallpaper", "Draw error", e)
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas) } catch (e: Exception) {}
                }
                if (BuildFlags.DEBUG) {
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
            if (isScreenOff) {
                // AOD shows the last composed frame and the mode, not the playback state, decides what belongs in it.
                targetViewAlpha = 1.0f
                viewAlpha = 1.0f
            }

            // State change transition (symmetric speeds)
            if (viewAlpha != targetViewAlpha) {
                val speed = 6.0f
                viewAlpha += (targetViewAlpha - viewAlpha) * (dt * speed).coerceAtMost(1.0f)
                if (Math.abs(viewAlpha - targetViewAlpha) < 0.005f) viewAlpha = targetViewAlpha
            }

            if (viewAlpha <= 0.0f) {
                if (metadataTransitionProgress < 1.0f) {
                    metadataTransitionProgress = 1.0f
                    prevTitleLayout = null
                    prevArtistLayout = null
                }
                if (cardFadeProgress < 1.0f) {
                    cardFadeProgress = 1.0f
                    prevAlbumArt = null
                }
            }

            val spacingChanged = Math.abs(rememberedWordSpacing - Tuning.wordSpacing) > 0.0001f
            if (lines != null && (lyricLayouts == null || lyricBitmaps == null || lineOffsets == null || spacingChanged)) {
                if (spacingChanged) {
                    lyricBitmaps?.forEach { it.recycle() }
                    lyricBitmaps = null
                    lyricLayouts = null
                    lineOffsets = null
                }
                val (layouts, bitmaps, linesWithMeasuredWords) = LyricsRenderer.buildLyricLayouts(
                    lines,
                    activePaint,
                    maxTextWidth
                )

                currentLyrics = linesWithMeasuredWords
                lines = linesWithMeasuredWords

                lyricLayouts = layouts
                lyricBitmaps = bitmaps
                rememberedWordSpacing = Tuning.wordSpacing

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
                scrollVelocity = 0f
                lastFocusIndex = -1

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

                val holdMax = Tuning.lineHoldMaxMs
                val wordOverlapMs = Tuning.wordOverlapMs
                val wordMinAnimationMs = Tuning.wordMinAnimationMs
                val wordMotionTrailFraction = Tuning.wordMotionTrailFraction
                val wordMotionTrailMinMs = Tuning.wordMotionTrailMinMs
                val wordMotionTrailMaxMs = Tuning.wordMotionTrailMaxMs
                val wordMotionDurationFloorMs = Tuning.wordMotionDurationFloorMs
                val wordLeadInMs = Tuning.wordLeadInMs
                val wordRiseDurationMs = Tuning.wordRiseDurationMs
                val wordSettleDurationMs = Tuning.wordSettleDurationMs
                val effectiveFloorMs = SyllableAnimator.getEffectiveMotionFloor(
                    wordMotionDurationFloorMs,
                    wordLeadInMs,
                    wordRiseDurationMs,
                    wordSettleDurationMs
                )

                val prevReleaseTime = if (currentIndex > 0) {
                    val prevLine = lines[currentIndex - 1]
                    val nextNextStart = if (currentIndex + 1 < lines.size) lines[currentIndex + 1].startTime else Long.MAX_VALUE
                    SyllableAnimator.getLineReleaseTime(
                        prevLine.words,
                        prevLine.endTime,
                        lines[currentIndex].startTime,
                        nextNextStart,
                        holdMax,
                        wordMotionTrailFraction,
                        wordMotionTrailMinMs,
                        wordMotionTrailMaxMs,
                        wordOverlapMs,
                        wordMinAnimationMs,
                        effectiveFloorMs
                    )
                } else {
                    lines[0].startTime
                }

                val focusIndex = if (currentIndex > 0 && adjustedPos < prevReleaseTime) currentIndex - 1 else currentIndex
                val scrollTarget = offsets[focusIndex]

                if (focusIndex != lastFocusIndex) {
                    val glideMs = SyllableAnimator.glideDurationMs(
                        Math.abs(scrollTarget - scrollY),
                        Tuning.baseGlideMs,
                        Tuning.referenceDistancePx
                    )
                    scrollOmega = 4.74f / (glideMs / 1000f)
                    lastFocusIndex = focusIndex
                }

                // Exact solution of a critically damped spring remains stable across frame rate variations and large steps.
                val safeDt = dt.coerceAtMost(0.033f)
                val x0 = scrollY - scrollTarget
                val v0 = scrollVelocity
                scrollY = scrollTarget + SyllableAnimator.springPosition(x0, v0, scrollOmega, safeDt)
                scrollVelocity = SyllableAnimator.springVelocity(x0, v0, scrollOmega, safeDt)


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

                val preRollSettleMs = Tuning.preRollSettleMs
                val preRollMaxLift = Tuning.preRollMaxLift

                for (i in (currentIndex - visibleRange)..(currentIndex + visibleRange)) {
                    if (i in layouts.indices) {
                        val line = lines[i]
                        val layout = layouts[i]
                        val bmp = bitmaps[i]
                        val lineCenterY = offsets[i]

                        if (lineCenterY + bmp.height / 2f < clipTop || lineCenterY - bmp.height / 2f > clipBottom) continue

                        val entryLinear = ((adjustedPos - line.startTime) / transitionDuration).coerceIn(0f, 1f)
                        val exitLinear = if (i < lines.size - 1) {
                            // A line held until the line after next started is two behind by the
                            // time it exits, so its exit has to be timed from its own release too.
                            val releaseTime = when (i) {
                                currentIndex - 1 -> prevReleaseTime
                                currentIndex - 2 -> SyllableAnimator.getLineReleaseTime(
                                    line.words,
                                    line.endTime,
                                    lines[i + 1].startTime,
                                    lines[i + 2].startTime,
                                    holdMax,
                                    wordMotionTrailFraction,
                                    wordMotionTrailMinMs,
                                    wordMotionTrailMaxMs,
                                    wordOverlapMs,
                                    wordMinAnimationMs,
                                    effectiveFloorMs
                                )
                                else -> lines[i + 1].startTime
                            }
                            ((adjustedPos - releaseTime) / transitionDuration).coerceIn(0f, 1f)
                        } else 0f

                        val easedEntry = 1f - (1f - entryLinear) * (1f - entryLinear)
                        val easedExit = 1f - (1f - exitLinear) * (1f - exitLinear)
                        val easedFactor = (easedEntry - easedExit).coerceIn(0f, 1f)

                        canvas.save()

                        val isActive = i == currentIndex
                        val isFadingOut = i < currentIndex && exitLinear < 1f
                        val hasWordTiming = line.words != null && line.words.isNotEmpty()
                        val restScale = Tuning.wordScaleStart
                        val scale = if (hasWordTiming) {
                            restScale
                        } else {
                            restScale + (1f - restScale) * easedFactor
                        }
                        canvas.scale(scale, scale, centerX, lineCenterY)

                        val targetAlpha = INACTIVE_LYRIC_ALPHA.toFloat() + ((230f - INACTIVE_LYRIC_ALPHA.toFloat()) * easedFactor)

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
                                // (80 alpha). Smoothly brighten from 80->80+50=130 instead.
                                val firstWordOnset = line.words.minOfOrNull {
                                    if (it.fullStartTime != 0L) it.fullStartTime else it.startTime
                                } ?: wordGatePos
                                val hasPreRoll = firstWordOnset > line.startTime + 100L
                                val isPreRollPhase = wordGatePos < firstWordOnset && hasPreRoll
                                val inactiveAlpha = if (hasPreRoll) {
                                    SyllableAnimator.getPreRollInactiveAlpha(
                                        wordGatePos,
                                        line.startTime,
                                        firstWordOnset,
                                        preRollSettleMs,
                                        preRollMaxLift
                                    )
                                } else {
                                    INACTIVE_LYRIC_ALPHA
                                }

                                if (isPreRollPhase) {
                                    for (word in line.words) {
                                        val span = word.spanRef as? WordGradientSpan ?: continue
                                        span.progress = 0f
                                        span.linearProgress = 0f
                                        span.wholeWordLinearProgress = 0f
                                        span.motionProgress = 0f
                                        span.motionWindowMs = 0L
                                        span.exitFade = 0f
                                        span.activeAlpha = inactiveAlpha
                                        span.inactiveAlpha = inactiveAlpha
                                    }
                                } else {
                                    for (wordIndex in line.words.indices) {
                                        val word = line.words[wordIndex]
                                        val span = word.spanRef as? WordGradientSpan ?: continue

                                        val startT = if (word.fullStartTime == 0L) word.startTime else word.fullStartTime
                                        val endT = if (word.fullEndTime == 0L) word.endTime else word.fullEndTime
                                        val prevEndT = if (wordIndex > 0) {
                                            val prev = line.words[wordIndex - 1]
                                            if (prev.fullEndTime != 0L) prev.fullEndTime else prev.endTime
                                        } else {
                                            line.startTime
                                        }
                                        val motionStartT = SyllableAnimator.getMotionWordStart(
                                            startT,
                                            line.startTime,
                                            prevEndT,
                                            wordLeadInMs
                                        )
                                        val effectiveEndT = SyllableAnimator.getExtendedWordEnd(
                                            startT,
                                            endT,
                                            line.endTime,
                                            wordOverlapMs,
                                            wordMinAnimationMs
                                        )
                                        val motionEndT = SyllableAnimator.getMotionWordEnd(
                                            startT,
                                            endT,
                                            line.endTime,
                                            wordMotionTrailFraction,
                                            wordMotionTrailMinMs,
                                            wordMotionTrailMaxMs,
                                            wordOverlapMs,
                                            wordMinAnimationMs,
                                            effectiveFloorMs,
                                            maxOverrunMs = holdMax + transitionDuration.toLong()
                                        )

                                        val sweepLinearProgress = when {
                                            wordGatePos >= effectiveEndT -> 1f
                                            wordGatePos <= startT -> 0f
                                            else -> {
                                                ((wordGatePos - startT).toFloat() / (effectiveEndT - startT).toFloat()).coerceIn(0f, 1f)
                                            }
                                        }

                                        val motionWindowMs = Math.max(1L, motionEndT - motionStartT)
                                        val motionLinearProgress = when {
                                            wordGatePos >= motionEndT -> 1f
                                            wordGatePos <= motionStartT -> 0f
                                            else -> {
                                                ((wordGatePos - motionStartT).toFloat() / motionWindowMs.toFloat()).coerceIn(0f, 1f)
                                            }
                                        }

                                        val fullWordEasedProgress = SyllableAnimator.getEasedProgress(sweepLinearProgress, word.text)

                                        val startProp = word.partStartProp
                                        val endProp = if (word.partEndProp == 0f) 1f else word.partEndProp

                                        val targetProgress = if (endProp > startProp) {
                                            ((fullWordEasedProgress - startProp) / (endProp - startProp)).coerceIn(0f, 1f)
                                        } else {
                                            fullWordEasedProgress
                                        }

                                        span.progress = targetProgress
                                        span.linearProgress = if (endProp > startProp) {
                                            ((sweepLinearProgress - startProp) / (endProp - startProp)).coerceIn(0f, 1f)
                                        } else {
                                            sweepLinearProgress
                                        }
                                        span.wholeWordLinearProgress = sweepLinearProgress.coerceIn(0f, 1f)
                                        span.motionProgress = motionLinearProgress.coerceIn(0f, 1f)
                                        span.motionWindowMs = motionWindowMs
                                        span.exitFade = 0f
                                        span.activeAlpha = 230
                                        span.inactiveAlpha = inactiveAlpha
                                    }
                                }
                            } else {
                                val fadeProgress = exitLinear.coerceIn(0f, 1f)
                                val currentAlpha = (230 - (230 - INACTIVE_LYRIC_ALPHA) * fadeProgress).toInt()

                                for (wordIndex in line.words.indices) {
                                    val word = line.words[wordIndex]
                                    val span = word.spanRef as? WordGradientSpan ?: continue

                                    val startT = if (word.fullStartTime == 0L) word.startTime else word.fullStartTime
                                    val endT = if (word.fullEndTime == 0L) word.endTime else word.fullEndTime
                                    val prevEndT = if (wordIndex > 0) {
                                        val prev = line.words[wordIndex - 1]
                                        if (prev.fullEndTime != 0L) prev.fullEndTime else prev.endTime
                                    } else {
                                        line.startTime
                                    }
                                    val motionStartT = SyllableAnimator.getMotionWordStart(
                                        startT,
                                        line.startTime,
                                        prevEndT,
                                        wordLeadInMs
                                    )
                                    val motionEndT = SyllableAnimator.getMotionWordEnd(
                                        startT,
                                        endT,
                                        line.endTime,
                                        wordMotionTrailFraction,
                                        wordMotionTrailMinMs,
                                        wordMotionTrailMaxMs,
                                        wordOverlapMs,
                                        wordMinAnimationMs,
                                        effectiveFloorMs,
                                        maxOverrunMs = holdMax + transitionDuration.toLong()
                                    )

                                    val motionWindowMs = Math.max(1L, motionEndT - motionStartT)
                                    val motionLinearProgress = when {
                                        adjustedPos >= motionEndT -> 1f
                                        adjustedPos <= motionStartT -> 0f
                                        else -> {
                                            ((adjustedPos - motionStartT).toFloat() / motionWindowMs.toFloat()).coerceIn(0f, 1f)
                                        }
                                    }

                                    span.progress = 1f
                                    span.linearProgress = 1f
                                    span.wholeWordLinearProgress = 1f
                                    span.motionProgress = motionLinearProgress.coerceIn(0f, 1f)
                                    span.motionWindowMs = if (span.motionProgress > 0f) motionWindowMs else 0L
                                    span.exitFade = easedExit
                                    span.activeAlpha = currentAlpha
                                    span.inactiveAlpha = INACTIVE_LYRIC_ALPHA
                                }
                            }

                            layout.paint.shader = null
                            layout.paint.color = Color.WHITE
                            layout.paint.alpha = 255

                            canvas.translate(centerX - layout.width / 2f, lineCenterY - (layout.height / 2f))
                            val hasSpans = line.words.any { it.spanRef != null }
                            if (!hasSpans) {
                                activeLineLayerBounds.set(0f, 0f, layout.width.toFloat(), layout.height.toFloat())
                                activeLineLayerPaint.alpha = INACTIVE_LYRIC_ALPHA
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
                    drawMetadataWithAlbumArt(canvas, width, height)
                    canvas.restore()
                } else {
                    drawMetadataWithAlbumArt(canvas, width, height)
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
            aspect: Float,
            prevArt: Bitmap? = null,
            prevAspect: Float = 1.0f,
            cardProgress: Float = 1.0f
        ) {
            val centerX = width / 2f
            val centerY = height / 2f
            
            // Apply scale effect
            val scale = 0.96f + (0.04f * viewAlpha)
            canvas.save()
            canvas.scale(scale, scale, centerX, centerY)

            val isCardFading = cardProgress < 1.0f && (prevArt != null || art != null)
            val effectiveArt = art ?: if (isCardFading) prevArt else null
            val effectiveAspect = if (art != null) aspect else prevAspect

            val albumW = if (effectiveArt != null) MetadataArtLayout.fittedWidth(width, height, effectiveAspect) else 0f
            val albumH = if (effectiveArt != null) MetadataArtLayout.fittedHeight(width, height, effectiveAspect) else 0f
            val hasTitle = tLayout.text.isNotEmpty()
            val hasArtist = aLayout.text.isNotEmpty()
            val titleHeight = if (hasTitle) tLayout.height else 0
            val artistHeight = if (hasArtist) aLayout.height else 0
            val albumTextGap = if (effectiveArt != null && (hasTitle || hasArtist)) width * 0.04f else 0f
            val metadataGap = if (hasTitle && hasArtist) 5.0f else 0f
            
            val totalHeight = albumH + albumTextGap + titleHeight + metadataGap + artistHeight
            var currentY = centerY - (totalHeight / 2f)
            
            // Draw Album Art
            val cornerRadius = prefAlbumCornerRadius
            if (isCardFading) {
                if (prevArt != null) {
                    val prevW = MetadataArtLayout.fittedWidth(width, height, prevAspect)
                    val prevH = MetadataArtLayout.fittedHeight(width, height, prevAspect)
                    val sameRect = art != null && prevW == albumW && prevH == albumH
                    prevAlbumArtRect.set(
                        centerX - prevW / 2f,
                        currentY + (albumH - prevH) / 2f,
                        centerX + prevW / 2f,
                        currentY + (albumH + prevH) / 2f
                    )
                    prevAlbumArtSrcRect.set(0, 0, prevArt.width, prevArt.height)
                    prevAlbumArtPaint.alpha = CardFade.outgoingAlphaInt(cardProgress, sameRect)
                    canvas.save()
                    albumArtPath.reset()
                    albumArtPath.addRoundRect(prevAlbumArtRect, cornerRadius, cornerRadius, Path.Direction.CW)
                    canvas.clipPath(albumArtPath)
                    canvas.drawBitmap(prevArt, prevAlbumArtSrcRect, prevAlbumArtRect, prevAlbumArtPaint)
                    canvas.restore()
                }
                if (art != null) {
                    albumArtRect.set(centerX - albumW / 2f, currentY, centerX + albumW / 2f, currentY + albumH)
                    albumArtSrcRect.set(0, 0, art.width, art.height)
                    albumArtPaint.alpha = CardFade.incomingAlphaInt(cardProgress)
                    canvas.save()
                    albumArtPath.reset()
                    albumArtPath.addRoundRect(albumArtRect, cornerRadius, cornerRadius, Path.Direction.CW)
                    canvas.clipPath(albumArtPath)
                    canvas.drawBitmap(art, albumArtSrcRect, albumArtRect, albumArtPaint)
                    canvas.restore()
                }
                currentY += albumH + albumTextGap
            } else {
                art?.let { bmp ->
                    albumArtRect.set(centerX - albumW / 2f, currentY, centerX + albumW / 2f, currentY + albumH)
                    albumArtSrcRect.set(0, 0, bmp.width, bmp.height)
                    albumArtPaint.alpha = 255
                    canvas.save()
                    albumArtPath.reset()
                    albumArtPath.addRoundRect(albumArtRect, cornerRadius, cornerRadius, Path.Direction.CW)
                    canvas.clipPath(albumArtPath)
                    canvas.drawBitmap(bmp, albumArtSrcRect, albumArtRect, albumArtPaint)
                    canvas.restore()
                    currentY += albumH + albumTextGap
                }
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

        private fun drawMetadataWithAlbumArt(canvas: Canvas, width: Float, height: Float) {
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

                val rawTitle = currentTitle ?: IdleScreenSettings.idleTitle(notificationAccessGranted, prefIdleTitle)
                val title = cleanTitle(rawTitle)
                metadataTitleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, metadataMaxTextWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED)
                    .build()
                
                val artist = currentArtist ?: IdleScreenSettings.idleSubtitle(notificationAccessGranted)
                metadataArtistLayout = StaticLayout.Builder.obtain(artist, 0, artist.length, artistPaintForMetadata, metadataMaxTextWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED)
                    .build()
            }

            if (metadataTransitionProgress < 1.0f) {
                val elapsed = SystemClock.elapsedRealtime() - metadataTransitionStartTime
                metadataTransitionProgress = CardFade.progressAt(elapsed)
            }

            if (cardFadeProgress < 1.0f) {
                val elapsed = SystemClock.elapsedRealtime() - cardFadeStartTime
                cardFadeProgress = CardFade.progressAt(elapsed)
                if (CardFade.isComplete(cardFadeProgress)) {
                    prevAlbumArt = null
                }
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
                    prevTitleLayout = null
                    prevArtistLayout = null
                    if (CardFade.isComplete(cardFadeProgress)) {
                        prevAlbumArt = null
                    }
                }
                drawMetadataLayouts(
                    canvas, width, height,
                    metadataTitleLayout!!, metadataArtistLayout!!,
                    albumArt, albumArtAspect,
                    prevAlbumArt, prevAlbumArtAspect,
                    cardFadeProgress
                )
            }
        }

        private fun drawSimpleLayout(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
            LyricsRenderer.drawSimpleLayout(canvas, layout, x, y)
        }

        private fun drawInstrumentalProgress(canvas: Canvas, layout: StaticLayout, progress: Float, position: Long, line: LyricLine) {
            LyricsRenderer.drawInstrumentalProgress(canvas, layout, progress, position, line)
        }

        private fun showToast(message: String) {
            if (!prefStatusToasts) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastToastTime < TOAST_COOLDOWN_MS) return
            lastToastTime = now
            mainHandler.post {
                android.widget.Toast.makeText(this@LyricsWallpaperService, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        private fun hasNotificationAccess(): Boolean {
            val cn = ComponentName(this@LyricsWallpaperService, NotificationService::class.java)
            val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            return flat != null && flat.contains(cn.flattenToString())
        }

        private fun triggerBgTransition(newBlurred: Bitmap, incomingGeneration: Int) {
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

            val decision = BgHandoff.decide(
                hasCurrentBg = currentBgArt != null,
                isTransitioning = isTransitioning,
                currentGen = targetBgGeneration,
                incomingGen = incomingGeneration
            )
            if (BuildFlags.DEBUG) {
                Log.d("Wallpaper", "Bg transition decision: $decision (gen=$incomingGeneration, targetGen=$targetBgGeneration)")
            }

            when (decision) {
                BgHandoffDecision.DROP_STALE -> {
                    newBlurred.recycle()
                }
                BgHandoffDecision.START_FRESH -> {
                    currentBgArt = newBlurred
                    nextBgArt = null
                    blendProgress = 0f
                    isTransitioning = false
                    accumulatedTime = timeOffset
                    currentSeedX = seedX
                    currentSeedY = seedY
                    targetBgGeneration = incomingGeneration
                }
                BgHandoffDecision.REPLACE_TARGET -> {
                    val oldTarget = nextBgArt
                    nextBgArt = newBlurred
                    oldTarget?.recycle()
                    targetBgGeneration = incomingGeneration
                }
                BgHandoffDecision.ADVANCE_AND_START -> {
                    currentBgArt?.recycle()
                    currentBgArt = nextBgArt
                    currentSeedX = nextSeedX
                    currentSeedY = nextSeedY
                    nextBgArt = newBlurred
                    blendProgress = 0f
                    isTransitioning = true
                    nextAccumulatedTime = timeOffset
                    nextSeedX = seedX
                    nextSeedY = seedY
                    targetBgGeneration = incomingGeneration
                }
                BgHandoffDecision.START_TRANSITION -> {
                    nextBgArt = newBlurred
                    blendProgress = 0f
                    isTransitioning = true
                    nextAccumulatedTime = timeOffset
                    nextSeedX = seedX
                    nextSeedY = seedY
                    targetBgGeneration = incomingGeneration
                }
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
