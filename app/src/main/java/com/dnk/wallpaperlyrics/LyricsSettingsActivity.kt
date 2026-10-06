package com.dnk.wallpaperlyrics

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dnk.wallpaperlyrics.LyricsSettings as LS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LyricsSettingsActivity : AppCompatActivity() {

    private val lyricsStorage by lazy { LyricsStorage.forContext(this) }
    private lateinit var prefs: SharedPreferences
    private lateinit var contentContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        try {
            val rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(
                    LS.dpToPx(this@LyricsSettingsActivity, 24f),
                    LS.dpToPx(this@LyricsSettingsActivity, 16f),
                    LS.dpToPx(this@LyricsSettingsActivity, 24f),
                    LS.dpToPx(this@LyricsSettingsActivity, 40f)
                )
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
                    topMargin = LS.dpToPx(this@LyricsSettingsActivity, 24f)
                    bottomMargin = LS.dpToPx(this@LyricsSettingsActivity, 16f)
                }
            }

            val backButton = android.widget.ImageView(this).apply {
                val arrowDrawable = LS.CustomIconDrawable(this@LyricsSettingsActivity, LS.IconType.ARROW_LEFT)
                setImageDrawable(arrowDrawable)
                val size = LS.dpToPx(this@LyricsSettingsActivity, 48f)
                layoutParams = android.widget.RelativeLayout.LayoutParams(size, size).apply {
                    addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT)
                    addRule(android.widget.RelativeLayout.CENTER_VERTICAL)
                }
                setPadding(
                    LS.dpToPx(this@LyricsSettingsActivity, 12f),
                    LS.dpToPx(this@LyricsSettingsActivity, 12f),
                    LS.dpToPx(this@LyricsSettingsActivity, 12f),
                    LS.dpToPx(this@LyricsSettingsActivity, 12f)
                )
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
                text = "Lyrics"
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

            contentContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            rootLayout.addView(contentContainer)

            buildUi()

            val scrollView = ScrollView(this).apply {
                isFillViewport = true
            }
            scrollView.addView(rootLayout)
            setContentView(scrollView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun addSectionHeader(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 13f
            setTextColor(Color.parseColor("#8E8E93"))
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(
                LS.dpToPx(this@LyricsSettingsActivity, 12f),
                LS.dpToPx(this@LyricsSettingsActivity, 16f),
                LS.dpToPx(this@LyricsSettingsActivity, 12f),
                LS.dpToPx(this@LyricsSettingsActivity, 8f)
            )
        }
    }

    private fun buildUi() {
        contentContainer.addView(addSectionHeader("Source"))
        val sourceCard = LS.SettingsCard(this).apply {
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
                    this@LyricsSettingsActivity,
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

                        SettingsDialogs.showOptionPickerDialog(
                            this@LyricsSettingsActivity,
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

            addRow(LS.SettingsRow(
                this@LyricsSettingsActivity,
                LS.IconType.LIST_MUSIC,
                "Custom Lyrics Provider",
                "Configure custom local/remote API override",
                LS.TrailingType.CHEVRON,
                onClick = {
                    startActivity(Intent(this@LyricsSettingsActivity, EnhancedLyricsActivity::class.java))
                }
            ))
        }
        contentContainer.addView(sourceCard)

        contentContainer.addView(addSectionHeader("Maintenance"))
        val maintenanceCard = LS.SettingsCard(this).apply {
            addRow(LS.SettingsRow(
                this@LyricsSettingsActivity,
                LS.IconType.RELOAD,
                "Force Re-fetch Lyrics",
                "Purge cache and reload active song. Hold to pick a provider",
                LS.TrailingType.CHEVRON,
                onClick = {
                    sendBroadcast(Intent("com.dnk.wallpaperlyrics.FORCE_RELOAD_LYRICS").apply {
                        setPackage(packageName)
                    })
                    Toast.makeText(this@LyricsSettingsActivity, "Re-fetch command sent", Toast.LENGTH_SHORT).show()
                },
                onLongClick = {
                    openProviderPicker()
                    true
                }
            ))

            addRow(LS.SettingsRow(
                this@LyricsSettingsActivity,
                LS.IconType.EDIT,
                "Edit Synced Lyrics",
                "Override cached lyrics of current song manually",
                LS.TrailingType.CHEVRON,
                onClick = {
                    openLyricsOverrideFlow()
                }
            ))

            addRow(LS.SettingsRow(
                this@LyricsSettingsActivity,
                LS.IconType.DELETE,
                "Clear Lyrics Cache",
                "Delete cached lyrics; preserves hand-edited overrides",
                LS.TrailingType.NONE,
                onClick = {
                    SettingsDialogs.showCustomConfirmDialog(
                        this@LyricsSettingsActivity,
                        "Clear Lyrics Cache?",
                        "Proceeding will delete all cached lyrics from providers. Hand-edited overrides will not be deleted.",
                        "Clear Cache"
                    ) {
                        CoroutineScope(Dispatchers.IO).launch {
                            lyricsStorage.clearCache()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@LyricsSettingsActivity, "Cache cleared!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            ))

            addRow(LS.SettingsRow(
                this@LyricsSettingsActivity,
                LS.IconType.DELETE,
                "Clear Saved Overrides",
                "Permanently delete all hand-edited lyric overrides",
                LS.TrailingType.NONE,
                onClick = {
                    SettingsDialogs.showCustomConfirmDialog(
                        this@LyricsSettingsActivity,
                        "Clear Saved Overrides?",
                        "Proceeding will delete all hand-edited lyric overrides. This action cannot be undone.",
                        "Clear Overrides"
                    ) {
                        CoroutineScope(Dispatchers.IO).launch {
                            lyricsStorage.clearAllOverrides()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@LyricsSettingsActivity, "Saved overrides cleared!", Toast.LENGTH_SHORT).show()
                            }
                            sendBroadcast(Intent("com.dnk.wallpaperlyrics.RELOAD_LYRICS").apply {
                                setPackage(packageName)
                            })
                        }
                    }
                }
            ))
        }
        contentContainer.addView(maintenanceCard)
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

    private fun openLyricsOverrideFlow() {
        val active = SettingsDialogs.getActiveSongMetadata(this)
        if (active == null) {
            if (!SettingsDialogs.isNotificationServiceEnabled(this)) {
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
                                Toast.makeText(this@LyricsSettingsActivity, "Override cleared!", Toast.LENGTH_SHORT).show()
                            }
                            sendBroadcast(Intent("com.dnk.wallpaperlyrics.RELOAD_LYRICS").apply {
                                setPackage(packageName)
                            })
                        } else {
                            val parsed = LyricsManager.parseLrcText(newLrc, isAuthoritative = true)
                            if (parsed == null) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@LyricsSettingsActivity, "Invalid LRC format. No lines parsed.", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                val saved = lyricsStorage.saveOverride(title, artist, parsed)
                                withContext(Dispatchers.Main) {
                                    if (saved) {
                                        Toast.makeText(this@LyricsSettingsActivity, "Lyrics saved!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(this@LyricsSettingsActivity, "Failed to save lyrics", Toast.LENGTH_SHORT).show()
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
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(true)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(LS.dpToPx(this@LyricsSettingsActivity, 24f), LS.dpToPx(this@LyricsSettingsActivity, 24f), LS.dpToPx(this@LyricsSettingsActivity, 24f), LS.dpToPx(this@LyricsSettingsActivity, 20f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = LS.dpToPx(this@LyricsSettingsActivity, 16f).toFloat()
            }
        }

        val titleText = TextView(this).apply {
            text = "Override Synced Lyrics"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(0, 0, 0, LS.dpToPx(this@LyricsSettingsActivity, 4f))
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
            setPadding(0, 0, 0, LS.dpToPx(this@LyricsSettingsActivity, 16f))
        }
        container.addView(subtitleText)

        val inputEdit = EditText(this).apply {
            setText(initialLrc)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(Color.WHITE)
            textSize = 14f
            isVerticalScrollBarEnabled = true
            gravity = Gravity.TOP or Gravity.START
            setPadding(LS.dpToPx(this@LyricsSettingsActivity, 16f), LS.dpToPx(this@LyricsSettingsActivity, 12f), LS.dpToPx(this@LyricsSettingsActivity, 16f), LS.dpToPx(this@LyricsSettingsActivity, 12f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#242424"))
                cornerRadius = LS.dpToPx(this@LyricsSettingsActivity, 10f).toFloat()
            }

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LS.dpToPx(this@LyricsSettingsActivity, 220f)
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                textCursorDrawable = ColorDrawable(Color.parseColor("#b7b7b7"))
            }
        }
        container.addView(inputEdit)

        val tipText = TextView(this).apply {
            text = "Format: [minutes:seconds.centiseconds] lyric line\nExample: [00:15.50] In the beginning..."
            textSize = 11f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, LS.dpToPx(this@LyricsSettingsActivity, 8f), 0, 0)
        }
        container.addView(tipText)

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, LS.dpToPx(this@LyricsSettingsActivity, 16f), 0, 0)
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
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(inputEdit, InputMethodManager.SHOW_IMPLICIT)
        }

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.88f).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        dialog.show()
    }

    private fun openProviderPicker() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val customEnabled = prefs.getBoolean("custom_lyrics_enabled", false)
        val customEndpoint = prefs.getString("custom_lyrics_endpoint", null)
        val options = LyricsProviders.getPickerOptions(customEnabled, customEndpoint)
        SettingsDialogs.showOptionPickerDialog(
            activity = this,
            title = "Fetch from provider",
            options = options,
            currentValue = "",
            onOptionSelected = { providerId ->
                sendBroadcast(Intent("com.dnk.wallpaperlyrics.FETCH_LYRICS_FROM_PROVIDER").apply {
                    setPackage(packageName)
                    putExtra("provider", providerId)
                })
            }
        )
    }
}
