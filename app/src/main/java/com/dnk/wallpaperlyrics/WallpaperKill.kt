package com.dnk.wallpaperlyrics

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.widget.Toast

// An app cannot force stop itself. Killing only the process is not enough: Android rebinds the
// wallpaper within about two seconds. A real force stop makes Android drop the live wallpaper
// for the default one, so this does the same: clear it, then end the process.
object WallpaperKill {

    fun kill(activity: Activity) {
        val wpm = WallpaperManager.getInstance(activity)
        val ours = activity.packageName
        val systemOurs = wpm.wallpaperInfo?.packageName == ours
        val lockOurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            wpm.getWallpaperInfo(WallpaperManager.FLAG_LOCK)?.packageName == ours
        // A lock screen that shares the home wallpaper reports no info of its own. Clearing only
        // the home screen then moves this wallpaper to the lock screen, and Android rebinds it.
        val lockShared = try {
            wpm.getWallpaperId(WallpaperManager.FLAG_LOCK) < 0
        } catch (e: Exception) {
            true
        }
        var which = 0
        if (systemOurs) which = which or WallpaperManager.FLAG_SYSTEM
        // A separate lock screen image is left alone.
        if (lockOurs || (systemOurs && lockShared)) which = which or WallpaperManager.FLAG_LOCK
        try {
            if (which != 0) wpm.clear(which)
        } catch (e: Exception) {
            Toast.makeText(activity, "Could not reset the wallpaper", Toast.LENGTH_SHORT).show()
            return
        }
        activity.finishAffinity()
        Process.killProcess(Process.myPid())
    }

    // Home or lock screen. isWallpaperActive looks at the home screen only.
    fun isRunning(context: Context): Boolean {
        val wpm = WallpaperManager.getInstance(context)
        if (wpm.wallpaperInfo?.packageName == context.packageName) return true
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            wpm.getWallpaperInfo(WallpaperManager.FLAG_LOCK)?.packageName == context.packageName
    }

    fun openPicker(activity: Activity) {
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(activity, LyricsWallpaperService::class.java)
            )
            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(activity, "Could not open wallpaper picker", Toast.LENGTH_SHORT).show()
        }
    }
}
