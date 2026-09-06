package com.dnk.wallpaperlyrics

import android.content.Context
import android.content.Intent
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
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import com.dnk.wallpaperlyrics.LyricsSettings as LS

class EnhancedLyricsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        try {
            val rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(LS.dpToPx(this@EnhancedLyricsActivity, 24f), LS.dpToPx(this@EnhancedLyricsActivity, 16f), LS.dpToPx(this@EnhancedLyricsActivity, 24f), LS.dpToPx(this@EnhancedLyricsActivity, 40f))
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
                    topMargin = LS.dpToPx(this@EnhancedLyricsActivity, 24f)
                    bottomMargin = LS.dpToPx(this@EnhancedLyricsActivity, 16f)
                }
            }

            val backButton = android.widget.ImageView(this).apply {
                val arrowDrawable = LS.CustomIconDrawable(this@EnhancedLyricsActivity, LS.IconType.ARROW_LEFT)
                setImageDrawable(arrowDrawable)
                val size = LS.dpToPx(this@EnhancedLyricsActivity, 32f)
                layoutParams = android.widget.RelativeLayout.LayoutParams(size, size).apply {
                    addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT)
                    addRule(android.widget.RelativeLayout.CENTER_VERTICAL)
                }
                setPadding(LS.dpToPx(this@EnhancedLyricsActivity, 4f), LS.dpToPx(this@EnhancedLyricsActivity, 4f), LS.dpToPx(this@EnhancedLyricsActivity, 4f), LS.dpToPx(this@EnhancedLyricsActivity, 4f))
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
                    setPadding(LS.dpToPx(this@EnhancedLyricsActivity, 12f), LS.dpToPx(this@EnhancedLyricsActivity, 24f), LS.dpToPx(this@EnhancedLyricsActivity, 12f), LS.dpToPx(this@EnhancedLyricsActivity, 8f))
                }
                rootLayout.addView(header)
            }

            addSectionHeader("Custom Provider Overrides")
            val card1 = LS.SettingsCard(this).apply {
                addRow(LS.SettingsRow(
                    this@EnhancedLyricsActivity,
                    LS.IconType.LIST_MUSIC,
                    "Enable Custom Provider",
                    "Query custom API override before falling back to syncedlyrics",
                    LS.TrailingType.SWITCH,
                    prefs.getBoolean("custom_lyrics_enabled", false).toString(),
                    onCheckedChange = { checked ->
                        prefs.edit().putBoolean("custom_lyrics_enabled", checked).apply()
                    }
                ))

                val initialEndpoint = prefs.getString("custom_lyrics_endpoint", "http://10.0.2.2:8000/api/lyrics") ?: "http://10.0.2.2:8000/api/lyrics"
                lateinit var endpointRow: LS.SettingsRow
                endpointRow = LS.SettingsRow(
                    this@EnhancedLyricsActivity,
                    LS.IconType.LINK,
                    "API Endpoint URL",
                    "REST URL endpoint for custom lyrics fetching",
                    LS.TrailingType.VALUE,
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

                val initialFormat = prefs.getString("custom_lyrics_format", "LRC") ?: "LRC"
                lateinit var formatRow: LS.SettingsRow
                formatRow = LS.SettingsRow(
                    this@EnhancedLyricsActivity,
                    LS.IconType.BRACES,
                    "Response Format",
                    "Expected payload response container",
                    LS.TrailingType.VALUE,
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

                val initialTimeout = prefs.getFloat("custom_lyrics_timeout", 60f)
                lateinit var timeoutRow: LS.SettingsRow
                timeoutRow = LS.SettingsRow(
                    this@EnhancedLyricsActivity,
                    LS.IconType.TIMER,
                    "Request Timeout",
                    "Maximum execution timeout waiting for response",
                    LS.TrailingType.VALUE,
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
            setPadding(LS.dpToPx(this@EnhancedLyricsActivity, 24f), LS.dpToPx(this@EnhancedLyricsActivity, 24f), LS.dpToPx(this@EnhancedLyricsActivity, 24f), LS.dpToPx(this@EnhancedLyricsActivity, 20f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = LS.dpToPx(this@EnhancedLyricsActivity, 16f).toFloat()
            }
        }

        val titleText = TextView(this).apply {
            text = if (unit.isNotEmpty()) "$title ($unit)" else title
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(0, 0, 0, LS.dpToPx(this@EnhancedLyricsActivity, 16f))
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
            setPadding(LS.dpToPx(this@EnhancedLyricsActivity, 16f), LS.dpToPx(this@EnhancedLyricsActivity, 12f), LS.dpToPx(this@EnhancedLyricsActivity, 16f), LS.dpToPx(this@EnhancedLyricsActivity, 12f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#242424"))
                cornerRadius = LS.dpToPx(this@EnhancedLyricsActivity, 10f).toFloat()
                setStroke(LS.dpToPx(this@EnhancedLyricsActivity, 1f), Color.parseColor("#444444"))
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
            setPadding(0, LS.dpToPx(this@EnhancedLyricsActivity, 20f), 0, 0)
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
            setPadding(LS.dpToPx(this@EnhancedLyricsActivity, 24f), LS.dpToPx(this@EnhancedLyricsActivity, 24f), LS.dpToPx(this@EnhancedLyricsActivity, 24f), LS.dpToPx(this@EnhancedLyricsActivity, 20f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = LS.dpToPx(this@EnhancedLyricsActivity, 16f).toFloat()
            }
        }

        val titleText = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(0, 0, 0, LS.dpToPx(this@EnhancedLyricsActivity, 16f))
        }
        container.addView(titleText)

        options.forEach { (displayName, value) ->
            val optionLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val p12 = LS.dpToPx(this@EnhancedLyricsActivity, 12f)
                val p16 = LS.dpToPx(this@EnhancedLyricsActivity, 16f)
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
                background = LS.CustomIconDrawable(this@EnhancedLyricsActivity, LS.IconType.BRACES)
                layoutParams = LinearLayout.LayoutParams(LS.dpToPx(this@EnhancedLyricsActivity, 24f), LS.dpToPx(this@EnhancedLyricsActivity, 24f)).apply {
                    rightMargin = LS.dpToPx(this@EnhancedLyricsActivity, 16f)
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
                    val checkDrawable = LS.CustomIconDrawable(this@EnhancedLyricsActivity, LS.IconType.CHECK)
                    setImageDrawable(checkDrawable)
                    layoutParams = LinearLayout.LayoutParams(LS.dpToPx(this@EnhancedLyricsActivity, 20f), LS.dpToPx(this@EnhancedLyricsActivity, 20f))
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
}
