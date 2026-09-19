package com.dnk.wallpaperlyrics

/**
 * Shared preference keys and offset calculation helpers for automatic lyrics synchronization.
 * No Android dependencies so logic can be validated directly in JVM unit tests.
 */
object AutoSyncSettings {
    const val KEY_ENABLED = "auto_sync_enabled"
    const val KEY_STATUS = "auto_sync_status"
    const val AUTO_OFFSET_PREFIX = "auto_offset_"

    fun autoOffsetKey(title: String, artist: String?): String = "auto_offset_${title}_${artist}"

    fun manualDelayKey(title: String, artist: String?): String = "song_delay_${title}_${artist}"

    /**
     * Offset added on top of the global offset for a song.
     * A non-zero manual delay always wins; the auto offset only counts when enabled.
     */
    fun songOffset(manualDelayMs: Int, autoOffsetMs: Int?, autoEnabled: Boolean): Int {
        if (manualDelayMs != 0) return manualDelayMs
        if (autoEnabled && autoOffsetMs != null) return autoOffsetMs
        return 0
    }

    fun shouldDetect(autoEnabled: Boolean, manualDelayMs: Int, storedAutoOffsetMs: Int?): Boolean {
        return autoEnabled && manualDelayMs == 0 && storedAutoOffsetMs == null
    }
}
