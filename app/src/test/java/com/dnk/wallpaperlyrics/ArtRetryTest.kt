package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtRetryTest {

    @Test
    fun `delayAfterFailure returns 2000ms after first failure`() {
        assertEquals(2000L, ArtRetry.delayAfterFailure(1))
    }

    @Test
    fun `delayAfterFailure returns 5000ms after second failure`() {
        assertEquals(5000L, ArtRetry.delayAfterFailure(2))
    }

    @Test
    fun `delayAfterFailure returns null after third failure to stop retries`() {
        assertNull(ArtRetry.delayAfterFailure(3))
    }

    @Test
    fun `delayAfterFailure returns null for zero failed attempts`() {
        assertNull(ArtRetry.delayAfterFailure(0))
    }

    @Test
    fun `delayAfterFailure returns null for large numbers of failures`() {
        assertNull(ArtRetry.delayAfterFailure(4))
        assertNull(ArtRetry.delayAfterFailure(10))
        assertNull(ArtRetry.delayAfterFailure(100))
        assertNull(ArtRetry.delayAfterFailure(Int.MAX_VALUE))
    }

    @Test
    fun `delayAfterFailure returns null for negative failed attempts`() {
        assertNull(ArtRetry.delayAfterFailure(-1))
        assertNull(ArtRetry.delayAfterFailure(-100))
    }
}
