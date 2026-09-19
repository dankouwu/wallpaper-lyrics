package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class AodModeTest {

    @Test
    fun `each of the three stored strings maps to its mode`() {
        assertEquals(AodMode.METADATA_ONLY, AodMode.fromPref("metadata"))
        assertEquals(AodMode.OFF, AodMode.fromPref("off"))
        assertEquals(AodMode.METADATA_AND_BACKGROUND, AodMode.fromPref("metadata_background"))
    }

    @Test
    fun `null maps to METADATA_AND_BACKGROUND`() {
        assertEquals(AodMode.METADATA_AND_BACKGROUND, AodMode.fromPref(null))
    }

    @Test
    fun `unrecognised string maps to METADATA_AND_BACKGROUND`() {
        assertEquals(AodMode.METADATA_AND_BACKGROUND, AodMode.fromPref("unrecognised"))
        assertEquals(AodMode.METADATA_AND_BACKGROUND, AodMode.fromPref("unknown"))
        assertEquals(AodMode.METADATA_AND_BACKGROUND, AodMode.fromPref(""))
    }

    @Test
    fun `toPref then fromPref round trips for all three modes`() {
        assertEquals(AodMode.METADATA_ONLY, AodMode.fromPref(AodMode.toPref(AodMode.METADATA_ONLY)))
        assertEquals(AodMode.OFF, AodMode.fromPref(AodMode.toPref(AodMode.OFF)))
        assertEquals(AodMode.METADATA_AND_BACKGROUND, AodMode.fromPref(AodMode.toPref(AodMode.METADATA_AND_BACKGROUND)))
    }
}
