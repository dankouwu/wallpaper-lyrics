package com.dnk.wallpaperlyrics

import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.net.Uri
import android.media.session.MediaSessionManager
import android.os.Bundle
import android.widget.LinearLayout
import android.view.Gravity
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import android.widget.TextView
import android.widget.EditText
import android.text.InputType
import android.view.View
import android.widget.Button
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import com.dnk.wallpaperlyrics.LyricsSettings as LS

class BackgroundSettingsActivity : AppCompatActivity() {

    private var palettePreview: ColorPalettePreviewView? = null

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
                val size = LS.dpToPx(this@BackgroundSettingsActivity, 32f)
                layoutParams = android.widget.RelativeLayout.LayoutParams(size, size).apply {
                    addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT)
                    addRule(android.widget.RelativeLayout.CENTER_VERTICAL)
                }
                setPadding(LS.dpToPx(this@BackgroundSettingsActivity, 4f), LS.dpToPx(this@BackgroundSettingsActivity, 4f), LS.dpToPx(this@BackgroundSettingsActivity, 4f), LS.dpToPx(this@BackgroundSettingsActivity, 4f))
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
                text = "Background Settings"
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
                    "Dynamic Theming",
                    "Sync highlights with Material You palette",
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
                        showCustomEditDialog("Set Corner Radius", currentRadius.toString(), 0f, 120f, false, "dp") { newVal ->
                            val radiusVal = newVal.toInt()
                            prefs.edit().putFloat("album_corner_radius", radiusVal.toFloat()).apply()
                            radiusRow.updateValue("${radiusVal}dp")
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
                        showCustomEditDialog("Set Fluid Speed", String.format("%.1f", currentSpeed), 0.1f, 10.0f, true, "x") { newVal ->
                            prefs.edit().putFloat("bg_speed", newVal).apply()
                            speedRow.updateValue(String.format("%.1fx", newVal))
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

                val resetRow = LS.SettingsRow(
                    this@BackgroundSettingsActivity,
                    LS.IconType.RELOAD,
                    "Reset Colors",
                    "Restore the built in palette",
                    LS.TrailingType.NONE,
                    onClick = {
                        prefs.edit().apply {
                            putInt(IdleScreenSettings.KEY_IDLE_ACCENT, IdleScreenSettings.DEFAULT_ACCENT)
                            putInt(IdleScreenSettings.KEY_IDLE_BASE, IdleScreenSettings.DEFAULT_BASE)
                            putInt(IdleScreenSettings.KEY_IDLE_MID, IdleScreenSettings.DEFAULT_MID)
                            putInt(IdleScreenSettings.KEY_IDLE_HIGHLIGHT, IdleScreenSettings.DEFAULT_HIGHLIGHT)
                            apply()
                        }
                        accentRow.updateValue(IdleScreenSettings.formatHexColor(IdleScreenSettings.DEFAULT_ACCENT))
                        (accentSwatch.background as? GradientDrawable)?.setColor(IdleScreenSettings.DEFAULT_ACCENT)

                        baseRow.updateValue(IdleScreenSettings.formatHexColor(IdleScreenSettings.DEFAULT_BASE))
                        (baseSwatch.background as? GradientDrawable)?.setColor(IdleScreenSettings.DEFAULT_BASE)

                        midRow.updateValue(IdleScreenSettings.formatHexColor(IdleScreenSettings.DEFAULT_MID))
                        (midSwatch.background as? GradientDrawable)?.setColor(IdleScreenSettings.DEFAULT_MID)

                        highlightRow.updateValue(IdleScreenSettings.formatHexColor(IdleScreenSettings.DEFAULT_HIGHLIGHT))
                        (highlightSwatch.background as? GradientDrawable)?.setColor(IdleScreenSettings.DEFAULT_HIGHLIGHT)
                    }
                )
                addRow(resetRow)
            }
            rootLayout.addView(card3)

            val scrollView = android.widget.ScrollView(this).apply {
                isFillViewport = true
            }
            scrollView.addView(rootLayout)
            setContentView(scrollView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        palettePreview?.refreshColors()
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
        onSave: (String) -> Unit
    ) {
        val dialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setCancelable(true)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(LS.dpToPx(this@BackgroundSettingsActivity, 24f), LS.dpToPx(this@BackgroundSettingsActivity, 24f), LS.dpToPx(this@BackgroundSettingsActivity, 24f), LS.dpToPx(this@BackgroundSettingsActivity, 20f))
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
            setPadding(0, 0, 0, LS.dpToPx(this@BackgroundSettingsActivity, 16f))
        }
        container.addView(titleText)

        val inputEdit = EditText(this).apply {
            setText(initialVal)
            this.inputType = inputType
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
        }
        container.addView(inputEdit)

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
                onSave(inputEdit.text.toString())
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
        onValueSaved: (Float) -> Unit
    ) {
        val displayTitle = if (unit.isNotEmpty()) "$title ($unit)" else title
        val inputType = if (isFloat) {
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        } else {
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        showBaseInputDialog(displayTitle, initialVal, inputType) { textStr ->
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
        val dialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setCancelable(true)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                LS.dpToPx(this@BackgroundSettingsActivity, 24f),
                LS.dpToPx(this@BackgroundSettingsActivity, 24f),
                LS.dpToPx(this@BackgroundSettingsActivity, 24f),
                LS.dpToPx(this@BackgroundSettingsActivity, 20f)
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = LS.dpToPx(this@BackgroundSettingsActivity, 16f).toFloat()
            }
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, LS.dpToPx(this@BackgroundSettingsActivity, 16f))
        }

        val titleText = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleRow.addView(titleText)

        val closeButton = android.widget.ImageView(this).apply {
            val arrowDrawable = LS.CustomIconDrawable(this@BackgroundSettingsActivity, LS.IconType.ARROW_LEFT)
            setImageDrawable(arrowDrawable)
            val size = LS.dpToPx(this@BackgroundSettingsActivity, 32f)
            layoutParams = LinearLayout.LayoutParams(size, size)
            setPadding(
                LS.dpToPx(this@BackgroundSettingsActivity, 4f),
                LS.dpToPx(this@BackgroundSettingsActivity, 4f),
                LS.dpToPx(this@BackgroundSettingsActivity, 4f),
                LS.dpToPx(this@BackgroundSettingsActivity, 4f)
            )
            isClickable = true
            val outVal = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outVal, true)
            setBackgroundResource(outVal.resourceId)
            setOnClickListener { dialog.dismiss() }
        }
        titleRow.addView(closeButton)
        container.addView(titleRow)

        // Deriving hue from RGB loses it whenever value or saturation reaches zero:
        // dragging the handle into the black corner and back with an RGB source of truth
        // resets hue to red. Storing HSV directly preserves hue across the entire SV surface.
        val initialHsv = FloatArray(3)
        Color.colorToHSV(initialColor or 0xFF000000.toInt(), initialHsv)
        var currentHue = initialHsv[0]
        var currentSaturation = initialHsv[1]
        var currentValue = initialHsv[2]

        // Single suppression flag to break watcher feedback loops during programmatic setText
        var isProgrammaticUpdate = false

        val previewSwatch = View(this).apply {
            val h = LS.dpToPx(this@BackgroundSettingsActivity, 32f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                h
            ).apply {
                bottomMargin = LS.dpToPx(this@BackgroundSettingsActivity, 14f)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = LS.dpToPx(this@BackgroundSettingsActivity, 10f).toFloat()
                setColor(initialColor or 0xFF000000.toInt())
                setStroke(LS.dpToPx(this@BackgroundSettingsActivity, 1f), Color.parseColor("#555555"))
            }
        }

        val svView = SaturationValueView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LS.dpToPx(this@BackgroundSettingsActivity, 200f)
            )
            setHue(currentHue)
            setSaturationAndValue(currentSaturation, currentValue)
        }

        val hueSlider = HueSliderView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LS.dpToPx(this@BackgroundSettingsActivity, 24f)
            ).apply {
                topMargin = LS.dpToPx(this@BackgroundSettingsActivity, 16f)
            }
            setHue(currentHue)
        }

        var isRgbMode = true
        val modeChip = TextView(this).apply {
            text = "RGB"
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            gravity = Gravity.CENTER
            setPadding(
                LS.dpToPx(this@BackgroundSettingsActivity, 14f),
                LS.dpToPx(this@BackgroundSettingsActivity, 10f),
                LS.dpToPx(this@BackgroundSettingsActivity, 14f),
                LS.dpToPx(this@BackgroundSettingsActivity, 10f)
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#242424"))
                cornerRadius = LS.dpToPx(this@BackgroundSettingsActivity, 10f).toFloat()
                setStroke(LS.dpToPx(this@BackgroundSettingsActivity, 1f), Color.parseColor("#444444"))
            }
            isClickable = true
        }

        fun createNumericEdit(): EditText {
            return EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setTextColor(Color.WHITE)
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(
                    LS.dpToPx(this@BackgroundSettingsActivity, 6f),
                    LS.dpToPx(this@BackgroundSettingsActivity, 10f),
                    LS.dpToPx(this@BackgroundSettingsActivity, 6f),
                    LS.dpToPx(this@BackgroundSettingsActivity, 10f)
                )
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#242424"))
                    cornerRadius = LS.dpToPx(this@BackgroundSettingsActivity, 10f).toFloat()
                    setStroke(LS.dpToPx(this@BackgroundSettingsActivity, 1f), Color.parseColor("#444444"))
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    textCursorDrawable = android.graphics.drawable.ColorDrawable(Color.parseColor("#b7b7b7"))
                }
            }
        }

        val editR = createNumericEdit().apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = LS.dpToPx(this@BackgroundSettingsActivity, 4f)
            }
        }
        val editG = createNumericEdit().apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = LS.dpToPx(this@BackgroundSettingsActivity, 4f)
            }
        }
        val editB = createNumericEdit().apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val rgbLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
            addView(editR)
            addView(editG)
            addView(editB)
        }

        val editHex = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(
                LS.dpToPx(this@BackgroundSettingsActivity, 12f),
                LS.dpToPx(this@BackgroundSettingsActivity, 10f),
                LS.dpToPx(this@BackgroundSettingsActivity, 12f),
                LS.dpToPx(this@BackgroundSettingsActivity, 10f)
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#242424"))
                cornerRadius = LS.dpToPx(this@BackgroundSettingsActivity, 10f).toFloat()
                setStroke(LS.dpToPx(this@BackgroundSettingsActivity, 1f), Color.parseColor("#444444"))
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                textCursorDrawable = android.graphics.drawable.ColorDrawable(Color.parseColor("#b7b7b7"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val hexLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
            addView(editHex)
        }

        val fieldsContainer = android.widget.FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = LS.dpToPx(this@BackgroundSettingsActivity, 8f)
            }
            addView(rgbLayout)
            addView(hexLayout)
        }

        modeChip.setOnClickListener {
            isRgbMode = !isRgbMode
            modeChip.text = if (isRgbMode) "RGB" else "HEX"
            rgbLayout.visibility = if (isRgbMode) View.VISIBLE else View.GONE
            hexLayout.visibility = if (isRgbMode) View.GONE else View.VISIBLE
        }

        val numericRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = LS.dpToPx(this@BackgroundSettingsActivity, 16f)
            }
            addView(modeChip)
            addView(fieldsContainer)
        }

        fun updateFieldsAndPreview() {
            val colorInt = Color.HSVToColor(floatArrayOf(currentHue, currentSaturation, currentValue)) or 0xFF000000.toInt()
            (previewSwatch.background as? GradientDrawable)?.setColor(colorInt)

            if (isProgrammaticUpdate) return
            isProgrammaticUpdate = true
            try {
                val r = Color.red(colorInt).toString()
                val g = Color.green(colorInt).toString()
                val b = Color.blue(colorInt).toString()
                val hex = IdleScreenSettings.formatHexColor(colorInt)

                if (editR.text.toString() != r) editR.setText(r)
                if (editG.text.toString() != g) editG.setText(g)
                if (editB.text.toString() != b) editB.setText(b)
                if (editHex.text.toString() != hex) editHex.setText(hex)
            } finally {
                isProgrammaticUpdate = false
            }
        }

        updateFieldsAndPreview()

        svView.onSaturationValueChanged = { sat, valLevel ->
            currentSaturation = sat
            currentValue = valLevel
            updateFieldsAndPreview()
        }

        hueSlider.onHueChanged = { h ->
            currentHue = h
            svView.setHue(h)
            updateFieldsAndPreview()
        }

        val rgbWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (isProgrammaticUpdate) return
                val r = editR.text.toString().toIntOrNull()
                val g = editG.text.toString().toIntOrNull()
                val b = editB.text.toString().toIntOrNull()
                if (r != null && r in 0..255 && g != null && g in 0..255 && b != null && b in 0..255) {
                    val colorInt = Color.rgb(r, g, b) or 0xFF000000.toInt()
                    val hsv = FloatArray(3)
                    Color.colorToHSV(colorInt, hsv)
                    currentHue = hsv[0]
                    currentSaturation = hsv[1]
                    currentValue = hsv[2]

                    isProgrammaticUpdate = true
                    try {
                        val hex = IdleScreenSettings.formatHexColor(colorInt)
                        if (editHex.text.toString() != hex) editHex.setText(hex)
                        svView.setHue(currentHue)
                        svView.setSaturationAndValue(currentSaturation, currentValue)
                        hueSlider.setHue(currentHue)
                        (previewSwatch.background as? GradientDrawable)?.setColor(colorInt)
                    } finally {
                        isProgrammaticUpdate = false
                    }
                }
            }
        }
        editR.addTextChangedListener(rgbWatcher)
        editG.addTextChangedListener(rgbWatcher)
        editB.addTextChangedListener(rgbWatcher)

        val hexWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (isProgrammaticUpdate) return
                val parsed = IdleScreenSettings.parseHexColor(editHex.text.toString())
                if (parsed != null) {
                    val colorInt = parsed or 0xFF000000.toInt()
                    val hsv = FloatArray(3)
                    Color.colorToHSV(colorInt, hsv)
                    currentHue = hsv[0]
                    currentSaturation = hsv[1]
                    currentValue = hsv[2]

                    isProgrammaticUpdate = true
                    try {
                        val r = Color.red(colorInt).toString()
                        val g = Color.green(colorInt).toString()
                        val b = Color.blue(colorInt).toString()
                        if (editR.text.toString() != r) editR.setText(r)
                        if (editG.text.toString() != g) editG.setText(g)
                        if (editB.text.toString() != b) editB.setText(b)
                        svView.setHue(currentHue)
                        svView.setSaturationAndValue(currentSaturation, currentValue)
                        hueSlider.setHue(currentHue)
                        (previewSwatch.background as? GradientDrawable)?.setColor(colorInt)
                    } finally {
                        isProgrammaticUpdate = false
                    }
                }
            }
        }
        editHex.addTextChangedListener(hexWatcher)

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        var savedColors = IdleScreenSettings.parseSavedColors(
            prefs.getString(IdleScreenSettings.KEY_IDLE_SAVED_COLORS, null)
        )

        val savedColorsHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = LS.dpToPx(this@BackgroundSettingsActivity, 16f)
                bottomMargin = LS.dpToPx(this@BackgroundSettingsActivity, 8f)
            }
        }

        val savedColorsTitle = TextView(this).apply {
            text = "Saved Colors"
            textSize = 14f
            setTextColor(Color.parseColor("#CCCCCC"))
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        savedColorsHeader.addView(savedColorsTitle)

        val plusContainer = android.widget.FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LS.dpToPx(this@BackgroundSettingsActivity, 44f),
                LS.dpToPx(this@BackgroundSettingsActivity, 44f)
            )
            isClickable = true
            val outVal = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outVal, true)
            setBackgroundResource(outVal.resourceId)
        }

        val plusInner = TextView(this).apply {
            text = "+"
            textSize = 20f
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
            layoutParams = android.widget.FrameLayout.LayoutParams(
                LS.dpToPx(this@BackgroundSettingsActivity, 28f),
                LS.dpToPx(this@BackgroundSettingsActivity, 28f)
            ).apply {
                gravity = Gravity.CENTER
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#242424"))
                setStroke(LS.dpToPx(this@BackgroundSettingsActivity, 1f), Color.parseColor("#444444"))
            }
        }
        plusContainer.addView(plusInner)
        savedColorsHeader.addView(plusContainer)

        val savedColorsScroll = android.widget.HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = false
        }

        val savedColorsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        savedColorsScroll.addView(savedColorsLayout)

        fun renderSavedColors() {
            savedColorsLayout.removeAllViews()
            val swatchSize = LS.dpToPx(this@BackgroundSettingsActivity, 28f)
            val containerSize = LS.dpToPx(this@BackgroundSettingsActivity, 44f)
            for (color in savedColors) {
                val swatchContainer = android.widget.FrameLayout(this@BackgroundSettingsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(containerSize, containerSize).apply {
                        rightMargin = LS.dpToPx(this@BackgroundSettingsActivity, 4f)
                    }
                    isClickable = true
                    val outVal = TypedValue()
                    theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outVal, true)
                    setBackgroundResource(outVal.resourceId)
                }

                val circleView = View(this@BackgroundSettingsActivity).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(swatchSize, swatchSize).apply {
                        gravity = Gravity.CENTER
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color)
                        setStroke(LS.dpToPx(this@BackgroundSettingsActivity, 1f), Color.parseColor("#555555"))
                    }
                }
                swatchContainer.addView(circleView)

                swatchContainer.setOnClickListener {
                    val hsv = FloatArray(3)
                    Color.colorToHSV(color or 0xFF000000.toInt(), hsv)
                    currentHue = hsv[0]
                    currentSaturation = hsv[1]
                    currentValue = hsv[2]
                    svView.setHue(currentHue)
                    svView.setSaturationAndValue(currentSaturation, currentValue)
                    hueSlider.setHue(currentHue)
                    updateFieldsAndPreview()
                }

                swatchContainer.setOnLongClickListener {
                    val hexStr = IdleScreenSettings.formatHexColor(color)
                    savedColors = savedColors.filter { it != color }
                    prefs.edit().putString(
                        IdleScreenSettings.KEY_IDLE_SAVED_COLORS,
                        IdleScreenSettings.formatSavedColors(savedColors)
                    ).apply()
                    android.widget.Toast.makeText(
                        this@BackgroundSettingsActivity,
                        "Removed $hexStr",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    renderSavedColors()
                    true
                }

                savedColorsLayout.addView(swatchContainer)
            }
        }

        renderSavedColors()

        plusContainer.setOnClickListener {
            val currentColorInt = Color.HSVToColor(floatArrayOf(currentHue, currentSaturation, currentValue)) or 0xFF000000.toInt()
            savedColors = IdleScreenSettings.addSavedColor(savedColors, currentColorInt)
            prefs.edit().putString(
                IdleScreenSettings.KEY_IDLE_SAVED_COLORS,
                IdleScreenSettings.formatSavedColors(savedColors)
            ).apply()
            renderSavedColors()
        }

        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(previewSwatch)
            addView(svView)
            addView(hueSlider)
            addView(numericRow)
            addView(savedColorsHeader)
            addView(savedColorsScroll)
        }

        val dialogScrollView = android.widget.ScrollView(this).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        dialogScrollView.addView(scrollContent)
        container.addView(dialogScrollView)

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
                val finalColor = Color.HSVToColor(floatArrayOf(currentHue, currentSaturation, currentValue)) or 0xFF000000.toInt()
                onColorSaved(finalColor)
                dialog.dismiss()
            }
        }
        buttonLayout.addView(saveButton)
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
