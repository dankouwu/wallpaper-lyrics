package com.dnk.wallpaperlyrics

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

class MediaObserver(
    private val context: Context,
    private val onMetadataChanged: (MediaMetadata?) -> Unit,
    private val onPlaybackStateChanged: (PlaybackState?) -> Unit
) {
    private val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private var activeController: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private val componentName = ComponentName(context, NotificationService::class.java)

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            // Use this@MediaObserver to call the lambda passed in the constructor
            this@MediaObserver.onMetadataChanged(metadata)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            this@MediaObserver.onPlaybackStateChanged(state)
        }
    }

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateActiveController(controllers)
    }

    fun start() {
        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionListener, componentName, handler)
            refresh()
        } catch (e: Exception) {
            Log.e("MediaObserver", "Error starting observer", e)
        }
    }

    fun stop() {
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener)
            activeController?.unregisterCallback(callback)
        } catch (e: Exception) {}
    }

    fun refresh() {
        kotlin.concurrent.thread(start = true) {
            try {
                val controllers = mediaSessionManager.getActiveSessions(componentName)
                handler.post {
                    updateActiveController(controllers)
                }
            } catch (e: Exception) {
                Log.e("MediaObserver", "Failed to refresh sessions", e)
            }
        }
    }

    private fun updateActiveController(controllers: List<MediaController>?) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val preferred = prefs.getString("preferred_media_player", "default") ?: "default"

        val newController = if (preferred == "default") {
            controllers?.firstOrNull()
        } else {
            controllers?.find { 
                val pkg = it.packageName.lowercase()
                when (preferred) {
                    "spotify" -> pkg.contains("spotify")
                    "tidal" -> pkg.contains("tidal")
                    "kdeconnect" -> pkg.contains("kdeconnect")
                    else -> false
                }
            }
        }

        if (newController?.packageName != activeController?.packageName) {
            activeController?.unregisterCallback(callback)
            activeController = newController
            activeController?.registerCallback(callback, handler)
            
            this@MediaObserver.onMetadataChanged(activeController?.metadata)
            this@MediaObserver.onPlaybackStateChanged(activeController?.playbackState)
        }
    }
    
    fun getPlaybackState(): PlaybackState? {
        return activeController?.playbackState
    }

    fun getActivePackageName(): String? = activeController?.packageName

    /**
     * Compute the extrapolated playback position directly from the live PlaybackState.
     * This avoids all intermediate caching that can go stale between resync cycles.
     */
    fun getCurrentPosition(): Long {
        val state = activeController?.playbackState ?: return 0L
        if (state.state != PlaybackState.STATE_PLAYING) return state.position

        val speed = if (state.playbackSpeed > 0f) state.playbackSpeed else 1.0f
        return if (state.lastPositionUpdateTime > 0L) {
            val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            state.position + (elapsed * speed).toLong()
        } else {
            // No timestamp to extrapolate from. The caller falls back to its own
            // extrapolation in this case.
            state.position
        }
    }
}
