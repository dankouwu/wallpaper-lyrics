package com.dnk.wallpaperlyrics

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dnk.wallpaperlyrics.LyricsSettings as LS

class TimingActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var contentContainer: LinearLayout
    private lateinit var songOffsetRow: LS.SettingsRow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        try {
            val rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(
                    LS.dpToPx(this@TimingActivity, 24f),
                    LS.dpToPx(this@TimingActivity, 16f),
                    LS.dpToPx(this@TimingActivity, 24f),
                    LS.dpToPx(this@TimingActivity, 40f)
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
                    topMargin = LS.dpToPx(this@TimingActivity, 24f)
                    bottomMargin = LS.dpToPx(this@TimingActivity, 16f)
                }
            }

            val backButton = android.widget.ImageView(this).apply {
                val arrowDrawable = LS.CustomIconDrawable(this@TimingActivity, LS.IconType.ARROW_LEFT)
                setImageDrawable(arrowDrawable)
                val size = LS.dpToPx(this@TimingActivity, 48f)
                layoutParams = android.widget.RelativeLayout.LayoutParams(size, size).apply {
                    addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT)
                    addRule(android.widget.RelativeLayout.CENTER_VERTICAL)
                }
                setPadding(
                    LS.dpToPx(this@TimingActivity, 12f),
                    LS.dpToPx(this@TimingActivity, 12f),
                    LS.dpToPx(this@TimingActivity, 12f),
                    LS.dpToPx(this@TimingActivity, 12f)
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
                text = "Timing & Sync"
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
                LS.dpToPx(this@TimingActivity, 12f),
                LS.dpToPx(this@TimingActivity, 16f),
                LS.dpToPx(this@TimingActivity, 12f),
                LS.dpToPx(this@TimingActivity, 8f)
            )
        }
    }

    private fun buildUi() {
        contentContainer.addView(addSectionHeader("Offsets"))
        val card = LS.SettingsCard(this).apply {
            val initialOffset = prefs.getInt("sync_offset", 0)
            lateinit var offsetRow: LS.SettingsRow
            offsetRow = LS.SettingsRow(
                this@TimingActivity,
                LS.IconType.CLOCK,
                "Manual Sync Offset",
                "Offset lyrics alignment manually",
                LS.TrailingType.VALUE,
                "${initialOffset}ms",
                onClick = {
                    val currentOffset = prefs.getInt("sync_offset", 0)
                    SettingsDialogs.showCustomEditDialog(
                        this@TimingActivity,
                        "Set Sync Offset",
                        currentOffset.toString(),
                        -1000f,
                        1000f,
                        false,
                        "ms",
                        hint = "Negative shows lyrics earlier. Positive delays them."
                    ) { newVal ->
                        val offsetVal = newVal.toInt()
                        prefs.edit().putInt("sync_offset", offsetVal).apply()
                        offsetRow.updateValue("${offsetVal}ms")
                    }
                }
            )
            addRow(offsetRow)

            val activeSong = SettingsDialogs.getActiveSongMetadata(this@TimingActivity)
            val (initSubtitle, initVal) = if (activeSong != null) {
                val (title, artist) = activeSong
                val songOffset = prefs.getInt("song_delay_${title}_${artist}", 0)
                Pair("Offset for: $title - $artist", "${songOffset}ms")
            } else {
                Pair("No active song playing", "0ms")
            }
            songOffsetRow = LS.SettingsRow(
                this@TimingActivity,
                LS.IconType.CLOCK,
                "Song Specific Delay",
                initSubtitle,
                LS.TrailingType.VALUE,
                initVal,
                onClick = {
                    val active = SettingsDialogs.getActiveSongMetadata(this@TimingActivity)
                    if (active == null) {
                        Toast.makeText(this@TimingActivity, "No active music session found", Toast.LENGTH_SHORT).show()
                    } else {
                        val (title, artist) = active
                        val songKey = "song_delay_${title}_${artist}"
                        val currentSongOffset = prefs.getInt(songKey, 0)
                        SettingsDialogs.showCustomEditDialog(
                            this@TimingActivity,
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

            val bluetoothOffsetRow = LS.SettingsRow(
                this@TimingActivity,
                LS.IconType.BLUETOOTH,
                "Bluetooth Offsets",
                "A separate offset per paired device",
                LS.TrailingType.CHEVRON,
                onClick = {
                    startActivity(Intent(this@TimingActivity, BluetoothOffsetActivity::class.java))
                }
            )
            addRow(bluetoothOffsetRow)
        }
        contentContainer.addView(card)
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
