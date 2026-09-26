package com.dnk.wallpaperlyrics

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dnk.wallpaperlyrics.LyricsSettings as LS

class PermissionsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var contentContainer: LinearLayout
    private lateinit var notificationRow: LS.SettingsRow
    private lateinit var wallpaperRow: LS.SettingsRow
    private lateinit var batteryRow: LS.SettingsRow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        try {
            val rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(
                    LS.dpToPx(this@PermissionsActivity, 24f),
                    LS.dpToPx(this@PermissionsActivity, 16f),
                    LS.dpToPx(this@PermissionsActivity, 24f),
                    LS.dpToPx(this@PermissionsActivity, 40f)
                )
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
                    topMargin = LS.dpToPx(this@PermissionsActivity, 24f)
                    bottomMargin = LS.dpToPx(this@PermissionsActivity, 16f)
                }
            }

            val backButton = android.widget.ImageView(this).apply {
                val arrowDrawable = LS.CustomIconDrawable(this@PermissionsActivity, LS.IconType.ARROW_LEFT)
                setImageDrawable(arrowDrawable)
                val size = LS.dpToPx(this@PermissionsActivity, 48f)
                layoutParams = android.widget.RelativeLayout.LayoutParams(size, size).apply {
                    addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT)
                    addRule(android.widget.RelativeLayout.CENTER_VERTICAL)
                }
                setPadding(
                    LS.dpToPx(this@PermissionsActivity, 12f),
                    LS.dpToPx(this@PermissionsActivity, 12f),
                    LS.dpToPx(this@PermissionsActivity, 12f),
                    LS.dpToPx(this@PermissionsActivity, 12f)
                )
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
                text = "Permissions & Notifications"
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
                LS.dpToPx(this@PermissionsActivity, 12f),
                LS.dpToPx(this@PermissionsActivity, 16f),
                LS.dpToPx(this@PermissionsActivity, 12f),
                LS.dpToPx(this@PermissionsActivity, 8f)
            )
        }
    }

    private fun buildUi() {
        contentContainer.addView(addSectionHeader("System Access"))
        val systemAccessCard = LS.SettingsCard(this).apply {
            notificationRow = LS.SettingsRow(
                this@PermissionsActivity,
                LS.IconType.BELL,
                "Notification Access",
                "Required to read music session details",
                LS.TrailingType.CHEVRON,
                onClick = {
                    try {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    } catch (e: Exception) {
                        Toast.makeText(this@PermissionsActivity, "Could not open settings", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            addRow(notificationRow)

            wallpaperRow = LS.SettingsRow(
                this@PermissionsActivity,
                LS.IconType.IMAGE,
                "Activate Live Wallpaper",
                "Choose this wallpaper in picker menu",
                LS.TrailingType.CHEVRON,
                onClick = {
                    try {
                        val intent = Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                        intent.putExtra(
                            android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                            android.content.ComponentName(this@PermissionsActivity, LyricsWallpaperService::class.java)
                        )
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this@PermissionsActivity, "Could not open wallpaper picker", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            addRow(wallpaperRow)

            batteryRow = LS.SettingsRow(
                this@PermissionsActivity,
                LS.IconType.GAUGE,
                "Battery Unrestricted",
                "Required so the wallpaper is not throttled",
                LS.TrailingType.CHEVRON,
                onClick = {
                    try {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (e: Exception) {
                        Toast.makeText(this@PermissionsActivity, "Could not open battery settings", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            addRow(batteryRow)
        }
        contentContainer.addView(systemAccessCard)

        contentContainer.addView(addSectionHeader("Notifications"))
        val notificationsCard = LS.SettingsCard(this).apply {
            val persistentNotifRow = LS.SettingsRow(
                this@PermissionsActivity,
                LS.IconType.BELL,
                "Persistent Controls",
                "Show control notification in status bar",
                LS.TrailingType.SWITCH,
                initialVal = prefs.getBoolean("persistent_notification", false).toString(),
                onCheckedChange = { isChecked ->
                    prefs.edit().putBoolean("persistent_notification", isChecked).apply()
                }
            )
            addRow(persistentNotifRow)

            addRow(LS.SettingsRow(
                this@PermissionsActivity,
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
                notificationRow.updateSubtitle("Required to read music session details")
            }
        }
        if (::wallpaperRow.isInitialized) {
            if (SettingsDialogs.isWallpaperActive(this)) {
                wallpaperRow.setTrailing(LS.TrailingType.CHECK)
                wallpaperRow.updateSubtitle("Active")
            } else {
                wallpaperRow.setTrailing(LS.TrailingType.CHEVRON)
                wallpaperRow.updateSubtitle("Choose this wallpaper in picker menu")
            }
        }
        if (::batteryRow.isInitialized) {
            if (SettingsDialogs.isBatteryUnrestricted(this)) {
                batteryRow.setTrailing(LS.TrailingType.CHECK)
                batteryRow.updateSubtitle("Unrestricted")
            } else {
                batteryRow.setTrailing(LS.TrailingType.CHEVRON)
                batteryRow.updateSubtitle("Required so the wallpaper is not throttled")
            }
        }
    }
}
