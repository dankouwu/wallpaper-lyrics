package com.dnk.wallpaperlyrics

/**
 * Keys, defaults and color parsing for the idle screen shown when nothing is playing.
 * No Android imports, so the parsing rules can be tested directly.
 */
object IdleScreenSettings {
    const val KEY_IDLE_TITLE = "idle_title_text"
    const val KEY_IDLE_ACCENT = "idle_color_accent"
    const val KEY_IDLE_BASE = "idle_color_base"
    const val KEY_IDLE_MID = "idle_color_mid"
    const val KEY_IDLE_HIGHLIGHT = "idle_color_highlight"

    const val KEY_IDLE_SAVED_COLORS = "idle_saved_colors"
    const val MAX_SAVED_COLORS = 12

    const val DEFAULT_IDLE_TITLE = "No Music Playing"
    const val DEFAULT_ACCENT = 0xFFFF0055.toInt()
    const val DEFAULT_BASE = 0xFF0A0B1A.toInt()
    const val DEFAULT_MID = 0xFF7A22FF.toInt()
    const val DEFAULT_HIGHLIGHT = 0xFFD6C7FF.toInt()

    fun parseHexColor(input: String): Int? {
        val trimmed = input.trim()
        val hex = if (trimmed.startsWith('#')) trimmed.substring(1) else trimmed
        if (hex.length != 6 && hex.length != 8) return null
        // toLong(16) accepts a leading sign, so "-12345" would parse to a real color.
        if (!hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
        val fullHex = try {
            hex.toLong(16)
        } catch (e: NumberFormatException) {
            return null
        }
        return if (hex.length == 6) {
            (0xFF000000.toLong() or fullHex).toInt()
        } else {
            (0xFF000000.toLong() or (fullHex and 0x00FFFFFF.toLong())).toInt()
        }
    }

    fun formatHexColor(color: Int): String {
        val rgb = color and 0x00FFFFFF
        return String.format("#%06X", rgb)
    }

    fun resolveIdleTitle(stored: String?): String {
        return stored ?: DEFAULT_IDLE_TITLE
    }

    fun parseSavedColors(stored: String?): List<Int> {
        if (stored == null) {
            return listOf(DEFAULT_ACCENT, DEFAULT_BASE, DEFAULT_MID, DEFAULT_HIGHLIGHT)
        }
        if (stored.isEmpty()) {
            return emptyList()
        }
        return stored.split(',')
            .mapNotNull { parseHexColor(it) }
            .distinct()
            .take(MAX_SAVED_COLORS)
    }

    fun formatSavedColors(colors: List<Int>): String {
        return colors.joinToString(",") { formatHexColor(it) }
    }

    fun addSavedColor(existing: List<Int>, color: Int): List<Int> {
        val filtered = existing.filter { it != color }
        return (listOf(color) + filtered).take(MAX_SAVED_COLORS)
    }

    fun componentFromTouch(position: Float, size: Float): Float {
        if (size <= 0f) return 0f
        return (position / size).coerceIn(0f, 1f)
    }
}
