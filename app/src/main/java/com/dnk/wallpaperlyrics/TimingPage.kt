package com.dnk.wallpaperlyrics

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.dnk.wallpaperlyrics.LyricsSettings as LS

class TimingPage(activity: MainActivity) : SettingsPage(activity) {

    private lateinit var prefs: SharedPreferences
    private lateinit var contentContainer: LinearLayout
    private lateinit var songOffsetRow: LS.SettingsRow
    private lateinit var scrollView: ScrollView
    private var offsetDialog: android.app.Dialog? = null

    override fun onCreate() {

        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        try {
            val rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(
                    LS.dpToPx(this@TimingPage, 24f),
                    LS.dpToPx(this@TimingPage, 16f),
                    LS.dpToPx(this@TimingPage, 24f),
                    LS.dpToPx(this@TimingPage, 40f)
                )
                setBackgroundColor(Color.parseColor("#242424"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }

            val headerLayout = LS.pageHeader(this, "Timing & Sync") { finish() }
            rootLayout.addView(headerLayout)

            contentContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            rootLayout.addView(contentContainer)

            scrollView = ScrollView(this).apply {
                isFillViewport = true
            }
            scrollView.addView(rootLayout)
            buildUi()
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
                LS.dpToPx(this@TimingPage, 12f),
                LS.dpToPx(this@TimingPage, 16f),
                LS.dpToPx(this@TimingPage, 12f),
                LS.dpToPx(this@TimingPage, 8f)
            )
        }
    }

    private fun buildUi() {
        contentContainer.addView(addSectionHeader("Offsets"))
        val card = LS.SettingsCard(this).apply {
            val initialOffset = prefs.getInt("sync_offset", 0)
            lateinit var offsetRow: LS.SettingsRow
            offsetRow = LS.SettingsRow(
                this@TimingPage,
                LS.IconType.CLOCK,
                "Manual Sync Offset",
                "Offset lyrics alignment manually",
                LS.TrailingType.VALUE,
                "${initialOffset}ms",
                onClick = {
                    val currentOffset = prefs.getInt("sync_offset", 0)
                    offsetDialog = SettingsDialogs.showCustomEditDialog(
                        activity,
                        "Set Sync Offset",
                        currentOffset.toString(),
                        -1000f,
                        1000f,
                        false,
                        "ms",
                        hint = "Negative shows lyrics earlier. Positive delays them.",
                        previewBehind = scrollView
                    ) { newVal ->
                        val offsetVal = newVal.toInt()
                        prefs.edit().putInt("sync_offset", offsetVal).apply()
                        offsetRow.updateValue("${offsetVal}ms")
                    }
                }
            )
            addRow(offsetRow)

            val activeSong = SettingsDialogs.getActiveSongMetadata(this@TimingPage)
            val (initSubtitle, initVal) = if (activeSong != null) {
                val (title, artist) = activeSong
                val songOffset = prefs.getInt("song_delay_${title}_${artist}", 0)
                Pair("Offset for: $title - $artist", "${songOffset}ms")
            } else {
                Pair("No active song playing", "0ms")
            }
            songOffsetRow = LS.SettingsRow(
                this@TimingPage,
                LS.IconType.CLOCK,
                "Song Specific Delay",
                initSubtitle,
                LS.TrailingType.VALUE,
                initVal,
                onClick = {
                    val active = SettingsDialogs.getActiveSongMetadata(this@TimingPage)
                    if (active == null) {
                        Toast.makeText(this@TimingPage, "No active music session found", Toast.LENGTH_SHORT).show()
                    } else {
                        val (title, artist) = active
                        val songKey = "song_delay_${title}_${artist}"
                        val currentSongOffset = prefs.getInt(songKey, 0)
                        offsetDialog = SettingsDialogs.showCustomEditDialog(
                            activity,
                            "Set Delay for ${title}",
                            currentSongOffset.toString(),
                            -10000f,
                            10000f,
                            false,
                            "ms",
                            previewBehind = scrollView
                        ) { newVal ->
                            val offsetVal = newVal.toInt()
                            prefs.edit().putInt(songKey, offsetVal).apply()
                            songOffsetRow.updateValue("${offsetVal}ms")
                        }
                    }
                }
            )
            addRow(songOffsetRow)

            val bluetoothOffsetRow = LS.SettingsRow(
                this@TimingPage,
                LS.IconType.BLUETOOTH,
                "Bluetooth Offsets",
                "A separate offset per paired device",
                LS.TrailingType.CHEVRON,
                onClick = {
                    activity.openPage(BluetoothOffsetPage(activity))
                }
            )
            addRow(bluetoothOffsetRow)
        }
        contentContainer.addView(card)
    }

    // The preview delta lives in memory, so it must not outlive the dialog.
    override fun onStop() {
        super.onStop()
        offsetDialog?.dismiss()
    }

    override fun onResume() {
        super.onResume()
        if (::songOffsetRow.isInitialized) {
            val activeSong = SettingsDialogs.getActiveSongMetadata(this)
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
}
