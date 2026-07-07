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
        val newController = controllers?.find { 
            val pkg = it.packageName.lowercase()
            pkg.contains("spotify") || pkg.contains("tidal")
        }

        if (newController?.packageName != activeController?.packageName) {
            activeController?.unregisterCallback(callback)
            activeController = newController
            activeController?.registerCallback(callback, handler)
            
            this@MediaObserver.onMetadataChanged(activeController?.metadata)
            this@MediaObserver.onPlaybackStateChanged(activeController?.playbackState)
        }
    }
    
    fun getCurrentPosition(): Long {
        val state = activeController?.playbackState ?: return 0L
        if (state.state != PlaybackState.STATE_PLAYING) return state.position
        
        val timeDiff = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
        return state.position + (timeDiff * state.playbackSpeed).toLong()
    }
}
