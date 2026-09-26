package com.dnk.wallpaperlyrics

import android.app.Activity
import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.session.MediaSessionManager
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.dnk.wallpaperlyrics.LyricsSettings as LS

object SettingsDialogs {

    fun showCustomEditDialog(
        activity: Activity,
        title: String,
        initialVal: String,
        minVal: Float,
        maxVal: Float,
        isFloat: Boolean,
        unit: String,
        hint: String = "",
        onValueSaved: (Float) -> Unit
    ) {
        val dialog = Dialog(activity).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(true)
        }

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(LS.dpToPx(activity, 24f), LS.dpToPx(activity, 24f), LS.dpToPx(activity, 24f), LS.dpToPx(activity, 20f))
            clipToPadding = false
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = LS.dpToPx(activity, 16f).toFloat()
            }
        }

        val titleText = TextView(activity).apply {
            text = if (unit.isNotEmpty()) "$title ($unit)" else title
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(0, 0, 0, LS.dpToPx(activity, 8f))
        }
        container.addView(titleText)

        if (hint.isNotEmpty()) {
            val hintText = TextView(activity).apply {
                text = hint
                setTextColor(Color.parseColor("#8E8E93"))
                textSize = 12f
            }
            container.addView(hintText)
        }

        val seekBar = LS.SettingsSlider(activity).apply {
            max = 1000
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LS.dpToPx(activity, 48f)
            ).apply {
                marginStart = LS.dpToPx(activity, -10f)
                marginEnd = LS.dpToPx(activity, -10f)
                topMargin = LS.dpToPx(activity, 12f)
                bottomMargin = LS.dpToPx(activity, -12f)
            }
            val parsedInitial = initialVal.toFloatOrNull()?.coerceIn(minVal, maxVal) ?: minVal
            progress = (((parsedInitial - minVal) / (maxVal - minVal)) * 1000f).toInt()
        }
        container.addView(seekBar)

        val rangeRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = LS.dpToPx(activity, 16f)
            }
            setPadding(0, 0, 0, 0)
        }

        val minText = TextView(activity).apply {
            text = if (isFloat) "$minVal" else "${minVal.toInt()}"
            setTextColor(Color.parseColor("#8E8E93"))
            textSize = 13f
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        rangeRow.addView(minText)

        val maxText = TextView(activity).apply {
            text = if (isFloat) "$maxVal" else "${maxVal.toInt()}"
            setTextColor(Color.parseColor("#8E8E93"))
            textSize = 13f
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        rangeRow.addView(maxText)

        container.addView(rangeRow)

        val inputEdit = EditText(activity).apply {
            setText(initialVal)
            inputType = if (isFloat) {
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            } else {
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            }
            showSoftInputOnFocus = false
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(LS.dpToPx(activity, 16f), LS.dpToPx(activity, 12f), LS.dpToPx(activity, 16f), LS.dpToPx(activity, 12f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#242424"))
                cornerRadius = LS.dpToPx(activity, 10f).toFloat()
                setStroke(LS.dpToPx(activity, 1f), Color.parseColor("#444444"))
            }
            setSelection(text.length)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                textCursorDrawable = ColorDrawable(Color.parseColor("#b7b7b7"))
            }

            setOnClickListener {
                val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        container.addView(inputEdit)

        var syncing = false

        seekBar.onProgressChanged = { progress, fromUser ->
            if (fromUser && !syncing) {
                syncing = true
                val value = minVal + (maxVal - minVal) * (progress / 1000f)
                val formatted = if (isFloat) {
                    String.format("%.1f", value)
                } else {
                    value.toInt().toString()
                }
                inputEdit.setText(formatted)
                inputEdit.setSelection(inputEdit.text.length)
                syncing = false
            }
        }

        inputEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!syncing) {
                    syncing = true
                    val parsed = s?.toString()?.toFloatOrNull()
                    if (parsed != null && parsed in minVal..maxVal) {
                        val progress = (((parsed - minVal) / (maxVal - minVal)) * 1000f).toInt()
                        seekBar.progress = progress
                    }
                    syncing = false
                }
            }
        })

        val buttonLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, LS.dpToPx(activity, 20f), 0, 0)
        }

        val cancelButton = Button(activity).apply {
            text = "Cancel"
            setTextColor(Color.parseColor("#8E8E93"))
            transformationMethod = null
            background = null
            setOnClickListener { dialog.dismiss() }
        }
        buttonLayout.addView(cancelButton)

        val saveButton = Button(activity).apply {
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
        }

        dialog.window?.apply {
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.85f).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        dialog.show()
    }

    fun showOptionPickerDialog(
        activity: Activity,
        title: String,
        options: List<Pair<String, String>>,
        currentValue: String,
        onOptionSelected: (String) -> Unit
    ) {
        val dialog = Dialog(activity).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(true)
        }

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(LS.dpToPx(activity, 24f), LS.dpToPx(activity, 24f), LS.dpToPx(activity, 24f), LS.dpToPx(activity, 20f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = LS.dpToPx(activity, 16f).toFloat()
            }
        }

        val titleText = TextView(activity).apply {
            text = title
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(0, 0, 0, LS.dpToPx(activity, 16f))
        }
        container.addView(titleText)

        options.forEach { (displayName, value) ->
            val optionLayout = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val p12 = LS.dpToPx(activity, 12f)
                val p16 = LS.dpToPx(activity, 16f)
                setPadding(p16, p12, p16, p12)
                isClickable = true

                val outVal = TypedValue()
                activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outVal, true)
                setBackgroundResource(outVal.resourceId)

                setOnClickListener {
                    onOptionSelected(value)
                    dialog.dismiss()
                }
            }

            val iconType = when (value) {
                "spotify" -> LS.IconType.SPOTIFY
                "tidal" -> LS.IconType.TIDAL
                "kdeconnect" -> LS.IconType.KDECONNECT
                else -> LS.IconType.LIST_MUSIC
            }

            val iconView = View(activity).apply {
                background = LS.CustomIconDrawable(activity, iconType)
                layoutParams = LinearLayout.LayoutParams(LS.dpToPx(activity, 24f), LS.dpToPx(activity, 24f)).apply {
                    rightMargin = LS.dpToPx(activity, 16f)
                }
            }
            optionLayout.addView(iconView)

            val optionText = TextView(activity).apply {
                text = displayName
                textSize = 16f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            optionLayout.addView(optionText)

            if (value == currentValue) {
                val checkView = ImageView(activity).apply {
                    val checkDrawable = LS.CustomIconDrawable(activity, LS.IconType.CHECK)
                    setImageDrawable(checkDrawable)
                    layoutParams = LinearLayout.LayoutParams(LS.dpToPx(activity, 20f), LS.dpToPx(activity, 20f))
                }
                optionLayout.addView(checkView)
            }

            container.addView(optionLayout)
        }

        val buttonLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, LS.dpToPx(activity, 16f), 0, 0)
        }

        val cancelButton = Button(activity).apply {
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
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.85f).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        dialog.show()
    }

    fun showCustomConfirmDialog(
        activity: Activity,
        title: String,
        message: String,
        confirmText: String,
        onConfirm: () -> Unit
    ) {
        val dialog = Dialog(activity).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(true)
        }

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(LS.dpToPx(activity, 24f), LS.dpToPx(activity, 24f), LS.dpToPx(activity, 24f), LS.dpToPx(activity, 20f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = LS.dpToPx(activity, 16f).toFloat()
            }
        }

        val titleText = TextView(activity).apply {
            text = title
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(0, 0, 0, LS.dpToPx(activity, 10f))
        }
        container.addView(titleText)

        val msgText = TextView(activity).apply {
            text = message
            textSize = 14f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, 0, 0, LS.dpToPx(activity, 20f))
        }
        container.addView(msgText)

        val buttonLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val cancelButton = Button(activity).apply {
            text = "Cancel"
            setTextColor(Color.parseColor("#8E8E93"))
            transformationMethod = null
            background = null
            setOnClickListener { dialog.dismiss() }
        }
        buttonLayout.addView(cancelButton)

        val confirmButton = Button(activity).apply {
            text = confirmText
            setTextColor(Color.parseColor("#FF453A"))
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
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.85f).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        dialog.show()
    }

    fun getActiveSongMetadata(context: Context): TrackResolution.ResolvedTrack? {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val pubTitle = prefs.getString(TrackResolution.KEY_WALLPAPER_TITLE, null)
        val pubArtist = prefs.getString(TrackResolution.KEY_WALLPAPER_ARTIST, null)

        return TrackResolution.resolveTrack(pubTitle, pubArtist) {
            resolveSessionTrack(context)
        }
    }

    fun resolveSessionTrack(context: Context): Pair<String, String>? {
        try {
            val componentName = ComponentName(context, NotificationService::class.java)
            val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val controllers = mediaSessionManager.getActiveSessions(componentName)
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val preferred = prefs.getString("preferred_media_player", "default") ?: "default"
            val candidates = controllers.map { controller ->
                val meta = controller.metadata
                val title = meta?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
                val state = controller.playbackState?.state ?: MediaSessionChoice.STATE_NONE
                MediaSessionChoice.Candidate(
                    packageName = controller.packageName,
                    hasUsableMetadata = !title.isNullOrBlank(),
                    playbackState = state
                ) to controller
            }
            val chosen = MediaSessionChoice.choose(candidates.map { it.first }, preferred)
            val active = candidates.firstOrNull { it.first == chosen }?.second
            if (active != null) {
                val metadata = active.metadata
                val title = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
                val artist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
                if (!title.isNullOrBlank() && !artist.isNullOrBlank()) {
                    return Pair(title.trim(), artist.trim())
                }
            }
        } catch (e: Exception) {
        }
        return null
    }

    fun isNotificationServiceEnabled(context: Context): Boolean {
        val cn = ComponentName(context, NotificationService::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    fun isWallpaperActive(context: Context): Boolean {
        val wpm = android.app.WallpaperManager.getInstance(context)
        val info = wpm.wallpaperInfo
        return info != null && info.packageName == context.packageName
    }

    fun isBatteryUnrestricted(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}
