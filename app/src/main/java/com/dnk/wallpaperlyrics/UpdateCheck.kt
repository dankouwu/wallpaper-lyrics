package com.dnk.wallpaperlyrics

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object UpdateCheck {

    const val PREF_UPDATE_CHECK_ENABLED = "update_check_enabled"
    const val PREF_LATEST_TAG = "update_latest_tag"
    const val PREF_LATEST_URL = "update_latest_url"
    const val PREF_LAST_CHECKED_MS = "update_last_checked_ms"
    const val PREF_NOTIFIED_TAG = "update_notified_tag"
    const val PREF_DISMISSED_TAG = "update_dismissed_tag"

    const val WORK_NAME = "update_check"
    const val NOTIFICATION_CHANNEL_ID = "app_updates"
    const val NOTIFICATION_ID = 1002

    private const val GITHUB_LATEST_RELEASE_URL =
        "https://api.github.com/repos/dankouwu/wallpaper-lyrics/releases/latest"

    data class GitHubRelease(
        @SerializedName("tag_name") val tagName: String,
        @SerializedName("html_url") val htmlUrl: String
    )

    sealed class CheckResult {
        data class NewerAvailable(val tag: String, val url: String) : CheckResult()
        data class UpToDate(val tag: String, val url: String) : CheckResult()
        data class Failed(val message: String) : CheckResult()
    }

    fun isNewerVersion(candidate: String, current: String): Boolean {
        val candidateParts = parseVersionParts(candidate) ?: return false
        val currentParts = parseVersionParts(current) ?: return false
        val maxLen = maxOf(candidateParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val c = candidateParts.getOrElse(i) { 0 }
            val cur = currentParts.getOrElse(i) { 0 }
            if (c != cur) {
                return c > cur
            }
        }
        return false
    }

    private fun parseVersionParts(v: String): List<Int>? {
        val trimmed = v.trim()
        if (trimmed.isEmpty()) return null
        val withoutV = if (trimmed.startsWith("v", ignoreCase = true)) {
            trimmed.substring(1)
        } else {
            trimmed
        }
        val clean = withoutV.substringBefore('-').substringBefore('+')
        if (clean.isEmpty()) return null
        val tokens = clean.split('.')
        val parts = mutableListOf<Int>()
        for (token in tokens) {
            if (token.isEmpty()) return null
            val num = token.toIntOrNull() ?: return null
            if (num < 0) return null
            parts.add(num)
        }
        return parts
    }

    fun calculateInitialDelayMs(
        now: ZonedDateTime,
        targetHour: Int = 12,
        targetMinute: Int = 0
    ): Long {
        var target = now.withHour(targetHour).withMinute(targetMinute).withSecond(0).withNano(0)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        return Duration.between(now, target).toMillis()
    }

    fun parseReleaseJson(json: String): GitHubRelease? {
        if (json.isBlank()) return null
        return try {
            val release = Gson().fromJson(json, GitHubRelease::class.java) ?: return null
            if (release.tagName.isNotBlank() && release.htmlUrl.isNotBlank()) {
                release
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchLatestRelease(versionName: String): GitHubRelease? = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(GITHUB_LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "WallpaperLyrics/$versionName")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected HTTP code ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("Empty response body")
            parseReleaseJson(body) ?: throw IOException("Failed to parse release JSON")
        }
    }

    suspend fun checkNow(context: Context, installedVersion: String): CheckResult = withContext(Dispatchers.IO) {
        try {
            val release = fetchLatestRelease(installedVersion)
                ?: return@withContext CheckResult.Failed("Couldn't reach GitHub")

            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString(PREF_LATEST_TAG, release.tagName)
                putString(PREF_LATEST_URL, release.htmlUrl)
                putLong(PREF_LAST_CHECKED_MS, System.currentTimeMillis())
                apply()
            }

            if (isNewerVersion(release.tagName, installedVersion)) {
                CheckResult.NewerAvailable(release.tagName, release.htmlUrl)
            } else {
                CheckResult.UpToDate(release.tagName, release.htmlUrl)
            }
        } catch (e: Exception) {
            CheckResult.Failed("Couldn't reach GitHub")
        }
    }

    fun scheduleDailyCheck(context: Context) {
        val initialDelayMs = calculateInitialDelayMs(ZonedDateTime.now(ZoneId.systemDefault()))
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Uses initial delay so the 24 hour periodic run lands around local 12:00
        val periodicWork = PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWork
        )
    }

    fun cancelDailyCheck(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun postUpdateNotification(context: Context, tag: String, releaseUrl: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "App updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for new app releases"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cleanTag = tag.trim().removePrefix("v").removePrefix("V")
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Update available")
            .setContentText("Wallpaper Lyrics $cleanTag is available")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun showUpdateDialog(
        activity: Activity,
        installedVersion: String,
        latestTag: String,
        releaseUrl: String,
        rememberDismissal: Boolean = true
    ) {
        val dialog = android.app.Dialog(activity).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setCancelable(true)
        }

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                LyricsSettings.dpToPx(activity, 24f),
                LyricsSettings.dpToPx(activity, 24f),
                LyricsSettings.dpToPx(activity, 24f),
                LyricsSettings.dpToPx(activity, 20f)
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = LyricsSettings.dpToPx(activity, 16f).toFloat()
            }
        }

        val titleText = TextView(activity).apply {
            text = "Update available"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, LyricsSettings.dpToPx(activity, 10f))
        }
        container.addView(titleText)

        val cleanLatest = latestTag.trim().removePrefix("v").removePrefix("V")
        val cleanInstalled = installedVersion.trim().removePrefix("v").removePrefix("V")
        val msgText = TextView(activity).apply {
            text = "A newer version of Wallpaper Lyrics is available.\n\nInstalled: $cleanInstalled\nLatest: $cleanLatest"
            textSize = 14f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, 0, 0, LyricsSettings.dpToPx(activity, 20f))
        }
        container.addView(msgText)

        val buttonLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        fun markDismissed() {
            if (!rememberDismissal) return
            val prefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
            prefs.edit().putString(PREF_DISMISSED_TAG, latestTag).apply()
        }

        val laterButton = Button(activity).apply {
            text = "Later"
            setTextColor(Color.parseColor("#8E8E93"))
            transformationMethod = null
            background = null
            setOnClickListener {
                markDismissed()
                dialog.dismiss()
            }
        }
        buttonLayout.addView(laterButton)

        val downloadButton = Button(activity).apply {
            text = "Download"
            setTextColor(Color.WHITE)
            transformationMethod = null
            background = null
            setOnClickListener {
                markDismissed()
                dialog.dismiss()
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)))
                } catch (e: Exception) {
                    Toast.makeText(activity, "Could not open release link", Toast.LENGTH_SHORT).show()
                }
            }
        }
        buttonLayout.addView(downloadButton)
        container.addView(buttonLayout)

        dialog.setContentView(container)

        dialog.setOnCancelListener {
            markDismissed()
        }

        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.85f).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        dialog.show()
    }
}
