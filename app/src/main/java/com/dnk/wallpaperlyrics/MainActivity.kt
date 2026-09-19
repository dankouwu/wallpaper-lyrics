package com.dnk.wallpaperlyrics

import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.net.Uri
import android.media.session.MediaSessionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.view.Gravity
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.widget.EditText
import android.text.InputType
import android.view.View
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import com.dnk.wallpaperlyrics.LyricsSettings as LS
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val lyricsStorage by lazy { LyricsStorage.forContext(this) }
    private lateinit var notificationRow: LS.SettingsRow
    private lateinit var wallpaperRow: LS.SettingsRow
    private lateinit var batteryRow: LS.SettingsRow
    private lateinit var songOffsetRow: LS.SettingsRow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        if (BuildFlags.DEBUG) {
            Tuning.load(this)
        }

        try {
            val rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(LS.dpToPx(this@MainActivity, 24f), LS.dpToPx(this@MainActivity, 16f), LS.dpToPx(this@MainActivity, 24f), LS.dpToPx(this@MainActivity, 40f))
                setBackgroundColor(Color.parseColor("#242424"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }

            val titleView = TextView(this).apply {
                text = "Wallpaper Lyrics"
                textSize = 26f
                setTextColor(Color.WHITE)
                setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD))
                paint.isFakeBoldText = true
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = LS.dpToPx(this@MainActivity, 24f)
                    bottomMargin = LS.dpToPx(this@MainActivity, 16f)
                }
            }
            rootLayout.addView(titleView)

            fun addSectionHeader(title: String, textColor: Int = Color.parseColor("#8E8E93")) {
                val header = TextView(this).apply {
                    text = title
                    textSize = 13f
                    setTextColor(textColor)
                    setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
                    setPadding(LS.dpToPx(this@MainActivity, 12f), LS.dpToPx(this@MainActivity, 24f), LS.dpToPx(this@MainActivity, 12f), LS.dpToPx(this@MainActivity, 8f))
                }
                rootLayout.addView(header)
            }

            addSectionHeader("System Access")
            val card1 = LS.SettingsCard(this).apply {
                notificationRow = LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.BELL,
                    "Notification Access",
                    "Required to read music session details",
                    LS.TrailingType.CHEVRON,
                    onClick = {
                        try {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Could not open settings", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                addRow(notificationRow)

                wallpaperRow = LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.IMAGE,
                    "Activate Live Wallpaper",
                    "Choose this wallpaper in picker menu",
                    LS.TrailingType.CHEVRON,
                    onClick = {
                        try {
                            val intent = Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                            intent.putExtra(android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                android.content.ComponentName(this@MainActivity, LyricsWallpaperService::class.java))
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Could not open wallpaper picker", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                addRow(wallpaperRow)

                batteryRow = LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.GAUGE,
                    "Battery Unrestricted",
                    "Required so the wallpaper is not throttled",
                    LS.TrailingType.CHEVRON,
                    onClick = {
                        try {
                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Could not open battery settings", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                addRow(batteryRow)
            }
            rootLayout.addView(card1)

            addSectionHeader("General")
            val card2 = LS.SettingsCard(this).apply {
                addRow(LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.SLIDERS,
                    "Background Settings",
                    "Configure liquid wallpaper visuals",
                    LS.TrailingType.CHEVRON,
                    onClick = {
                        startActivity(Intent(this@MainActivity, BackgroundSettingsActivity::class.java))
                    }
                ))

                addRow(LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.LIST_MUSIC,
                    "Custom Lyrics Provider",
                    "Configure custom local/remote API override",
                    LS.TrailingType.CHEVRON,
                    onClick = {
                        startActivity(Intent(this@MainActivity, EnhancedLyricsActivity::class.java))
                    }
                ))

                val persistentNotifRow = LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.BELL,
                    "Persistent Controls",
                    "Show control notification in status bar",
                    LS.TrailingType.SWITCH,
                    initialVal = prefs.getBoolean("persistent_notification", false).toString(),
                    onCheckedChange = { isChecked ->
                        prefs.edit().putBoolean("persistent_notification", isChecked).apply()
                    }
                )
                addRow(persistentNotifRow)

                addRow(LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.INFO,
                    "Status Messages",
                    "Toast when lyrics are fetched or missing",
                    LS.TrailingType.SWITCH,
                    initialVal = prefs.getBoolean(LS.KEY_STATUS_TOASTS, true).toString(),
                    onCheckedChange = { isChecked ->
                        prefs.edit().putBoolean(LS.KEY_STATUS_TOASTS, isChecked).apply()
                    }
                ))

                val showPlayerSelection = listOf(isSpotifyInstalled(), isTidalInstalled(), isKdeConnectInstalled()).count { it } >= 1
                if (showPlayerSelection) {
                    val currentPref = prefs.getString("preferred_media_player", "default") ?: "default"
                    val displayValue = when (currentPref) {
                        "spotify" -> "Spotify"
                        "tidal" -> "Tidal"
                        "kdeconnect" -> "KDE Connect"
                        else -> "Default"
                    }
                    lateinit var playerRow: LS.SettingsRow
                    playerRow = LS.SettingsRow(
                        this@MainActivity,
                        LS.IconType.SQUARE_PLAY,
                        "Preferred Media Player",
                        "Track lyrics from a specific player",
                        LS.TrailingType.VALUE,
                        displayValue,
                        onClick = {
                            val activePref = prefs.getString("preferred_media_player", "default") ?: "default"
                            val options = mutableListOf(Pair("Default", "default"))
                            if (isSpotifyInstalled()) options.add(Pair("Spotify", "spotify"))
                            if (isTidalInstalled()) options.add(Pair("Tidal", "tidal"))
                            if (isKdeConnectInstalled()) options.add(Pair("KDE Connect", "kdeconnect"))

                            showOptionPickerDialog(
                                "Preferred Media Player",
                                options,
                                activePref
                            ) { selectedVal ->
                                prefs.edit().putString("preferred_media_player", selectedVal).apply()
                                val newDisplayValue = when (selectedVal) {
                                    "spotify" -> "Spotify"
                                    "tidal" -> "Tidal"
                                    "kdeconnect" -> "KDE Connect"
                                    else -> "Default"
                                }
                                playerRow.updateValue(newDisplayValue)
                            }
                        }
                    )
                    addRow(playerRow)
                }

                val currentAodPref = prefs.getString(AodMode.KEY, AodMode.toPref(AodMode.METADATA_AND_BACKGROUND)) ?: AodMode.toPref(AodMode.METADATA_AND_BACKGROUND)
                val aodDisplayValue = when (AodMode.fromPref(currentAodPref)) {
                    AodMode.METADATA_AND_BACKGROUND -> "Metadata and background"
                    AodMode.METADATA_ONLY -> "Metadata only"
                    AodMode.OFF -> "Off"
                }
                lateinit var aodRow: LS.SettingsRow
                aodRow = LS.SettingsRow(
                    this@MainActivity,
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

                        showOptionPickerDialog(
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
            rootLayout.addView(card2)

            addSectionHeader("Timing & Speed")
            val card3 = LS.SettingsCard(this).apply {
                val initialOffset = prefs.getInt("sync_offset", 0)
                lateinit var offsetRow: LS.SettingsRow
                offsetRow = LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.CLOCK,
                    "Manual Sync Offset",
                    "Offset lyrics alignment manually",
                    LS.TrailingType.VALUE,
                    "${initialOffset}ms",
                    onClick = {
                        val currentOffset = prefs.getInt("sync_offset", 0)
                        showCustomEditDialog("Set Sync Offset", currentOffset.toString(), -1000f, 1000f, false, "ms", hint = "Negative shows lyrics earlier. Positive delays them.") { newVal ->
                            val offsetVal = newVal.toInt()
                            prefs.edit().putInt("sync_offset", offsetVal).apply()
                            offsetRow.updateValue("${offsetVal}ms")
                        }
                    }
                )
                addRow(offsetRow)

                val bluetoothOffsetRow = LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.BLUETOOTH,
                    "Bluetooth Offsets",
                    "A separate offset per paired device",
                    LS.TrailingType.CHEVRON,
                    onClick = {
                        startActivity(Intent(this@MainActivity, BluetoothOffsetActivity::class.java))
                    }
                )
                addRow(bluetoothOffsetRow)

                val activeSong = getActiveSongMetadata()
                val (initSubtitle, initVal) = if (activeSong != null) {
                    val (title, artist) = activeSong
                    val songOffset = prefs.getInt("song_delay_${title}_${artist}", 0)
                    Pair("Offset for: $title - $artist", "${songOffset}ms")
                } else {
                    Pair("No active song playing", "0ms")
                }
                songOffsetRow = LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.CLOCK,
                    "Song Specific Delay",
                    initSubtitle,
                    LS.TrailingType.VALUE,
                    initVal,
                    onClick = {
                        val active = getActiveSongMetadata()
                        if (active == null) {
                            Toast.makeText(this@MainActivity, "No active music session found", Toast.LENGTH_SHORT).show()
                        } else {
                            val (title, artist) = active
                            val songKey = "song_delay_${title}_${artist}"
                            val currentSongOffset = prefs.getInt(songKey, 0)
                            showCustomEditDialog(
                                "Set Delay for ${title}",
                                currentSongOffset.toString(),
                                -10000f,
                                10000f,
                                false,
                                "ms"
                            ) { newVal ->
                                val offsetVal = newVal.toInt()
                                prefs.edit().putInt(songKey, offsetVal).apply()
                                songOffsetRow.updateValue("${offsetVal}ms")
                            }
                        }
                    }
                )
                addRow(songOffsetRow)
            }
            rootLayout.addView(card3)

            addSectionHeader("Maintenance")
            val card4 = LS.SettingsCard(this).apply {
                addRow(LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.RELOAD,
                    "Force Re-fetch Lyrics",
                    "Purge cache and reload active song from server",
                    LS.TrailingType.CHEVRON,
                    onClick = {
                        sendBroadcast(Intent("com.dnk.wallpaperlyrics.FORCE_RELOAD_LYRICS").apply {
                            setPackage(packageName)
                        })
                        Toast.makeText(this@MainActivity, "Re-fetch command sent", Toast.LENGTH_SHORT).show()
                    }
                ))

                addRow(LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.EDIT,
                    "Edit Synced Lyrics",
                    "Override cached lyrics of current song manually",
                    LS.TrailingType.CHEVRON,
                    onClick = {
                        openLyricsOverrideFlow()
                    }
                ))

                addRow(LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.DELETE,
                    "Clear Lyrics Cache",
                    "Delete cached lyrics; preserves hand-edited overrides",
                    LS.TrailingType.NONE,
                    onClick = {
                        showCustomConfirmDialog(
                            "Clear Lyrics Cache?",
                            "Proceeding will delete all cached lyrics from providers. Hand-edited overrides will not be deleted.",
                            "Clear Cache"
                        ) {
                            CoroutineScope(Dispatchers.IO).launch {
                                lyricsStorage.clearCache()
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@MainActivity, "Cache cleared!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                ))

                addRow(LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.DELETE,
                    "Clear Saved Overrides",
                    "Permanently delete all hand-edited lyric overrides",
                    LS.TrailingType.NONE,
                    onClick = {
                        showCustomConfirmDialog(
                            "Clear Saved Overrides?",
                            "Proceeding will delete all hand-edited lyric overrides. This action cannot be undone.",
                            "Clear Overrides"
                        ) {
                            CoroutineScope(Dispatchers.IO).launch {
                                lyricsStorage.clearAllOverrides()
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@MainActivity, "Saved overrides cleared!", Toast.LENGTH_SHORT).show()
                                }
                                sendBroadcast(Intent("com.dnk.wallpaperlyrics.RELOAD_LYRICS").apply {
                                    setPackage(packageName)
                                })
                            }
                        }
                    }
                ))
            }
            rootLayout.addView(card4)

            addSectionHeader("About")
            val card5 = LS.SettingsCard(this).apply {
                addRow(LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.FILE_STACK,
                    "Version",
                    "2.0.0",
                    LS.TrailingType.NONE,
                    onClick = {}
                ))

                addRow(LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.GITHUB,
                    "GitHub Repository",
                    "View source code and star the project",
                    LS.TrailingType.CHEVRON,
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/dankouwu/wallpaper-lyrics"))
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Could not open repository link", Toast.LENGTH_SHORT).show()
                        }
                    }
                ))

                addRow(LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.BUG,
                    "Report an Issue",
                    "Submit bugs or request new features",
                    LS.TrailingType.CHEVRON,
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/dankouwu/wallpaper-lyrics/issues"))
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Could not open issue link", Toast.LENGTH_SHORT).show()
                        }
                    }
                ))

                addRow(LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.COPYRIGHT,
                    "License",
                    "Apache License 2.0 terms",
                    LS.TrailingType.CHEVRON,
                    onClick = {
                        showAboutDialog(
                            "Apache License 2.0",
                            "Copyright 2026 dankouwu\n\nLicensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. You may obtain a copy of the License at:\n\nhttp://www.apache.org/licenses/LICENSE-2.0\n\nUnless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License."
                        )
                    }
                ))

                addRow(LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.INFO,
                    "Credits & Libraries",
                    "Developer details and open-source packages",
                    LS.TrailingType.CHEVRON,
                    onClick = {
                        showAboutDialog(
                            "Credits & Libraries",
                            "Developers & Contributors:\n- dankouwu\n- riveerxd\n\nLibraries Used:\n- AndroidX core & components\n- Gson (Google JSON serializer)\n- LRCLIB (Synced lyrics provider)\n- Lucide Icons (lucide.dev)\n\nThank you for using Wallpaper Lyrics!"
                        )
                    }
                ))
            }
            rootLayout.addView(card5)

            if (BuildFlags.DEBUG) {
                addSectionHeader("Debug (Debug Build Only)", Color.parseColor("#F59E0B"))
                val debugCard = LS.SettingsCard(this).apply {
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#282015"))
                        setStroke(LS.dpToPx(this@MainActivity, 1f), Color.parseColor("#5C4012"))
                        cornerRadius = LS.dpToPx(this@MainActivity, 18f).toFloat()
                    }
                    addRow(LS.SettingsRow(
                        this@MainActivity,
                        LS.IconType.SLIDERS,
                        "Live Parameter Tuning",
                        "Adjust word motion curves and rendering values",
                        LS.TrailingType.CHEVRON,
                        onClick = {
                            try {
                                startActivity(Intent().setClassName(this@MainActivity, "com.dnk.wallpaperlyrics.DebugTuningActivity"))
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "Could not open tuning screen", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ))
                }
                rootLayout.addView(debugCard)
            }

            val scrollView = android.widget.ScrollView(this).apply {
                isFillViewport = true
            }
            scrollView.addView(rootLayout)
            setContentView(scrollView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val cn = ComponentName(this, NotificationService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    private fun isWallpaperActive(): Boolean {
        val wpm = android.app.WallpaperManager.getInstance(this)
        val info = wpm.wallpaperInfo
        return info != null && info.packageName == packageName
    }

    private fun isBatteryUnrestricted(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (::notificationRow.isInitialized) {
            if (isNotificationServiceEnabled()) {
                notificationRow.setTrailing(LS.TrailingType.CHECK)
                notificationRow.updateSubtitle("Granted")
            } else {
                notificationRow.setTrailing(LS.TrailingType.CHEVRON)
                notificationRow.updateSubtitle("Required to read music session details")
            }
        }
        if (::wallpaperRow.isInitialized) {
            if (isWallpaperActive()) {
                wallpaperRow.setTrailing(LS.TrailingType.CHECK)
                wallpaperRow.updateSubtitle("Active")
            } else {
                wallpaperRow.setTrailing(LS.TrailingType.CHEVRON)
                wallpaperRow.updateSubtitle("Choose this wallpaper in picker menu")
            }
        }
        if (::batteryRow.isInitialized) {
            if (isBatteryUnrestricted()) {
                batteryRow.setTrailing(LS.TrailingType.CHECK)
                batteryRow.updateSubtitle("Unrestricted")
            } else {
                batteryRow.setTrailing(LS.TrailingType.CHEVRON)
                batteryRow.updateSubtitle("Required so the wallpaper is not throttled")
            }
        }
        if (::songOffsetRow.isInitialized) {
            val activeSong = getActiveSongMetadata()
            if (activeSong != null) {
                val (title, artist) = activeSong
                val songKey = "song_delay_${title}_${artist}"
                val currentSongOffset = prefs.getInt(songKey, 0)
                songOffsetRow.updateSubtitle("Offset for: $title - $artist")
                songOffsetRow.updateValue("${currentSongOffset}ms")
            } else {
                songOffsetRow.updateSubtitle("No active song playing")
                songOffsetRow.updateValue("0ms")
            }
        }
    }

    private fun getActiveSongMetadata(): TrackResolution.ResolvedTrack? {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val pubTitle = prefs.getString(TrackResolution.KEY_WALLPAPER_TITLE, null)
        val pubArtist = prefs.getString(TrackResolution.KEY_WALLPAPER_ARTIST, null)

        return TrackResolution.resolveTrack(pubTitle, pubArtist) {
            resolveSessionTrack()
        }
    }

    private fun resolveSessionTrack(): Pair<String, String>? {
        try {
            val componentName = ComponentName(this, NotificationService::class.java)
            val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val controllers = mediaSessionManager.getActiveSessions(componentName)
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            val preferred = prefs.getString("preferred_media_player", "default") ?: "default"
            val candidates = controllers.map { controller ->
                val meta = controller.metadata
                val title = meta?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
                val state = controller.playbackState?.state ?: MediaSessionChoice.STATE_NONE
                MediaSessionChoice.Candidate(
                    packageName = controller.packageName,
                    hasUsableMetadata = !title.isNullOrBlank(),
                    playbackState = state
                ) to controller
            }
            val chosen = MediaSessionChoice.choose(candidates.map { it.first }, preferred)
            val active = candidates.firstOrNull { it.first == chosen }?.second
            if (active != null) {
                val metadata = active.metadata
                val title = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
                val artist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
                if (!title.isNullOrBlank() && !artist.isNullOrBlank()) {
                    return Pair(title.trim(), artist.trim())
                }
            }
        } catch (e: Exception) {
            // Ignore security/listener exception or any issues, return null
        }
        return null
    }

    private fun showCustomEditDialog(
        title: String,
        initialVal: String,
        minVal: Float,
        maxVal: Float,
        isFloat: Boolean,
        unit: String,
        hint: String = "",
        onValueSaved: (Float) -> Unit
    ) {
        val dialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setCancelable(true)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(LS.dpToPx(this@MainActivity, 24f), LS.dpToPx(this@MainActivity, 24f), LS.dpToPx(this@MainActivity, 24f), LS.dpToPx(this@MainActivity, 20f))
            // The slider is widened past this padding so its track lines up with the text,
            // which puts half the thumb in the padding at either end of its travel.
            clipToPadding = false
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = LS.dpToPx(this@MainActivity, 16f).toFloat()
            }
        }

        val titleText = TextView(this).apply {
            text = if (unit.isNotEmpty()) "$title ($unit)" else title
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(0, 0, 0, LS.dpToPx(this@MainActivity, 8f))
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

        val seekBar = LS.SettingsSlider(this).apply {
            max = 1000
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LS.dpToPx(this@MainActivity, 48f)
            ).apply {
                // The slider insets its track by the thumb radius, so the view is widened
                // by the same amount to bring the track back out to the content edge.
                marginStart = LS.dpToPx(this@MainActivity, -10f)
                marginEnd = LS.dpToPx(this@MainActivity, -10f)
                topMargin = LS.dpToPx(this@MainActivity, 12f)
                // Slider is 48dp for the touch target but draws a 4dp track, so the labels
                // are pulled up into the slack rather than the view being shrunk
                bottomMargin = LS.dpToPx(this@MainActivity, -12f)
            }
            val parsedInitial = initialVal.toFloatOrNull()?.coerceIn(minVal, maxVal) ?: minVal
            progress = (((parsedInitial - minVal) / (maxVal - minVal)) * 1000f).toInt()
        }
        container.addView(seekBar)

        val rangeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = LS.dpToPx(this@MainActivity, 16f)
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

        val inputEdit = EditText(this).apply {
            setText(initialVal)
            inputType = if (isFloat) {
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            } else {
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            }
            showSoftInputOnFocus = false
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(LS.dpToPx(this@MainActivity, 16f), LS.dpToPx(this@MainActivity, 12f), LS.dpToPx(this@MainActivity, 16f), LS.dpToPx(this@MainActivity, 12f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#242424"))
                cornerRadius = LS.dpToPx(this@MainActivity, 10f).toFloat()
                setStroke(LS.dpToPx(this@MainActivity, 1f), Color.parseColor("#444444"))
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

        var syncing = false

        seekBar.onProgressChanged = { progress, fromUser ->
            if (fromUser && !syncing) {
                syncing = true
                val value = minVal + (maxVal - minVal) * (progress / 1000f)
                val formatted = if (isFloat) {
                    String.format("%.1f", value)
                } else {
                    value.toInt().toString()
                }
                inputEdit.setText(formatted)
                inputEdit.setSelection(inputEdit.text.length)
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
                    }
                    syncing = false
                }
            }
        })

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, LS.dpToPx(this@MainActivity, 20f), 0, 0)
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
                val textStr = inputEdit.text.toString()
                val floatVal = textStr.toFloatOrNull()
                if (floatVal != null) {
                    val clamped = floatVal.coerceIn(minVal, maxVal)
                    onValueSaved(clamped)
                }
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

    private fun showAboutDialog(title: String, message: String) {
        val dialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setCancelable(true)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(LS.dpToPx(this@MainActivity, 24f), LS.dpToPx(this@MainActivity, 24f), LS.dpToPx(this@MainActivity, 24f), LS.dpToPx(this@MainActivity, 20f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = LS.dpToPx(this@MainActivity, 16f).toFloat()
            }
        }

        val titleText = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(0, 0, 0, LS.dpToPx(this@MainActivity, 10f))
        }
        container.addView(titleText)

        val msgText = TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, 0, 0, LS.dpToPx(this@MainActivity, 20f))
        }
        container.addView(msgText)

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val closeButton = Button(this).apply {
            text = "Close"
            setTextColor(Color.parseColor("#E0E0E0"))
            transformationMethod = null
            background = null
            setOnClickListener { dialog.dismiss() }
        }
        buttonLayout.addView(closeButton)
        container.addView(buttonLayout)

        dialog.setContentView(container)

        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.85f).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        dialog.show()
    }

    private fun showCustomConfirmDialog(
        title: String,
        message: String,
        confirmText: String,
        onConfirm: () -> Unit
    ) {
        val dialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setCancelable(true)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(LS.dpToPx(this@MainActivity, 24f), LS.dpToPx(this@MainActivity, 24f), LS.dpToPx(this@MainActivity, 24f), LS.dpToPx(this@MainActivity, 20f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = LS.dpToPx(this@MainActivity, 16f).toFloat()
            }
        }

        val titleText = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(0, 0, 0, LS.dpToPx(this@MainActivity, 10f))
        }
        container.addView(titleText)

        val msgText = TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, 0, 0, LS.dpToPx(this@MainActivity, 20f))
        }
        container.addView(msgText)

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val cancelButton = Button(this).apply {
            text = "Cancel"
            setTextColor(Color.parseColor("#8E8E93"))
            transformationMethod = null
            background = null
            setOnClickListener { dialog.dismiss() }
        }
        buttonLayout.addView(cancelButton)

        val confirmButton = Button(this).apply {
            text = confirmText
            setTextColor(Color.parseColor("#FF453A"))
            transformationMethod = null
            background = null
            setOnClickListener {
                onConfirm()
                dialog.dismiss()
            }
        }
        buttonLayout.addView(confirmButton)
        container.addView(buttonLayout)

        dialog.setContentView(container)

        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.85f).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        dialog.show()
    }

    private fun openLyricsOverrideFlow() {
        val active = getActiveSongMetadata()
        if (active == null) {
            if (!isNotificationServiceEnabled()) {
                Toast.makeText(this, "Notification permission required", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No active track metadata found", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val (title, artist) = active
        CoroutineScope(Dispatchers.IO).launch {
            val existingLrc = lyricsStorage.getLyricsForEdit(title, artist)
            withContext(Dispatchers.Main) {
                showLyricsEditDialog(title, artist, existingLrc, isFallback = active.isFallback) { newLrc ->
                    CoroutineScope(Dispatchers.IO).launch {
                        if (newLrc.isBlank()) {
                            lyricsStorage.clearOverride(title, artist)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "Override cleared!", Toast.LENGTH_SHORT).show()
                            }
                            sendBroadcast(Intent("com.dnk.wallpaperlyrics.RELOAD_LYRICS").apply {
                                setPackage(packageName)
                            })
                        } else {
                            val parsed = LyricsManager.parseLrcText(newLrc, isAuthoritative = true)
                            if (parsed == null) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@MainActivity, "Invalid LRC format. No lines parsed.", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                val saved = lyricsStorage.saveOverride(title, artist, parsed)
                                withContext(Dispatchers.Main) {
                                    if (saved) {
                                        Toast.makeText(this@MainActivity, "Lyrics saved!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(this@MainActivity, "Failed to save lyrics", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                sendBroadcast(Intent("com.dnk.wallpaperlyrics.RELOAD_LYRICS").apply {
                                    setPackage(packageName)
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showLyricsEditDialog(
        songTitle: String,
        songArtist: String,
        initialLrc: String,
        isFallback: Boolean = false,
        onLrcSaved: (String) -> Unit
    ) {
        val dialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setCancelable(true)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(LS.dpToPx(this@MainActivity, 24f), LS.dpToPx(this@MainActivity, 24f), LS.dpToPx(this@MainActivity, 24f), LS.dpToPx(this@MainActivity, 20f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = LS.dpToPx(this@MainActivity, 16f).toFloat()
            }
        }

        val titleText = TextView(this).apply {
            text = "Override Synced Lyrics"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(0, 0, 0, LS.dpToPx(this@MainActivity, 4f))
        }
        container.addView(titleText)

        val subtitleText = TextView(this).apply {
            text = if (isFallback) {
                "$songTitle - $songArtist (from media session)"
            } else {
                "$songTitle - $songArtist"
            }
            textSize = 13f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, 0, 0, LS.dpToPx(this@MainActivity, 16f))
        }
        container.addView(subtitleText)

        val inputEdit = EditText(this).apply {
            setText(initialLrc)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(Color.WHITE)
            textSize = 14f
            isVerticalScrollBarEnabled = true
            gravity = Gravity.TOP or Gravity.START
            setPadding(LS.dpToPx(this@MainActivity, 16f), LS.dpToPx(this@MainActivity, 12f), LS.dpToPx(this@MainActivity, 16f), LS.dpToPx(this@MainActivity, 12f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#242424"))
                cornerRadius = LS.dpToPx(this@MainActivity, 10f).toFloat()
            }

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LS.dpToPx(this@MainActivity, 220f)
            )

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                textCursorDrawable = android.graphics.drawable.ColorDrawable(Color.parseColor("#b7b7b7"))
            }
        }
        container.addView(inputEdit)

        val tipText = TextView(this).apply {
            text = "Format: [minutes:seconds.centiseconds] lyric line\nExample: [00:15.50] In the beginning..."
            textSize = 11f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, LS.dpToPx(this@MainActivity, 8f), 0, 0)
        }
        container.addView(tipText)

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, LS.dpToPx(this@MainActivity, 16f), 0, 0)
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
                onLrcSaved(inputEdit.text.toString())
                dialog.dismiss()
            }
        }
        buttonLayout.addView(saveButton)
        container.addView(buttonLayout)

        dialog.setContentView(container)

        dialog.setOnShowListener {
            inputEdit.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(inputEdit, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }

        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.88f).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        dialog.show()
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun isSpotifyInstalled(): Boolean {
        return isPackageInstalled("com.spotify.music") || isPackageInstalled("com.spotify.lite")
    }

    private fun isTidalInstalled(): Boolean {
        return isPackageInstalled("com.aspiro.tidal")
    }

    private fun isKdeConnectInstalled(): Boolean {
        return isPackageInstalled("org.kde.kdeconnect_tp")
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun showOptionPickerDialog(
        title: String,
        options: List<Pair<String, String>>,
        currentValue: String,
        onOptionSelected: (String) -> Unit
    ) {
        val dialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setCancelable(true)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(LS.dpToPx(this@MainActivity, 24f), LS.dpToPx(this@MainActivity, 24f), LS.dpToPx(this@MainActivity, 24f), LS.dpToPx(this@MainActivity, 20f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = LS.dpToPx(this@MainActivity, 16f).toFloat()
            }
        }

        val titleText = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(0, 0, 0, LS.dpToPx(this@MainActivity, 16f))
        }
        container.addView(titleText)

        options.forEach { (displayName, value) ->
            val optionLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val p12 = LS.dpToPx(this@MainActivity, 12f)
                val p16 = LS.dpToPx(this@MainActivity, 16f)
                setPadding(p16, p12, p16, p12)
                isClickable = true

                val outVal = TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, outVal, true)
                setBackgroundResource(outVal.resourceId)

                setOnClickListener {
                    onOptionSelected(value)
                    dialog.dismiss()
                }
            }

            val iconType = when (value) {
                "spotify" -> LS.IconType.SPOTIFY
                "tidal" -> LS.IconType.TIDAL
                "kdeconnect" -> LS.IconType.KDECONNECT
                else -> LS.IconType.LIST_MUSIC
            }

            val iconView = View(this).apply {
                background = LS.CustomIconDrawable(this@MainActivity, iconType)
                layoutParams = LinearLayout.LayoutParams(LS.dpToPx(this@MainActivity, 24f), LS.dpToPx(this@MainActivity, 24f)).apply {
                    rightMargin = LS.dpToPx(this@MainActivity, 16f)
                }
            }
            optionLayout.addView(iconView)

            val optionText = TextView(this).apply {
                text = displayName
                textSize = 16f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            optionLayout.addView(optionText)

            if (value == currentValue) {
                val checkView = android.widget.ImageView(this).apply {
                    val checkDrawable = LS.CustomIconDrawable(this@MainActivity, LS.IconType.CHECK)
                    setImageDrawable(checkDrawable)
                    layoutParams = LinearLayout.LayoutParams(LS.dpToPx(this@MainActivity, 20f), LS.dpToPx(this@MainActivity, 20f))
                }
                optionLayout.addView(checkView)
            }

            container.addView(optionLayout)
        }

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, LS.dpToPx(this@MainActivity, 16f), 0, 0)
        }

        val cancelButton = Button(this).apply {
            text = "Cancel"
            setTextColor(Color.parseColor("#8E8E93"))
            transformationMethod = null
            background = null
            setOnClickListener { dialog.dismiss() }
        }
        buttonLayout.addView(cancelButton)
        container.addView(buttonLayout)

        dialog.setContentView(container)

        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.85f).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        dialog.show()
    }
}
