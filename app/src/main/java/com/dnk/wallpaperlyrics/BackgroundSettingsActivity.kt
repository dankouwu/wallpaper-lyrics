package com.dnk.wallpaperlyrics

import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.SharedPreferences
import android.net.Uri
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.LinearLayout
import android.view.Gravity
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import android.widget.TextView
import android.widget.EditText
import android.text.InputType
import android.view.View
import android.widget.Button
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.Toast
import java.util.Locale
import com.dnk.wallpaperlyrics.LyricsSettings as LS

class BackgroundSettingsActivity : AppCompatActivity() {

    private var palettePreview: ColorPalettePreviewView? = null
    private var previewView: BackgroundPreviewView? = null

    inner class ColorPalettePreviewView(context: Context) : LinearLayout(context) {
        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            refreshColors()
        }

        fun refreshColors() {
            Thread {
                val colors = getCurrentAlbumColors()
                runOnUiThread {
                    removeAllViews()
                    val size = LS.dpToPx(context, 14f)
                    val overlap = LS.dpToPx(context, -4f)

                    colors.forEachIndexed { idx, color ->
                        val dot = View(context).apply {
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(color)
                                setStroke(LS.dpToPx(context, 1f), Color.parseColor("#333333"))
                            }
                            layoutParams = LayoutParams(size, size).apply {
                                if (idx > 0) leftMargin = overlap
                            }
                        }
                        addView(dot)
                    }
                }
            }.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        try {
            val rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(LS.dpToPx(this@BackgroundSettingsActivity, 24f), LS.dpToPx(this@BackgroundSettingsActivity, 16f), LS.dpToPx(this@BackgroundSettingsActivity, 24f), LS.dpToPx(this@BackgroundSettingsActivity, 40f))
                setBackgroundColor(Color.parseColor("#242424"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }

            val headerLayout = android.widget.RelativeLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = LS.dpToPx(this@BackgroundSettingsActivity, 24f)
                    bottomMargin = LS.dpToPx(this@BackgroundSettingsActivity, 16f)
                }
            }

            val backButton = android.widget.ImageView(this).apply {
                val arrowDrawable = LS.CustomIconDrawable(this@BackgroundSettingsActivity, LS.IconType.ARROW_LEFT)
                setImageDrawable(arrowDrawable)
                val size = LS.dpToPx(this@BackgroundSettingsActivity, 48f)
                layoutParams = android.widget.RelativeLayout.LayoutParams(size, size).apply {
                    addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT)
                    addRule(android.widget.RelativeLayout.CENTER_VERTICAL)
                }
                setPadding(LS.dpToPx(this@BackgroundSettingsActivity, 12f), LS.dpToPx(this@BackgroundSettingsActivity, 12f), LS.dpToPx(this@BackgroundSettingsActivity, 12f), LS.dpToPx(this@BackgroundSettingsActivity, 12f))
                isClickable = true
                val outVal = TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outVal, true)
                setBackgroundResource(outVal.resourceId)
                setOnClickListener {
                    finish()
                }
            }
            headerLayout.addView(backButton)

            val titleView = TextView(this).apply {
                text = "Background & Display"
                textSize = 24f
                setTextColor(Color.WHITE)
                setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD))
                paint.isFakeBoldText = true
                gravity = Gravity.CENTER
                layoutParams = android.widget.RelativeLayout.LayoutParams(
                    android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    addRule(android.widget.RelativeLayout.CENTER_IN_PARENT)
                }
            }
            headerLayout.addView(titleView)
            rootLayout.addView(headerLayout)

            val bgPreview = BackgroundPreviewView(this@BackgroundSettingsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LS.dpToPx(this@BackgroundSettingsActivity, 200f)
                ).apply {
                    bottomMargin = LS.dpToPx(this@BackgroundSettingsActivity, 20f)
                }
                val initialRadius = prefs.getFloat("album_corner_radius", 48f)
                val initialSpeed = prefs.getFloat("bg_speed", 1.0f)
                val initialStatic = prefs.getBoolean("static_bg", false)
                val initialAccent = prefs.getInt(IdleScreenSettings.KEY_IDLE_ACCENT, IdleScreenSettings.DEFAULT_ACCENT)
                val initialBase = prefs.getInt(IdleScreenSettings.KEY_IDLE_BASE, IdleScreenSettings.DEFAULT_BASE)
                val initialMid = prefs.getInt(IdleScreenSettings.KEY_IDLE_MID, IdleScreenSettings.DEFAULT_MID)
                val initialHighlight = prefs.getInt(IdleScreenSettings.KEY_IDLE_HIGHLIGHT, IdleScreenSettings.DEFAULT_HIGHLIGHT)
                val initialSaturation = prefs.getFloat("bg_saturation", Tuning.chromaExponent)
                initSettings(
                    initialRadius,
                    initialSpeed,
                    initialStatic,
                    initialAccent,
                    initialBase,
                    initialMid,
                    initialHighlight,
                    initialSaturation
                )
            }
            previewView = bgPreview
            rootLayout.addView(bgPreview)

            fun addSectionHeader(title: String) {
                val header = TextView(this).apply {
                    text = title
                    textSize = 13f
                    setTextColor(Color.parseColor("#8E8E93"))
                    setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
                    setPadding(LS.dpToPx(this@BackgroundSettingsActivity, 12f), LS.dpToPx(this@BackgroundSettingsActivity, 24f), LS.dpToPx(this@BackgroundSettingsActivity, 12f), LS.dpToPx(this@BackgroundSettingsActivity, 8f))
                }
                rootLayout.addView(header)
            }

            addSectionHeader("Visual Style")
            val card1 = LS.SettingsCard(this).apply {
                val dynRow = LS.SettingsRow(
                    this@BackgroundSettingsActivity,
                    LS.IconType.PALETTE,
                    "Album Colours On System Theme",
                    "Recolour Quick Settings and the launcher to match the current album art",
                    LS.TrailingType.SWITCH,
                    prefs.getBoolean("dynamic_theming", false).toString(),
                    onCheckedChange = { checked ->
                        prefs.edit().putBoolean("dynamic_theming", checked).apply()
                    }
                )
                // Inject palette preview before the switch
                val preview = ColorPalettePreviewView(this@BackgroundSettingsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        rightMargin = LS.dpToPx(this@BackgroundSettingsActivity, 12f)
                    }
                }
                palettePreview = preview
                dynRow.addView(preview, dynRow.childCount - 1)
                addRow(dynRow)

                val initialRadius = prefs.getFloat("album_corner_radius", 48f).toInt()
                lateinit var radiusRow: LS.SettingsRow
                radiusRow = LS.SettingsRow(
                    this@BackgroundSettingsActivity,
                    LS.IconType.CORNER,
                    "Album Corner Radius",
                    "Modify corners from sharp to circular",
                    LS.TrailingType.VALUE,
                    "${initialRadius}dp",
                    onClick = {
                        val currentRadius = prefs.getFloat("album_corner_radius", 48f).toInt()
                        showCustomEditDialog(
                            "Set Corner Radius",
                            currentRadius.toString(),
                            0f,
                            120f,
                            false,
                            "dp",
                            onValuePreview = { previewRadius ->
                                previewView?.setCornerRadius(previewRadius)
                            },
                            onDismissWithoutSave = {
                                val savedRadius = prefs.getFloat("album_corner_radius", 48f)
                                previewView?.setCornerRadius(savedRadius)
                            }
                        ) { newVal ->
                            val radiusVal = newVal.toInt()
                            prefs.edit().putFloat("album_corner_radius", radiusVal.toFloat()).apply()
                            radiusRow.updateValue("${radiusVal}dp")
                            previewView?.setCornerRadius(radiusVal.toFloat())
                        }
                    }
                )
                addRow(radiusRow)

                addRow(LS.SettingsRow(
                    this@BackgroundSettingsActivity,
                    LS.IconType.IMAGE,
                    "Static Background",
                    "Render static blurred artwork without fluid animations",
                    LS.TrailingType.SWITCH,
                    prefs.getBoolean("static_bg", false).toString(),
                    onCheckedChange = { checked ->
                        prefs.edit().putBoolean("static_bg", checked).apply()
                        previewView?.setStaticBg(checked)
                    }
                ))
            }
            rootLayout.addView(card1)

            addSectionHeader("Performance")
            val card2 = LS.SettingsCard(this).apply {
                val initialSpeed = prefs.getFloat("bg_speed", 1.0f)
                lateinit var speedRow: LS.SettingsRow
                speedRow = LS.SettingsRow(
                    this@BackgroundSettingsActivity,
                    LS.IconType.GAUGE,
                    "Background Speed",
                    "Control velocity of background liquid",
                    LS.TrailingType.VALUE,
                    String.format("%.1fx", initialSpeed),
                    onClick = {
                        val currentSpeed = prefs.getFloat("bg_speed", 1.0f)
                        showCustomEditDialog(
                            "Set Fluid Speed",
                            String.format("%.1f", currentSpeed),
                            0.1f,
                            10.0f,
                            true,
                            "x",
                            onValuePreview = { previewSpeed ->
                                previewView?.setSpeed(previewSpeed)
                            },
                            onDismissWithoutSave = {
                                val savedSpeed = prefs.getFloat("bg_speed", 1.0f)
                                previewView?.setSpeed(savedSpeed)
                            }
                        ) { newVal ->
                            prefs.edit().putFloat("bg_speed", newVal).apply()
                            speedRow.updateValue(String.format("%.1fx", newVal))
                            previewView?.setSpeed(newVal)
                        }
                    }
                )
                addRow(speedRow)

                addRow(LS.SettingsRow(
                    this@BackgroundSettingsActivity,
                    LS.IconType.LIST_MUSIC,
                    "Album/Title/Artist Only Mode",
                    "Disable lyrics fetching and rendering to conserve battery and data",
                    LS.TrailingType.SWITCH,
                    prefs.getBoolean("metadata_only_mode", false).toString(),
                    onCheckedChange = { checked ->
                        prefs.edit().putBoolean("metadata_only_mode", checked).apply()
                    }
                ))
            }
            rootLayout.addView(card2)

            addSectionHeader("No Music Playing")
            val card3 = LS.SettingsCard(this).apply {
                fun formatIdleTextDisplay(text: String): String {
                    if (text.isEmpty()) return "None"
                    return if (text.length > 20) text.take(17) + "..." else text
                }

                fun createSwatch(color: Int): View {
                    val size = LS.dpToPx(this@BackgroundSettingsActivity, 14f)
                    return View(this@BackgroundSettingsActivity).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(color)
                            setStroke(LS.dpToPx(this@BackgroundSettingsActivity, 1f), Color.parseColor("#555555"))
                        }
                        layoutParams = LinearLayout.LayoutParams(size, size).apply {
                            rightMargin = LS.dpToPx(this@BackgroundSettingsActivity, 6f)
                        }
                    }
                }

                val currentStoredTitle = prefs.getString(IdleScreenSettings.KEY_IDLE_TITLE, null)
                val initialTitle = IdleScreenSettings.resolveIdleTitle(currentStoredTitle)
                lateinit var idleTextRow: LS.SettingsRow
                idleTextRow = LS.SettingsRow(
                    this@BackgroundSettingsActivity,
                    LS.IconType.EDIT,
                    "Idle Text",
                    "Shown when no music is playing",
                    LS.TrailingType.VALUE,
                    formatIdleTextDisplay(initialTitle),
                    onClick = {
                        val currentText = IdleScreenSettings.resolveIdleTitle(prefs.getString(IdleScreenSettings.KEY_IDLE_TITLE, null))
                        showTextEditDialog("Idle Text", currentText) { newText ->
                            prefs.edit().putString(IdleScreenSettings.KEY_IDLE_TITLE, newText).apply()
                            idleTextRow.updateValue(formatIdleTextDisplay(newText))
                        }
                    }
                )
                addRow(idleTextRow)

                fun reloadPreviewColors() {
                    val accent = prefs.getInt(IdleScreenSettings.KEY_IDLE_ACCENT, IdleScreenSettings.DEFAULT_ACCENT)
                    val base = prefs.getInt(IdleScreenSettings.KEY_IDLE_BASE, IdleScreenSettings.DEFAULT_BASE)
                    val mid = prefs.getInt(IdleScreenSettings.KEY_IDLE_MID, IdleScreenSettings.DEFAULT_MID)
                    val highlight = prefs.getInt(IdleScreenSettings.KEY_IDLE_HIGHLIGHT, IdleScreenSettings.DEFAULT_HIGHLIGHT)
                    previewView?.setIdleColors(accent, base, mid, highlight)
                    previewView?.forceIdleMode()
                }

                fun addColorRow(
                    title: String,
                    key: String,
                    defaultColor: Int
                ): Pair<LS.SettingsRow, View> {
                    val currentColor = prefs.getInt(key, defaultColor)
                    val swatch = createSwatch(currentColor)
                    lateinit var colorRow: LS.SettingsRow
                    colorRow = LS.SettingsRow(
                        this@BackgroundSettingsActivity,
                        LS.IconType.PALETTE,
                        title,
                        "",
                        LS.TrailingType.VALUE,
                        IdleScreenSettings.formatHexColor(currentColor),
                        onClick = {
                            val color = prefs.getInt(key, defaultColor)
                            showColorPickerDialog(title, color) { newColor ->
                                prefs.edit().putInt(key, newColor).apply()
                                colorRow.updateValue(IdleScreenSettings.formatHexColor(newColor))
                                (swatch.background as? GradientDrawable)?.setColor(newColor)
                                reloadPreviewColors()
                            }
                        }
                    )
                    colorRow.addView(swatch, colorRow.childCount - 1)
                    addRow(colorRow)
                    return Pair(colorRow, swatch)
                }

                val (accentRow, accentSwatch) = addColorRow("Accent", IdleScreenSettings.KEY_IDLE_ACCENT, IdleScreenSettings.DEFAULT_ACCENT)
                val (baseRow, baseSwatch) = addColorRow("Base", IdleScreenSettings.KEY_IDLE_BASE, IdleScreenSettings.DEFAULT_BASE)
                val (midRow, midSwatch) = addColorRow("Mid", IdleScreenSettings.KEY_IDLE_MID, IdleScreenSettings.DEFAULT_MID)
                val (highlightRow, highlightSwatch) = addColorRow("Highlight", IdleScreenSettings.KEY_IDLE_HIGHLIGHT, IdleScreenSettings.DEFAULT_HIGHLIGHT)

                fun updateColorViews(accent: Int, base: Int, mid: Int, highlight: Int) {
                    accentRow.updateValue(IdleScreenSettings.formatHexColor(accent))
                    (accentSwatch.background as? GradientDrawable)?.setColor(accent)

                    baseRow.updateValue(IdleScreenSettings.formatHexColor(base))
                    (baseSwatch.background as? GradientDrawable)?.setColor(base)

                    midRow.updateValue(IdleScreenSettings.formatHexColor(mid))
                    (midSwatch.background as? GradientDrawable)?.setColor(mid)

                    highlightRow.updateValue(IdleScreenSettings.formatHexColor(highlight))
                    (highlightSwatch.background as? GradientDrawable)?.setColor(highlight)

                    reloadPreviewColors()
                }

                fun applyIdleColors(accent: Int, base: Int, mid: Int, highlight: Int) {
                    prefs.edit().apply {
                        putInt(IdleScreenSettings.KEY_IDLE_ACCENT, accent)
                        putInt(IdleScreenSettings.KEY_IDLE_BASE, base)
                        putInt(IdleScreenSettings.KEY_IDLE_MID, mid)
                        putInt(IdleScreenSettings.KEY_IDLE_HIGHLIGHT, highlight)
                        apply()
                    }
                    updateColorViews(accent, base, mid, highlight)
                }

                fun extractAndApplyPalette(art: Bitmap, isFetchedBitmap: Boolean) {
                    kotlin.concurrent.thread(start = true) {
                        try {
                            val palette = AuroraRenderer.extractPalette(art)
                            mediaHandler.post {
                                isExtractingSongColors = false
                                if (!isFinishing && !isDestroyed) {
                                    applyIdleColors(palette.accent, palette.base, palette.mid, palette.highlight)
                                }
                            }
                        } catch (e: Exception) {
                            mediaHandler.post {
                                isExtractingSongColors = false
                            }
                        } finally {
                            // Metadata bitmaps belong to the media session: never recycle them.
                            if (isFetchedBitmap) {
                                art.recycle()
                            }
                        }
                    }
                }

                val songColorsRow = LS.SettingsRow(
                    this@BackgroundSettingsActivity,
                    LS.IconType.PALETTE,
                    "Use Song Colors",
                    currentSongColorsSubtitle(),
                    LS.TrailingType.NONE,
                    onClick = {
                        if (isExtractingSongColors) return@SettingsRow

                        val meta = activeMediaController?.metadata
                        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
                        if (meta == null || title.isNullOrBlank()) {
                            Toast.makeText(this@BackgroundSettingsActivity, "Nothing is playing", Toast.LENGTH_SHORT).show()
                            return@SettingsRow
                        }

                        val rawBitmap = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)

                        if (rawBitmap != null) {
                            isExtractingSongColors = true
                            extractAndApplyPalette(rawBitmap, isFetchedBitmap = false)
                        } else {
                            val uriStr = meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                                ?: meta.getString(MediaMetadata.METADATA_KEY_ART_URI)
                            if (uriStr.isNullOrBlank()) {
                                Toast.makeText(this@BackgroundSettingsActivity, "This song has no cover art", Toast.LENGTH_SHORT).show()
                                return@SettingsRow
                            }

                            isExtractingSongColors = true
                            lyricsManager.fetchBitmap(uriStr) { fetchedBitmap ->
                                if (fetchedBitmap == null) {
                                    mediaHandler.post {
                                        isExtractingSongColors = false
                                        if (!isFinishing && !isDestroyed) {
                                            Toast.makeText(this@BackgroundSettingsActivity, "This song has no cover art", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    return@fetchBitmap
                                }
                                extractAndApplyPalette(fetchedBitmap, isFetchedBitmap = true)
                            }
                        }
                    }
                )
                useSongColorsRow = songColorsRow
                addRow(songColorsRow)

                val resetRow = LS.SettingsRow(
                    this@BackgroundSettingsActivity,
                    LS.IconType.RELOAD,
                    "Reset Colors",
                    "Restore the built in palette",
                    LS.TrailingType.NONE,
                    onClick = {
                        prefs.edit().apply {
                            remove(IdleScreenSettings.KEY_IDLE_ACCENT)
                            remove(IdleScreenSettings.KEY_IDLE_BASE)
                            remove(IdleScreenSettings.KEY_IDLE_MID)
                            remove(IdleScreenSettings.KEY_IDLE_HIGHLIGHT)
                            apply()
                        }
                        updateColorViews(
                            IdleScreenSettings.DEFAULT_ACCENT,
                            IdleScreenSettings.DEFAULT_BASE,
                            IdleScreenSettings.DEFAULT_MID,
                            IdleScreenSettings.DEFAULT_HIGHLIGHT
                        )
                    }
                )
                addRow(resetRow)
            }
            rootLayout.addView(card3)

            addSectionHeader("Screen Off")
            val cardAod = LS.SettingsCard(this).apply {
                val currentAodPref = prefs.getString(AodMode.KEY, AodMode.toPref(AodMode.METADATA_AND_BACKGROUND)) ?: AodMode.toPref(AodMode.METADATA_AND_BACKGROUND)
                val aodDisplayValue = when (AodMode.fromPref(currentAodPref)) {
                    AodMode.METADATA_AND_BACKGROUND -> "Metadata and background"
                    AodMode.METADATA_ONLY -> "Metadata only"
                    AodMode.OFF -> "Off"
                }
                lateinit var aodRow: LS.SettingsRow
                aodRow = LS.SettingsRow(
                    this@BackgroundSettingsActivity,
                    LS.IconType.CLOCK,
                    "Always On Display",
                    "What shows while the screen is off",
                    LS.TrailingType.VALUE,
                    aodDisplayValue,
                    onClick = {
                        val activePref = prefs.getString(AodMode.KEY, AodMode.toPref(AodMode.METADATA_AND_BACKGROUND)) ?: AodMode.toPref(AodMode.METADATA_AND_BACKGROUND)
                        val options = listOf(
                            Pair("Metadata and background", "metadata_background"),
                            Pair("Metadata only", "metadata"),
                            Pair("Off", "off")
                        )

                        SettingsDialogs.showOptionPickerDialog(
                            this@BackgroundSettingsActivity,
                            "Always On Display",
                            options,
                            activePref
                        ) { selectedVal ->
                            prefs.edit().putString(AodMode.KEY, selectedVal).apply()
                            val newDisplayValue = when (AodMode.fromPref(selectedVal)) {
                                AodMode.METADATA_AND_BACKGROUND -> "Metadata and background"
                                AodMode.METADATA_ONLY -> "Metadata only"
                                AodMode.OFF -> "Off"
                            }
                            aodRow.updateValue(newDisplayValue)
                        }
                    }
                )
                addRow(aodRow)
            }
            rootLayout.addView(cardAod)

            val scrollView = android.widget.ScrollView(this).apply {
                isFillViewport = true
            }
            scrollView.addView(rootLayout)
            setContentView(scrollView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var useSongColorsRow: LS.SettingsRow? = null
    private var isExtractingSongColors = false
    private val lyricsManager by lazy { LyricsManager(applicationContext) }
    private var activeMediaController: MediaController? = null
    private var activeSessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private val mediaHandler = Handler(Looper.getMainLooper())
    // Playback state is cached so periodic position updates do not trigger session queries.
    private var lastPlaybackState: Int? = null
    private val wallpaperTrackListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == TrackResolution.KEY_WALLPAPER_TITLE || key == TrackResolution.KEY_WALLPAPER_ARTIST) {
            refreshMediaSession()
        }
    }

    private fun currentSongColorsSubtitle(metadata: MediaMetadata? = activeMediaController?.metadata): String {
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        return if (!title.isNullOrBlank()) "Match the song that is playing" else "Nothing is playing"
    }

    private fun updateUseSongColorsSubtitle(metadata: MediaMetadata? = activeMediaController?.metadata) {
        val subtitle = currentSongColorsSubtitle(metadata)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            useSongColorsRow?.updateSubtitle(subtitle)
        } else {
            mediaHandler.post { useSongColorsRow?.updateSubtitle(subtitle) }
        }
    }

    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (SettingsDialogs.isWallpaperActive(this@BackgroundSettingsActivity)) {
                refreshMediaSession()
            } else {
                previewView?.updateSong(hasNotificationAccess = true, metadata = metadata)
                updateUseSongColorsSubtitle(metadata)
            }
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val currentState = state?.state ?: PlaybackState.STATE_NONE
            if (currentState == lastPlaybackState) return
            lastPlaybackState = currentState
            refreshMediaSession()
        }

        override fun onSessionDestroyed() {
            refreshMediaSession()
        }
    }

    private fun hasNotificationAccess(): Boolean {
        val cn = ComponentName(this, NotificationService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    private fun startSessionObserver() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(wallpaperTrackListener)

        if (!hasNotificationAccess()) {
            previewView?.updateSong(hasNotificationAccess = false, metadata = null)
            updateUseSongColorsSubtitle(null)
            return
        }

        val componentName = ComponentName(this, NotificationService::class.java)
        val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return

        if (activeSessionsListener == null) {
            activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                selectController(controllers)
            }
        }
        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(
                activeSessionsListener!!,
                componentName,
                mediaHandler
            )
        } catch (e: SecurityException) {
            previewView?.updateSong(hasNotificationAccess = false, metadata = null)
            updateUseSongColorsSubtitle(null)
            return
        } catch (e: Exception) {}

        refreshMediaSession()
    }

    private fun refreshMediaSession() {
        if (!hasNotificationAccess()) {
            previewView?.updateSong(hasNotificationAccess = false, metadata = null)
            updateUseSongColorsSubtitle(null)
            return
        }
        val componentName = ComponentName(this, NotificationService::class.java)
        val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        if (mediaSessionManager == null) {
            previewView?.updateSong(hasNotificationAccess = true, metadata = null)
            updateUseSongColorsSubtitle(null)
            return
        }

        // Binder call getActiveSessions throws SecurityException without access and must stay off main thread.
        kotlin.concurrent.thread(start = true) {
            try {
                val controllers = mediaSessionManager.getActiveSessions(componentName)
                mediaHandler.post {
                    if (!isFinishing && !isDestroyed) {
                        selectController(controllers)
                    }
                }
            } catch (e: SecurityException) {
                mediaHandler.post {
                    previewView?.updateSong(hasNotificationAccess = false, metadata = null)
                    updateUseSongColorsSubtitle(null)
                }
            } catch (e: Exception) {
                mediaHandler.post {
                    previewView?.updateSong(hasNotificationAccess = true, metadata = null)
                    updateUseSongColorsSubtitle(null)
                }
            }
        }
    }

    private fun selectController(controllers: List<MediaController>?) {
        val controllersList = controllers ?: emptyList()
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val preferred = prefs.getString("preferred_media_player", "default") ?: "default"

        val candidates = controllersList.map { controller ->
            val meta = controller.metadata
            val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            val state = controller.playbackState?.state ?: PlaybackState.STATE_NONE
            MediaSessionChoice.Candidate(
                packageName = controller.packageName,
                hasUsableMetadata = !title.isNullOrBlank(),
                playbackState = state,
                isCurrent = controller.sessionToken == activeMediaController?.sessionToken,
                title = title,
                artist = artist
            )
        }
        val isWallpaperActive = SettingsDialogs.isWallpaperActive(this)
        val publishedTitle = prefs.getString(TrackResolution.KEY_WALLPAPER_TITLE, null)
        val publishedArtist = prefs.getString(TrackResolution.KEY_WALLPAPER_ARTIST, null)

        val chosen = MediaSessionChoice.chooseForPreview(
            candidates = candidates,
            preferred = preferred,
            isWallpaperActive = isWallpaperActive,
            publishedTitle = publishedTitle,
            publishedArtist = publishedArtist
        )
        val chosenIndex = if (chosen != null) candidates.indexOfFirst { it === chosen } else -1
        val newController = if (chosenIndex >= 0) controllersList.getOrNull(chosenIndex) else null

        if (newController?.sessionToken != activeMediaController?.sessionToken) {
            try {
                activeMediaController?.unregisterCallback(mediaControllerCallback)
            } catch (e: Exception) {}
            activeMediaController = newController
            lastPlaybackState = newController?.playbackState?.state
            try {
                newController?.registerCallback(mediaControllerCallback, mediaHandler)
            } catch (e: Exception) {}
        }

        previewView?.updateSong(hasNotificationAccess = true, metadata = newController?.metadata)
        updateUseSongColorsSubtitle(newController?.metadata)
    }

    private fun stopSessionObserver() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(wallpaperTrackListener)

        val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        activeSessionsListener?.let {
            try {
                mediaSessionManager?.removeOnActiveSessionsChangedListener(it)
            } catch (e: Exception) {}
        }
        activeSessionsListener = null

        try {
            activeMediaController?.unregisterCallback(mediaControllerCallback)
        } catch (e: Exception) {}
        activeMediaController = null
        lastPlaybackState = null
        updateUseSongColorsSubtitle(null)
    }

    override fun onResume() {
        super.onResume()
        palettePreview?.refreshColors()
        previewView?.onResume()
        startSessionObserver()
    }

    override fun onPause() {
        super.onPause()
        stopSessionObserver()
        previewView?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSessionObserver()
        previewView?.release()
        useSongColorsRow = null
    }

    private fun getCurrentAlbumColors(): List<Int> {
        val colors = mutableListOf<Int>()
        try {
            val componentName = ComponentName(this, NotificationService::class.java)
            val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val controllers = mediaSessionManager.getActiveSessions(componentName)
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            val preferred = prefs.getString("preferred_media_player", "default") ?: "default"
            val active = if (preferred == "default") {
                controllers.firstOrNull()
            } else {
                controllers.find {
                    val pkg = it.packageName.lowercase()
                    when (preferred) {
                        "spotify" -> pkg.contains("spotify")
                        "tidal" -> pkg.contains("tidal")
                        "kdeconnect" -> pkg.contains("kdeconnect")
                        else -> false
                    }
                }
            }
            if (active != null) {
                val metadata = active.metadata
                if (metadata != null) {
                    var art = metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                        ?: metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
                    if (art == null) {
                        val artUriStr = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                            ?: metadata.getString(android.media.MediaMetadata.METADATA_KEY_ART_URI)
                        if (!artUriStr.isNullOrBlank() && artUriStr.startsWith("content://")) {
                            try {
                                val uri = Uri.parse(artUriStr)
                                val inputStream = contentResolver.openInputStream(uri)
                                art = android.graphics.BitmapFactory.decodeStream(inputStream)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    if (art != null) {
                        val p = androidx.palette.graphics.Palette.from(art).generate()
                        val accent = p.vibrantSwatch?.rgb ?: p.dominantSwatch?.rgb
                        val mid = p.mutedSwatch?.rgb ?: p.lightVibrantSwatch?.rgb
                        val highlight = p.lightMutedSwatch?.rgb ?: p.darkVibrantSwatch?.rgb

                        if (accent != null) colors.add(accent)
                        if (mid != null) colors.add(mid)
                        if (highlight != null) colors.add(highlight)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (colors.size < 3) {
            colors.clear()
            colors.add(Color.parseColor("#30D158"))
            colors.add(Color.parseColor("#0A84FF"))
            colors.add(Color.parseColor("#BF5AF2"))
        }
        return colors
    }

    private fun showBaseInputDialog(
        title: String,
        initialVal: String,
        inputType: Int,
        minVal: Float? = null,
        maxVal: Float? = null,
        isFloat: Boolean = false,
        hint: String = "",
        onValuePreview: ((Float) -> Unit)? = null,
        onDismissWithoutSave: (() -> Unit)? = null,
        onSave: (String) -> Unit
    ) {
        var isSaved = false
        val dialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setCancelable(true)
            setOnDismissListener {
                if (!isSaved) {
                    onDismissWithoutSave?.invoke()
                }
            }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(LS.dpToPx(this@BackgroundSettingsActivity, 24f), LS.dpToPx(this@BackgroundSettingsActivity, 24f), LS.dpToPx(this@BackgroundSettingsActivity, 24f), LS.dpToPx(this@BackgroundSettingsActivity, 20f))
            // The slider is widened past this padding so its track lines up with the text,
            // which puts half the thumb in the padding at either end of its travel.
            clipToPadding = false
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = LS.dpToPx(this@BackgroundSettingsActivity, 16f).toFloat()
            }
        }

        val titleText = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(0, 0, 0, LS.dpToPx(this@BackgroundSettingsActivity, 8f))
        }
        container.addView(titleText)

        if (hint.isNotEmpty()) {
            val hintText = TextView(this).apply {
                text = hint
                setTextColor(Color.parseColor("#8E8E93"))
                textSize = 12f
            }
            container.addView(hintText)
        }

        val seekBar = if (minVal != null && maxVal != null) {
            val bar = LS.SettingsSlider(this).apply {
                max = 1000
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LS.dpToPx(this@BackgroundSettingsActivity, 48f)
                ).apply {
                    // The slider insets its track by the thumb radius, so the view is widened
                    // by the same amount to bring the track back out to the content edge.
                    marginStart = LS.dpToPx(this@BackgroundSettingsActivity, -10f)
                    marginEnd = LS.dpToPx(this@BackgroundSettingsActivity, -10f)
                    topMargin = LS.dpToPx(this@BackgroundSettingsActivity, 12f)
                    // Slider is 48dp for the touch target but draws a 4dp track, so the labels
                    // are pulled up into the slack rather than the view being shrunk
                    bottomMargin = LS.dpToPx(this@BackgroundSettingsActivity, -12f)
                }
                val parsedInitial = initialVal.toFloatOrNull()?.coerceIn(minVal, maxVal) ?: minVal
                progress = (((parsedInitial - minVal) / (maxVal - minVal)) * 1000f).toInt()
            }
            container.addView(bar)

            val rangeRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = LS.dpToPx(this@BackgroundSettingsActivity, 16f)
                }
                setPadding(0, 0, 0, 0)
            }

            val minText = TextView(this).apply {
                text = if (isFloat) "$minVal" else "${minVal.toInt()}"
                setTextColor(Color.parseColor("#8E8E93"))
                textSize = 13f
                gravity = Gravity.START
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            rangeRow.addView(minText)

            val maxText = TextView(this).apply {
                text = if (isFloat) "$maxVal" else "${maxVal.toInt()}"
                setTextColor(Color.parseColor("#8E8E93"))
                textSize = 13f
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            rangeRow.addView(maxText)

            container.addView(rangeRow)

            bar
        } else {
            null
        }

        val inputEdit = EditText(this).apply {
            setText(initialVal)
            this.inputType = inputType
            showSoftInputOnFocus = false
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(LS.dpToPx(this@BackgroundSettingsActivity, 16f), LS.dpToPx(this@BackgroundSettingsActivity, 12f), LS.dpToPx(this@BackgroundSettingsActivity, 16f), LS.dpToPx(this@BackgroundSettingsActivity, 12f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#242424"))
                cornerRadius = LS.dpToPx(this@BackgroundSettingsActivity, 10f).toFloat()
                setStroke(LS.dpToPx(this@BackgroundSettingsActivity, 1f), Color.parseColor("#444444"))
            }
            setSelection(text.length)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                textCursorDrawable = android.graphics.drawable.ColorDrawable(Color.parseColor("#b7b7b7"))
            }

            setOnClickListener {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
        container.addView(inputEdit)

        if (seekBar != null && minVal != null && maxVal != null) {
            var syncing = false

            seekBar.onProgressChanged = { progress, fromUser ->
                if (fromUser && !syncing) {
                    syncing = true
                    val value = minVal + (maxVal - minVal) * (progress / 1000f)
                    val formatted = if (isFloat) {
                        String.format(Locale.US, "%.1f", value)
                    } else {
                        value.toInt().toString()
                    }
                    inputEdit.setText(formatted)
                    inputEdit.setSelection(inputEdit.text.length)
                    onValuePreview?.invoke(value)
                    syncing = false
                }
            }

            inputEdit.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (!syncing) {
                        syncing = true
                        val parsed = s?.toString()?.toFloatOrNull()
                        if (parsed != null && parsed in minVal..maxVal) {
                            val progress = (((parsed - minVal) / (maxVal - minVal)) * 1000f).toInt()
                            seekBar.progress = progress
                            onValuePreview?.invoke(parsed)
                        }
                        syncing = false
                    }
                }
            })
        }

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, LS.dpToPx(this@BackgroundSettingsActivity, 20f), 0, 0)
        }

        val cancelButton = Button(this).apply {
            text = "Cancel"
            setTextColor(Color.parseColor("#8E8E93"))
            transformationMethod = null
            background = null
            setOnClickListener { dialog.dismiss() }
        }
        buttonLayout.addView(cancelButton)

        val saveButton = Button(this).apply {
            text = "Save"
            setTextColor(Color.parseColor("#E0E0E0"))
            transformationMethod = null
            background = null
            setOnClickListener {
                isSaved = true
                onSave(inputEdit.text.toString())
                dialog.dismiss()
            }
        }
        buttonLayout.addView(saveButton)
        container.addView(buttonLayout)

        dialog.setContentView(container)

        dialog.setOnShowListener {
            inputEdit.requestFocus()
        }

        dialog.window?.apply {
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.85f).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        dialog.show()
    }

    private fun showCustomEditDialog(
        title: String,
        initialVal: String,
        minVal: Float,
        maxVal: Float,
        isFloat: Boolean,
        unit: String,
        hint: String = "",
        onValuePreview: ((Float) -> Unit)? = null,
        onDismissWithoutSave: (() -> Unit)? = null,
        onValueSaved: (Float) -> Unit
    ) {
        val displayTitle = if (unit.isNotEmpty()) "$title ($unit)" else title
        val inputType = if (isFloat) {
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        } else {
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        showBaseInputDialog(
            displayTitle,
            initialVal,
            inputType,
            minVal,
            maxVal,
            isFloat,
            hint,
            onValuePreview = onValuePreview,
            onDismissWithoutSave = onDismissWithoutSave
        ) { textStr ->
            val floatVal = textStr.toFloatOrNull()
            if (floatVal != null) {
                val clamped = floatVal.coerceIn(minVal, maxVal)
                onValueSaved(clamped)
            }
        }
    }

    private fun showTextEditDialog(
        title: String,
        initialVal: String,
        onValueSaved: (String) -> Unit
    ) {
        showBaseInputDialog(title, initialVal, InputType.TYPE_CLASS_TEXT) { textStr ->
            onValueSaved(textStr)
        }
    }

    private fun showColorPickerDialog(
        title: String,
        initialColor: Int,
        onColorSaved: (Int) -> Unit
    ) {
        SettingsDialogs.showColorPickerDialog(this, title, initialColor, onColorSaved)
    }
}
