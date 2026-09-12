package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewModeTest {

    @Test
    fun `forced idle look takes precedence over song and access flags`() {
        assertEquals(
            PreviewMode.IDLE_FORCED,
            decidePreviewMode(
                hasNotificationAccess = true,
                hasSong = true,
                hasCover = true,
                idleForced = true
            )
        )
        assertEquals(
            PreviewMode.IDLE_FORCED,
            decidePreviewMode(
                hasNotificationAccess = false,
                hasSong = false,
                hasCover = false,
                idleForced = true
            )
        )
        assertEquals(
            PreviewMode.IDLE_FORCED,
            decidePreviewMode(
                hasNotificationAccess = false,
                hasSong = true,
                hasCover = true,
                idleForced = true
            )
        )
    }

    @Test
    fun `missing notification access returns access needed hint when idle not forced`() {
        assertEquals(
            PreviewMode.IDLE_ACCESS_NEEDED,
            decidePreviewMode(
                hasNotificationAccess = false,
                hasSong = true,
                hasCover = true,
                idleForced = false
            )
        )
        assertEquals(
            PreviewMode.IDLE_ACCESS_NEEDED,
            decidePreviewMode(
                hasNotificationAccess = false,
                hasSong = false,
                hasCover = false,
                idleForced = false
            )
        )
    }

    @Test
    fun `song with cover and access returns song mode`() {
        assertEquals(
            PreviewMode.SONG,
            decidePreviewMode(
                hasNotificationAccess = true,
                hasSong = true,
                hasCover = true,
                idleForced = false
            )
        )
    }

    @Test
    fun `song without cover falls back to play hint`() {
        assertEquals(
            PreviewMode.IDLE_PLAY_HINT,
            decidePreviewMode(
                hasNotificationAccess = true,
                hasSong = true,
                hasCover = false,
                idleForced = false
            )
        )
    }

    @Test
    fun `no song playing returns play hint`() {
        assertEquals(
            PreviewMode.IDLE_PLAY_HINT,
            decidePreviewMode(
                hasNotificationAccess = true,
                hasSong = false,
                hasCover = false,
                idleForced = false
            )
        )
        assertEquals(
            PreviewMode.IDLE_PLAY_HINT,
            decidePreviewMode(
                hasNotificationAccess = true,
                hasSong = false,
                hasCover = true,
                idleForced = false
            )
        )
    }

    @Test
    fun `same metadata gives equal keys and matches key comparison`() {
        val keyA = buildSongPreviewKey("Title", "Artist", "Album", "content://art/1")
        val keyB = buildSongPreviewKey("Title", "Artist", "Album", "content://art/1")
        assertEquals(keyA, keyB)
        org.junit.Assert.assertTrue(isSameSongPreview(keyA, keyB))
    }

    @Test
    fun `changed title gives different keys`() {
        val baseKey = buildSongPreviewKey("Title", "Artist", "Album", "content://art/1")
        val changedKey = buildSongPreviewKey("New Title", "Artist", "Album", "content://art/1")
        org.junit.Assert.assertNotEquals(baseKey, changedKey)
        org.junit.Assert.assertFalse(isSameSongPreview(baseKey, changedKey))
    }

    @Test
    fun `changed art URI gives different keys`() {
        val baseKey = buildSongPreviewKey("Title", "Artist", "Album", "content://art/1")
        val changedKey = buildSongPreviewKey("Title", "Artist", "Album", "content://art/2")
        org.junit.Assert.assertNotEquals(baseKey, changedKey)
        org.junit.Assert.assertFalse(isSameSongPreview(baseKey, changedKey))
    }

    @Test
    fun `changed artist or album gives different keys`() {
        val baseKey = buildSongPreviewKey("Title", "Artist", "Album", "content://art/1")
        val changedArtist = buildSongPreviewKey("Title", "New Artist", "Album", "content://art/1")
        val changedAlbum = buildSongPreviewKey("Title", "Artist", "New Album", "content://art/1")
        org.junit.Assert.assertNotEquals(baseKey, changedArtist)
        org.junit.Assert.assertNotEquals(baseKey, changedAlbum)
        org.junit.Assert.assertFalse(isSameSongPreview(baseKey, changedArtist))
        org.junit.Assert.assertFalse(isSameSongPreview(baseKey, changedAlbum))
    }

    @Test
    fun `null fields are handled without error`() {
        val nullKeyA = buildSongPreviewKey(null, null, null, null)
        val nullKeyB = buildSongPreviewKey(null, null, null, null)
        assertEquals(nullKeyA, nullKeyB)

        val populatedKey = buildSongPreviewKey("Title", "Artist", "Album", "content://art/1")
        org.junit.Assert.assertNotEquals(nullKeyA, populatedKey)

        org.junit.Assert.assertFalse(isSameSongPreview(null, populatedKey))
        org.junit.Assert.assertFalse(isSameSongPreview(populatedKey, nullKeyA))
        org.junit.Assert.assertFalse(isSameSongPreview(populatedKey, null))
        org.junit.Assert.assertFalse(isSameSongPreview(null, null))
    }
}
