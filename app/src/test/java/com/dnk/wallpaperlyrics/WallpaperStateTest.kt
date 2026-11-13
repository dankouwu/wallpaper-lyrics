package com.dnk.wallpaperlyrics

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperStateTest {

    private val home = Any()
    private val lock = Any()
    private val colors = listOf(1, 2, 3, 4)
    private val track = WallpaperState.Snapshot("com.spotify.music", "Song", "Artist", colors)
    private val idle = WallpaperState.Snapshot("com.spotify.music", null, null, listOf(5, 6, 7, 8))

    @After
    fun reset() {
        WallpaperState.clear(home)
        WallpaperState.clear(lock)
    }

    @Test
    fun `publishing tells readers only when what is shown changes`() {
        assertTrue(WallpaperState.publish(home, track))
        assertFalse(WallpaperState.publish(home, track.copy(colors = listOf(1, 2, 3, 4))))
        assertTrue(WallpaperState.publish(home, idle))
        assertEquals(idle, WallpaperState.current)
    }

    @Test
    fun `an engine going away clears the state only if it set it last`() {
        WallpaperState.publish(home, track)
        WallpaperState.publish(lock, track)
        assertFalse(WallpaperState.clear(home))
        assertEquals(track, WallpaperState.current)
        assertTrue(WallpaperState.clear(lock))
        assertNull(WallpaperState.current)
    }

    @Test
    fun `idle rows carry the colors but no track`() {
        assertArrayEquals(
            arrayOf<Any?>("idle", null, null, null, 5, 6, 7, 8),
            WallpaperStateProvider.row(idle)
        )
        assertArrayEquals(
            arrayOf<Any?>("track", "com.spotify.music", "Song", "Artist", 1, 2, 3, 4),
            WallpaperStateProvider.row(track)
        )
    }
}
