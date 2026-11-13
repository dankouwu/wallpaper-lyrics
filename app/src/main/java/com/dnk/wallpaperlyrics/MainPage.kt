package com.dnk.wallpaperlyrics

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.dnk.wallpaperlyrics.LyricsSettings as LS

class MainPage(activity: MainActivity) : SettingsPage(activity) {

    private lateinit var permissionsNavRow: LS.SettingsRow
    private lateinit var runningRow: LS.SettingsRow

    override fun onCreate() {

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        if (BuildFlags.DEBUG) {
            Tuning.load(this)
        }

        try {
            val rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(LS.dpToPx(this@MainPage, 24f), LS.dpToPx(this@MainPage, 16f), LS.dpToPx(this@MainPage, 24f), LS.dpToPx(this@MainPage, 40f))
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
                    topMargin = LS.dpToPx(this@MainPage, 24f)
                    bottomMargin = LS.dpToPx(this@MainPage, 16f)
                }
            }
            rootLayout.addView(titleView)

            fun addSectionHeader(title: String, textColor: Int = Color.parseColor("#8E8E93")) {
                val header = TextView(this).apply {
                    text = title
                    textSize = 13f
                    setTextColor(textColor)
                    setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
                    setPadding(LS.dpToPx(this@MainPage, 12f), LS.dpToPx(this@MainPage, 24f), LS.dpToPx(this@MainPage, 12f), LS.dpToPx(this@MainPage, 8f))
                }
                rootLayout.addView(header)
            }

            val runningCard = LS.SettingsCard(this).apply {
                val running = WallpaperKill.isRunning(this@MainPage)
                runningRow = LS.SettingsRow(
                    this@MainPage,
                    LS.IconType.IMAGE,
                    "Running",
                    runningSubtitle(running),
                    LS.TrailingType.SWITCH,
                    running.toString(),
                    onCheckedChange = { checked ->
                        if (checked) {
                            runningRow.setChecked(false)
                            WallpaperKill.openPicker(activity)
                        } else {
                            // Stays on until confirmed: the process is gone afterwards anyway.
                            runningRow.setChecked(true)
                            SettingsDialogs.showCustomConfirmDialog(
                                activity,
                                "Stop Wallpaper Lyrics?",
                                "Your wallpaper goes back to the system default and the app stops, as a force stop would. Switch it back on to set it again.",
                                "Stop"
                            ) {
                                WallpaperKill.kill(activity)
                            }
                        }
                    }
                )
                addRow(runningRow)
            }
            rootLayout.addView(runningCard)

            addSectionHeader("Settings")
            val settingsCard = LS.SettingsCard(this).apply {
                val notifOk = SettingsDialogs.isNotificationServiceEnabled(this@MainPage)
                val wallOk = SettingsDialogs.isWallpaperActive(this@MainPage)
                val battOk = SettingsDialogs.isBatteryUnrestricted(this@MainPage)
                val attentionCount = (if (notifOk) 0 else 1) + (if (wallOk) 0 else 1) + (if (battOk) 0 else 1)
                val initialPermsSubtitle = when (attentionCount) {
                    0 -> "All set"
                    1 -> "1 needs attention"
                    2 -> "2 need attention"
                    else -> "3 need attention"
                }

                permissionsNavRow = LS.SettingsRow(
                    this@MainPage,
                    LS.IconType.BELL,
                    "Permissions & Notifications",
                    initialPermsSubtitle,
                    LS.TrailingType.CHEVRON,
                    onClick = {
                        activity.openPage(PermissionsPage(activity))
                    }
                )
                addRow(permissionsNavRow)

                addRow(LS.SettingsRow(
                    this@MainPage,
                    LS.IconType.SLIDERS,
                    "Background & Display",
                    "Wallpaper visuals, idle screen and always on display",
                    LS.TrailingType.CHEVRON,
                    onClick = {
                        activity.openPage(BackgroundSettingsPage(activity))
                    }
                ))

                addRow(LS.SettingsRow(
                    this@MainPage,
                    LS.IconType.LIST_MUSIC,
                    "Lyrics",
                    "Media player, provider, cache and edits",
                    LS.TrailingType.CHEVRON,
                    onClick = {
                        activity.openPage(LyricsSettingsPage(activity))
                    }
                ))

                addRow(LS.SettingsRow(
                    this@MainPage,
                    LS.IconType.CLOCK,
                    "Timing & Sync",
                    "Offsets for songs and Bluetooth devices",
                    LS.TrailingType.CHEVRON,
                    onClick = {
                        activity.openPage(TimingPage(activity))
                    }
                ))
            }
            rootLayout.addView(settingsCard)

            addSectionHeader("About")
            val cardAbout = LS.SettingsCard(this).apply {
                addRow(LS.SettingsRow(
                    this@MainPage,
                    LS.IconType.FILE_STACK,
                    "Version",
                    packageManager.getPackageInfo(packageName, 0).versionName,
                    LS.TrailingType.CHEVRON,
                    onClick = {
                        activity.openPage(VersionPage(activity))
                    }
                ))

                addRow(LS.SettingsRow(
                    this@MainPage,
                    LS.IconType.GITHUB,
                    "GitHub Repository",
                    "View source code and star the project",
                    LS.TrailingType.EXTERNAL,
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/dankouwu/wallpaper-lyrics"))
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this@MainPage, "Could not open repository link", Toast.LENGTH_SHORT).show()
                        }
                    }
                ))

                addRow(LS.SettingsRow(
                    this@MainPage,
                    LS.IconType.BUG,
                    "Report an Issue",
                    "Submit bugs or request new features",
                    LS.TrailingType.EXTERNAL,
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/dankouwu/wallpaper-lyrics/issues"))
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this@MainPage, "Could not open issue link", Toast.LENGTH_SHORT).show()
                        }
                    }
                ))

                addRow(LS.SettingsRow(
                    this@MainPage,
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
                    this@MainPage,
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
                        setStroke(LS.dpToPx(this@MainPage, 1f), Color.parseColor("#5C4012"))
                        cornerRadius = LS.dpToPx(this@MainPage, 18f).toFloat()
                    }
                    addRow(LS.SettingsRow(
                        this@MainPage,
                        LS.IconType.SLIDERS,
                        "Live Parameter Tuning",
                        "Adjust word motion curves and rendering values",
                        LS.TrailingType.CHEVRON,
                        onClick = {
                            try {
                                startActivity(Intent().setClassName(this@MainPage, "com.dnk.wallpaperlyrics.DebugTuningActivity"))
                            } catch (e: Exception) {
                                Toast.makeText(this@MainPage, "Could not open tuning screen", Toast.LENGTH_SHORT).show()
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
                UpdateCheck.showUpdateDialog(activity, currentVersion, latestTag, latestUrl)
            }
        }
    }

    private fun runningSubtitle(running: Boolean) =
        if (running) "Set as your wallpaper" else "Stopped. Switch on to set it as your wallpaper"

    override fun onResume() {
        super.onResume()
        if (::runningRow.isInitialized) {
            val running = WallpaperKill.isRunning(this)
            runningRow.setChecked(running)
            runningRow.updateSubtitle(runningSubtitle(running))
        }
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
            setPadding(LS.dpToPx(this@MainPage, 24f), LS.dpToPx(this@MainPage, 24f), LS.dpToPx(this@MainPage, 24f), LS.dpToPx(this@MainPage, 20f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = LS.dpToPx(this@MainPage, 16f).toFloat()
            }
        }

        val titleText = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(0, 0, 0, LS.dpToPx(this@MainPage, 10f))
        }
        container.addView(titleText)

        val msgText = TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, 0, 0, LS.dpToPx(this@MainPage, 20f))
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
}
