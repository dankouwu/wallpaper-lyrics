package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.hypot

class ChromaBoostTest {

    private data class Lab(val l: Float, val a: Float, val b: Float)

    private fun srgbToLinear(c: Float): Float {
        val clamped = c.coerceIn(0f, 1f)
        return if (clamped <= 0.04045f) {
            clamped / 12.92f
        } else {
            Math.pow(((clamped + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        }
    }

    private fun rgbToOklab(rByte: Int, gByte: Int, bByte: Int): Lab {
        val lr = srgbToLinear(rByte / 255f)
        val lg = srgbToLinear(gByte / 255f)
        val lb = srgbToLinear(bByte / 255f)

        val l = 0.4122214708f * lr + 0.5363325363f * lg + 0.0514459929f * lb
        val m = 0.2119034982f * lr + 0.6806995451f * lg + 0.1073969566f * lb
        val s = 0.0883024619f * lr + 0.2817188376f * lg + 0.6299787005f * lb

        val l_ = Math.cbrt(l.toDouble()).toFloat()
        val m_ = Math.cbrt(m.toDouble()).toFloat()
        val s_ = Math.cbrt(s.toDouble()).toFloat()

        val lOut = 0.2104542553f * l_ + 0.7936177850f * m_ - 0.0040720468f * s_
        val aOut = 1.9779984951f * l_ - 2.4285922050f * m_ + 0.4505937099f * s_
        val bOut = 0.0259040371f * l_ + 0.7827717662f * m_ - 0.8086757660f * s_

        return Lab(lOut, aOut, bOut)
    }

    private fun colorToOklab(color: Int): Lab {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return rgbToOklab(r, g, b)
    }

    private fun hueDegrees(lab: Lab): Float {
        val deg = Math.toDegrees(atan2(lab.b.toDouble(), lab.a.toDouble())).toFloat()
        return if (deg >= 0f) deg else deg + 360f
    }

    private fun hueDifferenceDegrees(h1: Float, h2: Float): Float {
        val diff = kotlin.math.abs(h1 - h2) % 360f
        return if (diff <= 180f) diff else 360f - diff
    }

    @Test
    fun `maintainer example produces expected deep rich tone within channel tolerance`() {
        // Hue is held, so it does not land exactly on #FF46A2, which is a slightly different hue.
        val input = 0xFFC3909B.toInt()
        val result = AuroraRenderer.boostChromaColor(input, 3.5f)
        val r = (result shr 16) and 0xFF
        val g = (result shr 8) and 0xFF
        val b = result and 0xFF
        assertTrue(kotlin.math.abs(r - 253) <= 8)
        assertTrue(kotlin.math.abs(g - 97) <= 8)
        assertTrue(kotlin.math.abs(b - 145) <= 8)
    }

    @Test
    fun `lightness is preserved within point zero one for muted sample color`() {
        val color = 0xFFC3909B.toInt()
        val inLab = colorToOklab(color)
        val outLab = colorToOklab(AuroraRenderer.boostChromaColor(color, 3.5f))
        assertEquals(inLab.l, outLab.l, 0.01f)
    }

    @Test
    fun `lightness is reduced for saturated sample colors`() {
        val expectedRatios = listOf(
            0xFF1E4E7A.toInt() to 0.80f,
            0xFFE02040.toInt() to 0.77f
        )
        for ((color, expectedRatio) in expectedRatios) {
            val inLab = colorToOklab(color)
            val outLab = colorToOklab(AuroraRenderer.boostChromaColor(color, 3.5f))
            val ratio = outLab.l / inLab.l
            assertEquals(expectedRatio, ratio, 0.03f)
        }
    }

    @Test
    fun `hue is preserved within one and a half degrees for sample colors`() {
        val colors = listOf(0xFFC3909B.toInt(), 0xFF1E4E7A.toInt(), 0xFFE02040.toInt())
        for (color in colors) {
            val inHue = hueDegrees(colorToOklab(color))
            val outHue = hueDegrees(colorToOklab(AuroraRenderer.boostChromaColor(color, 3.5f)))
            assertTrue(hueDifferenceDegrees(inHue, outHue) <= 1.5f)
        }
    }

    @Test
    fun `chroma rises by at least two and a half times for example color`() {
        val input = 0xFFC3909B.toInt()
        val inLab = colorToOklab(input)
        val outLab = colorToOklab(AuroraRenderer.boostChromaColor(input, 3.5f))
        val inChroma = hypot(inLab.a, inLab.b)
        val outChroma = hypot(outLab.a, outLab.b)
        assertTrue(outChroma >= inChroma * 2.5f)
    }

    @Test
    fun `color already at gamut edge holds hue and reduces lightness`() {
        val input = 0xFFFF46A2.toInt()
        val result = AuroraRenderer.boostChromaColor(input, 3.5f)
        val inLab = colorToOklab(input)
        val outLab = colorToOklab(result)
        val inHue = hueDegrees(inLab)
        val outHue = hueDegrees(outLab)
        assertTrue(hueDifferenceDegrees(inHue, outHue) <= 1.5f)
        val ratio = outLab.l / inLab.l
        assertEquals(0.77f, ratio, 0.03f)
    }

    @Test
    fun `grey stays grey with chroma below point zero two`() {
        val input = 0xFF6E6E70.toInt()
        val result = AuroraRenderer.boostChromaColor(input, 3.5f)
        val lab = colorToOklab(result)
        val chroma = hypot(lab.a, lab.b)
        assertTrue(chroma < 0.02f)
    }

    @Test
    fun `pure white and pure black are returned unchanged`() {
        assertEquals(0xFFFFFFFF.toInt(), AuroraRenderer.boostChromaColor(0xFFFFFFFF.toInt(), 3.5f))
        assertEquals(0xFF000000.toInt(), AuroraRenderer.boostChromaColor(0xFF000000.toInt(), 3.5f))
    }

    @Test
    fun `alpha byte is preserved unchanged`() {
        val input = (0x80 shl 24) or (0xC3 shl 16) or (0x90 shl 8) or 0x9B
        val result = AuroraRenderer.boostChromaColor(input, 3.5f)
        assertEquals(0x80, (result ushr 24) and 0xFF)
    }

    @Test
    fun `every channel of every result is clamped within zero to two hundred fifty five`() {
        for (r in 0..255 step 32) {
            for (g in 0..255 step 32) {
                for (b in 0..255 step 32) {
                    for (boost in listOf(0.5f, 1.0f, 2.0f, 3.5f, 5.0f)) {
                        val color = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        val result = AuroraRenderer.boostChromaColor(color, boost)
                        val outR = (result shr 16) and 0xFF
                        val outG = (result shr 8) and 0xFF
                        val outB = result and 0xFF
                        assertTrue(outR in 0..255)
                        assertTrue(outG in 0..255)
                        assertTrue(outB in 0..255)
                    }
                }
            }
        }
    }

    @Test
    fun `srgb to linear table matches formula for all two hundred fifty six entries`() {
        val field = AuroraRenderer::class.java.getDeclaredField("srgbToLinearTable").apply {
            isAccessible = true
        }
        val table = field.get(AuroraRenderer) as FloatArray
        assertEquals(256, table.size)
        for (i in 0 until 256) {
            val c = i / 255f
            val expected = if (c <= 0.04045f) {
                c / 12.92f
            } else {
                Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
            }
            assertEquals(expected, table[i], 1e-7f)
        }
    }

    @Test
    fun `maintainer saturated red example deepens lightness and matches target rgb within channel tolerance`() {
        // The maintainer's stated #aa0611 rotates hue by about five degrees, which this implementation deliberately does not do.
        val input = 0xFFEC213E.toInt()
        val result = AuroraRenderer.boostChromaColor(input, 4.5f)
        val inLab = colorToOklab(input)
        val outLab = colorToOklab(result)
        val lightnessRatio = outLab.l / inLab.l
        assertEquals(0.77f, lightnessRatio, 0.02f)

        val r = (result shr 16) and 0xFF
        val g = (result shr 8) and 0xFF
        val b = result and 0xFF
        assertTrue(kotlin.math.abs(r - 169) <= 10)
        assertTrue(kotlin.math.abs(g - 7) <= 10)
        assertTrue(kotlin.math.abs(b - 38) <= 10)
    }

    @Test
    fun `muted color preserves lightness while saturated color loses lightness`() {
        val muted = 0xFFC3909B.toInt()
        val saturated = 0xFFEC213E.toInt()
        val mutedIn = colorToOklab(muted)
        val mutedOut = colorToOklab(AuroraRenderer.boostChromaColor(muted, 4.5f))
        val saturatedIn = colorToOklab(saturated)
        val saturatedOut = colorToOklab(AuroraRenderer.boostChromaColor(saturated, 4.5f))

        val mutedRatio = mutedOut.l / mutedIn.l
        val saturatedRatio = saturatedOut.l / saturatedIn.l

        assertEquals(1.0f, mutedRatio, 0.01f)
        assertEquals(0.77f, saturatedRatio, 0.03f)
        assertTrue(mutedRatio > saturatedRatio)
        assertTrue(mutedRatio - saturatedRatio >= 0.15f)
    }

    @Test
    fun `grey keeps lightness ratio of one within point zero one`() {
        val input = 0xFF6E6E70.toInt()
        val inLab = colorToOklab(input)
        val outLab = colorToOklab(AuroraRenderer.boostChromaColor(input, 4.5f))
        val ratio = outLab.l / inLab.l
        assertEquals(1.0f, ratio, 0.01f)
    }
}
