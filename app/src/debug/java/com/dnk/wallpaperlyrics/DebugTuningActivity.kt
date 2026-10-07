package com.dnk.wallpaperlyrics

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Debug-only activity hosting live parameter tuning sliders.
 * Reached from the debug section in MainActivity settings.
 */
class DebugTuningActivity : Activity() {

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 101
        private const val REQUEST_PICK_ZIP = 102
    }

    private class ParamRowViews(
        val param: Tuning.Tunable,
        val valueView: TextView,
        val slider: LyricsSettings.SettingsSlider
    )

    private val boundViews = mutableListOf<ParamRowViews>()
    private val activityScope = CoroutineScope(Dispatchers.Main + Job())
    private var undoRow: LyricsSettings.SettingsRow? = null

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
            text = "PALETTE GENERATOR"
            textSize = 13f
            setTextColor(Color.parseColor("#F59E0B"))
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        paletteHeaderRow.addView(paletteTitle)

        val paletteStateView = TextView(this).apply {
            textSize = 12f
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(
                LyricsSettings.dpToPx(this@DebugTuningActivity, 8f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 4f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 8f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 4f)
            )
        }
        paletteHeaderRow.addView(paletteStateView)

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

        var currentPalette: IntArray = if (Tuning.paletteOverride.size == 4) {
            Tuning.paletteOverride.clone()
        } else {
            VersionPalette.forVersion(BuildConfig.VERSION_NAME)
        }
        var paletteState: String = if (Tuning.paletteOverride.size == 4) "Edited" else "Pinned"

        val swatchesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val roleNames = listOf("Accent", "Base", "Mid", "Highlight")
        val swatchViews = Array(4) { View(this@DebugTuningActivity) }
        val hexLabels = Array(4) { TextView(this@DebugTuningActivity) }

        fun updatePaletteUI() {
            for (i in 0 until 4) {
                (swatchViews[i].background as? GradientDrawable)?.setColor(currentPalette[i])
                hexLabels[i].text = IdleScreenSettings.formatHexColor(currentPalette[i])
            }
            paletteStateView.text = paletteState
            paletteStateView.setTextColor(
                when (paletteState) {
                    "Generated" -> Color.parseColor("#F59E0B")
                    "Edited" -> Color.parseColor("#34C759")
                    else -> Color.parseColor("#8E8E93")
                }
            )
        }

        for (i in 0 until 4) {
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                minimumHeight = LyricsSettings.dpToPx(this@DebugTuningActivity, 48f)
                setPadding(
                    LyricsSettings.dpToPx(this@DebugTuningActivity, 4f),
                    LyricsSettings.dpToPx(this@DebugTuningActivity, 6f),
                    LyricsSettings.dpToPx(this@DebugTuningActivity, 4f),
                    LyricsSettings.dpToPx(this@DebugTuningActivity, 6f)
                )
                isClickable = true
                val outVal = TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, outVal, true)
                setBackgroundResource(outVal.resourceId)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val roleLabel = TextView(this).apply {
                text = roleNames[i]
                textSize = 11f
                setTextColor(Color.parseColor("#8E8E93"))
                setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL))
            }
            column.addView(roleLabel)

            val swatchSize = LyricsSettings.dpToPx(this@DebugTuningActivity, 28f)
            swatchViews[i] = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(swatchSize, swatchSize).apply {
                    topMargin = LyricsSettings.dpToPx(this@DebugTuningActivity, 4f)
                    bottomMargin = LyricsSettings.dpToPx(this@DebugTuningActivity, 4f)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(LyricsSettings.dpToPx(this@DebugTuningActivity, 1f), Color.parseColor("#555555"))
                }
            }
            column.addView(swatchViews[i])

            hexLabels[i] = TextView(this).apply {
                textSize = 11f
                setTextColor(Color.WHITE)
                setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            }
            column.addView(hexLabels[i])

            val roleIndex = i
            column.setOnClickListener {
                SettingsDialogs.showColorPickerDialog(
                    this@DebugTuningActivity,
                    "Role $roleIndex (${roleNames[roleIndex]})",
                    currentPalette[roleIndex]
                ) { newColor ->
                    val updated = currentPalette.clone()
                    updated[roleIndex] = newColor
                    currentPalette = updated
                    paletteState = "Edited"
                    Tuning.paletteOverride = updated.clone()
                    Tuning.save(this@DebugTuningActivity)
                    updatePaletteUI()
                    val intent = Intent("com.dnk.wallpaperlyrics.DEBUG_PALETTE_CHANGED").apply {
                        setPackage(packageName)
                    }
                    sendBroadcast(intent)
                }
            }

            swatchesContainer.addView(column)
        }
        paletteRowContainer.addView(swatchesContainer)

        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = LyricsSettings.dpToPx(this@DebugTuningActivity, 12f)
            }
        }

        fun createActionButton(title: String, textColorHex: String): TextView {
            return TextView(this).apply {
                text = title
                textSize = 12f
                setTextColor(Color.parseColor(textColorHex))
                setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
                gravity = Gravity.CENTER
                minimumHeight = LyricsSettings.dpToPx(this@DebugTuningActivity, 48f)
                setPadding(
                    LyricsSettings.dpToPx(this@DebugTuningActivity, 8f),
                    LyricsSettings.dpToPx(this@DebugTuningActivity, 8f),
                    LyricsSettings.dpToPx(this@DebugTuningActivity, 8f),
                    LyricsSettings.dpToPx(this@DebugTuningActivity, 8f)
                )
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1C1C1E"))
                    cornerRadius = LyricsSettings.dpToPx(this@DebugTuningActivity, 8f).toFloat()
                    setStroke(LyricsSettings.dpToPx(this@DebugTuningActivity, 1f), Color.parseColor("#443A2A"))
                }
                isClickable = true
            }
        }

        val randomizeButton = createActionButton("Randomize", "#F59E0B").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = LyricsSettings.dpToPx(this@DebugTuningActivity, 6f)
            }
            setOnClickListener {
                val seed = java.util.Random().nextInt()
                val generated = VersionPalette.generate(seed)
                currentPalette = generated
                paletteState = "Generated"
                Tuning.paletteOverride = generated.clone()
                Tuning.save(this@DebugTuningActivity)
                updatePaletteUI()
                val intent = Intent("com.dnk.wallpaperlyrics.DEBUG_PALETTE_CHANGED").apply {
                    setPackage(packageName)
                }
                sendBroadcast(intent)
            }
        }
        buttonsRow.addView(randomizeButton)

        val copyValuesButton = createActionButton("Copy values", "#FFFFFF").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = LyricsSettings.dpToPx(this@DebugTuningActivity, 6f)
            }
            setOnClickListener {
                val vName = BuildConfig.VERSION_NAME
                val hex0 = String.format(Locale.US, "%08X", currentPalette[0])
                val hex1 = String.format(Locale.US, "%08X", currentPalette[1])
                val hex2 = String.format(Locale.US, "%08X", currentPalette[2])
                val hex3 = String.format(Locale.US, "%08X", currentPalette[3])
                val line1 = "\"$vName\" to intArrayOf(0x${hex0}.toInt(), 0x${hex1}.toInt(), 0x${hex2}.toInt(), 0x${hex3}.toInt()),"
                val line2 = "${IdleScreenSettings.formatHexColor(currentPalette[0])} ${IdleScreenSettings.formatHexColor(currentPalette[1])} ${IdleScreenSettings.formatHexColor(currentPalette[2])} ${IdleScreenSettings.formatHexColor(currentPalette[3])}"
                val dump = "$line1\n$line2"
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Palette Values", dump))
                Toast.makeText(this@DebugTuningActivity, "Copied palette values", Toast.LENGTH_SHORT).show()
            }
        }
        buttonsRow.addView(copyValuesButton)

        val clearButton = createActionButton("Clear", "#8E8E93").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                Tuning.paletteOverride = intArrayOf()
                Tuning.save(this@DebugTuningActivity)
                currentPalette = VersionPalette.forVersion(BuildConfig.VERSION_NAME)
                paletteState = "Pinned"
                updatePaletteUI()
                val intent = Intent("com.dnk.wallpaperlyrics.DEBUG_PALETTE_CHANGED").apply {
                    setPackage(packageName)
                }
                sendBroadcast(intent)
                Toast.makeText(this@DebugTuningActivity, "Cleared palette override", Toast.LENGTH_SHORT).show()
            }
        }
        buttonsRow.addView(clearButton)

        paletteRowContainer.addView(buttonsRow)

        val noteTextView = TextView(this).apply {
            text = "Applies when the idle colours are at their defaults (Reset Colors)"
            textSize = 12f
            setTextColor(Color.parseColor("#8E8E93"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = LyricsSettings.dpToPx(this@DebugTuningActivity, 10f)
            }
        }
        paletteRowContainer.addView(noteTextView)
        paletteCard.addView(paletteRowContainer)

        updatePaletteUI()

        rootLayout.addView(paletteHeaderRow)
        rootLayout.addView(paletteCard)

        val updateHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                LyricsSettings.dpToPx(this@DebugTuningActivity, 4f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 16f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 4f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 8f)
            )
        }

        val updateTitle = TextView(this).apply {
            text = "UPDATE CHECK"
            textSize = 13f
            setTextColor(Color.parseColor("#F59E0B"))
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        updateHeaderRow.addView(updateTitle)

        val updateCard = LyricsSettings.SettingsCard(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#282015"))
                setStroke(LyricsSettings.dpToPx(this@DebugTuningActivity, 1f), Color.parseColor("#5C4012"))
                cornerRadius = LyricsSettings.dpToPx(this@DebugTuningActivity, 18f).toFloat()
            }
        }

        val fakeTag = "9.9.9"
        val releaseUrl = "https://github.com/dankouwu/wallpaper-lyrics/releases/latest"

        updateCard.addRow(
            LyricsSettings.SettingsRow(
                this,
                LyricsSettings.IconType.INFO,
                "Show update popup",
                "Preview dialog with fake version $fakeTag",
                LyricsSettings.TrailingType.NONE,
                onClick = {
                    UpdateCheck.showUpdateDialog(
                        this@DebugTuningActivity,
                        BuildConfig.VERSION_NAME,
                        fakeTag,
                        releaseUrl,
                        rememberDismissal = false
                    )
                }
            )
        )

        updateCard.addRow(
            LyricsSettings.SettingsRow(
                this,
                LyricsSettings.IconType.BELL,
                "Post update notification",
                "Preview notification with fake version $fakeTag",
                LyricsSettings.TrailingType.NONE,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS)
                        Toast.makeText(this@DebugTuningActivity, "Notification permission needed", Toast.LENGTH_SHORT).show()
                    } else {
                        UpdateCheck.postUpdateNotification(this@DebugTuningActivity, fakeTag, releaseUrl)
                    }
                }
            )
        )

        rootLayout.addView(updateHeaderRow)
        rootLayout.addView(updateCard)

        val importHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                LyricsSettings.dpToPx(this@DebugTuningActivity, 4f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 16f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 4f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 8f)
            )
        }

        val importTitle = TextView(this).apply {
            text = "LYRICS IMPORT"
            textSize = 13f
            setTextColor(Color.parseColor("#F59E0B"))
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        importHeaderRow.addView(importTitle)

        val importCard = LyricsSettings.SettingsCard(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#282015"))
                setStroke(LyricsSettings.dpToPx(this@DebugTuningActivity, 1f), Color.parseColor("#5C4012"))
                cornerRadius = LyricsSettings.dpToPx(this@DebugTuningActivity, 18f).toFloat()
            }
        }

        importCard.addRow(
            LyricsSettings.SettingsRow(
                this,
                LyricsSettings.IconType.FILE_STACK,
                "Import lyrics zip",
                "Pick an alignment pipeline zip file",
                LyricsSettings.TrailingType.NONE,
                onClick = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/zip"
                    }
                    startActivityForResult(intent, REQUEST_PICK_ZIP)
                }
            )
        )

        val undoRowView = LyricsSettings.SettingsRow(
            this,
            LyricsSettings.IconType.RELOAD,
            "Undo last import",
            "Checking undo state...",
            LyricsSettings.TrailingType.NONE,
            onClick = {
                val storage = LyricsStorage.forContext(this@DebugTuningActivity)
                val undoDir = File(filesDir, "lyrics_import_undo")
                val importer = LyricsZipImporter(storage, undoDir)
                if (!importer.hasUndo()) {
                    Toast.makeText(this@DebugTuningActivity, "Nothing to undo", Toast.LENGTH_SHORT).show()
                    return@SettingsRow
                }
                SettingsDialogs.showCustomConfirmDialog(
                    activity = this@DebugTuningActivity,
                    title = "Undo last import",
                    message = "Restore cache files replaced by the last import and delete newly imported tracks?",
                    confirmText = "Undo"
                ) {
                    activityScope.launch {
                        val success = withContext(Dispatchers.IO) {
                            importer.undoLastImport()
                        }
                        if (success) {
                            val reloadIntent = Intent("com.dnk.wallpaperlyrics.RELOAD_LYRICS").apply {
                                setPackage(packageName)
                            }
                            sendBroadcast(reloadIntent)
                            Toast.makeText(this@DebugTuningActivity, "Restored previous lyrics cache", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@DebugTuningActivity, "Nothing to undo", Toast.LENGTH_SHORT).show()
                        }
                        updateUndoSubtitle()
                    }
                }
            }
        )
        undoRow = undoRowView
        importCard.addRow(undoRowView)
        updateUndoSubtitle()

        rootLayout.addView(importHeaderRow)
        rootLayout.addView(importCard)
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

    override fun onResume() {
        super.onResume()
        updateUndoSubtitle()
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.coroutineContext[Job]?.cancel()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_ZIP && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            startImport(uri)
        }
    }

    private fun updateUndoSubtitle() {
        activityScope.launch {
            val hasUndo = withContext(Dispatchers.IO) {
                val undoDir = File(filesDir, "lyrics_import_undo")
                undoDir.exists() && (undoDir.listFiles()?.isNotEmpty() == true)
            }
            undoRow?.updateSubtitle(
                if (hasUndo) "Restore cache before the last import" else "No previous import to undo"
            )
        }
    }

    private fun startImport(uri: android.net.Uri) {
        val (progressDialog, progressText) = showProgressDialog()
        activityScope.launch {
            var tempFile: File? = null
            try {
                val result = withContext(Dispatchers.IO) {
                    val temp = File(cacheDir, "import_temp_${System.currentTimeMillis()}.zip")
                    tempFile = temp
                    contentResolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw IllegalArgumentException("Could not open picked file")

                    val storage = LyricsStorage.forContext(applicationContext)
                    val undoDir = File(filesDir, "lyrics_import_undo")
                    val importer = LyricsZipImporter(storage, undoDir)

                    importer.importZip(temp) { done, total ->
                        activityScope.launch(Dispatchers.Main) {
                            progressText.text = "Importing $done of $total"
                        }
                    }
                }
                progressDialog.dismiss()

                val reloadIntent = Intent("com.dnk.wallpaperlyrics.RELOAD_LYRICS").apply {
                    setPackage(packageName)
                }
                sendBroadcast(reloadIntent)

                updateUndoSubtitle()
                showImportResultDialog(result)
            } catch (e: Exception) {
                progressDialog.dismiss()
                showImportErrorDialog(e.message ?: "Import failed")
            } finally {
                withContext(Dispatchers.IO) {
                    tempFile?.delete()
                }
            }
        }
    }

    private fun showProgressDialog(): Pair<Dialog, TextView> {
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                LyricsSettings.dpToPx(this@DebugTuningActivity, 24f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 24f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 24f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 24f)
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = LyricsSettings.dpToPx(this@DebugTuningActivity, 16f).toFloat()
            }
        }

        val titleText = TextView(this).apply {
            text = "Importing lyrics"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(0, 0, 0, LyricsSettings.dpToPx(this@DebugTuningActivity, 8f))
        }
        container.addView(titleText)

        val progressText = TextView(this).apply {
            text = "Preparing..."
            textSize = 14f
            setTextColor(Color.parseColor("#8E8E93"))
        }
        container.addView(progressText)

        dialog.setContentView(container)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.85f).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
        return Pair(dialog, progressText)
    }

    private fun showImportResultDialog(result: LyricsZipImporter.ImportResult) {
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(true)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                LyricsSettings.dpToPx(this@DebugTuningActivity, 24f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 24f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 24f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 20f)
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = LyricsSettings.dpToPx(this@DebugTuningActivity, 16f).toFloat()
            }
        }

        val titleText = TextView(this).apply {
            text = "Import complete"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(0, 0, 0, LyricsSettings.dpToPx(this@DebugTuningActivity, 10f))
        }
        container.addView(titleText)

        val sb = StringBuilder()
        sb.append("Imported new: ").append(result.importedNew).append("\n")
        sb.append("Replaced: ").append(result.replaced).append("\n")
        sb.append("Override active: ").append(result.overrideActive).append("\n")
        sb.append("Failed: ").append(result.failed)

        if (result.failedTitles.isNotEmpty()) {
            sb.append("\n\nFailed titles:\n")
            sb.append(result.failedTitles.joinToString("\n") { "- $it" })
        }

        val msgScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val msgText = TextView(this).apply {
            text = sb.toString()
            textSize = 14f
            setTextColor(Color.parseColor("#E5E5EA"))
            setPadding(0, 0, 0, LyricsSettings.dpToPx(this@DebugTuningActivity, 16f))
        }
        msgScrollView.addView(msgText)
        container.addView(msgScrollView)

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val okButton = Button(this).apply {
            text = "OK"
            setTextColor(Color.parseColor("#F59E0B"))
            transformationMethod = null
            background = null
            setOnClickListener { dialog.dismiss() }
        }
        buttonLayout.addView(okButton)
        container.addView(buttonLayout)

        dialog.setContentView(container)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.85f).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
    }

    private fun showImportErrorDialog(errorMessage: String) {
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(true)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                LyricsSettings.dpToPx(this@DebugTuningActivity, 24f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 24f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 24f),
                LyricsSettings.dpToPx(this@DebugTuningActivity, 20f)
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = LyricsSettings.dpToPx(this@DebugTuningActivity, 16f).toFloat()
            }
        }

        val titleText = TextView(this).apply {
            text = "Import failed"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(0, 0, 0, LyricsSettings.dpToPx(this@DebugTuningActivity, 10f))
        }
        container.addView(titleText)

        val msgText = TextView(this).apply {
            text = errorMessage
            textSize = 14f
            setTextColor(Color.parseColor("#FF7B72"))
            setPadding(0, 0, 0, LyricsSettings.dpToPx(this@DebugTuningActivity, 16f))
        }
        container.addView(msgText)

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val okButton = Button(this).apply {
            text = "OK"
            setTextColor(Color.parseColor("#F59E0B"))
            transformationMethod = null
            background = null
            setOnClickListener { dialog.dismiss() }
        }
        buttonLayout.addView(okButton)
        container.addView(buttonLayout)

        dialog.setContentView(container)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.85f).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
    }
}
