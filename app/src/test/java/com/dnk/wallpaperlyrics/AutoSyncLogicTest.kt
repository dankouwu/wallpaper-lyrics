package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSyncLogicTest {

    @Test
    fun formatSongFormatsCorrectly() {
        assertEquals("Song - Artist", AutoSyncLogic.formatSong("Song", "Artist"))
        assertEquals("Song", AutoSyncLogic.formatSong("Song", null))
        assertEquals("Song", AutoSyncLogic.formatSong("Song", ""))
        assertEquals("Song", AutoSyncLogic.formatSong("Song", "   "))
    }

    @Test
    fun formatSignedOffsetFormatsCorrectly() {
        assertEquals("+120", AutoSyncLogic.formatSignedOffset(120))
        assertEquals("-80", AutoSyncLogic.formatSignedOffset(-80))
        assertEquals("+0", AutoSyncLogic.formatSignedOffset(0))
    }

    @Test
    fun statusStringsMatchExactSpecification() {
        assertEquals("Off", AutoSyncLogic.statusOff())
        assertEquals("Audio access not granted", AutoSyncLogic.statusAudioAccessNotGranted())
        assertEquals("Waiting for a song with lyrics", AutoSyncLogic.statusWaitingForSongWithLyrics())
        assertEquals(
            "Using manual delay for Song - Artist",
            AutoSyncLogic.statusUsingManualDelay("Song", "Artist")
        )
        assertEquals(
            "Listening to Song - Artist",
            AutoSyncLogic.statusListening("Song", "Artist")
        )
        assertEquals(
            "Detected +120 ms for Song - Artist",
            AutoSyncLogic.statusDetected("Song", "Artist", 120)
        )
        assertEquals(
            "Detected -80 ms for Song",
            AutoSyncLogic.statusDetected("Song", null, -80)
        )
        assertEquals(
            "Already detected +120 ms for Song - Artist",
            AutoSyncLogic.statusAlreadyDetected("Song", "Artist", 120)
        )
        assertEquals(
            "Not sure yet for Song - Artist, still listening",
            AutoSyncLogic.statusNotSureYet("Song", "Artist")
        )
        assertEquals(
            "Could not detect Song - Artist, will retry next time",
            AutoSyncLogic.statusCouldNotDetect("Song", "Artist")
        )
        assertEquals(
            "Audio capture failed: init error",
            AutoSyncLogic.statusAudioCaptureFailed("init error")
        )
    }

    @Test
    fun shouldStartCaptureReturnsTrueOnlyWhenAllSixConditionsHold() {
        val valid = AutoSyncLogic.shouldStartCapture(
            enabled = true,
            hasAudioPermission = true,
            manualDelayMs = 0,
            storedAutoOffsetMs = null,
            title = "Bohemian Rhapsody",
            hasLyrics = true,
            isPlaying = true,
            isPreview = false
        )
        assertTrue(valid)

        // 1. Not enabled
        assertFalse(
            AutoSyncLogic.shouldStartCapture(
                enabled = false,
                hasAudioPermission = true,
                manualDelayMs = 0,
                storedAutoOffsetMs = null,
                title = "Bohemian Rhapsody",
                hasLyrics = true,
                isPlaying = true,
                isPreview = false
            )
        )

        // 2. No audio permission
        assertFalse(
            AutoSyncLogic.shouldStartCapture(
                enabled = true,
                hasAudioPermission = false,
                manualDelayMs = 0,
                storedAutoOffsetMs = null,
                title = "Bohemian Rhapsody",
                hasLyrics = true,
                isPlaying = true,
                isPreview = false
            )
        )

        // 3. Manual delay non-zero
        assertFalse(
            AutoSyncLogic.shouldStartCapture(
                enabled = true,
                hasAudioPermission = true,
                manualDelayMs = 150,
                storedAutoOffsetMs = null,
                title = "Bohemian Rhapsody",
                hasLyrics = true,
                isPlaying = true,
                isPreview = false
            )
        )

        // 4. Stored auto offset exists
        assertFalse(
            AutoSyncLogic.shouldStartCapture(
                enabled = true,
                hasAudioPermission = true,
                manualDelayMs = 0,
                storedAutoOffsetMs = 200,
                title = "Bohemian Rhapsody",
                hasLyrics = true,
                isPlaying = true,
                isPreview = false
            )
        )

        // 5. Title null or blank
        assertFalse(
            AutoSyncLogic.shouldStartCapture(
                enabled = true,
                hasAudioPermission = true,
                manualDelayMs = 0,
                storedAutoOffsetMs = null,
                title = null,
                hasLyrics = true,
                isPlaying = true,
                isPreview = false
            )
        )
        assertFalse(
            AutoSyncLogic.shouldStartCapture(
                enabled = true,
                hasAudioPermission = true,
                manualDelayMs = 0,
                storedAutoOffsetMs = null,
                title = "   ",
                hasLyrics = true,
                isPlaying = true,
                isPreview = false
            )
        )

        // 6. Lyrics missing
        assertFalse(
            AutoSyncLogic.shouldStartCapture(
                enabled = true,
                hasAudioPermission = true,
                manualDelayMs = 0,
                storedAutoOffsetMs = null,
                title = "Bohemian Rhapsody",
                hasLyrics = false,
                isPlaying = true,
                isPreview = false
            )
        )

        // 7. Not playing
        assertFalse(
            AutoSyncLogic.shouldStartCapture(
                enabled = true,
                hasAudioPermission = true,
                manualDelayMs = 0,
                storedAutoOffsetMs = null,
                title = "Bohemian Rhapsody",
                hasLyrics = true,
                isPlaying = false,
                isPreview = false
            )
        )

        // 8. Preview engine
        assertFalse(
            AutoSyncLogic.shouldStartCapture(
                enabled = true,
                hasAudioPermission = true,
                manualDelayMs = 0,
                storedAutoOffsetMs = null,
                title = "Bohemian Rhapsody",
                hasLyrics = true,
                isPlaying = true,
                isPreview = true
            )
        )
    }

    @Test
    fun playbackSnapshotExtrapolatesCorrectly() {
        val paused = PlaybackSnapshot(
            positionMs = 5000L,
            updateTimeRealtimeMs = 10000L,
            speed = 1.0f,
            isPlaying = false
        )
        assertEquals(5000L, paused.currentPosition(nowRealtimeMs = 12000L))

        val playing = PlaybackSnapshot(
            positionMs = 5000L,
            updateTimeRealtimeMs = 10000L,
            speed = 1.0f,
            isPlaying = true
        )
        assertEquals(7000L, playing.currentPosition(nowRealtimeMs = 12000L))

        val doubleSpeed = PlaybackSnapshot(
            positionMs = 5000L,
            updateTimeRealtimeMs = 10000L,
            speed = 2.0f,
            isPlaying = true
        )
        assertEquals(9000L, doubleSpeed.currentPosition(nowRealtimeMs = 12000L))

        // Negative elapsed time protected
        assertEquals(5000L, playing.currentPosition(nowRealtimeMs = 9000L))
    }

    @Test
    fun isCaptureBlockedBlocksOnlyMatchingSong() {
        // Match exact title and artist
        assertTrue(
            AutoSyncLogic.isCaptureBlocked(
                currentTitle = "Song A",
                currentArtist = "Artist A",
                failedTitle = "Song A",
                failedArtist = "Artist A"
            )
        )

        // Match when both artists are null
        assertTrue(
            AutoSyncLogic.isCaptureBlocked(
                currentTitle = "Song A",
                currentArtist = null,
                failedTitle = "Song A",
                failedArtist = null
            )
        )

        // Match when one artist is blank and the other is null
        assertTrue(
            AutoSyncLogic.isCaptureBlocked(
                currentTitle = "Song A",
                currentArtist = "",
                failedTitle = "Song A",
                failedArtist = null
            )
        )

        // No block when no failed song was recorded
        assertFalse(
            AutoSyncLogic.isCaptureBlocked(
                currentTitle = "Song A",
                currentArtist = "Artist A",
                failedTitle = null,
                failedArtist = null
            )
        )

        // No block when current song is null or blank
        assertFalse(
            AutoSyncLogic.isCaptureBlocked(
                currentTitle = null,
                currentArtist = "Artist A",
                failedTitle = "Song A",
                failedArtist = "Artist A"
            )
        )
        assertFalse(
            AutoSyncLogic.isCaptureBlocked(
                currentTitle = "   ",
                currentArtist = "Artist A",
                failedTitle = "Song A",
                failedArtist = "Artist A"
            )
        )

        // No block when title is different
        assertFalse(
            AutoSyncLogic.isCaptureBlocked(
                currentTitle = "Song B",
                currentArtist = "Artist A",
                failedTitle = "Song A",
                failedArtist = "Artist A"
            )
        )

        // No block when artist is different
        assertFalse(
            AutoSyncLogic.isCaptureBlocked(
                currentTitle = "Song A",
                currentArtist = "Artist B",
                failedTitle = "Song A",
                failedArtist = "Artist A"
            )
        )
    }

    @Test
    fun shouldStartCaptureReturnsFalseWhenCaptureBlockedForSong() {
        assertFalse(
            AutoSyncLogic.shouldStartCapture(
                enabled = true,
                hasAudioPermission = true,
                manualDelayMs = 0,
                storedAutoOffsetMs = null,
                title = "Bohemian Rhapsody",
                hasLyrics = true,
                isPlaying = true,
                isPreview = false,
                failedTitle = "Bohemian Rhapsody",
                failedArtist = "Queen",
                artist = "Queen"
            )
        )

        assertTrue(
            AutoSyncLogic.shouldStartCapture(
                enabled = true,
                hasAudioPermission = true,
                manualDelayMs = 0,
                storedAutoOffsetMs = null,
                title = "Bohemian Rhapsody",
                hasLyrics = true,
                isPlaying = true,
                isPreview = false,
                failedTitle = "Another One Bites the Dust",
                failedArtist = "Queen",
                artist = "Queen"
            )
        )
    }
}

