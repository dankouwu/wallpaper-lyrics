package com.dnk.wallpaperlyrics

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dnk.wallpaperlyrics.LyricsSettings as LS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class VersionActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var contentContainer: LinearLayout
    private var isChecking = false
    private var checkStatusText = ""

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        try {
            val rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(
                    LS.dpToPx(this@VersionActivity, 24f),
                    LS.dpToPx(this@VersionActivity, 16f),
                    LS.dpToPx(this@VersionActivity, 24f),
                    LS.dpToPx(this@VersionActivity, 40f)
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
                    topMargin = LS.dpToPx(this@VersionActivity, 24f)
                    bottomMargin = LS.dpToPx(this@VersionActivity, 16f)
                }
            }

            val backButton = android.widget.ImageView(this).apply {
                val arrowDrawable = LS.CustomIconDrawable(this@VersionActivity, LS.IconType.ARROW_LEFT)
                setImageDrawable(arrowDrawable)
                val size = LS.dpToPx(this@VersionActivity, 48f)
                layoutParams = android.widget.RelativeLayout.LayoutParams(size, size).apply {
                    addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT)
                    addRule(android.widget.RelativeLayout.CENTER_VERTICAL)
                }
                setPadding(
                    LS.dpToPx(this@VersionActivity, 12f),
                    LS.dpToPx(this@VersionActivity, 12f),
                    LS.dpToPx(this@VersionActivity, 12f),
                    LS.dpToPx(this@VersionActivity, 12f)
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
                text = "Version & Updates"
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

            val scrollView = ScrollView(this).apply {
                isFillViewport = true
            }
            scrollView.addView(rootLayout)
            setContentView(scrollView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        rebuildUi()
    }

    private fun addSectionHeader(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 13f
            setTextColor(Color.parseColor("#8E8E93"))
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(
                LS.dpToPx(this@VersionActivity, 12f),
                LS.dpToPx(this@VersionActivity, 16f),
                LS.dpToPx(this@VersionActivity, 12f),
                LS.dpToPx(this@VersionActivity, 8f)
            )
        }
    }

    private fun formatCheckTime(ms: Long): String {
        if (ms <= 0L) return ""
        val dt = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
        return if (dt.toLocalDate() == now.toLocalDate()) {
            "today at " + dt.format(timeFormatter)
        } else if (dt.year == now.year) {
            val dateFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.US)
            dt.format(dateFormatter)
        } else {
            val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy, HH:mm", Locale.US)
            dt.format(dateFormatter)
        }
    }

    private fun rebuildUi() {
        contentContainer.removeAllViews()

        val installedVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "Unknown"
        }

        val latestTag = prefs.getString(UpdateCheck.PREF_LATEST_TAG, null)
        val latestUrl = prefs.getString(UpdateCheck.PREF_LATEST_URL, null)
        val lastCheckedMs = prefs.getLong(UpdateCheck.PREF_LAST_CHECKED_MS, 0L)
        val updateAvailable = !latestTag.isNullOrBlank() && UpdateCheck.isNewerVersion(latestTag, installedVersion)

        contentContainer.addView(addSectionHeader("Version"))
        val versionCard = LS.SettingsCard(this).apply {
            addRow(
                LS.SettingsRow(
                    this@VersionActivity,
                    LS.IconType.FILE_STACK,
                    "Installed version",
                    installedVersion,
                    LS.TrailingType.NONE,
                    onClick = {}
                )
            )

            val latestSubtitle = if (!latestTag.isNullOrBlank() && lastCheckedMs > 0L) {
                val timeStr = formatCheckTime(lastCheckedMs)
                if (timeStr.isNotEmpty()) "$latestTag (checked $timeStr)" else latestTag
            } else {
                "Not checked yet"
            }

            addRow(
                LS.SettingsRow(
                    this@VersionActivity,
                    LS.IconType.GITHUB,
                    "Latest release",
                    latestSubtitle,
                    LS.TrailingType.NONE,
                    onClick = {}
                )
            )

            if (updateAvailable && !latestUrl.isNullOrBlank()) {
                addRow(
                    LS.SettingsRow(
                        this@VersionActivity,
                        LS.IconType.LINK,
                        "Download update",
                        "Open release page on GitHub",
                        LS.TrailingType.EXTERNAL,
                        onClick = {
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latestUrl)))
                            } catch (e: Exception) {
                                Toast.makeText(this@VersionActivity, "Could not open release link", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                )
            }
        }
        contentContainer.addView(versionCard)

        contentContainer.addView(addSectionHeader("Updates"))
        val updatesCard = LS.SettingsCard(this).apply {
            val dailyEnabled = prefs.getBoolean(UpdateCheck.PREF_UPDATE_CHECK_ENABLED, false)
            addRow(
                LS.SettingsRow(
                    this@VersionActivity,
                    LS.IconType.CLOCK,
                    "Check for updates daily",
                    "Checks GitHub once a day around 12:00",
                    LS.TrailingType.SWITCH,
                    initialVal = if (dailyEnabled) "true" else "false",
                    onCheckedChange = { checked ->
                        prefs.edit().putBoolean(UpdateCheck.PREF_UPDATE_CHECK_ENABLED, checked).apply()
                        if (checked) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS)
                                }
                            }
                            UpdateCheck.scheduleDailyCheck(this@VersionActivity)
                        } else {
                            UpdateCheck.cancelDailyCheck(this@VersionActivity)
                        }
                    }
                )
            )

            val checkNowSubtitle = if (isChecking) {
                "Checking..."
            } else {
                checkStatusText
            }

            addRow(
                LS.SettingsRow(
                    this@VersionActivity,
                    LS.IconType.RELOAD,
                    "Check now",
                    checkNowSubtitle,
                    LS.TrailingType.NONE,
                    onClick = {
                        if (isChecking) return@SettingsRow

                        isChecking = true
                        checkStatusText = "Checking..."
                        rebuildUi()

                        CoroutineScope(Dispatchers.IO).launch {
                            val result = UpdateCheck.checkNow(this@VersionActivity, installedVersion)
                            withContext(Dispatchers.Main) {
                                isChecking = false
                                when (result) {
                                    is UpdateCheck.CheckResult.NewerAvailable -> {
                                        checkStatusText = "Update available: ${result.tag}"
                                        rebuildUi()
                                        UpdateCheck.showUpdateDialog(
                                            this@VersionActivity,
                                            installedVersion,
                                            result.tag,
                                            result.url
                                        )
                                    }
                                    is UpdateCheck.CheckResult.UpToDate -> {
                                        checkStatusText = "You're on the latest version"
                                        rebuildUi()
                                    }
                                    is UpdateCheck.CheckResult.Failed -> {
                                        checkStatusText = result.message
                                        rebuildUi()
                                    }
                                }
                            }
                        }
                    }
                )
            )
        }
        contentContainer.addView(updatesCard)
    }
}
