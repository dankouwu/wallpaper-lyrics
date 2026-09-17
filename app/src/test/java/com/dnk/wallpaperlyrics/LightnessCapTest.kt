package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.hypot

class LightnessCapTest {

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

    private fun computeLuma(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return Math.round(0.299f * r + 0.587f * g + 0.114f * b)
    }

    private fun computeMaxAdjacentBlockLumaStep(pixels: IntArray, width: Int, height: Int, blockSize: Int = 8): Float {
        val bw = width / blockSize
        val bh = height / blockSize
        val blockLuma = FloatArray(bw * bh)
        val blockPixels = (blockSize * blockSize).toFloat()

        for (by in 0 until bh) {
            val yOffset = by * blockSize
            for (bx in 0 until bw) {
                val xOffset = bx * blockSize
                var sum = 0.0f
                for (dy in 0 until blockSize) {
                    val row = (yOffset + dy) * width
                    for (dx in 0 until blockSize) {
                        sum += computeLuma(pixels[row + xOffset + dx])
                    }
                }
                blockLuma[by * bw + bx] = sum / blockPixels
            }
        }

        var maxStep = 0.0f
        for (by in 0 until bh) {
            for (bx in 0 until bw) {
                val lum = blockLuma[by * bw + bx]
                if (bx + 1 < bw) {
                    val rightLum = blockLuma[by * bw + bx + 1]
                    val step = Math.abs(lum - rightLum)
                    if (step > maxStep) maxStep = step
                }
                if (by + 1 < bh) {
                    val downLum = blockLuma[(by + 1) * bw + bx]
                    val step = Math.abs(lum - downLum)
                    if (step > maxStep) maxStep = step
                }
            }
        }
        return maxStep
    }

    @Test
    fun `white is capped into expected grey range`() {
        val white = 0xFFFFFFFF.toInt()
        val result = AuroraRenderer.capLightnessColor(white)
        val r = (result shr 16) and 0xFF
        val g = (result shr 8) and 0xFF
        val b = result and 0xFF
        val luma = computeLuma(result)

        assertTrue("Expected luma between 207 and 220, got $luma", luma in 207..220)
        assertTrue("R ($r) and G ($g) difference exceeds 1", kotlin.math.abs(r - g) <= 1)
        assertTrue("G ($g) and B ($b) difference exceeds 1", kotlin.math.abs(g - b) <= 1)
        assertTrue("R ($r) and B ($b) difference exceeds 1", kotlin.math.abs(r - b) <= 1)
    }

    @Test
    fun `colors at or below knee are returned bit identical`() {
        val samples = listOf(
            0xFF000000.toInt(),
            0xFF1E4E7A.toInt(),
            0xFFE02040.toInt(),
            0xFF6E6E70.toInt(),
            0xFF500000.toInt(),
            0xFF787878.toInt()
        )
        for (color in samples) {
            val lab = colorToOklab(color)
            assertTrue("Sample color 0x${Integer.toHexString(color)} has L=${lab.l}, expected <= 0.62", lab.l <= 0.62f)
            val result = AuroraRenderer.capLightnessColor(color)
            assertEquals("Color 0x${Integer.toHexString(color)} was modified below knee", color, result)
        }
    }

    @Test
    fun `output luma is monotonic across sRGB grey sweep`() {
        var prevLuma = -1
        for (g in 0..255) {
            val color = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            val result = AuroraRenderer.capLightnessColor(color)
            val luma = computeLuma(result)
            assertTrue("Luma decreased from $prevLuma to $luma at grey $g", luma >= prevLuma)
            prevLuma = luma
        }
    }

    @Test
    fun `lightness transition is continuous at knee`() {
        val samples = mutableListOf<Pair<Float, Float>>()
        for (g in 0..255) {
            val color = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            val inLab = colorToOklab(color)
            if (inLab.l in 0.55f..0.70f) {
                val outColor = AuroraRenderer.capLightnessColor(color)
                val outLab = colorToOklab(outColor)
                samples.add(inLab.l to outLab.l)
            }
        }
        assertTrue("Expected samples in range 0.55..0.70", samples.size >= 2)
        for (i in 0 until samples.size - 1) {
            val inStep = samples[i + 1].first - samples[i].first
            val outStep = samples[i + 1].second - samples[i].second
            assertTrue(
                "Step at sample $i (inStep=$inStep, outStep=$outStep) exceeds 1.1x input step",
                outStep <= 1.1f * inStep + 1e-6f
            )
        }
    }

    @Test
    fun `hue is preserved within two degrees for light colors`() {
        val pink = 0xFFF4C2D7.toInt()
        val blue = 0xFFBFD9F2.toInt()

        for (color in listOf(pink, blue)) {
            val inHue = hueDegrees(colorToOklab(color))
            val outColor = AuroraRenderer.capLightnessColor(color)
            val outHue = hueDegrees(colorToOklab(outColor))
            val diff = hueDifferenceDegrees(inHue, outHue)
            assertTrue("Hue difference was $diff degrees, expected <= 2.0 degrees", diff <= 2.0f)
        }
    }

    @Test
    fun `banding measurement on smooth bright field is bounded at or below four point two`() {
        val width = 64
        val height = 64
        val pixels = IntArray(width * height) { i ->
            val x = i % width
            val g = Math.round(200f + (x.toFloat() / (width - 1)) * 55f)
            (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }

        AuroraRenderer.capLightness(pixels, width, height)
        val maxStep = computeMaxAdjacentBlockLumaStep(pixels, width, height, 8)
        assertTrue("Maximum adjacent block luma step was $maxStep, expected at or below 4.2", maxStep <= 4.2f)
    }

    @Test
    fun `alpha byte is preserved and channel values clamped within bounds`() {
        val width = 8
        val height = 8
        val pixels = IntArray(width * height) { i ->
            val alpha = ((i * 37) and 0xFF) shl 24
            alpha or 0x00F0E0D0
        }
        val expectedAlphas = IntArray(pixels.size) { (pixels[it] ushr 24) and 0xFF }
        AuroraRenderer.capLightness(pixels, width, height)
        for (i in pixels.indices) {
            val actualAlpha = (pixels[i] ushr 24) and 0xFF
            assertEquals(expectedAlphas[i], actualAlpha)
            val r = (pixels[i] shr 16) and 0xFF
            val g = (pixels[i] shr 8) and 0xFF
            val b = pixels[i] and 0xFF
            assertTrue(r in 0..255)
            assertTrue(g in 0..255)
            assertTrue(b in 0..255)
        }

        val blackField = IntArray(16) { 0xFF000000.toInt() }
        AuroraRenderer.capLightness(blackField, 4, 4)
        val whiteField = IntArray(16) { 0xFFFFFFFF.toInt() }
        AuroraRenderer.capLightness(whiteField, 4, 4)
    }

    @Test
    fun `real case Journals shaped field lands between 207 and 220 mean luma`() {
        val width = 64
        val height = 64
        val pixels = IntArray(width * height) { i ->
            val noise = (i * 17 + (i % 7)) % 4
            val r = 251 + noise
            val g = 251 + ((noise + 1) % 4)
            val b = 251 + ((noise + 2) % 4)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        AuroraRenderer.boostChroma(pixels, width, height, AuroraRenderer.BACKGROUND_CHROMA_BOOST)
        AuroraRenderer.capLightness(pixels, width, height)

        var totalLuma = 0.0
        for (p in pixels) {
            totalLuma += computeLuma(p)
        }
        val meanLuma = totalLuma / pixels.size
        assertTrue("Mean luma was $meanLuma, expected between 207 and 220", meanLuma in 207.0..220.0)
    }

    @Test
    fun `sRGB grey 200 comes out with luma between 180 and 188`() {
        val grey200 = (0xFF shl 24) or (200 shl 16) or (200 shl 8) or 200
        val result = AuroraRenderer.capLightnessColor(grey200)
        val luma = computeLuma(result)
        assertTrue("Expected luma between 180 and 188, got $luma", luma in 180..188)
    }
}
