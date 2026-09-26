package com.dnk.wallpaperlyrics

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(UpdateCheck.PREF_UPDATE_CHECK_ENABLED, false)
        if (!enabled) {
            return@withContext Result.success()
        }

        val versionName = try {
            applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName
        } catch (e: Exception) {
            "0.0.0"
        }

        val release = try {
            UpdateCheck.fetchLatestRelease(versionName)
        } catch (e: Exception) {
            return@withContext Result.retry()
        } ?: return@withContext Result.retry()

        prefs.edit().apply {
            putString(UpdateCheck.PREF_LATEST_TAG, release.tagName)
            putString(UpdateCheck.PREF_LATEST_URL, release.htmlUrl)
            putLong(UpdateCheck.PREF_LAST_CHECKED_MS, System.currentTimeMillis())
            apply()
        }

        if (UpdateCheck.isNewerVersion(release.tagName, versionName)) {
            val notifiedTag = prefs.getString(UpdateCheck.PREF_NOTIFIED_TAG, null)
            if (notifiedTag != release.tagName) {
                UpdateCheck.postUpdateNotification(applicationContext, release.tagName, release.htmlUrl)
                prefs.edit().putString(UpdateCheck.PREF_NOTIFIED_TAG, release.tagName).apply()
            }
        }

        Result.success()
    }
}
