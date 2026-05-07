package com.example.wallpaperlyrics

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.view.Gravity
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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

            layout.addView(btn)
            layout.addView(btnWallpaper)
            setContentView(layout)
        } catch (e: Exception) {
            // If it still crashes, this might catch it, though unlikely in onCreate
            e.printStackTrace()
        }
    }
}
