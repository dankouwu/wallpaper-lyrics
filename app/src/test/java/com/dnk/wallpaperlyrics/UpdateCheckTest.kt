package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class UpdateCheckTest {

    @Test
    fun testIsNewerVersionSimpleUpgrade() {
        assertTrue(UpdateCheck.isNewerVersion("2.3.0", "2.2.0"))
        assertTrue(UpdateCheck.isNewerVersion("3.0.0", "2.9.9"))
        assertTrue(UpdateCheck.isNewerVersion("2.10.0", "2.9.9"))
    }

    @Test
    fun testIsNewerVersionSameOrOlder() {
        assertFalse(UpdateCheck.isNewerVersion("2.2.0", "2.2.0"))
        assertFalse(UpdateCheck.isNewerVersion("2.1.9", "2.2.0"))
        assertFalse(UpdateCheck.isNewerVersion("1.9.9", "2.0.0"))
    }

    @Test
    fun testIsNewerVersionPrefixV() {
        assertTrue(UpdateCheck.isNewerVersion("v2.3.0", "2.2.0"))
        assertTrue(UpdateCheck.isNewerVersion("V2.3.0", "2.2.0"))
        assertFalse(UpdateCheck.isNewerVersion("v2.2.0", "2.2.0"))
        assertFalse(UpdateCheck.isNewerVersion("V2.2.0", "v2.2.0"))
    }

    @Test
    fun testIsNewerVersionDropSuffixAfterDashOrPlus() {
        assertTrue(UpdateCheck.isNewerVersion("2.3.0-beta1", "2.2.0"))
        assertTrue(UpdateCheck.isNewerVersion("2.3.0+build.42", "2.2.0"))
        assertFalse(UpdateCheck.isNewerVersion("2.2.0-beta1", "2.2.0"))
        assertFalse(UpdateCheck.isNewerVersion("2.2.0+13", "2.2.0"))
    }

    @Test
    fun testIsNewerVersionPadMissingPartsWithZero() {
        assertFalse(UpdateCheck.isNewerVersion("2.2", "2.2.0"))
        assertFalse(UpdateCheck.isNewerVersion("2.2.0", "2.2"))
        assertTrue(UpdateCheck.isNewerVersion("2.2.1", "2.2"))
        assertFalse(UpdateCheck.isNewerVersion("2.1", "2.2.0"))
        assertTrue(UpdateCheck.isNewerVersion("2.2.0.1", "2.2.0"))
    }

    @Test
    fun testIsNewerVersionUnparseableInput() {
        assertFalse(UpdateCheck.isNewerVersion("invalid", "2.2.0"))
        assertFalse(UpdateCheck.isNewerVersion("2.3.0", "invalid"))
        assertFalse(UpdateCheck.isNewerVersion("", "2.2.0"))
        assertFalse(UpdateCheck.isNewerVersion("2.3.0", ""))
        assertFalse(UpdateCheck.isNewerVersion("v", "2.2.0"))
        assertFalse(UpdateCheck.isNewerVersion("...", "2.2.0"))
        assertFalse(UpdateCheck.isNewerVersion("2.a.0", "2.2.0"))
        assertFalse(UpdateCheck.isNewerVersion("2.3.0", "2.x.0"))
    }

    @Test
    fun testCalculateInitialDelayMsBeforeNoon() {
        val now = ZonedDateTime.of(2026, 9, 25, 10, 0, 0, 0, ZoneId.of("UTC"))
        val delay = UpdateCheck.calculateInitialDelayMs(now)
        val expected = 2L * 3600L * 1000L
        assertEquals(expected, delay)
    }

    @Test
    fun testCalculateInitialDelayMsExactlyNoon() {
        val now = ZonedDateTime.of(2026, 9, 25, 12, 0, 0, 0, ZoneId.of("UTC"))
        val delay = UpdateCheck.calculateInitialDelayMs(now)
        val expected = 24L * 3600L * 1000L
        assertEquals(expected, delay)
    }

    @Test
    fun testCalculateInitialDelayMsAfterNoon() {
        val now = ZonedDateTime.of(2026, 9, 25, 14, 30, 0, 0, ZoneId.of("UTC"))
        val delay = UpdateCheck.calculateInitialDelayMs(now)
        val expected = (21L * 3600L + 30L * 60L) * 1000L
        assertEquals(expected, delay)
    }

    @Test
    fun testCalculateInitialDelayMsLateNight() {
        val now = ZonedDateTime.of(2026, 9, 25, 23, 59, 0, 0, ZoneId.of("UTC"))
        val delay = UpdateCheck.calculateInitialDelayMs(now)
        val expected = (12L * 3600L + 1L * 60L) * 1000L
        assertEquals(expected, delay)
    }

    @Test
    fun testCalculateInitialDelayMsCustomZone() {
        val zone = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(2026, 9, 25, 11, 45, 0, 0, zone)
        val delay = UpdateCheck.calculateInitialDelayMs(now)
        val expected = 15L * 60L * 1000L
        assertEquals(expected, delay)
    }

    @Test
    fun testParseReleaseJsonValidFullResponse() {
        val json = """
            {
              "url": "https://api.github.com/repos/dankouwu/wallpaper-lyrics/releases/12345",
              "id": 12345,
              "tag_name": "2.2.0",
              "target_commitish": "main",
              "name": "Wallpaper Lyrics 2.2.0",
              "draft": false,
              "prerelease": false,
              "html_url": "https://github.com/dankouwu/wallpaper-lyrics/releases/tag/2.2.0",
              "body": "Release notes here"
            }
        """.trimIndent()

        val release = UpdateCheck.parseReleaseJson(json)
        assertNotNull(release)
        assertEquals("2.2.0", release?.tagName)
        assertEquals("https://github.com/dankouwu/wallpaper-lyrics/releases/tag/2.2.0", release?.htmlUrl)
    }

    @Test
    fun testParseReleaseJsonMinimal() {
        val json = """{"tag_name": "v3.0.0", "html_url": "https://github.com/dankouwu/wallpaper-lyrics/releases/tag/v3.0.0"}"""
        val release = UpdateCheck.parseReleaseJson(json)
        assertNotNull(release)
        assertEquals("v3.0.0", release?.tagName)
        assertEquals("https://github.com/dankouwu/wallpaper-lyrics/releases/tag/v3.0.0", release?.htmlUrl)
    }

    @Test
    fun testParseReleaseJsonMissingFields() {
        val missingUrl = """{"tag_name": "2.2.0"}"""
        assertNull(UpdateCheck.parseReleaseJson(missingUrl))

        val missingTag = """{"html_url": "https://github.com/dankouwu/wallpaper-lyrics"}"""
        assertNull(UpdateCheck.parseReleaseJson(missingTag))

        val blankTag = """{"tag_name": "  ", "html_url": "https://github.com/dankouwu/wallpaper-lyrics"}"""
        assertNull(UpdateCheck.parseReleaseJson(blankTag))
    }

    @Test
    fun testParseReleaseJsonMalformedOrEmpty() {
        assertNull(UpdateCheck.parseReleaseJson(""))
        assertNull(UpdateCheck.parseReleaseJson("not json"))
        assertNull(UpdateCheck.parseReleaseJson("{broken json"))
    }
}
