package com.dnk.wallpaperlyrics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat

/**
 * Rows, cards and icons shared by MainActivity, BackgroundSettingsActivity and
 * EnhancedLyricsActivity, which were all carrying their own copies of them.
 */
object LyricsSettings {

    // The union of every icon the three settings screens use.
    enum class IconType {
        BELL, IMAGE, PALETTE, CORNER, CLOCK, GAUGE, RELOAD, EDIT, DELETE, BLUETOOTH,
        GITHUB, BUG, COPYRIGHT, INFO, CHECK, FILE_STACK, SQUARE_PLAY, SPOTIFY, TIDAL,
        KDECONNECT, LIST_MUSIC, SLIDERS, ARROW_LEFT, LINK, BRACES, TIMER
    }

    enum class TrailingType { CHEVRON, SWITCH, VALUE, CHECK, NONE }

    fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics).toInt()
    }

    class CustomIconDrawable(
        private val context: Context,
        private val iconType: IconType
    ) : android.graphics.drawable.Drawable() {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dpToPx(context, 2f).toFloat()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun draw(canvas: Canvas) {
            val bounds = bounds
            if (iconType == IconType.DELETE) {
                paint.color = Color.parseColor("#FF453A")
            } else if (iconType == IconType.CHECK) {
                paint.color = Color.parseColor("#30D158")
            } else {
                paint.color = Color.parseColor("#CCCCCC")
            }

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
                    IconType.BELL -> drawPaths(canvas, listOf(
                        "M10.268 21a2 2 0 0 0 3.464 0",
                        "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326"
                    ))
                    IconType.IMAGE -> drawPaths(canvas, listOf(
                        "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
                        "M9 7a2 2 0 1 0 0 4 2 2 0 1 0 0-4",
                        "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"
                    ))
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
                    IconType.CORNER -> drawPaths(canvas, listOf(
                        "M21 11a8 8 0 0 0-8-8",
                        "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"
                    ))
                    IconType.CLOCK -> drawPaths(canvas, listOf(
                        "M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20",
                        "M12 6v6h4"
                    ))
                    IconType.GAUGE -> drawPaths(canvas, listOf(
                        "m12 14 4-4",
                        "M3.34 19a10 10 0 1 1 17.32 0"
                    ))
                    IconType.RELOAD -> drawPaths(canvas, listOf(
                        "M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8",
                        "M21 3v5h-5"
                    ))
                    IconType.EDIT -> drawPaths(canvas, listOf(
                        "M12 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7",
                        "M18.375 2.625a1 1 0 0 1 3 3l-9.013 9.014a2 2 0 0 1-.853.505l-2.873.84a.5.5 0 0 1-.62-.62l.84-2.873a2 2 0 0 1 .506-.852z"
                    ))
                    IconType.DELETE -> drawPaths(canvas, listOf(
                        "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
                        "M3 6h18",
                        "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"
                    ))
                    IconType.BLUETOOTH -> drawPaths(canvas, listOf(
                        "m7 7 10 10-5 5V2l5 5L7 17"
                    ))
                    IconType.GITHUB -> drawPaths(canvas, listOf(
                        "M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4",
                        "M9 18c-4.51 2-5-2-7-2"
                    ))
                    IconType.BUG -> drawPaths(canvas, listOf(
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
                    IconType.COPYRIGHT -> drawPaths(canvas, listOf(
                        "M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20",
                        "M14.83 14.83a4 4 0 1 1 0-5.66"
                    ))
                    IconType.INFO -> drawPaths(canvas, listOf(
                        "M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20",
                        "M12 16v-4",
                        "M12 8h.01"
                    ))
                    IconType.CHECK -> drawPaths(canvas, listOf(
                        "M20 6 9 17l-5-5"
                    ))
                    IconType.FILE_STACK -> drawPaths(canvas, listOf(
                        "M11 21a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-8a1 1 0 0 1 1-1",
                        "M16 16a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1",
                        "M21 6a2 2 0 0 0-.586-1.414l-2-2A2 2 0 0 0 17 2h-3a1 1 0 0 0-1 1v8a1 1 0 0 0 1 1h6a1 1 0 0 0 1-1z"
                    ))
                    IconType.SQUARE_PLAY -> drawPaths(canvas, listOf(
                        "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
                        "M9 9.003a1 1 0 0 1 1.517-.859l4.997 2.997a1 1 0 0 1 0 1.718l-4.997 2.997A1 1 0 0 1 9 14.996z"
                    ))
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
                    IconType.KDECONNECT -> drawPaths(canvas, listOf(
                        "M6.75 5.75h10.5M7.5 2.75h9c0.6925 0 1.25 0.5575 1.25 1.25v16c0 0.6925-0.5575 1.25-1.25 1.25H7.5c-0.6925 0-1.25-0.5575-1.25-1.25V4c0-0.6925 0.5575-1.25 1.25-1.25zm-0.75 15.5h10.5",
                        "M10.5 9.59375v4.75m3.09375-4.6875L11.8875 11.915l1.70625 2.49125"
                    ))
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
                    IconType.SLIDERS -> drawPaths(canvas, listOf(
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
                    IconType.ARROW_LEFT -> drawPaths(canvas, listOf(
                        "M19 12H5",
                        "m12 19-7-7 7-7"
                    ))
                    IconType.LINK -> drawPaths(canvas, listOf(
                        "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71",
                        "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"
                    ))
                    IconType.BRACES -> drawPaths(canvas, listOf(
                        "M8 3H7a2 2 0 0 0-2 2v5a2 2 0 0 1-2 2 2 2 0 0 1 2 2v5c0 1.1.9 2 2 2h1",
                        "M16 21h1a2 2 0 0 0 2-2v-5c0-1.1.9-2 2-2a2 2 0 0 1-2-2V5a2 2 0 0 0-2-2h-1"
                    ))
                    IconType.TIMER -> {
                        drawPaths(canvas, listOf(
                            "M10 2h4",
                            "M12 14l3-3"
                        ))
                        canvas.drawCircle(12f, 14f, 8f, paint)
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

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    class SettingsCard(context: Context) : LinearLayout(context) {
        init {
            orientation = VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = dpToPx(context, 18f).toFloat()
            }
            clipToOutline = true
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(context, 20f)
            }
        }

        fun addRow(row: SettingsRow) {
            if (childCount > 0) {
                val divider = View(context).apply {
                    setBackgroundColor(Color.parseColor("#444444"))
                    layoutParams = LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        dpToPx(context, 1f)
                    ).apply {
                        leftMargin = dpToPx(context, 56f)
                        rightMargin = dpToPx(context, 16f)
                    }
                }
                addView(divider)
            }
            addView(row)
        }
    }

    class SettingsRow(
        context: Context,
        val iconType: IconType,
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
            val padding16 = dpToPx(context, 16f)
            val padding18 = dpToPx(context, 18f)
            setPadding(padding16, padding18, padding16, padding18)
            isClickable = true

            val outVal = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outVal, true)
            setBackgroundResource(outVal.resourceId)

            val iconView = View(context).apply {
                background = CustomIconDrawable(context, iconType)
                layoutParams = LayoutParams(dpToPx(context, 24f), dpToPx(context, 24f)).apply {
                    rightMargin = dpToPx(context, 16f)
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
                if (iconType == IconType.DELETE) {
                    setTextColor(Color.parseColor("#FF453A"))
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
                setPadding(0, 2, dpToPx(context, 8f), 0)
                visibility = if (subtitle.isEmpty()) View.GONE else View.VISIBLE
            }
            textLayout.addView(rowSubtitleView)
            addView(textLayout)
            setupTrailing(context)
        }

        fun setTrailing(type: TrailingType, value: String = "") {
            if (trailingType != type) {
                trailingType = type
                setupTrailing(context)
                if (type == TrailingType.VALUE) updateValue(value)
            }
        }

        private fun setupTrailing(context: Context) {
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
                    trailingView = switchView
                    addView(switchView)
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
                            leftMargin = dpToPx(context, 12f)
                        }
                    }
                    trailingView = valueBadge
                    addView(valueBadge)
                    setOnClickListener { onClick?.invoke() }
                }
                TrailingType.CHECK -> {
                    val checkDrawable = CustomIconDrawable(context, IconType.CHECK)
                    val checkView = android.widget.ImageView(context).apply {
                        setImageDrawable(checkDrawable)
                        layoutParams = LayoutParams(dpToPx(context, 24f), dpToPx(context, 24f)).apply {
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

        fun updateValue(newValue: String) { valueBadge?.text = newValue }
        fun updateSubtitle(newSubtitle: String) {
            rowSubtitleView?.apply {
                text = newSubtitle
                visibility = if (newSubtitle.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }
}
