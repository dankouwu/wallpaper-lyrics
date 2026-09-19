package com.dnk.wallpaperlyrics

import java.util.Locale

/**
 * Thread-safe snapshot of media player playback state used to extrapolate
 * raw playback position without binder calls or allocations.
 */
data class PlaybackSnapshot(
    val positionMs: Long = 0L,
    val updateTimeRealtimeMs: Long = 0L,
    val speed: Float = 1.0f,
    val isPlaying: Boolean = false
) {
    fun currentPosition(nowRealtimeMs: Long): Long {
        if (!isPlaying) return positionMs
        val elapsed = (nowRealtimeMs - updateTimeRealtimeMs).coerceAtLeast(0L)
        val effectiveSpeed = if (speed > 0f) speed else 1.0f
        return positionMs + (elapsed * effectiveSpeed).toLong()
    }
}

/**
 * Pure decision logic and status formatting for automatic lyrics synchronization.
 * Contains no Android dependencies so logic can be validated directly in JVM unit tests.
 */
object AutoSyncLogic {

    fun formatSong(title: String, artist: String?): String {
        return if (artist.isNullOrBlank()) title else "$title - $artist"
    }

    fun formatSignedOffset(offsetMs: Int): String {
        return String.format(Locale.US, "%+d", offsetMs)
    }

    fun statusOff(): String = "Off"

    fun statusAudioAccessNotGranted(): String = "Audio access not granted"

    fun statusWaitingForSongWithLyrics(): String = "Waiting for a song with lyrics"

    fun statusUsingManualDelay(title: String, artist: String?): String {
        return "Using manual delay for ${formatSong(title, artist)}"
    }

    fun statusListening(title: String, artist: String?): String {
        return "Listening to ${formatSong(title, artist)}"
    }

    fun statusDetected(title: String, artist: String?, offsetMs: Int): String {
        return "Detected ${formatSignedOffset(offsetMs)} ms for ${formatSong(title, artist)}"
    }

    fun statusAlreadyDetected(title: String, artist: String?, offsetMs: Int): String {
        return "Already detected ${formatSignedOffset(offsetMs)} ms for ${formatSong(title, artist)}"
    }

    fun statusNotSureYet(title: String, artist: String?): String {
        return "Not sure yet for ${formatSong(title, artist)}, still listening"
    }

    fun statusCouldNotDetect(title: String, artist: String?): String {
        return "Could not detect ${formatSong(title, artist)}, will retry next time"
    }

    fun statusAudioCaptureFailed(reason: String): String {
        return "Audio capture failed: $reason"
    }

    fun isCaptureBlocked(
        currentTitle: String?,
        currentArtist: String?,
        failedTitle: String?,
        failedArtist: String?
    ): Boolean {
        if (currentTitle.isNullOrBlank() || failedTitle.isNullOrBlank()) return false
        val sameArtist = currentArtist?.takeIf { it.isNotBlank() } == failedArtist?.takeIf { it.isNotBlank() }
        return currentTitle == failedTitle && sameArtist
    }

    fun shouldStartCapture(
        enabled: Boolean,
        hasAudioPermission: Boolean,
        manualDelayMs: Int,
        storedAutoOffsetMs: Int?,
        title: String?,
        hasLyrics: Boolean,
        isPlaying: Boolean,
        isPreview: Boolean,
        failedTitle: String? = null,
        failedArtist: String? = null,
        artist: String? = null
    ): Boolean {
        if (isPreview) return false
        if (!enabled) return false
        if (!hasAudioPermission) return false
        if (title.isNullOrBlank()) return false
        if (!hasLyrics) return false
        if (!isPlaying) return false
        if (isCaptureBlocked(title, artist, failedTitle, failedArtist)) return false
        return AutoSyncSettings.shouldDetect(enabled, manualDelayMs, storedAutoOffsetMs)
    }
}

