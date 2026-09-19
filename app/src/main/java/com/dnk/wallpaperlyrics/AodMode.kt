package com.dnk.wallpaperlyrics

enum class AodMode {
    METADATA_ONLY,
    METADATA_AND_BACKGROUND,
    OFF;

    companion object {
        const val KEY = "aod_mode"

        fun fromPref(value: String?): AodMode = when (value) {
            "metadata" -> METADATA_ONLY
            "off" -> OFF
            else -> METADATA_AND_BACKGROUND
        }

        fun toPref(mode: AodMode): String = when (mode) {
            METADATA_ONLY -> "metadata"
            OFF -> "off"
            METADATA_AND_BACKGROUND -> "metadata_background"
        }
    }
}
