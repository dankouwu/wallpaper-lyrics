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

class MainActivity : AppCompatActivity() {

    private lateinit var notificationRow: SettingsRow
    private lateinit var wallpaperRow: SettingsRow
    private lateinit var songOffsetRow: SettingsRow
    private var palettePreview: ColorPalettePreviewView? = null

    enum class TrailingType { CHEVRON, SWITCH, VALUE, CHECK, NONE }

    class CustomIconDrawable(private val context: Context, private val iconType: IconType) : android.graphics.drawable.Drawable() {
        enum class IconType { BELL, IMAGE, PALETTE, CORNER, CLOCK, GAUGE, RELOAD, EDIT, DELETE, BLUETOOTH, GITHUB, BUG, COPYRIGHT, INFO, CHECK, FILE_STACK, SQUARE_PLAY, SPOTIFY, TIDAL, KDECONNECT, LIST_MUSIC, SLIDERS, ARROW_LEFT }
        
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                2f, // 2dp (Regular weight)
                context.resources.displayMetrics
            )
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun draw(canvas: Canvas) {
            val bounds = bounds
            
            if (iconType == IconType.DELETE) {
                paint.color = Color.parseColor("#FF453A") // Red for destructive actions
            } else if (iconType == IconType.CHECK) {
                paint.color = Color.parseColor("#30D158") // IOS success green
            } else {
                paint.color = Color.parseColor("#CCCCCC") // Light grey outline
            }

            // Uniform scale factor so all icons are exactly the same size
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
                    IconType.BELL -> {
                        drawPaths(canvas, listOf(
                            "M10.268 21a2 2 0 0 0 3.464 0",
                            "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326"
                        ))
                    }
                    IconType.IMAGE -> {
                        drawPaths(canvas, listOf(
                            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
                            "M9 7a2 2 0 1 0 0 4 2 2 0 1 0 0-4",
                            "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"
                        ))
                    }
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
                    IconType.CLOCK -> {
                        drawPaths(canvas, listOf(
                            "M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20",
                            "M12 6v6h4"
                        ))
                    }
                    IconType.GAUGE -> {
                        drawPaths(canvas, listOf(
                            "m12 14 4-4",
                            "M3.34 19a10 10 0 1 1 17.32 0"
                        ))
                    }
                    IconType.RELOAD -> {
                        drawPaths(canvas, listOf(
                            "M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8",
                            "M21 3v5h-5"
                        ))
                    }
                    IconType.EDIT -> {
                        drawPaths(canvas, listOf(
                            "M12 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7",
                            "M18.375 2.625a1 1 0 0 1 3 3l-9.013 9.014a2 2 0 0 1-.853.505l-2.873.84a.5.5 0 0 1-.62-.62l.84-2.873a2 2 0 0 1 .506-.852z"
                        ))
                    }
                    IconType.DELETE -> {
                        drawPaths(canvas, listOf(
                            "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
                            "M3 6h18",
                            "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"
                        ))
                    }
                    IconType.BLUETOOTH -> {
                        drawPaths(canvas, listOf(
                            "m7 7 10 10-5 5V2l5 5L7 17"
                        ))
                    }
                    IconType.GITHUB -> {
                        drawPaths(canvas, listOf(
                            "M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4",
                            "M9 18c-4.51 2-5-2-7-2"
                        ))
                    }
                    IconType.BUG -> {
                        drawPaths(canvas, listOf(
                            "M12 20v-9",
                            "M14 7a4 4 0 0 1 4 4v3a6 6 0 0 1-12 0v-3a4 4 0 0 1 4-4z",
                            "M14.12 3.88 16 2",
                            "M21 21a4 4 0 0 0-3.81-4",
                            "M21 5a4 4 0 0 1-3.55 3.97",
                            "M22 13h-4",
                            "M3 21a4 4 0 0 1 3.81-4",
                            "M3 5a4 4 0 0 0 3.55 3.97",
                            "M6 13H2",
                            "m8 2 1.88 1.88",
                            "M9 7.13V6a3 3 0 1 1 6 0v1.13"
                        ))
                    }
                    IconType.COPYRIGHT -> {
                        drawPaths(canvas, listOf(
                            "M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20",
                            "M14.83 14.83a4 4 0 1 1 0-5.66"
                        ))
                    }
                    IconType.INFO -> {
                        drawPaths(canvas, listOf(
                            "M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20",
                            "M12 16v-4",
                            "M12 8h.01"
                        ))
                    }
                    IconType.CHECK -> {
                        drawPaths(canvas, listOf(
                            "M20 6 9 17l-5-5"
                        ))
                    }
                    IconType.FILE_STACK -> {
                        drawPaths(canvas, listOf(
                            "M11 21a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-8a1 1 0 0 1 1-1",
                            "M16 16a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1",
                            "M21 6a2 2 0 0 0-.586-1.414l-2-2A2 2 0 0 0 17 2h-3a1 1 0 0 0-1 1v8a1 1 0 0 0 1 1h6a1 1 0 0 0 1-1z"
                        ))
                    }
                    IconType.SQUARE_PLAY -> {
                        drawPaths(canvas, listOf(
                            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
                            "M9 9.003a1 1 0 0 1 1.517-.859l4.997 2.997a1 1 0 0 1 0 1.718l-4.997 2.997A1 1 0 0 1 9 14.996z"
                        ))
                    }
                    IconType.SPOTIFY -> {
                        canvas.drawCircle(12f, 12f, 9.5f, paint)
                        drawPaths(canvas, listOf(
                            "M7.5 9c2.5-1 6.5-1 9 0",
                            "M8.5 12c2-.7 5-.7 7 0",
                            "M9.5 15c1.5-.5 3.5-.5 5 0"
                        ))
                    }
                    IconType.TIDAL -> {
                        paint.style = Paint.Style.FILL
                        drawPaths(canvas, listOf(
                            "M12 5l3.5 3.5l-3.5 3.5l-3.5-3.5z",
                            "M5 5l3.5 3.5l-3.5 3.5l-3.5-3.5z",
                            "M19 5l3.5 3.5l-3.5 3.5l-3.5-3.5z",
                            "M12 12l3.5 3.5l-3.5 3.5l-3.5-3.5z"
                        ))
                        paint.style = Paint.Style.STROKE
                    }
                    IconType.KDECONNECT -> {
                        drawPaths(canvas, listOf(
                            "M6.75 5.75h10.5M7.5 2.75h9c0.6925 0 1.25 0.5575 1.25 1.25v16c0 0.6925-0.5575 1.25-1.25 1.25H7.5c-0.6925 0-1.25-0.5575-1.25-1.25V4c0-0.6925 0.5575-1.25 1.25-1.25zm-0.75 15.5h10.5",
                            "M10.5 9.59375v4.75m3.09375-4.6875L11.8875 11.915l1.70625 2.49125"
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
                    IconType.SLIDERS -> {
                        drawPaths(canvas, listOf(
                            "M10 5H3",
                            "M12 19H3",
                            "M14 3v4",
                            "M16 17v4",
                            "M21 12h-9",
                            "M21 19h-5",
                            "M21 5h-7",
                            "M8 10v4",
                            "M8 12H3"
                        ))
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
                setColor(Color.parseColor("#333333")) // Card Background #333333
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
                // Automate divider border between rows!
                val divider = View(context).apply {
                    setBackgroundColor(Color.parseColor("#444444")) // Divider gray
                    layoutParams = LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        dpToPx(1f)
                    ).apply {
                        leftMargin = dpToPx(56f) // Start exactly at text label position
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
                                setStroke(dpToPx(1f), Color.parseColor("#333333")) // Standout border
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
        private var rowSubtitleView: TextView? = null
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

            // Left Icon
            val iconView = View(context).apply {
                background = CustomIconDrawable(context, iconType)
                layoutParams = LayoutParams(dpToPx(24f), dpToPx(24f)).apply {
                    rightMargin = dpToPx(16f)
                }
            }
            addView(iconView)

            // Middle Text Block
            val textLayout = LinearLayout(context).apply {
                orientation = VERTICAL
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            }
            val rowTitle = TextView(context).apply {
                text = title
                textSize = 16f
                if (iconType == CustomIconDrawable.IconType.DELETE) {
                    setTextColor(Color.parseColor("#FF453A")) // Red text for destructive items
                } else {
                    setTextColor(Color.WHITE)
                }
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            }
            textLayout.addView(rowTitle)
            rowSubtitleView = TextView(context).apply {
                text = subtitle
                textSize = 12f
                setTextColor(Color.parseColor("#8E8E93"))
                setPadding(0, 2, dpToPx(8f), 0)
                visibility = if (subtitle.isEmpty()) View.GONE else View.VISIBLE
            }
            textLayout.addView(rowSubtitleView)
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
                        text = "›" // Unicode chevron matching reference screenshot
                        textSize = 24f
                        setTextColor(Color.parseColor("#CCCCCC")) // Accent light grey matching icon color
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
                        this@MainActivity.palettePreview = preview
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
                        setTextColor(Color.parseColor("#878787")) // Accent muted grey for values
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
                TrailingType.CHECK -> {
                    val checkDrawable = CustomIconDrawable(context, CustomIconDrawable.IconType.CHECK)
                    val checkView = android.widget.ImageView(context).apply {
                        setImageDrawable(checkDrawable)
                        layoutParams = LayoutParams(dpToPx(24f), dpToPx(24f)).apply {
                            gravity = Gravity.CENTER_VERTICAL
                        }
                    }
                    trailingView = checkView
                    addView(checkView)
                    setOnClickListener { onClick?.invoke() }
                }
                TrailingType.NONE -> {
                    trailingView = null
                    setOnClickListener { onClick?.invoke() }
                }
            }
        }

        fun updateValue(newValue: String) {
            valueBadge?.text = newValue
        }

        fun updateSubtitle(newSubtitle: String) {
            rowSubtitleView?.apply {
                text = newSubtitle
                visibility = if (newSubtitle.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        try {
            // Root Container
            val rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dpToPx(24f), dpToPx(16f), dpToPx(24f), dpToPx(40f))
                setBackgroundColor(Color.parseColor("#242424")) // Deep Grayscale Background #242424
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
                paint.isFakeBoldText = true // Enforces extra heavy font weight
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dpToPx(24f)
                    bottomMargin = dpToPx(16f)
                }
            }
            rootLayout.addView(titleView)

            // Section Header Builder
            fun addSectionHeader(title: String) {
                val header = TextView(this).apply {
                    text = title
                    textSize = 13f
                    setTextColor(Color.parseColor("#8E8E93")) // iOS Muted Grey
                    setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
                    setPadding(dpToPx(12f), dpToPx(24f), dpToPx(12f), dpToPx(8f))
                }
                rootLayout.addView(header)
            }

            // CARD 1: General Settings
            addSectionHeader("General")
            val card1 = SettingsCard(this).apply {
                // Row 1: Notification Permission
                notificationRow = SettingsRow(
                    this@MainActivity,
                    CustomIconDrawable.IconType.BELL,
                    "Notification Access",
                    "Required to read music session details",
                    TrailingType.CHEVRON,
                    onClick = {
                        try {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Could not open settings", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                addRow(notificationRow)
                
                // Row 2: Activate Live Wallpaper
                wallpaperRow = SettingsRow(
                    this@MainActivity,
                    CustomIconDrawable.IconType.IMAGE,
                    "Activate Live Wallpaper",
                    "Choose this wallpaper in picker menu",
                    TrailingType.CHEVRON,
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

                // Row 3: Background Settings
                addRow(SettingsRow(
                    this@MainActivity,
                    CustomIconDrawable.IconType.SLIDERS,
                    "Background Settings",
                    "Configure liquid wallpaper visuals",
                    TrailingType.CHEVRON,
                    onClick = {
                        startActivity(Intent(this@MainActivity, BackgroundSettingsActivity::class.java))
                    }
                ))

                // Row 4: Custom Lyrics Provider
                addRow(SettingsRow(
                    this@MainActivity,
                    CustomIconDrawable.IconType.LIST_MUSIC,
                    "Custom Lyrics Provider",
                    "Configure custom local/remote API override",
                    TrailingType.CHEVRON,
                    onClick = {
                        startActivity(Intent(this@MainActivity, EnhancedLyricsActivity::class.java))
                    }
                ))

                // Row: Persistent Controls Switch
                val persistentNotifRow = SettingsRow(
                    this@MainActivity,
                    CustomIconDrawable.IconType.BELL,
                    "Persistent Controls",
                    "Show control notification in status bar",
                    TrailingType.SWITCH,
                    initialVal = prefs.getBoolean("persistent_notification", false).toString(),
                    onCheckedChange = { isChecked ->
                        prefs.edit().putBoolean("persistent_notification", isChecked).apply()
                    }
                )
                addRow(persistentNotifRow)

                // Row 5: Preferred Media Player (conditional)
                val showPlayerSelection = listOf(isSpotifyInstalled(), isTidalInstalled(), isKdeConnectInstalled()).count { it } >= 2
                if (showPlayerSelection) {
                    val currentPref = prefs.getString("preferred_media_player", "default") ?: "default"
                    val displayValue = when (currentPref) {
                        "spotify" -> "Spotify"
                        "tidal" -> "Tidal"
                        "kdeconnect" -> "KDE Connect"
                        else -> "Default"
                    }
                    lateinit var playerRow: SettingsRow
                    playerRow = SettingsRow(
                        this@MainActivity,
                        CustomIconDrawable.IconType.SQUARE_PLAY,
                        "Preferred Media Player",
                        "Track lyrics from a specific player",
                        TrailingType.VALUE,
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
            }
            rootLayout.addView(card1)

            // CARD 2: Timing & Sync
            addSectionHeader("Timing & Speed")
            val card2 = SettingsCard(this).apply {
                // Row 1: Sync Offset
                val initialOffset = prefs.getInt("sync_offset", 0)
                lateinit var offsetRow: SettingsRow
                offsetRow = SettingsRow(
                    this@MainActivity,
                    CustomIconDrawable.IconType.CLOCK,
                    "Manual Sync Offset",
                    "Offset lyrics alignment manually",
                    TrailingType.VALUE,
                    "${initialOffset}ms",
                    onClick = {
                        val currentOffset = prefs.getInt("sync_offset", 0)
                        showCustomEditDialog("Set Sync Offset", currentOffset.toString(), -1000f, 1000f, false, "ms") { newVal ->
                            val offsetVal = newVal.toInt()
                            prefs.edit().putInt("sync_offset", offsetVal).apply()
                            offsetRow.updateValue("${offsetVal}ms")
                        }
                    }
                )
                addRow(offsetRow)

                // Row 2: Song Specific Delay
                val activeSong = getActiveSongMetadata()
                val (initSubtitle, initVal) = if (activeSong != null) {
                    val (title, artist) = activeSong
                    val songOffset = prefs.getInt("song_delay_${title}_${artist}", 0)
                    Pair("Offset for: $title - $artist", "${songOffset}ms")
                } else {
                    Pair("No active song playing", "0ms")
                }
                songOffsetRow = SettingsRow(
                    this@MainActivity,
                    CustomIconDrawable.IconType.CLOCK,
                    "Song Specific Delay",
                    initSubtitle,
                    TrailingType.VALUE,
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
            rootLayout.addView(card2)

            // CARD 3: Maintenance
            addSectionHeader("Maintenance")
            val card3 = SettingsCard(this).apply {
                // Row 1: Force Re-fetch current song lyrics
                addRow(SettingsRow(
                    this@MainActivity,
                    CustomIconDrawable.IconType.RELOAD,
                    "Force Re-fetch Lyrics",
                    "Purge cache and reload active song from server",
                    TrailingType.CHEVRON,
                    onClick = {
                        sendBroadcast(Intent("com.dnk.wallpaperlyrics.FORCE_RELOAD_LYRICS").apply {
                            setPackage(packageName)
                        })
                        Toast.makeText(this@MainActivity, "Re-fetch command sent", Toast.LENGTH_SHORT).show()
                    }
                ))

                // Row 2: Edit Synced Lyrics
                addRow(SettingsRow(
                    this@MainActivity,
                    CustomIconDrawable.IconType.EDIT,
                    "Edit Synced Lyrics",
                    "Override cached lyrics of current song manually",
                    TrailingType.CHEVRON,
                    onClick = {
                        openLyricsOverrideFlow()
                    }
                ))

                // Row 3: Clear Cache (Destructive Action - Aligned standard row colored red)
                addRow(SettingsRow(
                    this@MainActivity,
                    CustomIconDrawable.IconType.DELETE,
                    "Clear Lyrics Cache",
                    "Delete all locally saved lyrics files",
                    TrailingType.NONE,
                    onClick = {
                        showCustomConfirmDialog(
                            "Clear Lyrics Cache?",
                            "Proceeding will delete all cached lyrics. This action cannot be undone.",
                            "Clear All"
                        ) {
                            val cacheDir = java.io.File(cacheDir, "lyrics_cache")
                            if (cacheDir.exists()) {
                                cacheDir.deleteRecursively()
                                cacheDir.mkdirs()
                                Toast.makeText(this@MainActivity, "Cache cleared!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ))
            }
            rootLayout.addView(card3)

            // CARD 4: About Section
            addSectionHeader("About")
            val card4 = SettingsCard(this).apply {
                // Row 1: Version
                addRow(SettingsRow(
                    this@MainActivity,
                    CustomIconDrawable.IconType.FILE_STACK,
                    "Version",
                    "1.5.0",
                    TrailingType.NONE,
                    onClick = {}
                ))

                // Row 2: GitHub Repository
                addRow(SettingsRow(
                    this@MainActivity,
                    CustomIconDrawable.IconType.GITHUB,
                    "GitHub Repository",
                    "View source code and star the project",
                    TrailingType.CHEVRON,
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/dankouwu/wallpaper-lyrics"))
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Could not open repository link", Toast.LENGTH_SHORT).show()
                        }
                    }
                ))

                // Row 2: Report an Issue
                addRow(SettingsRow(
                    this@MainActivity,
                    CustomIconDrawable.IconType.BUG,
                    "Report an Issue",
                    "Submit bugs or request new features",
                    TrailingType.CHEVRON,
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/dankouwu/wallpaper-lyrics/issues"))
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Could not open issue link", Toast.LENGTH_SHORT).show()
                        }
                    }
                ))

                // Row 3: License
                addRow(SettingsRow(
                    this@MainActivity,
                    CustomIconDrawable.IconType.COPYRIGHT,
                    "License",
                    "MIT License terms",
                    TrailingType.CHEVRON,
                    onClick = {
                        showAboutDialog(
                            "MIT License",
                            "Copyright (c) 2026 dankouwu\n\nPermission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\nThe above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.\n\nTHE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED."
                        )
                    }
                ))

                // Row 4: Credits & Libraries
                addRow(SettingsRow(
                    this@MainActivity,
                    CustomIconDrawable.IconType.INFO,
                    "Credits & Libraries",
                    "Developer details and open-source packages",
                    TrailingType.CHEVRON,
                    onClick = {
                        showAboutDialog(
                            "Credits & Libraries",
                            "Developers & Contributors:\n- dankouwu\n- riveerxd\n\nLibraries Used:\n- AndroidX core & components\n- Gson (Google JSON serializer)\n- LRCLIB (Synced lyrics provider)\n- Lucide Icons (lucide.dev)\n\nThank you for using Wallpaper Lyrics!"
                        )
                    }
                ))
            }
            rootLayout.addView(card4)

            val scrollView = android.widget.ScrollView(this).apply {
                isFillViewport = true // Forces child content to stretch and fill full screen height, solving reverse overflow gap!
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

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (::notificationRow.isInitialized) {
            if (isNotificationServiceEnabled()) {
                notificationRow.setTrailing(TrailingType.CHECK)
            } else {
                notificationRow.setTrailing(TrailingType.CHEVRON)
            }
        }
        if (::wallpaperRow.isInitialized) {
            if (isWallpaperActive()) {
                wallpaperRow.setTrailing(TrailingType.CHECK)
            } else {
                wallpaperRow.setTrailing(TrailingType.CHEVRON)
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
        palettePreview?.refreshColors()
    }

    private fun getActiveSongMetadata(): Pair<String, String>? {
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
            colors.add(Color.parseColor("#30D158")) // Green
            colors.add(Color.parseColor("#0A84FF")) // Blue
            colors.add(Color.parseColor("#BF5AF2")) // Purple
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

        // Main Dialog Card
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24f), dpToPx(24f), dpToPx(24f), dpToPx(20f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333")) // theme matching dialog background #333333
                cornerRadius = dpToPx(16f).toFloat()
                // Removed border stroke
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
                setColor(Color.parseColor("#242424")) // matching base theme background #242424
                cornerRadius = dpToPx(10f).toFloat()
                setStroke(dpToPx(1f), Color.parseColor("#444444"))
            }
            setSelection(text.length)
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                textCursorDrawable = android.graphics.drawable.ColorDrawable(Color.parseColor("#b7b7b7"))
            }
        }
        container.addView(inputEdit)

        // Buttons row aligned to the right
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
            setTextColor(Color.parseColor("#E0E0E0")) // Accent Light Grey (slightly lighter than #CCCCCC)
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

    private fun showAboutDialog(title: String, message: String) {
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
            text = title
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(0, 0, 0, dpToPx(10f))
        }
        container.addView(titleText)

        val msgText = TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, 0, 0, dpToPx(20f))
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
            setPadding(dpToPx(24f), dpToPx(24f), dpToPx(24f), dpToPx(20f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333")) // theme matching dialog background #333333
                cornerRadius = dpToPx(16f).toFloat()
                // Removed border stroke
            }
        }

        val titleText = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(0, 0, 0, dpToPx(10f))
        }
        container.addView(titleText)

        val msgText = TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, 0, 0, dpToPx(20f))
        }
        container.addView(msgText)

        // Buttons row aligned to the right
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
            setTextColor(Color.parseColor("#FF453A")) // Destructive Red
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
        val componentName = ComponentName(this, NotificationService::class.java)
        val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val controllers = try {
            mediaSessionManager.getActiveSessions(componentName)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Notification permission required", Toast.LENGTH_SHORT).show()
            return
        }

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

        if (active == null) {
            Toast.makeText(this, "No active Spotify, Tidal or KDE Connect session found", Toast.LENGTH_SHORT).show()
            return
        }

        val metadata = active.metadata
        val title = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)

        if (title.isNullOrBlank() || artist.isNullOrBlank()) {
            Toast.makeText(this, "No active track metadata found", Toast.LENGTH_SHORT).show()
            return
        }

        val cacheKey = "${title}_${artist}".hashCode().toString()
        val cacheDir = java.io.File(cacheDir, "lyrics_cache")
        val cacheFile = java.io.File(cacheDir, "$cacheKey.json")
        val missFile = java.io.File(cacheDir, "$cacheKey.miss")

        var existingLrc = ""
        if (cacheFile.exists()) {
            try {
                val json = cacheFile.readText()
                val gson = com.google.gson.Gson()
                val lines = gson.fromJson(json, Array<LyricLine>::class.java).toList()
                val sb = StringBuilder()
                for (line in lines) {
                    if (line.isInstrumental && line.content == "♪") continue
                    val lineTimeStr = LyricsManager.formatTime(line.startTime)
                    val words = line.words
                    val hasRealWords = words != null && words.isNotEmpty() && words.any { !it.isEstimated }
                    if (hasRealWords && words != null) {
                        val lineSb = StringBuilder()
                        lineSb.append("[$lineTimeStr]")
                        for (index in words.indices) {
                            val word = words[index]
                            lineSb.append("<${LyricsManager.formatTime(word.startTime)}>${word.text}")
                            if (index == words.size - 1) {
                                lineSb.append("<${LyricsManager.formatTime(word.endTime)}>")
                            }
                        }
                        sb.append(lineSb.toString()).append("\n")
                    } else {
                        sb.append("[$lineTimeStr] ${line.content}\n")
                    }
                }
                existingLrc = sb.toString().trim()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        showLyricsEditDialog(title, artist, existingLrc) { newLrc ->
            if (newLrc.isBlank()) {
                if (cacheFile.exists()) cacheFile.delete()
                if (missFile.exists()) missFile.delete()
                Toast.makeText(this, "Override cleared!", Toast.LENGTH_SHORT).show()
            } else {
                val parsed = LyricsManager.parseLrcText(newLrc)
                if (parsed == null) {
                    Toast.makeText(this, "Invalid LRC format. No lines parsed.", Toast.LENGTH_SHORT).show()
                    return@showLyricsEditDialog
                }
                
                try {
                    cacheDir.mkdirs()
                    val gson = com.google.gson.Gson()
                    cacheFile.writeText(gson.toJson(parsed))
                    if (missFile.exists()) missFile.delete()
                    Toast.makeText(this, "Lyrics saved!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to save lyrics", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }

            sendBroadcast(Intent("com.dnk.wallpaperlyrics.RELOAD_LYRICS").apply {
                setPackage(packageName)
            })
        }
    }

    private fun showLyricsEditDialog(
        songTitle: String,
        songArtist: String,
        initialLrc: String,
        onLrcSaved: (String) -> Unit
    ) {
        val dialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setCancelable(true)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24f), dpToPx(24f), dpToPx(24f), dpToPx(20f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333")) // theme matching dialog background #333333
                cornerRadius = dpToPx(16f).toFloat()
                // Removed border stroke
            }
        }

        val titleText = TextView(this).apply {
            text = "Override Synced Lyrics"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(0, 0, 0, dpToPx(4f))
        }
        container.addView(titleText)

        val subtitleText = TextView(this).apply {
            text = "$songTitle - $songArtist"
            textSize = 13f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, 0, 0, dpToPx(16f))
        }
        container.addView(subtitleText)

        val inputEdit = EditText(this).apply {
            setText(initialLrc)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(Color.WHITE)
            textSize = 14f
            isVerticalScrollBarEnabled = true
            gravity = Gravity.TOP or Gravity.START
            setPadding(dpToPx(16f), dpToPx(12f), dpToPx(16f), dpToPx(12f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#242424"))
                cornerRadius = dpToPx(10f).toFloat()
            }
            
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(220f)
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
            setPadding(0, dpToPx(8f), 0, 0)
        }
        container.addView(tipText)

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dpToPx(16f), 0, 0)
        }

        val outVal = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, outVal, true)

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
            setTextColor(Color.parseColor("#E0E0E0")) // Accent Light Grey (slightly lighter than #CCCCCC)
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



    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
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
            setPadding(dpToPx(24f), dpToPx(24f), dpToPx(24f), dpToPx(20f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = dpToPx(16f).toFloat()
            }
        }

        val titleText = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(0, 0, 0, dpToPx(16f))
        }
        container.addView(titleText)

        options.forEach { (displayName, value) ->
            val optionLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val p12 = dpToPx(12f)
                val p16 = dpToPx(16f)
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
                "spotify" -> CustomIconDrawable.IconType.SPOTIFY
                "tidal" -> CustomIconDrawable.IconType.TIDAL
                "kdeconnect" -> CustomIconDrawable.IconType.KDECONNECT
                else -> CustomIconDrawable.IconType.LIST_MUSIC
            }

            val iconView = View(this).apply {
                background = CustomIconDrawable(this@MainActivity, iconType)
                layoutParams = LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f)).apply {
                    rightMargin = dpToPx(16f)
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
                    val checkDrawable = CustomIconDrawable(this@MainActivity, CustomIconDrawable.IconType.CHECK)
                    setImageDrawable(checkDrawable)
                    layoutParams = LinearLayout.LayoutParams(dpToPx(20f), dpToPx(20f))
                }
                optionLayout.addView(checkView)
            }

            container.addView(optionLayout)
        }

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dpToPx(16f), 0, 0)
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
