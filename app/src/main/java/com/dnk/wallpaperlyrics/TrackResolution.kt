package com.dnk.wallpaperlyrics

import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * Shared preference keys, publication logic, and track resolution helpers.
 * Pure Kotlin functions remain accessible for direct JVM unit testing.
 */
object TrackResolution {
    const val PREFS_NAME = "settings"
    const val KEY_WALLPAPER_TITLE = "wallpaper_track_title"
    const val KEY_WALLPAPER_ARTIST = "wallpaper_track_artist"

    enum class TrackSource {
        WALLPAPER,
        MEDIA_SESSION
    }

    data class ResolvedTrack(
        val title: String,
        val artist: String,
        val source: TrackSource
    ) {
        val isFallback: Boolean
            get() = source == TrackSource.MEDIA_SESSION
    }

    sealed class PublicationAction {
        data class Publish(val title: String, val artist: String) : PublicationAction()
        object Clear : PublicationAction()
        object None : PublicationAction()
    }

    fun determinePublicationAction(
        isPreview: Boolean,
        title: String?,
        artist: String?
    ): PublicationAction {
        if (isPreview) return PublicationAction.None
        val cleanTitle = title?.trim()
        val cleanArtist = artist?.trim()
        return if (!cleanTitle.isNullOrEmpty() && !cleanArtist.isNullOrEmpty()) {
            PublicationAction.Publish(cleanTitle, cleanArtist)
        } else {
            PublicationAction.Clear
        }
    }

    fun determineClearAction(isPreview: Boolean): PublicationAction {
        return if (isPreview) PublicationAction.None else PublicationAction.Clear
    }

    fun publishTrack(prefs: SharedPreferences, isPreview: Boolean, title: String?, artist: String?) {
        when (val action = determinePublicationAction(isPreview, title, artist)) {
            is PublicationAction.Publish -> {
                prefs.edit()
                    .putString(KEY_WALLPAPER_TITLE, action.title)
                    .putString(KEY_WALLPAPER_ARTIST, action.artist)
                    .apply()
            }
            PublicationAction.Clear -> {
                prefs.edit()
                    .remove(KEY_WALLPAPER_TITLE)
                    .remove(KEY_WALLPAPER_ARTIST)
                    .apply()
            }
            PublicationAction.None -> Unit
        }
    }

    fun clearTrack(prefs: SharedPreferences, isPreview: Boolean) {
        if (determineClearAction(isPreview) is PublicationAction.Clear) {
            prefs.edit()
                .remove(KEY_WALLPAPER_TITLE)
                .remove(KEY_WALLPAPER_ARTIST)
                .apply()
        }
    }

    fun resolveTrack(
        publishedTitle: String?,
        publishedArtist: String?,
        sessionTrackProvider: () -> Pair<String, String>?
    ): ResolvedTrack? {
        val cleanPublishedTitle = publishedTitle?.trim()
        val cleanPublishedArtist = publishedArtist?.trim()
        if (!cleanPublishedTitle.isNullOrEmpty() && !cleanPublishedArtist.isNullOrEmpty()) {
            return ResolvedTrack(cleanPublishedTitle, cleanPublishedArtist, TrackSource.WALLPAPER)
        }

        val sessionTrack = sessionTrackProvider() ?: return null
        val sessionTitle = sessionTrack.first.trim()
        val sessionArtist = sessionTrack.second.trim()
        if (sessionTitle.isEmpty() || sessionArtist.isEmpty()) {
            return null
        }
        return ResolvedTrack(sessionTitle, sessionArtist, TrackSource.MEDIA_SESSION)
    }

    fun deriveLyricsCacheKey(title: String, artist: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest("${title}_$artist".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
