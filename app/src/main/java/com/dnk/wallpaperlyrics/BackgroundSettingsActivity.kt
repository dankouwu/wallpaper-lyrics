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
import androidx.appcompat.widget.SwitchCompat
import android.widget.TextView
import android.widget.EditText
import android.text.InputType
import android.view.View
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue

class BackgroundSettingsActivity : AppCompatActivity() {

    private var palettePreview: ColorPalettePreviewView? = null

    enum class TrailingType { CHEVRON, SWITCH, VALUE, NONE }

    class CustomIconDrawable(private val context: Context, private val iconType: IconType) : android.graphics.drawable.Drawable() {
        enum class IconType { PALETTE, CORNER, GAUGE, IMAGE, LIST_MUSIC, ARROW_LEFT }
        
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                2f,
                context.resources.displayMetrics
            )
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun draw(canvas: Canvas) {
            val bounds = bounds
            paint.color = Color.parseColor("#CCCCCC")

            val scaleFactor = 0.82f
            val s = (bounds.width().toFloat() * scaleFactor) / 24f
            val tx = bounds.left.toFloat() + bounds.width().toFloat() * (1f - scaleFactor) / 2f
            val ty = bounds.top.toFloat() + bounds.height().toFloat() * (1f - scaleFactor) / 2f
            
            val originalStroke = paint.strokeWidth
            paint.strokeWidth = originalStroke / s
            
            canvas.save()
            canvas.translate(tx, ty)
            canvas.scale(s, s)
            
            try {
                when (iconType) {
                    IconType.PALETTE -> {
                        drawPaths(canvas, listOf(
                            "M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z"
                        ))
                        val fillPaint = Paint(paint).apply { style = Paint.Style.FILL }
                        canvas.drawCircle(13.5f, 6.5f, 0.8f, fillPaint)
                        canvas.drawCircle(17.5f, 10.5f, 0.8f, fillPaint)
                        canvas.drawCircle(6.5f, 12.5f, 0.8f, fillPaint)
                        canvas.drawCircle(8.5f, 7.5f, 0.8f, fillPaint)
                    }
                    IconType.CORNER -> {
                        drawPaths(canvas, listOf(
                            "M21 11a8 8 0 0 0-8-8",
                            "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"
                        ))
                    }
                    IconType.GAUGE -> {
                        drawPaths(canvas, listOf(
                            "m12 14 4-4",
                            "M3.34 19a10 10 0 1 1 17.32 0"
                        ))
                    }
                    IconType.IMAGE -> {
                        drawPaths(canvas, listOf(
                            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
                            "M9 7a2 2 0 1 0 0 4 2 2 0 1 0 0-4",
                            "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"
                        ))
                    }
                    IconType.LIST_MUSIC -> {
                        drawPaths(canvas, listOf(
                            "M16 5H3",
                            "M11 12H3",
                            "M11 19H3",
                            "M21 16V5"
                        ))
                        val circlePaint = Paint(paint).apply { style = Paint.Style.STROKE }
                        canvas.drawCircle(18f, 16f, 3f, circlePaint)
                    }
                    IconType.ARROW_LEFT -> {
                        drawPaths(canvas, listOf(
                            "M19 12H5",
                            "m12 19-7-7 7-7"
                        ))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            canvas.restore()
            paint.strokeWidth = originalStroke
        }

        private fun drawPaths(canvas: Canvas, pathDataList: List<String>) {
            for (pathData in pathDataList) {
                try {
                    val path = androidx.core.graphics.PathParser.createPathFromPathData(pathData)
                    canvas.drawPath(path, paint)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        override fun getOpacity(): Int {
            return android.graphics.PixelFormat.TRANSLUCENT
        }
    }

    inner class SettingsCard(context: Context) : LinearLayout(context) {
        init {
            orientation = VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = dpToPx(18f).toFloat()
            }
            clipToOutline = true
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(20f)
            }
        }

        fun addRow(row: SettingsRow) {
            if (childCount > 0) {
                val divider = View(context).apply {
                    setBackgroundColor(Color.parseColor("#444444"))
                    layoutParams = LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        dpToPx(1f)
                    ).apply {
                        leftMargin = dpToPx(56f)
                        rightMargin = dpToPx(16f)
                    }
                }
                addView(divider)
            }
            addView(row)
        }
    }

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
                    val size = dpToPx(14f)
                    val overlap = dpToPx(-4f)
                    
                    colors.forEachIndexed { idx, color ->
                        val dot = View(context).apply {
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(color)
                                setStroke(dpToPx(1f), Color.parseColor("#333333"))
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

    inner class SettingsRow(
        context: Context,
        val iconType: CustomIconDrawable.IconType,
        val title: String,
        val subtitle: String = "",
        var trailingType: TrailingType = TrailingType.NONE,
        val initialVal: String = "",
        val onCheckedChange: ((Boolean) -> Unit)? = null,
        val onClick: (() -> Unit)? = null
    ) : LinearLayout(context) {

        private var valueBadge: TextView? = null
        private var trailingView: View? = null

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padding16 = dpToPx(16f)
            val padding18 = dpToPx(18f)
            setPadding(padding16, padding18, padding16, padding18)
            isClickable = true
            
            val outVal = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outVal, true)
            setBackgroundResource(outVal.resourceId)

            val iconView = View(context).apply {
                background = CustomIconDrawable(context, iconType)
                layoutParams = LayoutParams(dpToPx(24f), dpToPx(24f)).apply {
                    rightMargin = dpToPx(16f)
                }
            }
            addView(iconView)

            val textLayout = LinearLayout(context).apply {
                orientation = VERTICAL
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            }
            val rowTitle = TextView(context).apply {
                text = title
                textSize = 16f
                setTextColor(Color.WHITE)
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            }
            textLayout.addView(rowTitle)
            if (subtitle.isNotEmpty()) {
                val rowSubtitle = TextView(context).apply {
                    text = subtitle
                    textSize = 12f
                    setTextColor(Color.parseColor("#8E8E93"))
                    setPadding(0, 2, dpToPx(8f), 0)
                }
                textLayout.addView(rowSubtitle)
            }
            addView(textLayout)
            setupTrailing()
        }

        fun setTrailing(type: TrailingType, value: String = "") {
            if (trailingType != type) {
                trailingType = type
                setupTrailing()
                if (type == TrailingType.VALUE) {
                    updateValue(value)
                }
            }
        }

        private fun setupTrailing() {
            trailingView?.let { removeView(it) }

            when (trailingType) {
                TrailingType.CHEVRON -> {
                    val chevron = TextView(context).apply {
                        text = "›"
                        textSize = 24f
                        setTextColor(Color.parseColor("#CCCCCC"))
                        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                        gravity = Gravity.CENTER
                    }
                    trailingView = chevron
                    addView(chevron)
                    setOnClickListener { onClick?.invoke() }
                }
                TrailingType.SWITCH -> {
                    val container = LinearLayout(context).apply {
                        orientation = HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }

                    if (iconType == CustomIconDrawable.IconType.PALETTE) {
                        val preview = ColorPalettePreviewView(context).apply {
                            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                                rightMargin = dpToPx(12f)
                            }
                        }
                        this@BackgroundSettingsActivity.palettePreview = preview
                        container.addView(preview)
                    }

                    val switchView = SwitchCompat(context).apply {
                        isChecked = initialVal == "true"
                        isClickable = false
                        val states = arrayOf(
                            intArrayOf(-android.R.attr.state_checked),
                            intArrayOf(android.R.attr.state_checked)
                        )
                        val thumbColors = intArrayOf(Color.parseColor("#E5E5EA"), Color.parseColor("#FFFFFF"))
                        val trackColors = intArrayOf(Color.parseColor("#48484A"), Color.parseColor("#CCCCCC"))
                        thumbTintList = android.content.res.ColorStateList(states, thumbColors)
                        trackTintList = android.content.res.ColorStateList(states, trackColors)
                    }
                    container.addView(switchView)

                    trailingView = container
                    addView(container)

                    setOnClickListener {
                        switchView.isChecked = !switchView.isChecked
                        onCheckedChange?.invoke(switchView.isChecked)
                    }
                }
                TrailingType.VALUE -> {
                    valueBadge = TextView(context).apply {
                        text = initialVal
                        textSize = 16f
                        setTextColor(Color.parseColor("#878787"))
                        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
                        layoutParams = LayoutParams(
                            LayoutParams.WRAP_CONTENT,
                            LayoutParams.WRAP_CONTENT
                        ).apply {
                            leftMargin = dpToPx(12f)
                        }
                    }
                    trailingView = valueBadge
                    addView(valueBadge)
                    setOnClickListener { onClick?.invoke() }
                }
                else -> {}
            }
        }

        fun updateValue(newValue: String) {
            valueBadge?.text = newValue
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        try {
            val rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dpToPx(24f), dpToPx(16f), dpToPx(24f), dpToPx(40f))
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
                    topMargin = dpToPx(24f)
                    bottomMargin = dpToPx(16f)
                }
            }

            val backButton = android.widget.ImageView(this).apply {
                val arrowDrawable = CustomIconDrawable(this@BackgroundSettingsActivity, CustomIconDrawable.IconType.ARROW_LEFT)
                setImageDrawable(arrowDrawable)
                val size = dpToPx(32f)
                layoutParams = android.widget.RelativeLayout.LayoutParams(size, size).apply {
                    addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT)
                    addRule(android.widget.RelativeLayout.CENTER_VERTICAL)
                }
                setPadding(dpToPx(4f), dpToPx(4f), dpToPx(4f), dpToPx(4f))
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
                    setPadding(dpToPx(12f), dpToPx(24f), dpToPx(12f), dpToPx(8f))
                }
                rootLayout.addView(header)
            }

            addSectionHeader("Visual Style")
            val card1 = SettingsCard(this).apply {
                addRow(SettingsRow(
                    this@BackgroundSettingsActivity,
                    CustomIconDrawable.IconType.PALETTE,
                    "Dynamic Theming",
                    "Sync highlights with Material You palette",
                    TrailingType.SWITCH,
                    prefs.getBoolean("dynamic_theming", false).toString(),
                    onCheckedChange = { checked ->
                        prefs.edit().putBoolean("dynamic_theming", checked).apply()
                    }
                ))

                val initialRadius = prefs.getFloat("album_corner_radius", 48f).toInt()
                lateinit var radiusRow: SettingsRow
                radiusRow = SettingsRow(
                    this@BackgroundSettingsActivity,
                    CustomIconDrawable.IconType.CORNER,
                    "Album Corner Radius",
                    "Modify corners from sharp to circular",
                    TrailingType.VALUE,
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

                addRow(SettingsRow(
                    this@BackgroundSettingsActivity,
                    CustomIconDrawable.IconType.IMAGE,
                    "Static Background",
                    "Render static blurred artwork without fluid animations",
                    TrailingType.SWITCH,
                    prefs.getBoolean("static_bg", false).toString(),
                    onCheckedChange = { checked ->
                        prefs.edit().putBoolean("static_bg", checked).apply()
                    }
                ))
            }
            rootLayout.addView(card1)

            addSectionHeader("Performance")
            val card2 = SettingsCard(this).apply {
                val initialSpeed = prefs.getFloat("bg_speed", 1.0f)
                lateinit var speedRow: SettingsRow
                speedRow = SettingsRow(
                    this@BackgroundSettingsActivity,
                    CustomIconDrawable.IconType.GAUGE,
                    "Background Speed",
                    "Control velocity of background liquid",
                    TrailingType.VALUE,
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

                addRow(SettingsRow(
                    this@BackgroundSettingsActivity,
                    CustomIconDrawable.IconType.LIST_MUSIC,
                    "Album/Title/Artist Only Mode",
                    "Disable lyrics fetching and rendering to conserve battery and data",
                    TrailingType.SWITCH,
                    prefs.getBoolean("metadata_only_mode", false).toString(),
                    onCheckedChange = { checked ->
                        prefs.edit().putBoolean("metadata_only_mode", checked).apply()
                    }
                ))
            }
            rootLayout.addView(card2)

            val scrollView = android.widget.ScrollView(this).apply {
                isFillViewport = true
            }
            scrollView.addView(rootLayout)
            setContentView(scrollView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
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

    private fun showCustomEditDialog(
        title: String,
        initialVal: String,
        minVal: Float,
        maxVal: Float,
        isFloat: Boolean,
        unit: String,
        onValueSaved: (Float) -> Unit
    ) {
        val dialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setCancelable(true)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24f), dpToPx(24f), dpToPx(24f), dpToPx(20f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = dpToPx(16f).toFloat()
            }
        }

        val titleText = TextView(this).apply {
            text = if (unit.isNotEmpty()) "$title ($unit)" else title
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(0, 0, 0, dpToPx(16f))
        }
        container.addView(titleText)

        val inputEdit = EditText(this).apply {
            setText(initialVal)
            inputType = if (isFloat) {
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            } else {
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            }
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(dpToPx(16f), dpToPx(12f), dpToPx(16f), dpToPx(12f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#242424"))
                cornerRadius = dpToPx(10f).toFloat()
                setStroke(dpToPx(1f), Color.parseColor("#444444"))
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
            setPadding(0, dpToPx(20f), 0, 0)
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
}
