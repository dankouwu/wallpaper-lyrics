package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @org.junit.After
    fun tearDown() {
        Tuning.paletteVersionOverride = ""
    }

    @Test
    fun `default idle palette matches VersionPalette for build version name`() {
        val expected = VersionPalette.forVersion(BuildConfig.VERSION_NAME)
        assertEquals(expected[0], IdleScreenSettings.DEFAULT_ACCENT)
        assertEquals(expected[1], IdleScreenSettings.DEFAULT_BASE)
        assertEquals(expected[2], IdleScreenSettings.DEFAULT_MID)
        assertEquals(expected[3], IdleScreenSettings.DEFAULT_HIGHLIGHT)
    }

    @Test
    fun `effectivePaletteVersion returns trimmed override in debug when non-blank`() {
        val result = IdleScreenSettings.effectivePaletteVersion("1.0.0", " 2.3.0 ", isDebug = true)
        assertEquals("2.3.0", result)
    }

    @Test
    fun `effectivePaletteVersion returns real version in debug when override is blank or whitespace`() {
        assertEquals("1.0.0", IdleScreenSettings.effectivePaletteVersion("1.0.0", "", isDebug = true))
        assertEquals("1.0.0", IdleScreenSettings.effectivePaletteVersion("1.0.0", "   ", isDebug = true))
    }

    @Test
    fun `effectivePaletteVersion returns real version in release even with override`() {
        val result = IdleScreenSettings.effectivePaletteVersion("1.0.0", "2.3.0", isDebug = false)
        assertEquals("1.0.0", result)
    }

    @Test
    fun `default idle palette follows Tuning paletteVersionOverride when set and resets when cleared`() {
        Tuning.paletteVersionOverride = "2.3.0"
        val expectedOverride = VersionPalette.forVersion("2.3.0")
        assertEquals(expectedOverride[0], IdleScreenSettings.DEFAULT_ACCENT)
        assertEquals(expectedOverride[1], IdleScreenSettings.DEFAULT_BASE)
        assertEquals(expectedOverride[2], IdleScreenSettings.DEFAULT_MID)
        assertEquals(expectedOverride[3], IdleScreenSettings.DEFAULT_HIGHLIGHT)

        Tuning.paletteVersionOverride = ""
        val expectedDefault = VersionPalette.forVersion(BuildConfig.VERSION_NAME)
        assertEquals(expectedDefault[0], IdleScreenSettings.DEFAULT_ACCENT)
        assertEquals(expectedDefault[1], IdleScreenSettings.DEFAULT_BASE)
        assertEquals(expectedDefault[2], IdleScreenSettings.DEFAULT_MID)
        assertEquals(expectedDefault[3], IdleScreenSettings.DEFAULT_HIGHLIGHT)
    }

    @Test
    fun `Tuning resetAll clears paletteVersionOverride`() {
        Tuning.paletteVersionOverride = "2.4.0"
        Tuning.resetAll()
        assertEquals("", Tuning.paletteVersionOverride)
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

    @Test
    fun `idleTitle with missing notification access returns setup title`() {
        assertEquals(IdleScreenSettings.SETUP_TITLE, IdleScreenSettings.idleTitle(false, "Anything"))
    }

    @Test
    fun `idleTitle with notification access returns custom text`() {
        assertEquals("My Own Text", IdleScreenSettings.idleTitle(true, "My Own Text"))
    }

    @Test
    fun `idleTitle with notification access and empty string stays empty`() {
        assertEquals("", IdleScreenSettings.idleTitle(true, ""))
    }

    @Test
    fun `idleTitle with notification access and whitespace stays blank`() {
        assertEquals("   ", IdleScreenSettings.idleTitle(true, "   "))
    }

    @Test
    fun `idleSubtitle with missing notification access returns setup subtitle`() {
        assertEquals(IdleScreenSettings.SETUP_SUBTITLE, IdleScreenSettings.idleSubtitle(false))
    }

    @Test
    fun `idleSubtitle with notification access returns empty string`() {
        assertEquals("", IdleScreenSettings.idleSubtitle(true))
    }

    @Test
    fun `setup title is different from default idle title`() {
        assertNotEquals(IdleScreenSettings.DEFAULT_IDLE_TITLE, IdleScreenSettings.SETUP_TITLE)
    }
}

