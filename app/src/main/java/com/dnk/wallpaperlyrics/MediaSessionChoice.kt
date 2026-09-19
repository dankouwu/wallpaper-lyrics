package com.dnk.wallpaperlyrics

object MediaSessionChoice {
    // Mirrors android.media.session.PlaybackState.STATE_NONE
    const val STATE_NONE = 0
    // Mirrors android.media.session.PlaybackState.STATE_PLAYING
    const val STATE_PLAYING = 3
    // Mirrors android.media.session.PlaybackState.STATE_ERROR
    const val STATE_ERROR = 7

    data class Candidate(
        val packageName: String,
        val hasUsableMetadata: Boolean,
        val playbackState: Int,
        val isCurrent: Boolean = false,
        val hasEverPlayed: Boolean = true
    )

    fun isEligible(packageName: String, preferred: String): Boolean {
        return matchesPreference(packageName, preferred) &&
            !isNonMusicSystemPackage(packageName)
    }

    fun choose(candidates: List<Candidate>, preferred: String): Candidate? {
        // Package identity is the only immutable filter here. A session that has never played
        // is not drawn but is still watched by the observer, so a session restored at boot
        // cannot take the screen before anything plays.
        val eligible = candidates.filter { isEligible(it.packageName, preferred) && it.hasEverPlayed }

        if (eligible.isEmpty()) return null

        return eligible.maxWithOrNull(
            compareBy<Candidate> { it.playbackState == STATE_PLAYING && it.hasUsableMetadata }
                .thenBy { it.isCurrent }
                .thenBy { it.hasUsableMetadata }
                .thenBy { isKnownMusicPackage(it.packageName) }
                .thenBy { it.playbackState == STATE_PLAYING }
                .thenBy { it.playbackState != STATE_ERROR && it.playbackState != STATE_NONE }
                .thenBy { it.packageName }
        )
    }

    private fun matchesPreference(packageName: String, preferred: String): Boolean {
        if (preferred == "default") return true
        val pkg = packageName.lowercase()
        return when (preferred) {
            "spotify" -> pkg.contains("spotify")
            "tidal" -> pkg.contains("tidal")
            "kdeconnect" -> pkg.contains("kdeconnect")
            else -> false
        }
    }

    private fun isNonMusicSystemPackage(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        return pkg == "com.android.server.telecom" || pkg.contains("bluetooth")
    }

    private fun isKnownMusicPackage(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        return pkg.contains("spotify") || pkg.contains("tidal") || pkg.contains("kdeconnect")
    }
}
