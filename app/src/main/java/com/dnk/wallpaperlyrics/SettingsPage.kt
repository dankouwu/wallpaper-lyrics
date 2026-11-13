package com.dnk.wallpaperlyrics

import android.content.ContextWrapper
import android.view.View

// One settings screen. They used to be activities, but a back gesture can only drag a screen
// off and show the one under it when both live in the same window, so MainActivity now holds
// them as a stack. The Activity calls the screens used are passed through to it here.
abstract class SettingsPage(val activity: MainActivity) : ContextWrapper(activity) {

    lateinit var view: View
        private set

    var isFinishing = false
        internal set
    var isDestroyed = false
        internal set

    abstract fun onCreate()

    fun setContentView(content: View) {
        view = content
    }

    open fun onResume() {}
    open fun onPause() {}
    open fun onStop() {}
    open fun onDestroy() {}

    open fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {}

    fun finish() = activity.closePage(this)

    fun runOnUiThread(action: Runnable) = activity.runOnUiThread(action)

    fun requestPermissions(permissions: Array<String>, requestCode: Int) =
        activity.requestPermissions(permissions, requestCode)

    fun shouldShowRequestPermissionRationale(permission: String) =
        activity.shouldShowRequestPermissionRationale(permission)
}
