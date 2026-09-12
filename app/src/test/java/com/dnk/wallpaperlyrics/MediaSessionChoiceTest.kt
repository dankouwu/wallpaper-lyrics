package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.dnk.wallpaperlyrics.MediaSessionChoice.Candidate

class MediaSessionChoiceTest {

    @Test
    fun `emulator case telecom first bluetooth error second real music third selects music`() {
        val telecom = Candidate("com.android.server.telecom", hasUsableMetadata = true, playbackState = MediaSessionChoice.STATE_PLAYING)
        val bluetooth = Candidate("com.google.android.bluetooth", hasUsableMetadata = false, playbackState = MediaSessionChoice.STATE_ERROR)
        val spotify = Candidate("com.spotify.music", hasUsableMetadata = true, playbackState = MediaSessionChoice.STATE_PLAYING)

        val result = MediaSessionChoice.choose(listOf(telecom, bluetooth, spotify), "default")
        assertEquals(spotify, result)
    }

    @Test
    fun `single valid music session is chosen`() {
        val spotify = Candidate("com.spotify.music", hasUsableMetadata = true, playbackState = MediaSessionChoice.STATE_PLAYING)

        val result = MediaSessionChoice.choose(listOf(spotify), "default")
        assertEquals(spotify, result)
    }

    @Test
    fun `empty candidate list returns null`() {
        val result = MediaSessionChoice.choose(emptyList(), "default")
        assertNull(result)
    }

    @Test
    fun `list containing only non music junk packages returns null`() {
        val telecom = Candidate("com.android.server.telecom", hasUsableMetadata = true, playbackState = MediaSessionChoice.STATE_PLAYING)
        val bluetooth = Candidate("com.google.android.bluetooth", hasUsableMetadata = true, playbackState = MediaSessionChoice.STATE_PLAYING)

        val result = MediaSessionChoice.choose(listOf(telecom, bluetooth), "default")
        assertNull(result)
    }

    @Test
    fun `session in error state loses to valid candidate but is chosen when only candidate`() {
        val errorSession = Candidate("com.spotify.music", hasUsableMetadata = false, playbackState = MediaSessionChoice.STATE_ERROR)
        val validSession = Candidate("com.spotify.music", hasUsableMetadata = false, playbackState = 2)

        val resultAgainstValid = MediaSessionChoice.choose(listOf(errorSession, validSession), "default")
        assertEquals(validSession, resultAgainstValid)

        val resultReverse = MediaSessionChoice.choose(listOf(validSession, errorSession), "default")
        assertEquals(validSession, resultReverse)

        val resultSolo = MediaSessionChoice.choose(listOf(errorSession), "default")
        assertEquals(errorSession, resultSolo)

        val resultWithJunk = MediaSessionChoice.choose(
            listOf(
                Candidate("com.android.server.telecom", hasUsableMetadata = true, playbackState = MediaSessionChoice.STATE_PLAYING),
                errorSession
            ),
            "default"
        )
        assertEquals(errorSession, resultWithJunk)
    }

    @Test
    fun `session in state none with no metadata is chosen when only candidate`() {
        val freshlyCreated = Candidate("com.spotify.music", hasUsableMetadata = false, playbackState = MediaSessionChoice.STATE_NONE)

        val result = MediaSessionChoice.choose(listOf(freshlyCreated), "default")
        assertEquals(freshlyCreated, result)
    }

    @Test
    fun `session with metadata beats playing session without metadata`() {
        val pausedWithMeta = Candidate("com.spotify.music", hasUsableMetadata = true, playbackState = 2)
        val playingNoMeta = Candidate("com.spotify.music", hasUsableMetadata = false, playbackState = MediaSessionChoice.STATE_PLAYING)

        val result = MediaSessionChoice.choose(listOf(playingNoMeta, pausedWithMeta), "default")
        assertEquals(pausedWithMeta, result)

        val resultReverse = MediaSessionChoice.choose(listOf(pausedWithMeta, playingNoMeta), "default")
        assertEquals(pausedWithMeta, resultReverse)
    }

    @Test
    fun `telecom package is rejected even with metadata and playing state`() {
        val telecom = Candidate("com.android.server.telecom", hasUsableMetadata = true, playbackState = MediaSessionChoice.STATE_PLAYING)

        val result = MediaSessionChoice.choose(listOf(telecom), "default")
        assertNull(result)
    }

    @Test
    fun `preferred spotify selects spotify when both spotify and tidal are playing`() {
        val spotify = Candidate("com.spotify.music", hasUsableMetadata = true, playbackState = MediaSessionChoice.STATE_PLAYING)
        val tidal = Candidate("com.aspiro.tidal", hasUsableMetadata = true, playbackState = MediaSessionChoice.STATE_PLAYING)

        val result = MediaSessionChoice.choose(listOf(spotify, tidal), "spotify")
        assertEquals(spotify, result)

        val resultReverse = MediaSessionChoice.choose(listOf(tidal, spotify), "spotify")
        assertEquals(spotify, resultReverse)
    }

    @Test
    fun `preferred spotify returns null when only tidal is present`() {
        val tidal = Candidate("com.aspiro.tidal", hasUsableMetadata = true, playbackState = MediaSessionChoice.STATE_PLAYING)

        val result = MediaSessionChoice.choose(listOf(tidal), "spotify")
        assertNull(result)
    }

    @Test
    fun `playing session is preferred over paused session when both are valid music sessions`() {
        val paused = Candidate("com.spotify.music", hasUsableMetadata = true, playbackState = 2)
        val playing = Candidate("com.aspiro.tidal", hasUsableMetadata = true, playbackState = MediaSessionChoice.STATE_PLAYING)

        val result = MediaSessionChoice.choose(listOf(paused, playing), "default")
        assertEquals(playing, result)
    }

    @Test
    fun `session with blank title loses to candidate with title but is chosen when only survivor`() {
        val blankTitle = Candidate("com.spotify.music", hasUsableMetadata = false, playbackState = MediaSessionChoice.STATE_PLAYING)
        val withTitle = Candidate("com.generic.player", hasUsableMetadata = true, playbackState = MediaSessionChoice.STATE_PLAYING)

        val resultAgainstTitle = MediaSessionChoice.choose(listOf(blankTitle, withTitle), "default")
        assertEquals(withTitle, resultAgainstTitle)

        val resultReverse = MediaSessionChoice.choose(listOf(withTitle, blankTitle), "default")
        assertEquals(withTitle, resultReverse)

        val resultSolo = MediaSessionChoice.choose(listOf(blankTitle), "default")
        assertEquals(blankTitle, resultSolo)
    }

    @Test
    fun `single valid music session whose title is momentarily blank is chosen`() {
        val momentarilyBlank = Candidate("com.spotify.music", hasUsableMetadata = false, playbackState = MediaSessionChoice.STATE_PLAYING)

        val result = MediaSessionChoice.choose(listOf(momentarilyBlank), "default")
        assertEquals(momentarilyBlank, result)
    }

    @Test
    fun `package containing bluetooth is rejected even with metadata and playing state`() {
        val bluetooth = Candidate("com.google.android.bluetooth", hasUsableMetadata = true, playbackState = MediaSessionChoice.STATE_PLAYING)

        val result = MediaSessionChoice.choose(listOf(bluetooth), "default")
        assertNull(result)
    }

    @Test
    fun `substring matching works when package like com spotify lite matches spotify preference`() {
        val spotifyLite = Candidate("com.spotify.lite", hasUsableMetadata = true, playbackState = MediaSessionChoice.STATE_PLAYING)

        val result = MediaSessionChoice.choose(listOf(spotifyLite), "spotify")
        assertEquals(spotifyLite, result)
    }

    @Test
    fun `known music package is preferred over unknown music package`() {
        val unknownPlayer = Candidate("com.generic.player", hasUsableMetadata = true, playbackState = MediaSessionChoice.STATE_PLAYING)
        val spotify = Candidate("com.spotify.music", hasUsableMetadata = true, playbackState = MediaSessionChoice.STATE_PLAYING)

        val result = MediaSessionChoice.choose(listOf(unknownPlayer, spotify), "default")
        assertEquals(spotify, result)
    }

    @Test
    fun `first survivor is chosen when candidates have equal priority`() {
        val spotify = Candidate("com.spotify.music", hasUsableMetadata = true, playbackState = MediaSessionChoice.STATE_PLAYING)
        val tidal = Candidate("com.aspiro.tidal", hasUsableMetadata = true, playbackState = MediaSessionChoice.STATE_PLAYING)

        val result = MediaSessionChoice.choose(listOf(spotify, tidal), "default")
        assertEquals(spotify, result)

        val resultReverse = MediaSessionChoice.choose(listOf(tidal, spotify), "default")
        assertEquals(tidal, resultReverse)
    }

    @Test
    fun `playing candidate with metadata beats paused known music candidate with metadata that appears earlier in the list`() {
        val pausedSpotify = Candidate("com.spotify.music", hasUsableMetadata = true, playbackState = 2, isCurrent = true)
        val playingGeneric = Candidate("com.generic.player", hasUsableMetadata = true, playbackState = MediaSessionChoice.STATE_PLAYING, isCurrent = false)

        val result = MediaSessionChoice.choose(listOf(pausedSpotify, playingGeneric), "default")
        assertEquals(playingGeneric, result)
    }

    @Test
    fun `with nothing playing, current session is kept over earlier paused candidate with metadata`() {
        val pausedEarlier = Candidate("com.spotify.music", hasUsableMetadata = true, playbackState = 2, isCurrent = false)
        val pausedCurrent = Candidate("com.aspiro.tidal", hasUsableMetadata = true, playbackState = 2, isCurrent = true)

        val result = MediaSessionChoice.choose(listOf(pausedEarlier, pausedCurrent), "default")
        assertEquals(pausedCurrent, result)
    }

    @Test
    fun `with nothing playing and no current session among candidates, existing chain decides`() {
        val genericWithMeta = Candidate("com.generic.player", hasUsableMetadata = true, playbackState = 2, isCurrent = false)
        val spotifyWithMeta = Candidate("com.spotify.music", hasUsableMetadata = true, playbackState = 2, isCurrent = false)
        val spotifyNoMeta = Candidate("com.spotify.music", hasUsableMetadata = false, playbackState = 2, isCurrent = false)

        val result = MediaSessionChoice.choose(listOf(genericWithMeta, spotifyWithMeta, spotifyNoMeta), "default")
        assertEquals(spotifyWithMeta, result)
    }

    @Test
    fun `playing candidate without metadata does not beat paused current candidate with metadata`() {
        val pausedCurrent = Candidate("com.spotify.music", hasUsableMetadata = true, playbackState = 2, isCurrent = true)
        val playingNoMeta = Candidate("com.generic.player", hasUsableMetadata = false, playbackState = MediaSessionChoice.STATE_PLAYING, isCurrent = false)

        val result = MediaSessionChoice.choose(listOf(playingNoMeta, pausedCurrent), "default")
        assertEquals(pausedCurrent, result)
    }

    @Test
    fun `preference spotify never returns tidal candidate even when playing`() {
        val playingTidal = Candidate("com.aspiro.tidal", hasUsableMetadata = true, playbackState = MediaSessionChoice.STATE_PLAYING, isCurrent = false)

        val result = MediaSessionChoice.choose(listOf(playingTidal), "spotify")
        assertNull(result)
    }

    @Test
    fun `when two candidates are both playing with metadata current session listed second is chosen`() {
        val playingOther = Candidate("com.spotify.music", hasUsableMetadata = true, playbackState = MediaSessionChoice.STATE_PLAYING, isCurrent = false)
        val playingCurrent = Candidate("com.aspiro.tidal", hasUsableMetadata = true, playbackState = MediaSessionChoice.STATE_PLAYING, isCurrent = true)

        val result = MediaSessionChoice.choose(listOf(playingOther, playingCurrent), "default")
        assertEquals(playingCurrent, result)
    }
}

