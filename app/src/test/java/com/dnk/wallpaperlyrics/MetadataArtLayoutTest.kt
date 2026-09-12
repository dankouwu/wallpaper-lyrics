package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataArtLayoutTest {

    @Test
    fun `allowsNativeAspect returns true for YouTube packages including mixed case`() {
        assertTrue(MetadataArtLayout.allowsNativeAspect("com.google.android.youtube"))
        assertTrue(MetadataArtLayout.allowsNativeAspect("com.google.android.apps.youtube.music"))
        assertTrue(MetadataArtLayout.allowsNativeAspect("com.google.android.YouTube"))
    }

    @Test
    fun `allowsNativeAspect returns false for non YouTube packages null and blank`() {
        assertFalse(MetadataArtLayout.allowsNativeAspect("com.spotify.music"))
        assertFalse(MetadataArtLayout.allowsNativeAspect("com.aspiro.tidal"))
        assertFalse(MetadataArtLayout.allowsNativeAspect("org.kde.kdeconnect_tp"))
        assertFalse(MetadataArtLayout.allowsNativeAspect(null))
        assertFalse(MetadataArtLayout.allowsNativeAspect(""))
        assertFalse(MetadataArtLayout.allowsNativeAspect("   "))
    }

    @Test
    fun `aspectFor returns 1_0f when native aspect is not allowed`() {
        assertEquals(1.0f, MetadataArtLayout.aspectFor(false, 1280, 720), 0.0001f)
    }

    @Test
    fun `aspectFor returns width divided by height when native aspect is allowed`() {
        assertEquals(1280f / 720f, MetadataArtLayout.aspectFor(true, 1280, 720), 0.0001f)
        assertEquals(1.0f, MetadataArtLayout.aspectFor(true, 600, 600), 0.0001f)
    }

    @Test
    fun `aspectFor returns 1_0f when dimensions are zero or negative`() {
        assertEquals(1.0f, MetadataArtLayout.aspectFor(true, 0, 720), 0.0001f)
        assertEquals(1.0f, MetadataArtLayout.aspectFor(true, 1280, 0), 0.0001f)
        assertEquals(1.0f, MetadataArtLayout.aspectFor(true, -10, 720), 0.0001f)
        assertEquals(1.0f, MetadataArtLayout.aspectFor(true, 1280, -10), 0.0001f)
    }

    @Test
    fun `fitted dimensions at aspect 1_0f on 1080 by 2400 screen equal 756f`() {
        assertEquals(756f, MetadataArtLayout.fittedWidth(1080f, 2400f, 1.0f), 0.001f)
        assertEquals(756f, MetadataArtLayout.fittedHeight(1080f, 2400f, 1.0f), 0.001f)
    }

    @Test
    fun `fitted dimensions for 16 by 9 aspect on 1080 by 2400 screen keep width and scale height`() {
        val aspect = 16f / 9f
        assertEquals(756f, MetadataArtLayout.fittedWidth(1080f, 2400f, aspect), 0.001f)
        assertEquals(756f / (16f / 9f), MetadataArtLayout.fittedHeight(1080f, 2400f, aspect), 0.001f)
    }

    @Test
    fun `fitted dimensions for 9 by 16 aspect cap at vertical budget and keep horizontal padding`() {
        val aspect = 9f / 16f
        val expectedHeight = 2400f * 0.55f
        val expectedWidth = expectedHeight * aspect
        assertEquals(expectedHeight, MetadataArtLayout.fittedHeight(1080f, 2400f, aspect), 0.001f)
        assertEquals(expectedWidth, MetadataArtLayout.fittedWidth(1080f, 2400f, aspect), 0.001f)
        assertTrue(MetadataArtLayout.fittedWidth(1080f, 2400f, aspect) < 1080f * 0.70f)
    }

    private fun rows(height: Int, topBar: Int, bottomBar: Int) =
        BooleanArray(height) { it < topBar || it >= height - bottomBar }

    @Test
    fun `isLetterboxDark accepts black and near black and rejects real artwork`() {
        assertTrue(MetadataArtLayout.isLetterboxDark(0xFF000000.toInt()))
        assertTrue(MetadataArtLayout.isLetterboxDark(0xFF0A0A0A.toInt()))
        assertFalse(MetadataArtLayout.isLetterboxDark(0xFF303030.toInt()))
        assertFalse(MetadataArtLayout.isLetterboxDark(0xFFFFFFFF.toInt()))
    }

    @Test
    fun `contentRows trims the bars off a 16 by 9 picture in a square frame`() {
        assertEquals(60..422, MetadataArtLayout.contentRows(rows(484, 60, 61)))
    }

    @Test
    fun `contentRows keeps everything when there are no bars`() {
        assertEquals(0..359, MetadataArtLayout.contentRows(rows(360, 0, 0)))
    }

    @Test
    fun `contentRows keeps a dark band that does not touch an edge`() {
        val withBand = BooleanArray(400) { it in 150..250 }
        assertEquals(0..399, MetadataArtLayout.contentRows(withBand))
    }

    @Test
    fun `contentRows refuses to trim more than the cap off an edge`() {
        assertEquals(0..399, MetadataArtLayout.contentRows(rows(400, 200, 0)))
    }

    @Test
    fun `contentRows keeps an all dark cover intact`() {
        assertEquals(0..99, MetadataArtLayout.contentRows(BooleanArray(100) { true }))
    }

    @Test
    fun `contentRows on an empty array is empty`() {
        assertTrue(MetadataArtLayout.contentRows(BooleanArray(0)).isEmpty())
    }
}
