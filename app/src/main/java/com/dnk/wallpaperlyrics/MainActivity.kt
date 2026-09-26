package com.dnk.wallpaperlyrics

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dnk.wallpaperlyrics.LyricsSettings as LS
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    private lateinit var permissionsNavRow: LS.SettingsRow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        if (BuildFlags.DEBUG) {
            Tuning.load(this)
        }

        try {
            val rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(LS.dpToPx(this@MainActivity, 24f), LS.dpToPx(this@MainActivity, 16f), LS.dpToPx(this@MainActivity, 24f), LS.dpToPx(this@MainActivity, 40f))
                setBackgroundColor(Color.parseColor("#242424"))
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
                paint.isFakeBoldText = true
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = LS.dpToPx(this@MainActivity, 24f)
                    bottomMargin = LS.dpToPx(this@MainActivity, 16f)
                }
            }
            rootLayout.addView(titleView)

            fun addSectionHeader(title: String, textColor: Int = Color.parseColor("#8E8E93")) {
                val header = TextView(this).apply {
                    text = title
                    textSize = 13f
                    setTextColor(textColor)
                    setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
                    setPadding(LS.dpToPx(this@MainActivity, 12f), LS.dpToPx(this@MainActivity, 24f), LS.dpToPx(this@MainActivity, 12f), LS.dpToPx(this@MainActivity, 8f))
                }
                rootLayout.addView(header)
            }

            addSectionHeader("Settings")
            val settingsCard = LS.SettingsCard(this).apply {
                val notifOk = SettingsDialogs.isNotificationServiceEnabled(this@MainActivity)
                val wallOk = SettingsDialogs.isWallpaperActive(this@MainActivity)
                val battOk = SettingsDialogs.isBatteryUnrestricted(this@MainActivity)
                val attentionCount = (if (notifOk) 0 else 1) + (if (wallOk) 0 else 1) + (if (battOk) 0 else 1)
                val initialPermsSubtitle = when (attentionCount) {
                    0 -> "All set"
                    1 -> "1 needs attention"
                    2 -> "2 need attention"
                    else -> "3 need attention"
                }

                permissionsNavRow = LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.BELL,
                    "Permissions & Notifications",
                    initialPermsSubtitle,
                    LS.TrailingType.CHEVRON,
                    onClick = {
                        startActivity(Intent(this@MainActivity, PermissionsActivity::class.java))
                    }
                )
                addRow(permissionsNavRow)

                addRow(LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.SLIDERS,
                    "Background & Display",
                    "Wallpaper visuals, idle screen and always on display",
                    LS.TrailingType.CHEVRON,
                    onClick = {
                        startActivity(Intent(this@MainActivity, BackgroundSettingsActivity::class.java))
                    }
                ))

                addRow(LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.LIST_MUSIC,
                    "Lyrics",
                    "Media player, provider, cache and edits",
                    LS.TrailingType.CHEVRON,
                    onClick = {
                        startActivity(Intent(this@MainActivity, LyricsSettingsActivity::class.java))
                    }
                ))

                addRow(LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.CLOCK,
                    "Timing & Sync",
                    "Offsets for songs and Bluetooth devices",
                    LS.TrailingType.CHEVRON,
                    onClick = {
                        startActivity(Intent(this@MainActivity, TimingActivity::class.java))
                    }
                ))
            }
            rootLayout.addView(settingsCard)

            addSectionHeader("About")
            val cardAbout = LS.SettingsCard(this).apply {
                addRow(LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.FILE_STACK,
                    "Version",
                    packageManager.getPackageInfo(packageName, 0).versionName,
                    LS.TrailingType.CHEVRON,
                    onClick = {
                        startActivity(Intent(this@MainActivity, VersionActivity::class.java))
                    }
                ))

                addRow(LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.GITHUB,
                    "GitHub Repository",
                    "View source code and star the project",
                    LS.TrailingType.EXTERNAL,
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/dankouwu/wallpaper-lyrics"))
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Could not open repository link", Toast.LENGTH_SHORT).show()
                        }
                    }
                ))

                addRow(LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.BUG,
                    "Report an Issue",
                    "Submit bugs or request new features",
                    LS.TrailingType.EXTERNAL,
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/dankouwu/wallpaper-lyrics/issues"))
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Could not open issue link", Toast.LENGTH_SHORT).show()
                        }
                    }
                ))

                addRow(LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.COPYRIGHT,
                    "License",
                    "Apache License 2.0 terms",
                    LS.TrailingType.CHEVRON,
                    onClick = {
                        showAboutDialog(
                            "Apache License 2.0",
                            "Copyright 2026 dankouwu\n\nLicensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. You may obtain a copy of the License at:\n\nhttp://www.apache.org/licenses/LICENSE-2.0\n\nUnless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License."
                        )
                    }
                ))

                addRow(LS.SettingsRow(
                    this@MainActivity,
                    LS.IconType.INFO,
                    "Credits & Libraries",
                    "Developer details and open-source packages",
                    LS.TrailingType.CHEVRON,
                    onClick = {
                        showAboutDialog(
                            "Credits & Libraries",
                            "Developers & Contributors:\n- dankouwu\n- riveerxd\n\nLibraries Used:\n- AndroidX core & components\n- Gson (Google JSON serializer)\n- LRCLIB (Synced lyrics provider)\n- Lucide Icons (lucide.dev)\n\nThank you for using Wallpaper Lyrics!"
                        )
                    }
                ))
            }
            rootLayout.addView(cardAbout)

            if (BuildFlags.DEBUG) {
                addSectionHeader("Debug (Debug Build Only)", Color.parseColor("#F59E0B"))
                val debugCard = LS.SettingsCard(this).apply {
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#282015"))
                        setStroke(LS.dpToPx(this@MainActivity, 1f), Color.parseColor("#5C4012"))
                        cornerRadius = LS.dpToPx(this@MainActivity, 18f).toFloat()
                    }
                    addRow(LS.SettingsRow(
                        this@MainActivity,
                        LS.IconType.SLIDERS,
                        "Live Parameter Tuning",
                        "Adjust word motion curves and rendering values",
                        LS.TrailingType.CHEVRON,
                        onClick = {
                            try {
                                startActivity(Intent().setClassName(this@MainActivity, "com.dnk.wallpaperlyrics.DebugTuningActivity"))
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "Could not open tuning screen", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ))
                }
                rootLayout.addView(debugCard)
            }

            val scrollView = ScrollView(this).apply {
                isFillViewport = true
            }
            scrollView.addView(rootLayout)
            setContentView(scrollView)

            checkUpdatePopup(prefs)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkUpdatePopup(prefs: android.content.SharedPreferences) {
        val autoCheckEnabled = prefs.getBoolean(UpdateCheck.PREF_UPDATE_CHECK_ENABLED, false)
        if (autoCheckEnabled) {
            val latestTag = prefs.getString(UpdateCheck.PREF_LATEST_TAG, null)
            val latestUrl = prefs.getString(UpdateCheck.PREF_LATEST_URL, null)
            val dismissedTag = prefs.getString(UpdateCheck.PREF_DISMISSED_TAG, null)
            val currentVersion = try {
                packageManager.getPackageInfo(packageName, 0).versionName
            } catch (e: Exception) {
                ""
            }
            if (!latestTag.isNullOrBlank() && !latestUrl.isNullOrBlank() &&
                UpdateCheck.isNewerVersion(latestTag, currentVersion) &&
                latestTag != dismissedTag
            ) {
                UpdateCheck.showUpdateDialog(this, currentVersion, latestTag, latestUrl)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::permissionsNavRow.isInitialized) {
            val notifOk = SettingsDialogs.isNotificationServiceEnabled(this)
            val wallOk = SettingsDialogs.isWallpaperActive(this)
            val battOk = SettingsDialogs.isBatteryUnrestricted(this)
            val attentionCount = (if (notifOk) 0 else 1) + (if (wallOk) 0 else 1) + (if (battOk) 0 else 1)
            val subtitle = when (attentionCount) {
                0 -> "All set"
                1 -> "1 needs attention"
                2 -> "2 need attention"
                else -> "3 need attention"
            }
            permissionsNavRow.updateSubtitle(subtitle)
        }
    }

    private fun showAboutDialog(title: String, message: String) {
        val dialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setCancelable(true)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(LS.dpToPx(this@MainActivity, 24f), LS.dpToPx(this@MainActivity, 24f), LS.dpToPx(this@MainActivity, 24f), LS.dpToPx(this@MainActivity, 20f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = LS.dpToPx(this@MainActivity, 16f).toFloat()
            }
        }

        val titleText = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(0, 0, 0, LS.dpToPx(this@MainActivity, 10f))
        }
        container.addView(titleText)

        val msgText = TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, 0, 0, LS.dpToPx(this@MainActivity, 20f))
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

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
