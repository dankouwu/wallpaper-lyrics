package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSliderTouchTest {

    @Test
    fun testWithinTouchSlopIsUndecided() {
        val touchSlop = 16f
        assertEquals(
            LyricsSettings.SettingsSlider.DragDecision.UNDECIDED,
            LyricsSettings.SettingsSlider.decideDrag(0f, 0f, touchSlop)
        )
        assertEquals(
            LyricsSettings.SettingsSlider.DragDecision.UNDECIDED,
            LyricsSettings.SettingsSlider.decideDrag(10f, 5f, touchSlop)
        )
        assertEquals(
            LyricsSettings.SettingsSlider.DragDecision.UNDECIDED,
            LyricsSettings.SettingsSlider.decideDrag(-10f, -12f, touchSlop)
        )
        assertFalse(LyricsSettings.SettingsSlider.isPredominantlyHorizontal(10f, 5f, touchSlop))
    }

    @Test
    fun testPredominantlyVerticalDrag() {
        val touchSlop = 16f
        assertEquals(
            LyricsSettings.SettingsSlider.DragDecision.VERTICAL,
            LyricsSettings.SettingsSlider.decideDrag(4f, 30f, touchSlop)
        )
        assertFalse(LyricsSettings.SettingsSlider.isPredominantlyHorizontal(4f, 30f, touchSlop))

        assertEquals(
            LyricsSettings.SettingsSlider.DragDecision.VERTICAL,
            LyricsSettings.SettingsSlider.decideDrag(-5f, -40f, touchSlop)
        )
        assertFalse(LyricsSettings.SettingsSlider.isPredominantlyHorizontal(-5f, -40f, touchSlop))

        assertEquals(
            LyricsSettings.SettingsSlider.DragDecision.VERTICAL,
            LyricsSettings.SettingsSlider.decideDrag(25f, 25f, touchSlop)
        )
        assertFalse(LyricsSettings.SettingsSlider.isPredominantlyHorizontal(25f, 25f, touchSlop))
    }

    @Test
    fun testPredominantlyHorizontalDrag() {
        val touchSlop = 16f
        assertEquals(
            LyricsSettings.SettingsSlider.DragDecision.HORIZONTAL,
            LyricsSettings.SettingsSlider.decideDrag(30f, 4f, touchSlop)
        )
        assertTrue(LyricsSettings.SettingsSlider.isPredominantlyHorizontal(30f, 4f, touchSlop))

        assertEquals(
            LyricsSettings.SettingsSlider.DragDecision.HORIZONTAL,
            LyricsSettings.SettingsSlider.decideDrag(-35f, -6f, touchSlop)
        )
        assertTrue(LyricsSettings.SettingsSlider.isPredominantlyHorizontal(-35f, -6f, touchSlop))

        assertEquals(
            LyricsSettings.SettingsSlider.DragDecision.HORIZONTAL,
            LyricsSettings.SettingsSlider.decideDrag(80f, 15f, touchSlop)
        )
        assertTrue(LyricsSettings.SettingsSlider.isPredominantlyHorizontal(80f, 15f, touchSlop))
    }
}
