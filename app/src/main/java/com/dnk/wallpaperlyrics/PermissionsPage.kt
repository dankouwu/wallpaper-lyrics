package com.dnk.wallpaperlyrics

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.dnk.wallpaperlyrics.LyricsSettings as LS

class PermissionsPage(activity: MainActivity) : SettingsPage(activity) {

    private lateinit var prefs: SharedPreferences
    private lateinit var contentContainer: LinearLayout
    private lateinit var notificationRow: LS.SettingsRow
    private lateinit var wallpaperRow: LS.SettingsRow
    private lateinit var batteryRow: LS.SettingsRow

    private companion object {
        const val REQUEST_POST_NOTIFICATIONS = 103
    }

    override fun onCreate() {

        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        try {
            val rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(
                    LS.dpToPx(this@PermissionsPage, 24f),
                    LS.dpToPx(this@PermissionsPage, 16f),
                    LS.dpToPx(this@PermissionsPage, 24f),
                    LS.dpToPx(this@PermissionsPage, 40f)
                )
                setBackgroundColor(Color.parseColor("#242424"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }

            val headerLayout = LS.pageHeader(this, "Permissions & Notifications") { finish() }
            rootLayout.addView(headerLayout)

            contentContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            rootLayout.addView(contentContainer)

            buildUi()

            val scrollView = ScrollView(this).apply {
                isFillViewport = true
            }
            scrollView.addView(rootLayout)
            setContentView(scrollView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun addSectionHeader(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 13f
            setTextColor(Color.parseColor("#8E8E93"))
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(
                LS.dpToPx(this@PermissionsPage, 12f),
                LS.dpToPx(this@PermissionsPage, 16f),
                LS.dpToPx(this@PermissionsPage, 12f),
                LS.dpToPx(this@PermissionsPage, 8f)
            )
        }
    }

    private fun buildUi() {
        contentContainer.addView(addSectionHeader("System Access"))
        val systemAccessCard = LS.SettingsCard(this).apply {
            notificationRow = LS.SettingsRow(
                this@PermissionsPage,
                LS.IconType.BELL,
                "Notification Access",
                "Required to read music session details",
                LS.TrailingType.CHEVRON,
                onClick = {
                    try {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    } catch (e: Exception) {
                        Toast.makeText(this@PermissionsPage, "Could not open settings", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            addRow(notificationRow)

            wallpaperRow = LS.SettingsRow(
                this@PermissionsPage,
                LS.IconType.IMAGE,
                "Activate Live Wallpaper",
                "Choose this wallpaper in picker menu",
                LS.TrailingType.CHEVRON,
                onClick = {
                    try {
                        val intent = Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                        intent.putExtra(
                            android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                            android.content.ComponentName(this@PermissionsPage, LyricsWallpaperService::class.java)
                        )
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this@PermissionsPage, "Could not open wallpaper picker", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            addRow(wallpaperRow)

            batteryRow = LS.SettingsRow(
                this@PermissionsPage,
                LS.IconType.GAUGE,
                "Battery Unrestricted",
                "Required so the wallpaper is not throttled",
                LS.TrailingType.CHEVRON,
                onClick = {
                    try {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (e: Exception) {
                        Toast.makeText(this@PermissionsPage, "Could not open battery settings", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            addRow(batteryRow)
        }
        contentContainer.addView(systemAccessCard)

        contentContainer.addView(addSectionHeader("Notifications"))
        val notificationsCard = LS.SettingsCard(this).apply {
            val persistentNotifRow = LS.SettingsRow(
                this@PermissionsPage,
                LS.IconType.BELL,
                "Persistent Controls",
                "Show control notification in status bar",
                LS.TrailingType.SWITCH,
                initialVal = prefs.getBoolean("persistent_notification", false).toString(),
                onCheckedChange = { isChecked ->
                    prefs.edit().putBoolean("persistent_notification", isChecked).apply()
                    // Asked here rather than on launch: this is the first thing that needs it.
                    if (isChecked && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                        checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS)
                    }
                }
            )
            addRow(persistentNotifRow)

            addRow(LS.SettingsRow(
                this@PermissionsPage,
                LS.IconType.INFO,
                "Status Messages",
                "Toast when lyrics are fetched or missing",
                LS.TrailingType.SWITCH,
                initialVal = prefs.getBoolean(LS.KEY_STATUS_TOASTS, true).toString(),
                onCheckedChange = { isChecked ->
                    prefs.edit().putBoolean(LS.KEY_STATUS_TOASTS, isChecked).apply()
                }
            ))
        }
        contentContainer.addView(notificationsCard)
    }

    override fun onResume() {
        super.onResume()
        if (::notificationRow.isInitialized) {
            if (SettingsDialogs.isNotificationServiceEnabled(this)) {
                notificationRow.setTrailing(LS.TrailingType.CHECK)
                notificationRow.updateSubtitle("Granted")
            } else {
                notificationRow.setTrailing(LS.TrailingType.CHEVRON)
                notificationRow.updateSubtitle("Not granted. Required to read music session details")
            }
        }
        if (::wallpaperRow.isInitialized) {
            if (SettingsDialogs.isWallpaperActive(this)) {
                wallpaperRow.setTrailing(LS.TrailingType.CHECK)
                wallpaperRow.updateSubtitle("Active")
            } else {
                wallpaperRow.setTrailing(LS.TrailingType.CHEVRON)
                wallpaperRow.updateSubtitle("Not set. Choose this wallpaper in the picker")
            }
        }
        if (::batteryRow.isInitialized) {
            if (SettingsDialogs.isBatteryUnrestricted(this)) {
                batteryRow.setTrailing(LS.TrailingType.CHECK)
                batteryRow.updateSubtitle("Unrestricted")
            } else {
                batteryRow.setTrailing(LS.TrailingType.CHEVRON)
                batteryRow.updateSubtitle("Restricted. Required so the wallpaper is not throttled")
            }
        }
    }
}
