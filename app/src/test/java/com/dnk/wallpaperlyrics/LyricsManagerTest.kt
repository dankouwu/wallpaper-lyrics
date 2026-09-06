package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LyricsManagerTest {

    @Test
    fun `parseLrcText preserves close authoritative line start timestamps`() {
        val parsed = LyricsManager.parseLrcText(
            """
                [00:38.81]A deliberately lengthy first lyric line
                [00:41.14]A deliberately lengthy second lyric line
                [00:44.59]A deliberately lengthy third lyric line
            """.trimIndent(),
            durationMs = 60_000L
        )

        assertNotNull(parsed)
        val lyrics = parsed!!.filterNot { it.isInstrumental }
        assertEquals(listOf(38_810L, 41_140L, 44_590L), lyrics.map { it.startTime })
    }
}
