package com.dnk.wallpaperlyrics

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class EnhancedLyricsActivity : AppCompatActivity() {

    enum class TrailingType { CHEVRON, SWITCH, VALUE, NONE }

    class CustomIconDrawable(private val context: Context, private val iconType: IconType) : android.graphics.drawable.Drawable() {
        enum class IconType { BELL, IMAGE, PALETTE, CORNER, CLOCK, GAUGE, RELOAD, EDIT, DELETE, BLUETOOTH, GITHUB, BUG, COPYRIGHT, INFO, CHECK, FILE_STACK, SQUARE_PLAY, SPOTIFY, TIDAL, LIST_MUSIC, SLIDERS, ARROW_LEFT, LINK, BRACES, TIMER }
        
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
                    IconType.RELOAD -> {
                        drawPaths(canvas, listOf(
                            "M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8",
                            "M21 3v5h-5"
                        ))
                    }
                    IconType.CLOCK -> {
                        drawPaths(canvas, listOf(
                            "M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20",
                            "M12 6v6h4"
                        ))
                    }
                    IconType.FILE_STACK -> {
                        drawPaths(canvas, listOf(
                            "M11 21a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-8a1 1 0 0 1 1-1",
                            "M16 16a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1",
                            "M21 6a2 2 0 0 0-.586-1.414l-2-2A2 2 0 0 0 17 2h-3a1 1 0 0 0-1 1v8a1 1 0 0 0 1 1h6a1 1 0 0 0 1-1z"
                        ))
                    }
                    IconType.ARROW_LEFT -> {
                        drawPaths(canvas, listOf(
                            "M19 12H5",
                            "m12 19-7-7 7-7"
                        ))
                    }
                    IconType.CHECK -> {
                        drawPaths(canvas, listOf(
                            "M20 6 9 17l-5-5"
                        ))
                    }
                    IconType.LINK -> {
                        drawPaths(canvas, listOf(
                            "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71",
                            "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"
                        ))
                    }
                    IconType.BRACES -> {
                        drawPaths(canvas, listOf(
                            "M8 3H7a2 2 0 0 0-2 2v5a2 2 0 0 1-2 2 2 2 0 0 1 2 2v5c0 1.1.9 2 2 2h1",
                            "M16 21h1a2 2 0 0 0 2-2v-5c0-1.1.9-2 2-2a2 2 0 0 1-2-2V5a2 2 0 0 0-2-2h-1"
                        ))
                    }
                    IconType.TIMER -> {
                        drawPaths(canvas, listOf(
                            "M10 2h4",
                            "M12 14l3-3"
                        ))
                        canvas.drawCircle(12f, 14f, 8f, paint)
                    }
                    else -> {}
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
                val arrowDrawable = CustomIconDrawable(this@EnhancedLyricsActivity, CustomIconDrawable.IconType.ARROW_LEFT)
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
                text = "Custom Lyrics Provider"
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

            addSectionHeader("Custom Provider Overrides")
            val card1 = SettingsCard(this).apply {
                // Enable Custom Provider
                addRow(SettingsRow(
                    this@EnhancedLyricsActivity,
                    CustomIconDrawable.IconType.LIST_MUSIC,
                    "Enable Custom Provider",
                    "Query custom API override before falling back to syncedlyrics",
                    TrailingType.SWITCH,
                    prefs.getBoolean("custom_lyrics_enabled", false).toString(),
                    onCheckedChange = { checked ->
                        prefs.edit().putBoolean("custom_lyrics_enabled", checked).apply()
                    }
                ))

                // Endpoint URL
                val initialEndpoint = prefs.getString("custom_lyrics_endpoint", "http://10.0.2.2:8000/api/lyrics") ?: "http://10.0.2.2:8000/api/lyrics"
                lateinit var endpointRow: SettingsRow
                endpointRow = SettingsRow(
                    this@EnhancedLyricsActivity,
                    CustomIconDrawable.IconType.LINK,
                    "API Endpoint URL",
                    "REST URL endpoint for custom lyrics fetching",
                    TrailingType.VALUE,
                    initialEndpoint,
                    onClick = {
                        val currentEndpoint = prefs.getString("custom_lyrics_endpoint", "http://10.0.2.2:8000/api/lyrics") ?: "http://10.0.2.2:8000/api/lyrics"
                        showCustomEditDialog("API Endpoint", currentEndpoint, isNumber = false, isFloat = false, 0f, 0f, "") { newVal ->
                            prefs.edit().putString("custom_lyrics_endpoint", newVal).apply()
                            endpointRow.updateValue(newVal)
                        }
                    }
                )
                addRow(endpointRow)

                // Response Format
                val initialFormat = prefs.getString("custom_lyrics_format", "LRC") ?: "LRC"
                lateinit var formatRow: SettingsRow
                formatRow = SettingsRow(
                    this@EnhancedLyricsActivity,
                    CustomIconDrawable.IconType.BRACES,
                    "Response Format",
                    "Expected payload response container",
                    TrailingType.VALUE,
                    initialFormat,
                    onClick = {
                        val currentFormat = prefs.getString("custom_lyrics_format", "LRC") ?: "LRC"
                        showOptionPickerDialog(
                            "Response Format",
                            listOf(Pair("Raw LRC Text", "LRC"), Pair("JSON Object", "JSON")),
                            currentFormat
                        ) { selectedVal ->
                            prefs.edit().putString("custom_lyrics_format", selectedVal).apply()
                            formatRow.updateValue(selectedVal)
                        }
                    }
                )
                addRow(formatRow)

                // Timeout
                val initialTimeout = prefs.getFloat("custom_lyrics_timeout", 60f)
                lateinit var timeoutRow: SettingsRow
                timeoutRow = SettingsRow(
                    this@EnhancedLyricsActivity,
                    CustomIconDrawable.IconType.TIMER,
                    "Request Timeout",
                    "Maximum execution timeout waiting for response",
                    TrailingType.VALUE,
                    "${initialTimeout.toInt()}s",
                    onClick = {
                        val currentTimeout = prefs.getFloat("custom_lyrics_timeout", 60f)
                        showCustomEditDialog("API Timeout", currentTimeout.toInt().toString(), isNumber = true, isFloat = false, 5f, 300f, "seconds") { newVal ->
                            val secVal = newVal.toFloatOrNull() ?: 60f
                            prefs.edit().putFloat("custom_lyrics_timeout", secVal).apply()
                            timeoutRow.updateValue("${secVal.toInt()}s")
                        }
                    }
                )
                addRow(timeoutRow)
            }
            rootLayout.addView(card1)

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

            val iconView = View(this).apply {
                background = CustomIconDrawable(this@EnhancedLyricsActivity, CustomIconDrawable.IconType.BRACES)
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
                    val checkDrawable = CustomIconDrawable(this@EnhancedLyricsActivity, CustomIconDrawable.IconType.CHECK)
                    setImageDrawable(checkDrawable)
                    layoutParams = LinearLayout.LayoutParams(dpToPx(20f), dpToPx(20f))
                }
                optionLayout.addView(checkView)
            }

            container.addView(optionLayout)
        }

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

    private fun showCustomEditDialog(
        title: String,
        initialVal: String,
        isNumber: Boolean,
        isFloat: Boolean,
        minVal: Float,
        maxVal: Float,
        unit: String,
        onValueSaved: (String) -> Unit
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
            inputType = if (isNumber) {
                if (isFloat) {
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                } else {
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                }
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            }
            setTextColor(Color.WHITE)
            textSize = 16f
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
                val textStr = inputEdit.text.toString().trim()
                if (isNumber) {
                    val floatVal = textStr.toFloatOrNull()
                    if (floatVal != null) {
                        val clamped = floatVal.coerceIn(minVal, maxVal)
                        onValueSaved(clamped.toString())
                    }
                } else {
                    onValueSaved(textStr)
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
