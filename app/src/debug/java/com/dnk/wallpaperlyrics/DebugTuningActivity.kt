package com.dnk.wallpaperlyrics

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/**
 * Debug-only activity hosting live parameter tuning sliders.
 * Reached from the debug section in MainActivity settings.
 */
class DebugTuningActivity : Activity() {

    private class ParamRowViews(
        val param: Tuning.Tunable,
        val valueView: TextView,
        val slider: LyricsSettings.SettingsSlider
    )

    private val boundViews = mutableListOf<ParamRowViews>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load overrides to ensure UI reflects persisted state
        Tuning.load(this)

        window.statusBarColor = Color.parseColor("#1C1C1E")
        window.navigationBarColor = Color.parseColor("#1C1C1E")

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1C1C1E"))
            setPadding(
                LyricsSettings.dpToPx(this@DebugTuningActivity, 16f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 16f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 16f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 32f)
            )
        }

        // Top navigation and action bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, LyricsSettings.dpToPx(this@DebugTuningActivity, 16f))
        }

        val backButton = View(this).apply {
            background = LyricsSettings.CustomIconDrawable(this@DebugTuningActivity, LyricsSettings.IconType.ARROW_LEFT)
            layoutParams = LinearLayout.LayoutParams(
                LyricsSettings.dpToPx(this@DebugTuningActivity, 24f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 24f)
            ).apply {
                rightMargin = LyricsSettings.dpToPx(this@DebugTuningActivity, 16f)
            }
            setOnClickListener { finish() }
        }
        topBar.addView(backButton)

        val titleView = TextView(this).apply {
            text = "Debug Tuning"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topBar.addView(titleView)

        val copyButton = TextView(this).apply {
            text = "Copy Kotlin"
            textSize = 13f
            setTextColor(Color.parseColor("#1C1C1E"))
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(
                LyricsSettings.dpToPx(this@DebugTuningActivity, 12f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 6f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 12f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 6f)
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F59E0B"))
                cornerRadius = LyricsSettings.dpToPx(this@DebugTuningActivity, 14f).toFloat()
            }
            setOnClickListener {
                copyKotlinToClipboard()
            }
        }
        topBar.addView(copyButton)

        rootLayout.addView(topBar)

        val paletteHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                LyricsSettings.dpToPx(this@DebugTuningActivity, 4f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 16f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 4f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 8f)
            )
        }

        val paletteTitle = TextView(this).apply {
            text = "PALETTE VERSION"
            textSize = 13f
            setTextColor(Color.parseColor("#F59E0B"))
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        paletteHeaderRow.addView(paletteTitle)

        val paletteCard = LyricsSettings.SettingsCard(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#282015"))
                setStroke(LyricsSettings.dpToPx(this@DebugTuningActivity, 1f), Color.parseColor("#5C4012"))
                cornerRadius = LyricsSettings.dpToPx(this@DebugTuningActivity, 18f).toFloat()
            }
        }

        val paletteRowContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                LyricsSettings.dpToPx(this@DebugTuningActivity, 16f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 12f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 16f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 12f)
            )
        }

        val versionEditText = EditText(this).apply {
            isSingleLine = true
            maxLines = 1
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
            hint = "Empty uses ${BuildConfig.VERSION_NAME}"
            setHintTextColor(Color.parseColor("#8E8E93"))
            setTextColor(Color.WHITE)
            textSize = 14f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1C1C1E"))
                setStroke(LyricsSettings.dpToPx(this@DebugTuningActivity, 1f), Color.parseColor("#443A2A"))
                cornerRadius = LyricsSettings.dpToPx(this@DebugTuningActivity, 8f).toFloat()
            }
            setPadding(
                LyricsSettings.dpToPx(this@DebugTuningActivity, 12f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 8f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 12f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 8f)
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setText(Tuning.paletteVersionOverride)
            setSelection(text.length)
        }
        paletteRowContainer.addView(versionEditText)

        val swatchesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = LyricsSettings.dpToPx(this@DebugTuningActivity, 10f)
            }
        }

        val swatchViews = Array(4) {
            val size = LyricsSettings.dpToPx(this@DebugTuningActivity, 16f)
            View(this@DebugTuningActivity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(LyricsSettings.dpToPx(this@DebugTuningActivity, 1f), Color.parseColor("#555555"))
                }
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    rightMargin = LyricsSettings.dpToPx(this@DebugTuningActivity, 6f)
                }
            }
        }
        for (swatch in swatchViews) {
            swatchesContainer.addView(swatch)
        }

        val schemeTextView = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#E5E5EA"))
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = LyricsSettings.dpToPx(this@DebugTuningActivity, 4f)
            }
        }
        swatchesContainer.addView(schemeTextView)
        paletteRowContainer.addView(swatchesContainer)

        val noteTextView = TextView(this).apply {
            text = "Applies when the idle colours are at their defaults (Reset Colors)"
            textSize = 12f
            setTextColor(Color.parseColor("#8E8E93"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = LyricsSettings.dpToPx(this@DebugTuningActivity, 8f)
            }
        }
        paletteRowContainer.addView(noteTextView)
        paletteCard.addView(paletteRowContainer)

        fun updatePaletteDisplay(versionOverride: String) {
            val effective = IdleScreenSettings.effectivePaletteVersion(BuildConfig.VERSION_NAME, versionOverride, BuildConfig.DEBUG)
            val palette = VersionPalette.forVersion(effective)
            for (i in 0 until 4) {
                (swatchViews[i].background as? GradientDrawable)?.setColor(palette[i])
            }
            schemeTextView.text = VersionPalette.schemeForVersion(effective)
        }

        fun commitPaletteOverride(newOverride: String) {
            val trimmed = newOverride.trim()
            Tuning.paletteVersionOverride = trimmed
            Tuning.save(this@DebugTuningActivity)
            updatePaletteDisplay(trimmed)
            val intent = Intent("com.dnk.wallpaperlyrics.DEBUG_PALETTE_CHANGED").apply {
                setPackage(packageName)
            }
            sendBroadcast(intent)
        }

        updatePaletteDisplay(Tuning.paletteVersionOverride)

        val clearPaletteButton = TextView(this).apply {
            text = "Clear"
            textSize = 12f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(
                LyricsSettings.dpToPx(this@DebugTuningActivity, 8f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 4f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 8f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 4f)
            )
            setOnClickListener {
                versionEditText.setText("")
                commitPaletteOverride("")
                versionEditText.clearFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(versionEditText.windowToken, 0)
            }
        }
        paletteHeaderRow.addView(clearPaletteButton)

        versionEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitPaletteOverride(versionEditText.text.toString())
                versionEditText.clearFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(versionEditText.windowToken, 0)
                true
            } else {
                false
            }
        }

        versionEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && versionEditText.text.toString().trim() != Tuning.paletteVersionOverride) {
                commitPaletteOverride(versionEditText.text.toString())
            }
        }

        rootLayout.addView(paletteHeaderRow)
        rootLayout.addView(paletteCard)
        for (groupName in Tuning.groups) {
            val groupParams = Tuning.allParams.filter { it.group == groupName }
            if (groupParams.isEmpty()) continue

            // Section header row with title and group reset button
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    LyricsSettings.dpToPx(this@DebugTuningActivity, 4f),
                    LyricsSettings.dpToPx(this@DebugTuningActivity, 16f),
                    LyricsSettings.dpToPx(this@DebugTuningActivity, 4f),
                    LyricsSettings.dpToPx(this@DebugTuningActivity, 8f)
                )
            }

            val groupTitle = TextView(this).apply {
                text = groupName.uppercase(Locale.US)
                textSize = 13f
                setTextColor(Color.parseColor("#F59E0B"))
                setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            headerRow.addView(groupTitle)

            val resetGroupButton = TextView(this).apply {
                text = "Reset Group"
                textSize = 12f
                setTextColor(Color.parseColor("#8E8E93"))
                setPadding(
                    LyricsSettings.dpToPx(this@DebugTuningActivity, 8f),
                    LyricsSettings.dpToPx(this@DebugTuningActivity, 4f),
                    LyricsSettings.dpToPx(this@DebugTuningActivity, 8f),
                    LyricsSettings.dpToPx(this@DebugTuningActivity, 4f)
                )
                setOnClickListener {
                    Tuning.resetGroup(groupName)
                    Tuning.save(this@DebugTuningActivity)
                    refreshViews()
                    Toast.makeText(this@DebugTuningActivity, "Reset $groupName", Toast.LENGTH_SHORT).show()
                }
            }
            headerRow.addView(resetGroupButton)

            rootLayout.addView(headerRow)

            // Card container for slider rows
            val card = LyricsSettings.SettingsCard(this).apply {
                // Subtle dark amber border to signal debug status
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#282015"))
                    setStroke(LyricsSettings.dpToPx(this@DebugTuningActivity, 1f), Color.parseColor("#5C4012"))
                    cornerRadius = LyricsSettings.dpToPx(this@DebugTuningActivity, 18f).toFloat()
                }
            }

            var isFirstRow = true
            for (param in groupParams) {
                if (!isFirstRow) {
                    val divider = View(this).apply {
                        setBackgroundColor(Color.parseColor("#443A2A"))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LyricsSettings.dpToPx(this@DebugTuningActivity, 1f)
                        ).apply {
                            leftMargin = LyricsSettings.dpToPx(this@DebugTuningActivity, 16f)
                            rightMargin = LyricsSettings.dpToPx(this@DebugTuningActivity, 16f)
                        }
                    }
                    card.addView(divider)
                }
                isFirstRow = false

                val rowContainer = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(
                        LyricsSettings.dpToPx(this@DebugTuningActivity, 16f),
                        LyricsSettings.dpToPx(this@DebugTuningActivity, 12f),
                        LyricsSettings.dpToPx(this@DebugTuningActivity, 16f),
                        LyricsSettings.dpToPx(this@DebugTuningActivity, 12f)
                    )
                }

                // Label and current value header
                val labelRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val labelText = TextView(this).apply {
                    text = param.label
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                labelRow.addView(labelText)

                val valueText = TextView(this).apply {
                    text = formatParamValue(param)
                    textSize = 14f
                    setTextColor(if (param.isModified) Color.parseColor("#F59E0B") else Color.parseColor("#E5E5EA"))
                    setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
                }
                labelRow.addView(valueText)
                rowContainer.addView(labelRow)

                val descriptionText = TextView(this).apply {
                    text = param.description
                    textSize = 12f
                    setTextColor(Color.parseColor("#8E8E93"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = LyricsSettings.dpToPx(this@DebugTuningActivity, 2f)
                    }
                }
                rowContainer.addView(descriptionText)

                // Slider control
                val slider = LyricsSettings.SettingsSlider(this).apply {
                    max = 1000
                    progress = Tuning.valueToProgress(param.value, param.min, param.max, param.inverted)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LyricsSettings.dpToPx(this@DebugTuningActivity, 44f)
                    ).apply {
                        marginStart = LyricsSettings.dpToPx(this@DebugTuningActivity, -10f)
                        marginEnd = LyricsSettings.dpToPx(this@DebugTuningActivity, -10f)
                        topMargin = LyricsSettings.dpToPx(this@DebugTuningActivity, 6f)
                        bottomMargin = LyricsSettings.dpToPx(this@DebugTuningActivity, -10f)
                    }
                }

                val rowViews = ParamRowViews(param, valueText, slider)
                boundViews.add(rowViews)

                slider.onProgressChanged = { progressVal, fromUser ->
                    if (fromUser) {
                        val updated = Tuning.progressToValue(
                            progressVal,
                            param.min,
                            param.max,
                            param.inverted,
                            param.isInteger
                        )
                        param.value = updated
                        Tuning.save(this@DebugTuningActivity)
                        valueText.text = formatParamValue(param)
                        valueText.setTextColor(if (param.isModified) Color.parseColor("#F59E0B") else Color.parseColor("#E5E5EA"))
                    }
                }
                rowContainer.addView(slider)

                // Min and max labels
                val rangeRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = LyricsSettings.dpToPx(this@DebugTuningActivity, 2f)
                    }
                }

                val minLabel = TextView(this).apply {
                    text = formatEndpoint(param.min, param.isInteger)
                    textSize = 11f
                    setTextColor(Color.parseColor("#8E8E93"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                rangeRow.addView(minLabel)

                val maxLabel = TextView(this).apply {
                    text = formatEndpoint(param.max, param.isInteger)
                    textSize = 11f
                    setTextColor(Color.parseColor("#8E8E93"))
                    gravity = Gravity.END
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                rangeRow.addView(maxLabel)
                rowContainer.addView(rangeRow)

                card.addView(rowContainer)
            }

            rootLayout.addView(card)
        }

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.parseColor("#1C1C1E"))
            addView(rootLayout)
        }

        setContentView(scrollView)
    }

    private fun formatParamValue(param: Tuning.Tunable): String {
        return if (param.isInteger) {
            "${param.value.toLong()}"
        } else if (param.key == "shaderDitherAmplitude") {
            String.format(Locale.US, "%.4f", param.value)
        } else {
            String.format(Locale.US, "%.2f", param.value)
        }
    }

    private fun formatEndpoint(value: Float, isInteger: Boolean): String {
        return if (isInteger) {
            "${value.toLong()}"
        } else if (Math.abs(value - (value * 100).toInt() / 100f) > 0.0001f) {
            String.format(Locale.US, "%.4f", value)
        } else {
            String.format(Locale.US, "%.2f", value)
        }
    }

    private fun refreshViews() {
        for (bound in boundViews) {
            bound.slider.progress = Tuning.valueToProgress(
                bound.param.value,
                bound.param.min,
                bound.param.max,
                bound.param.inverted
            )
            bound.valueView.text = formatParamValue(bound.param)
            bound.valueView.setTextColor(
                if (bound.param.isModified) Color.parseColor("#F59E0B") else Color.parseColor("#E5E5EA")
            )
        }
    }

    private fun copyKotlinToClipboard() {
        val dump = Tuning.exportKotlin()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Tuned Motion Values", dump))
        Toast.makeText(this, "Copied tuning block to clipboard", Toast.LENGTH_SHORT).show()
    }
}
