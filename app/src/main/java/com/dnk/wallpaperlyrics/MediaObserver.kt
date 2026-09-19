package com.dnk.wallpaperlyrics

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
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
    private var lastDyingRefreshSessionToken: MediaSession.Token? = null
    private var lastActivePlaybackState: Int = PlaybackState.STATE_NONE
    private var currentControllers: List<MediaController> = emptyList()
    private val watchedSessions = mutableMapOf<MediaSession.Token, WatchedSession>()
    private val everPlayedTokens = mutableSetOf<MediaSession.Token>()
    private val handler = Handler(Looper.getMainLooper())
    private val componentName = ComponentName(context, NotificationService::class.java)

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            // Use this@MediaObserver to call the lambda passed in the constructor
            this@MediaObserver.onMetadataChanged(metadata)
            if (metadata == null) {
                rescanIfSessionGone()
            } else {
                val state = activeController?.playbackState?.state
                if (state != null && state != PlaybackState.STATE_NONE && state != PlaybackState.STATE_ERROR) {
                    lastDyingRefreshSessionToken = null
                }
            }
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            this@MediaObserver.onPlaybackStateChanged(state)
            val playbackState = state?.state ?: PlaybackState.STATE_NONE
            if (playbackState == PlaybackState.STATE_NONE || playbackState == PlaybackState.STATE_ERROR) {
                rescanIfSessionGone()
            } else {
                if (activeController?.metadata != null) {
                    lastDyingRefreshSessionToken = null
                }
            }
            if (playbackState != lastActivePlaybackState) {
                lastActivePlaybackState = playbackState
                updateActiveController(currentControllers)
            }
        }

        override fun onSessionDestroyed() {
            // Active session destruction requires rescanning to transfer playback to another player.
            refresh()
        }
    }

    private inner class WatchedSession(
        val controller: MediaController,
        var lastPlaybackState: Int
    ) : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            // Many players push PlaybackState updates for playback position extrapolation.
            // Re-selection is only necessary when the state enum itself changes.
            val newState = state?.state ?: PlaybackState.STATE_NONE
            if (newState != lastPlaybackState) {
                lastPlaybackState = newState
                updateActiveController(currentControllers)
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateActiveController(currentControllers)
        }

        override fun onSessionDestroyed() {
            // Drops dead sessions from the watched set immediately without waiting for a rescan.
            try {
                controller.unregisterCallback(this)
            } catch (e: Exception) {}
            watchedSessions.remove(controller.sessionToken)
            everPlayedTokens.remove(controller.sessionToken)
            currentControllers = currentControllers.filter { it.sessionToken != controller.sessionToken }
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
            activeController = null
            for (session in watchedSessions.values) {
                try {
                    session.controller.unregisterCallback(session)
                } catch (e: Exception) {}
            }
            watchedSessions.clear()
            currentControllers = emptyList()
            lastDyingRefreshSessionToken = null
            lastActivePlaybackState = PlaybackState.STATE_NONE
        } catch (e: Exception) {}
    }

    private fun rescanIfSessionGone() {
        val token = activeController?.sessionToken ?: return
        if (token != lastDyingRefreshSessionToken) {
            lastDyingRefreshSessionToken = token
            refresh()
        }
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
        val controllersList = controllers ?: emptyList()
        currentControllers = controllersList

        val currentTokens = controllersList.map { it.sessionToken }.toSet()
        everPlayedTokens.retainAll(currentTokens)
        for (controller in controllersList) {
            if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
                everPlayedTokens.add(controller.sessionToken)
            }
        }

        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val preferred = prefs.getString("preferred_media_player", "default") ?: "default"

        val candidates = controllersList.map { controller ->
            val meta = controller.metadata
            val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            val state = controller.playbackState?.state ?: PlaybackState.STATE_NONE
            MediaSessionChoice.Candidate(
                packageName = controller.packageName,
                hasUsableMetadata = !title.isNullOrBlank(),
                playbackState = state,
                isCurrent = controller.sessionToken == activeController?.sessionToken,
                hasEverPlayed = controller.sessionToken in everPlayedTokens
            )
        }
        val chosen = MediaSessionChoice.choose(candidates, preferred)
        val chosenIndex = if (chosen != null) candidates.indexOfFirst { it === chosen } else -1
        val newController = if (chosenIndex >= 0) controllersList.getOrNull(chosenIndex) else null
        val newActiveToken = newController?.sessionToken

        val eligibleNonActiveMap = controllersList
            .filter { it.sessionToken != newActiveToken && MediaSessionChoice.isEligible(it.packageName, preferred) }
            .associateBy { it.sessionToken }

        // Keep at most one callback registered per controller at all times.
        val watchedIterator = watchedSessions.iterator()
        while (watchedIterator.hasNext()) {
            val (token, session) = watchedIterator.next()
            if (token !in eligibleNonActiveMap) {
                try {
                    session.controller.unregisterCallback(session)
                } catch (e: Exception) {}
                watchedIterator.remove()
            }
        }

        // Compare session tokens rather than package names so that when a player destroys
        // and recreates its session, the new session controller replaces the dead one.
        if (newController?.sessionToken != activeController?.sessionToken) {
            try {
                activeController?.unregisterCallback(callback)
            } catch (e: Exception) {}
            activeController = newController
            lastDyingRefreshSessionToken = null
            lastActivePlaybackState = newController?.playbackState?.state ?: PlaybackState.STATE_NONE
            try {
                activeController?.registerCallback(callback, handler)
            } catch (e: Exception) {
                Log.e("MediaObserver", "Failed to register callback on active controller", e)
            }

            this@MediaObserver.onMetadataChanged(activeController?.metadata)
            this@MediaObserver.onPlaybackStateChanged(activeController?.playbackState)
        } else if (newController != null) {
            // Guard against a refresh loop: a refresh that selects the same controller must not
            // itself trigger another refresh while remaining in an empty or error state.
            val state = newController.playbackState?.state ?: PlaybackState.STATE_NONE
            if (state == PlaybackState.STATE_NONE || state == PlaybackState.STATE_ERROR || newController.metadata == null) {
                lastDyingRefreshSessionToken = newController.sessionToken
            }
        }

        for ((token, controller) in eligibleNonActiveMap) {
            val existing = watchedSessions[token]
            if (existing != null) {
                existing.lastPlaybackState = controller.playbackState?.state ?: PlaybackState.STATE_NONE
            } else {
                val session = WatchedSession(
                    controller = controller,
                    lastPlaybackState = controller.playbackState?.state ?: PlaybackState.STATE_NONE
                )
                try {
                    controller.registerCallback(session, handler)
                    watchedSessions[token] = session
                } catch (e: Exception) {
                    Log.e("MediaObserver", "Failed to register callback on non-active controller", e)
                }
            }
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
