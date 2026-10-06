package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsProvidersTest {

    @Test
    fun `picker options exclude custom provider when disabled`() {
        val options = LyricsProviders.getPickerOptions(customEnabled = false, customEndpoint = "http://10.0.2.2:8000/api")
        assertEquals(3, options.size)
        assertEquals(
            listOf(
                Pair("Musixmatch word sync (richsync only)", LyricsProviders.ID_MUSIXMATCH_WORDS),
                Pair("Musixmatch line sync (subtitle only)", LyricsProviders.ID_MUSIXMATCH_LINES),
                Pair("LRCLIB", LyricsProviders.ID_LRCLIB)
            ),
            options
        )
    }

    @Test
    fun `picker options exclude custom provider when endpoint is blank`() {
        val optionsEmpty = LyricsProviders.getPickerOptions(customEnabled = true, customEndpoint = "")
        assertEquals(3, optionsEmpty.size)

        val optionsBlank = LyricsProviders.getPickerOptions(customEnabled = true, customEndpoint = "   ")
        assertEquals(3, optionsBlank.size)

        val optionsNull = LyricsProviders.getPickerOptions(customEnabled = true, customEndpoint = null)
        assertEquals(3, optionsNull.size)
    }

    @Test
    fun `picker options include custom provider when enabled and endpoint is non blank`() {
        val options = LyricsProviders.getPickerOptions(customEnabled = true, customEndpoint = "http://10.0.2.2:8000/api")
        assertEquals(4, options.size)
        assertEquals(
            Pair("Custom provider", LyricsProviders.ID_CUSTOM),
            options[3]
        )
    }

    @Test
    fun `display name maps provider ids correctly for toasts`() {
        assertEquals("Musixmatch word sync", LyricsProviders.displayName(LyricsProviders.ID_MUSIXMATCH_WORDS))
        assertEquals("Musixmatch line sync", LyricsProviders.displayName(LyricsProviders.ID_MUSIXMATCH_LINES))
        assertEquals("LRCLIB", LyricsProviders.displayName(LyricsProviders.ID_LRCLIB))
        assertEquals("Custom provider", LyricsProviders.displayName(LyricsProviders.ID_CUSTOM))
    }
}
