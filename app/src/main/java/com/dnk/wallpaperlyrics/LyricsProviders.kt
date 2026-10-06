package com.dnk.wallpaperlyrics

object LyricsProviders {
    const val ID_MUSIXMATCH_WORDS = "musixmatch_words"
    const val ID_MUSIXMATCH_LINES = "musixmatch_lines"
    const val ID_LRCLIB = "lrclib"
    const val ID_CUSTOM = "custom"

    fun displayName(providerId: String): String = when (providerId) {
        ID_MUSIXMATCH_WORDS -> "Musixmatch word sync"
        ID_MUSIXMATCH_LINES -> "Musixmatch line sync"
        ID_LRCLIB -> "LRCLIB"
        ID_CUSTOM -> "Custom provider"
        else -> providerId
    }

    fun getPickerOptions(customEnabled: Boolean, customEndpoint: String?): List<Pair<String, String>> {
        val options = mutableListOf(
            Pair("Musixmatch word sync (richsync only)", ID_MUSIXMATCH_WORDS),
            Pair("Musixmatch line sync (subtitle only)", ID_MUSIXMATCH_LINES),
            Pair("LRCLIB", ID_LRCLIB)
        )
        if (customEnabled && !customEndpoint.isNullOrBlank()) {
            options.add(Pair("Custom provider", ID_CUSTOM))
        }
        return options
    }
}
