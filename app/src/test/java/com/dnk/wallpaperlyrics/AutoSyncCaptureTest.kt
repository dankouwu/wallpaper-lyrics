package com.dnk.wallpaperlyrics

import org.junit.Assert.assertFalse
import org.junit.Test

class AutoSyncCaptureTest {

    @Test
    fun processCaptureFlagDefaultsToFalse() {
        assertFalse(AutoSyncCapture.isProcessCaptureRunning())
    }
}
