package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class TrackResolutionTest {

    @Test
    fun `published track wins when title and artist are present and non-blank`() {
        var sessionProviderCalled = false
        val result = TrackResolution.resolveTrack(
            publishedTitle = "Never Gonna Give You Up",
            publishedArtist = "Rick Astley"
        ) {
            sessionProviderCalled = true
            Pair("Different Song", "Different Artist")
        }

        assertNotNull(result)
        assertEquals("Never Gonna Give You Up", result!!.title)
        assertEquals("Rick Astley", result.artist)
        assertEquals(TrackResolution.TrackSource.WALLPAPER, result.source)
        assertFalse(result.isFallback)
        assertFalse("Session provider must not be invoked when published track is valid", sessionProviderCalled)
    }

    @Test
    fun `session path is used when published track is null`() {
        val result = TrackResolution.resolveTrack(
            publishedTitle = null,
            publishedArtist = null
        ) {
            Pair("Bohemian Rhapsody", "Queen")
        }

        assertNotNull(result)
        assertEquals("Bohemian Rhapsody", result!!.title)
        assertEquals("Queen", result.artist)
        assertEquals(TrackResolution.TrackSource.MEDIA_SESSION, result.source)
        assertTrue(result.isFallback)
    }

    @Test
    fun `blank or whitespace-only published values count as absent and fall back to session`() {
        val testCases = listOf(
            Pair("", ""),
            Pair("   ", "   "),
            Pair("Valid Title", "   "),
            Pair("   ", "Valid Artist"),
            Pair(null, "Valid Artist"),
            Pair("Valid Title", null)
        )

        for ((title, artist) in testCases) {
            val result = TrackResolution.resolveTrack(
                publishedTitle = title,
                publishedArtist = artist
            ) {
                Pair("Fallback Song", "Fallback Artist")
            }

            assertNotNull("Failed for title='$title', artist='$artist'", result)
            assertEquals("Fallback Song", result!!.title)
            assertEquals("Fallback Artist", result.artist)
            assertEquals(TrackResolution.TrackSource.MEDIA_SESSION, result.source)
            assertTrue(result.isFallback)
        }
    }

    @Test
    fun `resolution returns null when both published track and session track are absent or blank`() {
        val resultNullSession = TrackResolution.resolveTrack(
            publishedTitle = null,
            publishedArtist = null
        ) {
            null
        }
        assertNull(resultNullSession)

        val resultBlankSession = TrackResolution.resolveTrack(
            publishedTitle = "   ",
            publishedArtist = ""
        ) {
            Pair("   ", "")
        }
        assertNull(resultBlankSession)
    }

    @Test
    fun `title and artist are trimmed during resolution`() {
        val resultPublished = TrackResolution.resolveTrack(
            publishedTitle = "  Song With Spaces  ",
            publishedArtist = "  Artist With Spaces  "
        ) {
            null
        }
        assertNotNull(resultPublished)
        assertEquals("Song With Spaces", resultPublished!!.title)
        assertEquals("Artist With Spaces", resultPublished.artist)

        val resultSession = TrackResolution.resolveTrack(
            publishedTitle = null,
            publishedArtist = null
        ) {
            Pair("  Session Spaces  ", "  Artist Spaces  ")
        }
        assertNotNull(resultSession)
        assertEquals("Session Spaces", resultSession!!.title)
        assertEquals("Artist Spaces", resultSession.artist)
    }

    @Test
    fun `round trip resolved title and artist produce identical cache key as wallpaper fetch path`() {
        val title = "Effortless"
        val artist = "Josh Woodward"

        val resolved = TrackResolution.resolveTrack(
            publishedTitle = title,
            publishedArtist = artist
        ) {
            null
        }
        assertNotNull(resolved)

        // Wallpaper's sha256("${title}_$artist") derivation as in LyricsManager
        val digest = MessageDigest.getInstance("SHA-256")
        val wallpaperExpectedKey = digest.digest("${title}_$artist".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val editorKey = TrackResolution.deriveLyricsCacheKey(resolved!!.title, resolved.artist)
        assertEquals(wallpaperExpectedKey, editorKey)
    }

    @Test
    fun `preview engines do not publish track`() {
        val previewAction = TrackResolution.determinePublicationAction(
            isPreview = true,
            title = "Preview Song",
            artist = "Preview Artist"
        )
        assertEquals(TrackResolution.PublicationAction.None, previewAction)

        val previewNullAction = TrackResolution.determinePublicationAction(
            isPreview = true,
            title = null,
            artist = null
        )
        assertEquals(TrackResolution.PublicationAction.None, previewNullAction)

        val previewClearAction = TrackResolution.determineClearAction(isPreview = true)
        assertEquals(TrackResolution.PublicationAction.None, previewClearAction)
    }

    @Test
    fun `non-preview engines publish and clear appropriately`() {
        val publishAction = TrackResolution.determinePublicationAction(
            isPreview = false,
            title = "Real Song",
            artist = "Real Artist"
        )
        assertEquals(TrackResolution.PublicationAction.Publish("Real Song", "Real Artist"), publishAction)

        val blankAction = TrackResolution.determinePublicationAction(
            isPreview = false,
            title = "   ",
            artist = "Real Artist"
        )
        assertEquals(TrackResolution.PublicationAction.Clear, blankAction)

        val clearAction = TrackResolution.determineClearAction(isPreview = false)
        assertEquals(TrackResolution.PublicationAction.Clear, clearAction)
    }
}
