package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdleScreenSettingsTest {

    @Test
    fun `resolveIdleTitle with null returns default title`() {
        assertEquals("No Music Playing", IdleScreenSettings.resolveIdleTitle(null))
    }

    @Test
    fun `resolveIdleTitle with empty string preserves empty string`() {
        assertEquals("", IdleScreenSettings.resolveIdleTitle(""))
    }

    @Test
    fun `resolveIdleTitle with whitespace preserves whitespace unchanged`() {
        assertEquals("   ", IdleScreenSettings.resolveIdleTitle("   "))
    }

    @Test
    fun `resolveIdleTitle with custom text returns custom text`() {
        assertEquals("Silence", IdleScreenSettings.resolveIdleTitle("Silence"))
    }

    @Test
    fun `parseHexColor accepts standard six digit, eight digit, and whitespace padded inputs`() {
        val expected = 0xFFFF0055.toInt()
        assertEquals(expected, IdleScreenSettings.parseHexColor("#FF0055"))
        assertEquals(expected, IdleScreenSettings.parseHexColor("ff0055"))
        assertEquals(expected, IdleScreenSettings.parseHexColor("#FFFF0055"))
        assertEquals(expected, IdleScreenSettings.parseHexColor("   #FF0055   "))
    }

    @Test
    fun `parseHexColor forces alpha to 0xFF for eight digit input with zero alpha`() {
        val expected = 0xFFFF0055.toInt()
        assertEquals(expected, IdleScreenSettings.parseHexColor("#00FF0055"))
        assertEquals(expected, IdleScreenSettings.parseHexColor("00FF0055"))
    }

    @Test
    fun `parseHexColor returns null for invalid inputs`() {
        assertNull(IdleScreenSettings.parseHexColor(""))
        assertNull(IdleScreenSettings.parseHexColor("#12345"))
        assertNull(IdleScreenSettings.parseHexColor("#GGGGGG"))
        assertNull(IdleScreenSettings.parseHexColor("not a color"))
        assertNull(IdleScreenSettings.parseHexColor("#"))
    }

    @Test
    fun `formatHexColor returns uppercase six digit hex dropping alpha`() {
        assertEquals("#FF0055", IdleScreenSettings.formatHexColor(0xFFFF0055.toInt()))
        assertEquals("#0A0B1A", IdleScreenSettings.formatHexColor(0x000A0B1A))
        assertEquals("#7A22FF", IdleScreenSettings.formatHexColor(0x7F7A22FF))
    }

    @Test
    fun `formatHexColor and parseHexColor round trip for each default color`() {
        val defaults = listOf(
            IdleScreenSettings.DEFAULT_ACCENT,
            IdleScreenSettings.DEFAULT_BASE,
            IdleScreenSettings.DEFAULT_MID,
            IdleScreenSettings.DEFAULT_HIGHLIGHT
        )
        for (color in defaults) {
            val hex = IdleScreenSettings.formatHexColor(color)
            val parsed = IdleScreenSettings.parseHexColor(hex)
            assertEquals(color, parsed)
            assertEquals(hex, IdleScreenSettings.formatHexColor(parsed!!))
        }
    }

    @Test
    fun `default color constants equal literals formerly hardcoded in wallpaper service`() {
        assertEquals(0xFFFF0055.toInt(), IdleScreenSettings.DEFAULT_ACCENT)
        assertEquals(0xFF0A0B1A.toInt(), IdleScreenSettings.DEFAULT_BASE)
        assertEquals(0xFF7A22FF.toInt(), IdleScreenSettings.DEFAULT_MID)
        assertEquals(0xFFD6C7FF.toInt(), IdleScreenSettings.DEFAULT_HIGHLIGHT)
    }

    @Test
    fun parseHexColorRejectsSignedInput() {
        assertNull(IdleScreenSettings.parseHexColor("-12345"))
        assertNull(IdleScreenSettings.parseHexColor("+12345"))
        assertNull(IdleScreenSettings.parseHexColor("#-12345"))
        assertNull(IdleScreenSettings.parseHexColor("-1234567"))
    }

    @Test
    fun `parseSavedColors with null returns four seeded defaults`() {
        val expected = listOf(
            IdleScreenSettings.DEFAULT_ACCENT,
            IdleScreenSettings.DEFAULT_BASE,
            IdleScreenSettings.DEFAULT_MID,
            IdleScreenSettings.DEFAULT_HIGHLIGHT
        )
        assertEquals(expected, IdleScreenSettings.parseSavedColors(null))
    }

    @Test
    fun `parseSavedColors with empty string returns empty list`() {
        assertEquals(emptyList<Int>(), IdleScreenSettings.parseSavedColors(""))
    }

    @Test
    fun `parseSavedColors skips malformed entries and keeps valid ones`() {
        val input = "#FF0055,invalid,#0A0B1A,12345,#7A22FF"
        val expected = listOf(
            0xFFFF0055.toInt(),
            0xFF0A0B1A.toInt(),
            0xFF7A22FF.toInt()
        )
        assertEquals(expected, IdleScreenSettings.parseSavedColors(input))
    }

    @Test
    fun `parseSavedColors drops duplicates and caps at MAX_SAVED_COLORS`() {
        val hexes = (0 until 15).map { String.format("#%06X", it * 0x101010) }
        val inputWithDuplicates = (listOf(hexes[0]) + hexes).joinToString(",")
        val result = IdleScreenSettings.parseSavedColors(inputWithDuplicates)
        assertEquals(IdleScreenSettings.MAX_SAVED_COLORS, result.size)
        assertEquals(hexes.take(IdleScreenSettings.MAX_SAVED_COLORS).map { IdleScreenSettings.parseHexColor(it) }, result)
    }

    @Test
    fun `formatSavedColors round trips through parseSavedColors`() {
        val colors = listOf(
            0xFFFF0055.toInt(),
            0xFF0A0B1A.toInt(),
            0xFF7A22FF.toInt(),
            0xFFD6C7FF.toInt()
        )
        val formatted = IdleScreenSettings.formatSavedColors(colors)
        val roundTripped = IdleScreenSettings.parseSavedColors(formatted)
        assertEquals(colors, roundTripped)
    }

    @Test
    fun `addSavedColor prepends a new colour`() {
        val existing = listOf(0xFF0A0B1A.toInt(), 0xFF7A22FF.toInt())
        val newColor = 0xFFFF0055.toInt()
        val result = IdleScreenSettings.addSavedColor(existing, newColor)
        assertEquals(listOf(newColor, 0xFF0A0B1A.toInt(), 0xFF7A22FF.toInt()), result)
    }

    @Test
    fun `addSavedColor moves existing colour to front without duplicating leaving size unchanged`() {
        val color1 = 0xFFFF0055.toInt()
        val color2 = 0xFF0A0B1A.toInt()
        val color3 = 0xFF7A22FF.toInt()
        val existing = listOf(color1, color2, color3)
        val result = IdleScreenSettings.addSavedColor(existing, color2)
        assertEquals(listOf(color2, color1, color3), result)
        assertEquals(3, result.size)
    }

    @Test
    fun `addSavedColor caps at MAX_SAVED_COLORS dropping oldest`() {
        val existing = (1..IdleScreenSettings.MAX_SAVED_COLORS).map { (it * 0x10000) or 0xFF000000.toInt() }
        val newColor = 0xFFFF0055.toInt()
        val result = IdleScreenSettings.addSavedColor(existing, newColor)
        assertEquals(IdleScreenSettings.MAX_SAVED_COLORS, result.size)
        assertEquals(newColor, result.first())
        assertEquals(existing.take(IdleScreenSettings.MAX_SAVED_COLORS - 1), result.drop(1))
    }

    @Test
    fun `componentFromTouch clamps below 0 and above 1 and returns midpoint for centre touch`() {
        val size = 200f
        assertEquals(0f, IdleScreenSettings.componentFromTouch(-50f, size), 0.0001f)
        assertEquals(1f, IdleScreenSettings.componentFromTouch(250f, size), 0.0001f)
        assertEquals(0.5f, IdleScreenSettings.componentFromTouch(100f, size), 0.0001f)
        assertEquals(0.25f, IdleScreenSettings.componentFromTouch(50f, size), 0.0001f)
    }

    @Test
    fun `componentFromTouch returns 0f for zero or negative size`() {
        assertEquals(0f, IdleScreenSettings.componentFromTouch(50f, 0f), 0.0001f)
        assertEquals(0f, IdleScreenSettings.componentFromTouch(50f, -100f), 0.0001f)
        assertEquals(0f, IdleScreenSettings.componentFromTouch(0f, 0f), 0.0001f)
    }
}

