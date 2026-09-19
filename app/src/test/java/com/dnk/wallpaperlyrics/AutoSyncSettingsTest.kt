package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSyncSettingsTest {

    @Test
    fun keysMatchExpectedConstants() {
        assertEquals("auto_sync_enabled", AutoSyncSettings.KEY_ENABLED)
        assertEquals("auto_sync_status", AutoSyncSettings.KEY_STATUS)
        assertEquals("auto_offset_", AutoSyncSettings.AUTO_OFFSET_PREFIX)
    }

    @Test
    fun autoOffsetKeyMatchesSongDelayKeyShapeForNonNullArtist() {
        val title = "Bohemian Rhapsody"
        val artist = "Queen"
        assertEquals("auto_offset_Bohemian Rhapsody_Queen", AutoSyncSettings.autoOffsetKey(title, artist))
        assertEquals("song_delay_Bohemian Rhapsody_Queen", AutoSyncSettings.manualDelayKey(title, artist))
    }

    @Test
    fun autoOffsetKeyMatchesSongDelayKeyShapeForNullArtist() {
        val title = "Unknown Track"
        assertEquals("auto_offset_Unknown Track_null", AutoSyncSettings.autoOffsetKey(title, null))
        assertEquals("song_delay_Unknown Track_null", AutoSyncSettings.manualDelayKey(title, null))
    }

    @Test
    fun songOffsetReturnsManualDelayWhenNonZeroRegardlessOfAutoSettings() {
        val manual = 250
        assertEquals(manual, AutoSyncSettings.songOffset(manualDelayMs = manual, autoOffsetMs = null, autoEnabled = false))
        assertEquals(manual, AutoSyncSettings.songOffset(manualDelayMs = manual, autoOffsetMs = null, autoEnabled = true))
        assertEquals(manual, AutoSyncSettings.songOffset(manualDelayMs = manual, autoOffsetMs = 500, autoEnabled = false))
        assertEquals(manual, AutoSyncSettings.songOffset(manualDelayMs = manual, autoOffsetMs = 500, autoEnabled = true))

        val negativeManual = -120
        assertEquals(negativeManual, AutoSyncSettings.songOffset(manualDelayMs = negativeManual, autoOffsetMs = 300, autoEnabled = true))
    }

    @Test
    fun songOffsetUsesAutoOffsetOnlyWhenManualDelayIsZeroAndAutoIsEnabled() {
        val auto = 420
        assertEquals(auto, AutoSyncSettings.songOffset(manualDelayMs = 0, autoOffsetMs = auto, autoEnabled = true))
        assertEquals(0, AutoSyncSettings.songOffset(manualDelayMs = 0, autoOffsetMs = auto, autoEnabled = false))
        assertEquals(0, AutoSyncSettings.songOffset(manualDelayMs = 0, autoOffsetMs = null, autoEnabled = true))
        assertEquals(0, AutoSyncSettings.songOffset(manualDelayMs = 0, autoOffsetMs = null, autoEnabled = false))
    }

    @Test
    fun shouldDetectReturnsTrueOnlyWhenEnabledAndManualDelayZeroAndNoStoredOffset() {
        assertTrue(AutoSyncSettings.shouldDetect(autoEnabled = true, manualDelayMs = 0, storedAutoOffsetMs = null))

        assertFalse(AutoSyncSettings.shouldDetect(autoEnabled = false, manualDelayMs = 0, storedAutoOffsetMs = null))
        assertFalse(AutoSyncSettings.shouldDetect(autoEnabled = true, manualDelayMs = 150, storedAutoOffsetMs = null))
        assertFalse(AutoSyncSettings.shouldDetect(autoEnabled = true, manualDelayMs = -100, storedAutoOffsetMs = null))
        assertFalse(AutoSyncSettings.shouldDetect(autoEnabled = true, manualDelayMs = 0, storedAutoOffsetMs = 200))
        assertFalse(AutoSyncSettings.shouldDetect(autoEnabled = true, manualDelayMs = 0, storedAutoOffsetMs = 0))
        assertFalse(AutoSyncSettings.shouldDetect(autoEnabled = false, manualDelayMs = 200, storedAutoOffsetMs = 200))
    }
}
