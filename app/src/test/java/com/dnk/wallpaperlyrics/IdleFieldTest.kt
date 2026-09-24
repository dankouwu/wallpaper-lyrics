package com.dnk.wallpaperlyrics

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.sqrt

class IdleFieldTest {

    private data class Lab(val l: Float, val a: Float, val b: Float)

    private fun srgbToLinear(c: Float): Float {
        val clamped = c.coerceIn(0f, 1f)
        return if (clamped <= 0.04045f) {
            clamped / 12.92f
        } else {
            Math.pow(((clamped + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        }
    }

    private fun colorToOklab(color: Int): Lab {
        val r = srgbToLinear(((color ushr 16) and 0xff) / 255f)
        val g = srgbToLinear(((color ushr 8) and 0xff) / 255f)
        val b = srgbToLinear((color and 0xff) / 255f)

        val l = 0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b
        val m = 0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b
        val s = 0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b

        val l_ = Math.cbrt(l.toDouble()).toFloat()
        val m_ = Math.cbrt(m.toDouble()).toFloat()
        val s_ = Math.cbrt(s.toDouble()).toFloat()

        val lOut = 0.2104542553f * l_ + 0.7936177850f * m_ - 0.0040720468f * s_
        val aOut = 1.9779984951f * l_ - 2.4285922050f * m_ + 0.4505937099f * s_
        val bOut = 0.0259040371f * l_ + 0.7827717662f * m_ - 0.8086757660f * s_
        return Lab(lOut, aOut, bOut)
    }

    private fun oklabDistance(c1: Lab, c2: Lab): Float {
        val dl = c1.l - c2.l
        val da = c1.a - c2.a
        val db = c1.b - c2.b
        return sqrt(dl * dl + da * da + db * db)
    }

    private val testPalette = intArrayOf(
        0xFF1A1A24.toInt(),
        0xFFB91C1C.toInt(),
        0xFF047857.toInt(),
        0xFFFDE047.toInt()
    )

    @Test
    fun deterministicOutput() {
        val out1 = IntArray(128 * 128)
        val out2 = IntArray(128 * 128)
        AuroraRenderer.composeIdleField(testPalette, 128, 128, out1)
        AuroraRenderer.composeIdleField(testPalette, 128, 128, out2)
        assertArrayEquals(out1, out2)
    }

    @Test
    fun differentPalettesGiveDifferentPictures() {
        val paletteB = intArrayOf(
            0xFF881337.toInt(),
            0xFF1E3A8A.toInt(),
            0xFF065F46.toInt(),
            0xFFF59E0B.toInt()
        )
        val outA = IntArray(128 * 128)
        val outB = IntArray(128 * 128)
        AuroraRenderer.composeIdleField(testPalette, 128, 128, outA)
        AuroraRenderer.composeIdleField(paletteB, 128, 128, outB)
        assertFalse(outA.contentEquals(outB))
    }

    @Test
    fun everyPixelIsOpaque() {
        val out = IntArray(128 * 128)
        AuroraRenderer.composeIdleField(testPalette, 128, 128, out)
        for (i in out.indices) {
            val alpha = (out[i] ushr 24) and 0xff
            assertEquals(255, alpha)
        }
    }

    @Test
    fun equalAreaCoverage() {
        val out = IntArray(128 * 128)
        AuroraRenderer.composeIdleField(testPalette, 128, 128, out)
        val paletteLabs = testPalette.map { colorToOklab(it) }
        val counts = IntArray(4)

        for (color in out) {
            val lab = colorToOklab(color)
            var bestIdx = 0
            var bestDist = Float.MAX_VALUE
            for (i in paletteLabs.indices) {
                val d = oklabDistance(lab, paletteLabs[i])
                if (d < bestDist) {
                    bestDist = d
                    bestIdx = i
                }
            }
            counts[bestIdx]++
        }

        val total = (128 * 128).toFloat()
        for (i in 0 until 4) {
            val fraction = counts[i] / total
            assertTrue(
                "Color $i coverage was $fraction, expected between 0.15 and 0.35",
                fraction in 0.15f..0.35f
            )
        }
    }

    @Test
    fun pureColoursAppear() {
        val out = IntArray(128 * 128)
        AuroraRenderer.composeIdleField(testPalette, 128, 128, out)
        val threshold = 0.01f
        for (c in testPalette) {
            val targetLab = colorToOklab(c)
            var minDist = Float.MAX_VALUE
            for (pixel in out) {
                val d = oklabDistance(colorToOklab(pixel), targetLab)
                if (d < minDist) {
                    minDist = d
                }
            }
            assertTrue(
                "Palette color 0x${Integer.toHexString(c)} min distance was $minDist, expected <= $threshold",
                minDist <= threshold
            )
        }
    }

    @Test
    fun orderingByLightness() {
        val out = IntArray(128 * 128)
        AuroraRenderer.composeIdleField(testPalette, 128, 128, out)
        val paletteLabs = testPalette.map { colorToOklab(it) }

        var darkestIdx = 0
        var lightestIdx = 0
        for (i in 1 until paletteLabs.size) {
            if (paletteLabs[i].l < paletteLabs[darkestIdx].l) darkestIdx = i
            if (paletteLabs[i].l > paletteLabs[lightestIdx].l) lightestIdx = i
        }

        var darkestSumL = 0.0
        var darkestCount = 0
        var lightestSumL = 0.0
        var lightestCount = 0

        for (pixel in out) {
            val lab = colorToOklab(pixel)
            var bestIdx = 0
            var bestDist = Float.MAX_VALUE
            for (i in paletteLabs.indices) {
                val d = oklabDistance(lab, paletteLabs[i])
                if (d < bestDist) {
                    bestDist = d
                    bestIdx = i
                }
            }
            if (bestIdx == darkestIdx) {
                darkestSumL += lab.l
                darkestCount++
            } else if (bestIdx == lightestIdx) {
                lightestSumL += lab.l
                lightestCount++
            }
        }

        assertTrue("Darkest color should have matching pixels", darkestCount > 0)
        assertTrue("Lightest color should have matching pixels", lightestCount > 0)
        val meanDarkL = (darkestSumL / darkestCount).toFloat()
        val meanLightL = (lightestSumL / lightestCount).toFloat()
        assertTrue(
            "Mean lightness of darkest pixels ($meanDarkL) must be lower than lightest pixels ($meanLightL)",
            meanDarkL < meanLightL
        )
    }

    @Test
    fun smoothNotSpeckled() {
        val w = 128
        val h = 128
        val out = IntArray(w * h)
        AuroraRenderer.composeIdleField(testPalette, w, h, out)
        val threshold = 0.35f
        var maxDiff = 0f

        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val currentLab = colorToOklab(out[row + x])
                if (x + 1 < w) {
                    val rightLab = colorToOklab(out[row + x + 1])
                    val d = oklabDistance(currentLab, rightLab)
                    if (d > maxDiff) maxDiff = d
                }
                if (y + 1 < h) {
                    val downLab = colorToOklab(out[row + w + x])
                    val d = oklabDistance(currentLab, downLab)
                    if (d > maxDiff) maxDiff = d
                }
            }
        }

        assertTrue(
            "Maximum adjacent OKLab diff was $maxDiff, expected <= $threshold",
            maxDiff <= threshold
        )
    }

    @Test
    fun fourIdenticalColoursGiveExactColourEverywhere() {
        val singleColor = 0xFF3B82F6.toInt()
        val palette = intArrayOf(singleColor, singleColor, singleColor, singleColor)
        val out = IntArray(128 * 128)
        AuroraRenderer.composeIdleField(palette, 128, 128, out)
        val expectedLab = colorToOklab(singleColor)

        for (pixel in out) {
            val d = oklabDistance(colorToOklab(pixel), expectedLab)
            assertTrue("Pixel differed from single color by $d", d < 0.001f)
        }
    }

    @Test
    fun sizes16And128BothWork() {
        val out16 = IntArray(16 * 16)
        AuroraRenderer.composeIdleField(testPalette, 16, 16, out16)
        for (pixel in out16) {
            assertEquals(255, (pixel ushr 24) and 0xff)
        }

        val out128 = IntArray(128 * 128)
        AuroraRenderer.composeIdleField(testPalette, 128, 128, out128)
        for (pixel in out128) {
            assertEquals(255, (pixel ushr 24) and 0xff)
        }
    }
}
