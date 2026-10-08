package com.dnk.wallpaperlyrics

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class VersionPaletteTest {

    private val pinned220 = intArrayOf(
        0xFF805D93.toInt(),
        0xFFD31277.toInt(),
        0xFF56BD54.toInt(),
        0xFF00DFFF.toInt()
    )

    private val pinned230 = intArrayOf(
        0xFF5A6F33.toInt(),
        0xFF00858A.toInt(),
        0xFFFA812A.toInt(),
        0xFF86D773.toInt()
    )

    @Test
    fun `forVersion 2 2 0 returns pinned four colors`() {
        val palette = VersionPalette.forVersion("2.2.0")
        assertArrayEquals(pinned220, palette)
    }

    @Test
    fun `forVersion 2 3 0 returns pinned four colors`() {
        val palette = VersionPalette.forVersion("2.3.0")
        assertArrayEquals(pinned230, palette)
    }

    @Test
    fun `forVersion with unpinned name returns palette of highest pinned version`() {
        val palette999 = VersionPalette.forVersion("9.9.9")
        assertArrayEquals(pinned230, palette999)

        val palette100 = VersionPalette.forVersion("1.0.0")
        assertArrayEquals(pinned230, palette100)
    }

    @Test
    fun `isPinned identifies pinned and unpinned versions`() {
        assertTrue(VersionPalette.isPinned("2.2.0"))
        assertTrue(VersionPalette.isPinned("2.3.0"))
        assertTrue(VersionPalette.isPinned(" 2.3.0 "))
        assertFalse(VersionPalette.isPinned("2.2.1"))
        assertFalse(VersionPalette.isPinned("9.9.9"))
    }

    @Test
    fun `current build version has a pinned palette`() {
        assertTrue(
            "Add four hex codes for ${BuildConfig.VERSION_NAME} to PINNED_VERSIONS in VersionPalette.kt",
            VersionPalette.isPinned(BuildConfig.VERSION_NAME)
        )
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
    fun `generated palettes for seeds 1 to 300 are opaque and neighboring roles separated by at least 0 08 in OKLab`() {
        val adjacentPairs = listOf(
            Pair(0, 1),
            Pair(0, 2),
            Pair(1, 3),
            Pair(2, 3)
        )
        for (seed in 1..300) {
            val palette = VersionPalette.generate(seed)
            assertEquals(4, palette.size)
            for (role in 0 until 4) {
                val alpha = (palette[role] ushr 24) and 0xFF
                assertEquals("Role $role in seed $seed must be opaque", 0xFF, alpha)
            }
            for ((r1, r2) in adjacentPairs) {
                val dist = oklabDeltaE(palette[r1], palette[r2])
                assertTrue(
                    "Adjacent roles $r1 and $r2 in seed $seed too close (OKLab distance $dist < 0.08)",
                    dist >= 0.08f
                )
            }
        }
    }

    @Test
    fun `forVersion returns clone protecting internal pinned array from mutation`() {
        val p1 = VersionPalette.forVersion("2.3.0")
        val originalColor = p1[0]
        p1[0] = 0
        val p2 = VersionPalette.forVersion("2.3.0")
        assertEquals(originalColor, p2[0])
    }

    private val roleTargetL = floatArrayOf(
        0.5355f, // Accent
        0.5682f, // Base
        0.7135f, // Mid
        0.8300f  // Highlight
    )

    @Test
    fun `every generated color has OKLab L within target plus or minus 0 031`() {
        for (seed in 1..20) {
            val palette = VersionPalette.generate(seed)
            for (role in 0 until 4) {
                val actualL = oklab(palette[role])[0]
                val targetL = roleTargetL[role]
                assertTrue(
                    "Role $role in seed $seed L=$actualL not within $targetL +/- 0.031",
                    Math.abs(actualL - targetL) <= 0.031f
                )
            }
        }
    }

    @Test
    fun `generate is deterministic for same seed and varies across different seeds`() {
        val p1 = VersionPalette.generate(42)
        val p2 = VersionPalette.generate(42)
        val p3 = VersionPalette.generate(43)
        assertArrayEquals(p1, p2)
        assertFalse(p1.contentEquals(p3))
    }

    @Test
    fun `forVersion hex matches expected values for pinned versions and fallback`() {
        fun toHex(palette: IntArray) = palette.map { String.format("#%06X", it and 0x00FFFFFF) }
        assertEquals(listOf("#805D93", "#D31277", "#56BD54", "#00DFFF"), toHex(VersionPalette.forVersion("2.2.0")))
        assertEquals(listOf("#5A6F33", "#00858A", "#FA812A", "#86D773"), toHex(VersionPalette.forVersion("2.3.0")))
        assertEquals(listOf("#5A6F33", "#00858A", "#FA812A", "#86D773"), toHex(VersionPalette.forVersion("9.9.9")))
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

    private fun oklabDeltaE(c1: Int, c2: Int): Float {
        val (l1, a1, b1) = oklab(c1)
        val (l2, a2, b2) = oklab(c2)
        val dL = l1 - l2
        val da = a1 - a2
        val db = b1 - b2
        return sqrt(dL * dL + da * da + db * db)
    }
}
