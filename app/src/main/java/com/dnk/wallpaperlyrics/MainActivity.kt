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

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        try {
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(50, 50, 50, 50)
            }

            val btn = Button(this).apply {
                text = "Grant Notification Access"
                setOnClickListener {
                    try {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Could not open settings", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            
            val btnWallpaper = Button(this).apply {
                text = "Set Wallpaper"
                setOnClickListener {
                    try {
                        val intent = Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                        intent.putExtra(android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                            android.content.ComponentName(this@MainActivity, LyricsWallpaperService::class.java))
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Could not open wallpaper picker", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            val themeSwitchContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 40, 0, 0)
            }

            val themeText = TextView(this).apply {
                text = "Dynamic System Theming (Material You)"
                textSize = 16f
            }

            val themeSwitch = SwitchCompat(this).apply {
                textOn = ""
                textOff = ""
                isChecked = prefs.getBoolean("dynamic_theming", false)
                setOnCheckedChangeListener { _, isChecked ->
                    prefs.edit().putBoolean("dynamic_theming", isChecked).apply()
                }
                setPadding(30, 0, 0, 0)
            }

            themeSwitchContainer.addView(themeText)
            themeSwitchContainer.addView(themeSwitch)

            layout.addView(btn)
            layout.addView(btnWallpaper)
            layout.addView(themeSwitchContainer)
            setContentView(layout)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
