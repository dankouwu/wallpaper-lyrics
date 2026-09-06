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
}
