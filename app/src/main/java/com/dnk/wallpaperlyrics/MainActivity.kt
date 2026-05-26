package com.dnk.wallpaperlyrics

import android.content.Context
import android.content.Intent
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
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        try {
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(60, 60, 60, 60)
                setBackgroundColor(Color.parseColor("#121212"))
            }

            val titleView = TextView(this).apply {
                text = "Wallpaper Lyrics"
                textSize = 28f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 80)
            }
            layout.addView(titleView)

            fun createStyledButton(label: String, onClick: () -> Unit): Button {
                return Button(this).apply {
                    text = label
                    setTextColor(Color.WHITE)
                    setBackground(GradientDrawable().apply {
                        setColor(Color.parseColor("#252525"))
                        cornerRadius = 24f
                    })
                    setPadding(40, 30, 40, 30)
                    setOnClickListener { onClick() }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 0, 30) }
                }
            }

            layout.addView(createStyledButton("Grant Notification Access") {
                try {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Could not open settings", Toast.LENGTH_SHORT).show()
                }
            })
            
            layout.addView(createStyledButton("Set Wallpaper") {
                try {
                    val intent = Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    intent.putExtra(android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        android.content.ComponentName(this@MainActivity, LyricsWallpaperService::class.java))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Could not open wallpaper picker", Toast.LENGTH_SHORT).show()
                }
            })

            layout.addView(createStyledButton("Clear Lyrics Cache") {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Clear Lyrics Cache?")
                    .setMessage("This will delete all saved lyrics. You will need an internet connection to fetch them again.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Clear All") { _, _ ->
                        val cacheDir = java.io.File(cacheDir, "lyrics_cache")
                        if (cacheDir.exists()) {
                            cacheDir.deleteRecursively()
                            cacheDir.mkdirs()
                            Toast.makeText(this, "Cache cleared!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .show()
            })

            val themeSwitchContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(20, 40, 20, 40)
            }

            val themeText = TextView(this).apply {
                text = "Dynamic System Theming"
                textSize = 16f
                setTextColor(Color.LTGRAY)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val themeSwitch = SwitchCompat(this).apply {
                isChecked = prefs.getBoolean("dynamic_theming", false)
                setOnCheckedChangeListener { _, isChecked ->
                    prefs.edit().putBoolean("dynamic_theming", isChecked).apply()
                }
            }

            themeSwitchContainer.addView(themeText)
            themeSwitchContainer.addView(themeSwitch)
            layout.addView(themeSwitchContainer)

            // Offset Section
            val offsetContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 60, 20, 0)
                gravity = Gravity.CENTER
            }

            val labelContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            val offsetLabel = TextView(this).apply {
                text = "Sync Offset (ms): "
                textSize = 16f
                setTextColor(Color.WHITE)
            }

            val currentOffset = prefs.getInt("sync_offset", 0)
            val offsetEdit = EditText(this).apply {
                setText(currentOffset.toString())
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                setTextColor(Color.WHITE)
                background = null // Remove underline for cleaner look
                gravity = Gravity.CENTER
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(10, 0, 10, 0)
            }

            labelContainer.addView(offsetLabel)
            labelContainer.addView(offsetEdit)

            val offsetSlider = android.widget.SeekBar(this).apply {
                max = 2000
                progress = currentOffset + 1000
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 30, 0, 10) }
            }

            // Sync Slider -> Edit & Prefs
            offsetSlider.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val realOffset = progress - 1000
                        offsetEdit.setText(realOffset.toString())
                        prefs.edit().putInt("sync_offset", realOffset).apply()
                    }
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })

            // Sync Edit -> Slider & Prefs
            offsetEdit.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val input = s?.toString()?.toIntOrNull() ?: 0
                    val clamped = input.coerceIn(-1000, 1000)
                    if (input != clamped) {
                        // Optional: don't auto-correct while typing, maybe only on focus lost
                    }
                    offsetSlider.progress = clamped + 1000
                    prefs.edit().putInt("sync_offset", clamped).apply()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
            
            val offsetHint = TextView(this).apply {
                text = "(Positive = Later, Negative = Earlier)"
                textSize = 12f
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
            }

            offsetContainer.addView(labelContainer)
            offsetContainer.addView(offsetSlider)
            offsetContainer.addView(offsetHint)

            layout.addView(offsetContainer)

            // Background Speed Section
            val speedContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 60, 20, 0)
                gravity = Gravity.CENTER
            }

            val speedLabelContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            val speedLabel = TextView(this).apply {
                text = "Background Speed: "
                textSize = 16f
                setTextColor(Color.WHITE)
            }

            val currentSpeed = prefs.getFloat("bg_speed", 1.0f)
            val speedEdit = EditText(this).apply {
                setText(String.format("%.1f", currentSpeed))
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setTextColor(Color.WHITE)
                background = null
                gravity = Gravity.CENTER
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(10, 0, 10, 0)
            }

            val speedLabelSuffix = TextView(this).apply {
                text = "x"
                textSize = 16f
                setTextColor(Color.WHITE)
            }

            speedLabelContainer.addView(speedLabel)
            speedLabelContainer.addView(speedEdit)
            speedLabelContainer.addView(speedLabelSuffix)

            val speedSlider = android.widget.SeekBar(this).apply {
                max = 99 // 0.1 to 10.0
                progress = ((currentSpeed - 0.1f) * 10f).toInt().coerceIn(0, 99)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 30, 0, 10) }
            }

            speedSlider.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val speed = 0.1f + (progress / 10f)
                        speedEdit.setText(String.format("%.1f", speed))
                        prefs.edit().putFloat("bg_speed", speed).apply()
                    }
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })

            speedEdit.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val input = s?.toString()?.toFloatOrNull() ?: 1.0f
                    val clamped = input.coerceIn(0.1f, 10.0f)
                    speedSlider.progress = ((clamped - 0.1f) * 10f).toInt()
                    prefs.edit().putFloat("bg_speed", clamped).apply()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            speedContainer.addView(speedLabelContainer)
            speedContainer.addView(speedSlider)
            layout.addView(speedContainer)
            
            val scrollView = android.widget.ScrollView(this)
            scrollView.addView(layout)
            setContentView(scrollView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
