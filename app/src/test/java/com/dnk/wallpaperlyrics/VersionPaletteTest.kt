package com.dnk.wallpaperlyrics

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.sqrt

class VersionPaletteTest {

    private val pinned220 = intArrayOf(
        0xFF805D93.toInt(),
        0xFFD31277.toInt(),
        0xFF56BD54.toInt(),
        0xFF00DFFF.toInt()
    )

    private val roleTargetL = floatArrayOf(
        0.5355f, // Accent
        0.5682f, // Base
        0.7135f, // Mid
        0.8300f  // Highlight
    )

    @Test
    fun `forVersion 2 2 0 returns pinned four colours`() {
        val palette = VersionPalette.forVersion("2.2.0")
        assertArrayEquals(pinned220, palette)
    }

    @Test
    fun `forVersion is deterministic and ignores leading and trailing whitespace`() {
        val first = VersionPalette.forVersion("2.3.0")
        val second = VersionPalette.forVersion("2.3.0")
        val padded = VersionPalette.forVersion("   2.3.0  \t\n ")
        assertArrayEquals(first, second)
        assertArrayEquals(first, padded)
    }

    @Test
    fun `generated palettes are unique across releases and differ from pinned 2 2 0`() {
        val versions = listOf("2.2.0", "2.2.1", "2.3.0", "2.4.0", "3.0.0", "10.0.0")
        val palettes = versions.map { VersionPalette.forVersion(it).toList() }
        val distinctPalettes = palettes.distinct()
        assertEquals("Each version must produce a distinct palette", versions.size, distinctPalettes.size)
    }

    @Test
    fun `every generated colour is opaque and has OKLab L within target plus or minus 0 031`() {
        val versions = listOf("2.2.1", "2.3.0", "2.4.0", "2.5.0", "3.0.0", "10.0.0")
        for (version in versions) {
            val palette = VersionPalette.forVersion(version)
            assertEquals("Palette must contain 4 roles", 4, palette.size)
            for (role in 0 until 4) {
                val color = palette[role]
                val alpha = (color ushr 24) and 0xFF
                assertEquals("Color at role $role in version $version must be opaque", 0xFF, alpha)

                val actualL = oklabL(color)
                val targetL = roleTargetL[role]
                assertTrue(
                    "Role $role in version $version L=$actualL not within $targetL +/- 0.031",
                    Math.abs(actualL - targetL) <= 0.031f
                )
            }
        }
    }

    @Test
    fun `neighbouring roles in generated palettes are separated by at least 0 08 in OKLab`() {
        val minAllowedDistance = 0.08f
        val versions = listOf("2.2.1", "2.3.0", "2.4.0", "2.5.0", "3.0.0", "10.0.0")
        for (version in versions) {
            val palette = VersionPalette.forVersion(version)
            val adjacentPairs = listOf(
                Pair(0, 1),
                Pair(0, 2),
                Pair(1, 3),
                Pair(2, 3)
            )
            for ((r1, r2) in adjacentPairs) {
                val dist = oklabDeltaE(palette[r1], palette[r2])
                assertTrue(
                    "Adjacent roles $r1 and $r2 in version $version too close (OKLab distance $dist < $minAllowedDistance)",
                    dist >= minAllowedDistance
                )
            }
        }
    }

    @Test
    fun `stable snapshot for version 2 3 0 catches accidental algorithm changes`() {
        val palette = VersionPalette.forVersion("2.3.0")
        val expected = intArrayOf(
            0xFF00747A.toInt(),
            0xFFCD008A.toInt(),
            0xFFF98126.toInt(),
            0xFF5ACDFF.toInt()
        )
        assertArrayEquals(expected, palette)
    }

    @Test
    fun `schemeForVersion correctly reports scheme for pinned and generated versions`() {
        assertEquals("Pinned", VersionPalette.schemeForVersion("2.2.0"))
        assertEquals("Triadic", VersionPalette.schemeForVersion("2.2.1"))
        assertEquals("Split complementary", VersionPalette.schemeForVersion("2.3.0"))
        assertEquals("Complementary", VersionPalette.schemeForVersion("2.4.0"))
        assertEquals("Complementary", VersionPalette.schemeForVersion("2.5.0"))
        assertEquals("Triadic", VersionPalette.schemeForVersion("3.0.0"))
    }

    @Test
    fun `generated releases match expected hex palettes and schemes`() {
        val expectedPalettes = mapOf(
            "2.2.1" to listOf("#536FAA", "#E8003C", "#58C15C", "#E7A0F2"),
            "2.3.0" to listOf("#00747A", "#CD008A", "#F98126", "#5ACDFF"),
            "2.4.0" to listOf("#546599", "#8F6B00", "#A28BFF", "#C6D563"),
            "2.5.0" to listOf("#A45C70", "#008C7D", "#FF7F4A", "#24D7FB"),
            "3.0.0" to listOf("#806B1D", "#008493", "#E47CD6", "#88E89F")
        )

        for ((version, expectedHex) in expectedPalettes) {
            val palette = VersionPalette.forVersion(version)
            val actualHex = palette.map { String.format("#%06X", it and 0x00FFFFFF) }
            assertEquals("Palette mismatch for $version", expectedHex, actualHex)
        }
    }

    private fun srgbToLinear(c: Float): Float {
        return if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055) / 1.055), 2.4).toFloat()
    }

    private fun oklab(color: Int): FloatArray {
        val rLin = srgbToLinear(((color shr 16) and 0xFF) / 255f)
        val gLin = srgbToLinear(((color shr 8) and 0xFF) / 255f)
        val bLin = srgbToLinear((color and 0xFF) / 255f)

        val l = 0.4122214708f * rLin + 0.5363325363f * gLin + 0.0514459929f * bLin
        val m = 0.2119034982f * rLin + 0.6806995451f * gLin + 0.1073969566f * bLin
        val s = 0.0883024619f * rLin + 0.2817188376f * gLin + 0.6299787005f * bLin

        val l_ = Math.cbrt(l.toDouble()).toFloat()
        val m_ = Math.cbrt(m.toDouble()).toFloat()
        val s_ = Math.cbrt(s.toDouble()).toFloat()

        val L = 0.2104542553f * l_ + 0.7936177850f * m_ - 0.0040720468f * s_
        val a = 1.9779984951f * l_ - 2.4285922050f * m_ + 0.4505937099f * s_
        val b = 0.0259040371f * l_ + 0.7827717662f * m_ - 0.8086757660f * s_
        return floatArrayOf(L, a, b)
    }

    private fun oklabL(color: Int): Float = oklab(color)[0]

    private fun oklabDeltaE(c1: Int, c2: Int): Float {
        val (l1, a1, b1) = oklab(c1)
        val (l2, a2, b2) = oklab(c2)
        val dL = l1 - l2
        val da = a1 - a2
        val db = b1 - b2
        return sqrt(dL * dL + da * da + db * db)
    }
}
