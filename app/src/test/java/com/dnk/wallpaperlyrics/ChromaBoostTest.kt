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
    fun `banding measurement on synthetic smooth dark low chroma field is bounded at or below two point one`() {
        val width = 64
        val height = 64
        val c1 = (0xFF shl 24) or (18 shl 16) or (15 shl 8) or 23
        val c2 = (0xFF shl 24) or (18 shl 16) or (16 shl 8) or 22
        val pixels = IntArray(width * height) { i ->
            val x = i % width
            if (x < width / 2) c1 else c2
        }

        assertEquals(0.0f, computeMaxAdjacentBlockLumaStep(pixels, width, height), 0.001f)

        AuroraRenderer.boostChroma(pixels, width, height, 4.5f)
        val maxStep = computeMaxAdjacentBlockLumaStep(pixels, width, height)
        assertTrue("Maximum adjacent block luma step was $maxStep, expected at or below 2.1", maxStep <= 2.1f)
    }

    @Test
    fun `alpha byte is preserved across boost path for entire field`() {
        val width = 8
        val height = 8
        val pixels = IntArray(width * height) { i ->
            val alpha = ((i * 37) and 0xFF) shl 24
            alpha or 0x00120F17
        }
        val expectedAlphas = IntArray(pixels.size) { (pixels[it] ushr 24) and 0xFF }
        AuroraRenderer.boostChroma(pixels, width, height, 4.5f)
        for (i in pixels.indices) {
            val actualAlpha = (pixels[i] ushr 24) and 0xFF
            assertEquals(expectedAlphas[i], actualAlpha)
        }
    }

    @Test
    fun `pure grey field stays pure grey with zero chroma after boost including dithering`() {
        val width = 16
        val height = 16
        val greys = intArrayOf(0x00, 0x20, 0x50, 0x6E, 0x80, 0xA0, 0xD0, 0xFF)
        val pixels = IntArray(width * height) { i ->
            val g = greys[i % greys.size]
            (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        val original = pixels.clone()
        AuroraRenderer.boostChroma(pixels, width, height, 4.5f)
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            assertEquals("Red and green should match for grey", r, g)
            assertEquals("Green and blue should match for grey", g, b)
            assertEquals("Grey pixel should be unchanged", original[i], color)
        }
    }

    @Test
    fun `every channel stays within zero to two hundred fifty five at black and white extremes`() {
        val width = 16
        val height = 16
        val testColors = intArrayOf(
            0xFF000000.toInt(),
            0xFF010001.toInt(),
            0xFF000100.toInt(),
            0xFF020101.toInt(),
            0xFFFEFEFF.toInt(),
            0xFFFFFFFE.toInt(),
            0xFFFEFFFF.toInt(),
            0xFFFFFFFF.toInt()
        )
        val pixels = IntArray(width * height) { i ->
            testColors[i % testColors.size]
        }
        AuroraRenderer.boostChroma(pixels, width, height, 5.0f)
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            assertTrue(r in 0..255)
            assertTrue(g in 0..255)
            assertTrue(b in 0..255)
        }
    }

    @Test
    fun `mean colour is preserved between dithered and undithered fields`() {
        val width = 64
        val height = 64
        val c1 = (0xFF shl 24) or (18 shl 16) or (15 shl 8) or 23
        val c2 = (0xFF shl 24) or (18 shl 16) or (16 shl 8) or 22
        val pixelsDithered = IntArray(width * height) { i ->
            val x = i % width
            if (x < width / 2) c1 else c2
        }
        val pixelsUndithered = pixelsDithered.clone()

        for (i in pixelsUndithered.indices) {
            pixelsUndithered[i] = AuroraRenderer.boostChromaColor(pixelsUndithered[i], 4.5f)
        }

        AuroraRenderer.boostChroma(pixelsDithered, width, height, 4.5f)

        var sumRDiff = 0.0
        var sumGDiff = 0.0
        var sumBDiff = 0.0
        val totalPixels = width * height

        for (i in 0 until totalPixels) {
            val dColor = pixelsDithered[i]
            val uColor = pixelsUndithered[i]
            sumRDiff += ((dColor shr 16) and 0xFF) - ((uColor shr 16) and 0xFF)
            sumGDiff += ((dColor shr 8) and 0xFF) - ((uColor shr 8) and 0xFF)
            sumBDiff += (dColor and 0xFF) - (uColor and 0xFF)
        }

        val meanRDiff = Math.abs(sumRDiff / totalPixels)
        val meanGDiff = Math.abs(sumGDiff / totalPixels)
        val meanBDiff = Math.abs(sumBDiff / totalPixels)

        assertTrue("Mean red shift was $meanRDiff, expected at or below 0.35", meanRDiff <= 0.35)
        assertTrue("Mean green shift was $meanGDiff, expected at or below 0.35", meanGDiff <= 0.35)
        assertTrue("Mean blue shift was $meanBDiff, expected at or below 0.35", meanBDiff <= 0.35)
    }

    @Test
    fun `transfer function is strictly monotonic with no flat region across and beyond cap`() {
        val lightness = 0.65f
        val hueRad = Math.toRadians(30.0).toFloat()
        val hueA = kotlin.math.cos(hueRad)
        val hueB = kotlin.math.sin(hueRad)
        val ceiling = AuroraRenderer.maxChromaAt(lightness, hueA, hueB)
        val cap = ceiling * 0.98f
        val boost = 4.5f

        // Sweep input chroma so that scaled = chroma * boost sweeps from zero through well past cap.
        val step = 0.0002f
        val minChroma = 0.001f
        val maxChroma = (cap * 1.5f) / boost
        val minStepBound = 1e-6f

        var prevOutput = AuroraRenderer.rollOffChroma(minChroma * boost, cap)
        var chroma = minChroma + step
        var stepCount = 0
        while (chroma <= maxChroma) {
            val output = AuroraRenderer.rollOffChroma(chroma * boost, cap)
            val diff = output - prevOutput
            assertTrue(
                "Output chroma per-step increase must be at least $minStepBound, but was $diff at chroma=$chroma (scaled=${chroma * boost}, cap=$cap)",
                diff >= minStepBound
            )
            prevOutput = output
            chroma += step
            stepCount++
        }
        assertTrue("Expected to evaluate multiple steps across the knee and cap", stepCount > 50)
    }

    @Test
    fun `inputs below the knee are bit identical to pre change output at production boost`() {
        val colorsBelowKnee = listOf(
            0xFF3A4048.toInt() to 0xFF244065.toInt(),
            0xFF3A3C40.toInt() to 0xFF333C4E.toInt(),
            0xFF403C3A.toInt() to 0xFF4A3930.toInt(),
            0xFF3C403A.toInt() to 0xFF334529.toInt(),
            0xFF504E4A.toInt() to 0xFF564D3B.toInt()
        )
        for ((input, expected) in colorsBelowKnee) {
            val result = AuroraRenderer.boostChromaColor(input, 4.5f)
            assertEquals("Expected bit-identical output for color %08X".format(input), expected, result)
        }

        // The transfer function response is strictly identity below knee fraction of cap
        val cap = 0.20f
        val knee = cap * 0.70f
        for (step in 0..100) {
            val scaled = knee * (step / 100f)
            val output = AuroraRenderer.rollOffChroma(scaled, cap)
            assertEquals(scaled, output, 1e-6f)
        }
    }

    @Test
    fun `cap is never exceeded and channels stay in range for very large input chroma`() {
        val lightness = 0.65f
        val hueRad = Math.toRadians(30.0).toFloat()
        val hueA = kotlin.math.cos(hueRad)
        val hueB = kotlin.math.sin(hueRad)
        val ceiling = AuroraRenderer.maxChromaAt(lightness, hueA, hueB)
        val cap = ceiling * 0.98f

        // Transfer function stays strictly below cap for large scaled chroma, and never exceeds cap
        val largeInputs = floatArrayOf(cap * 1.1f, cap * 1.5f, cap * 2.0f, cap * 3.0f)
        for (scaled in largeInputs) {
            val output = AuroraRenderer.rollOffChroma(scaled, cap)
            assertTrue("Output chroma ($output) must stay strictly below cap ($cap) for scaled=$scaled", output < cap)
        }

        // Extreme inputs asymptotically approach cap and never exceed it
        val extremeInputs = floatArrayOf(cap * 5.0f, cap * 10.0f, cap * 100.0f, 1000.0f)
        for (scaled in extremeInputs) {
            val output = AuroraRenderer.rollOffChroma(scaled, cap)
            assertTrue("Output chroma ($output) must never exceed cap ($cap) for scaled=$scaled", output <= cap)
        }

        // Color channels stay within 0..255 bounds under large chroma and extreme boosts
        val testColors = intArrayOf(
            0xFFC3909B.toInt(),
            0xFF1E4E7A.toInt(),
            0xFFE02040.toInt(),
            0xFFFF46A2.toInt(),
            0xFFEC213E.toInt(),
            0xFFFF0000.toInt(),
            0xFF00FF00.toInt(),
            0xFF0000FF.toInt()
        )
        for (color in testColors) {
            for (boost in floatArrayOf(4.5f, 10.0f, 50.0f, 100.0f)) {
                val result = AuroraRenderer.boostChromaColor(color, boost)
                val r = (result shr 16) and 0xFF
                val g = (result shr 8) and 0xFF
                val b = result and 0xFF
                assertTrue(r in 0..255)
                assertTrue(g in 0..255)
                assertTrue(b in 0..255)
            }
        }
    }

    @Test
    fun `degenerate inputs and pure grey return sane in range values without throwing or NaN`() {
        val degenerateCaps = floatArrayOf(0.0f, -0.1f, 1e-7f, 1e-6f)
        for (cap in degenerateCaps) {
            val res = AuroraRenderer.rollOffChroma(0.5f, cap)
            assertTrue("Degenerate cap $cap must not produce NaN", !res.isNaN())
            assertTrue("Degenerate cap $cap must produce value <= cap", res <= cap + 1e-6f)
        }

        // Pure greys return sane in-range values unchanged
        val greys = intArrayOf(0xFF000000.toInt(), 0xFF808080.toInt(), 0xFFFFFFFF.toInt(), 0xFF121212.toInt())
        for (grey in greys) {
            val res = AuroraRenderer.boostChromaColor(grey, 4.5f)
            assertEquals("Grey $grey should be returned unchanged", grey, res)
            val r = (res shr 16) and 0xFF
            val g = (res shr 8) and 0xFF
            val b = res and 0xFF
            assertTrue(r in 0..255)
            assertTrue(g in 0..255)
            assertTrue(b in 0..255)
        }
    }

    @Test
    fun `near white with tiny above guard chroma preserves lightness without depth darkening`() {
        val input = 0xFFFFFDFE.toInt()
        val inLuma = computeLuma(input)
        val result = AuroraRenderer.boostChromaColor(input, 4.5f)
        val outLuma = computeLuma(result)
        val lumaDiff = Math.abs(outLuma - inLuma)
        val bound = 2
        assertTrue(
            "Output luma ($outLuma) must be within $bound levels of input luma ($inLuma), but diff was $lumaDiff",
            lumaDiff <= bound
        )
    }

    @Test
    fun `no manufactured structure on near neutral field`() {
        val width = 16
        val height = 16
        val pixels = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            val r = 255
            val g = if ((x + y) % 2 == 0) 255 else 253
            val b = if (x % 2 == 0) 255 else 254
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val inLumas = pixels.map { computeLuma(it) }
        val inRange = inLumas.maxOrNull()!! - inLumas.minOrNull()!!

        AuroraRenderer.boostChroma(pixels, width, height, 4.5f)

        val outLumas = pixels.map { computeLuma(it) }
        val outRange = outLumas.maxOrNull()!! - outLumas.minOrNull()!!
        val bound = 3
        assertTrue(
            "Output luma range was $outRange, expected within bound of $bound (input range was $inRange)",
            outRange <= bound
        )

        var maxAdjacentStep = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val lum = outLumas[y * width + x]
                if (x + 1 < width) {
                    val step = Math.abs(lum - outLumas[y * width + (x + 1)])
                    if (step > maxAdjacentStep) maxAdjacentStep = step
                }
                if (y + 1 < height) {
                    val step = Math.abs(lum - outLumas[(y + 1) * width + x])
                    if (step > maxAdjacentStep) maxAdjacentStep = step
                }
            }
        }
        assertTrue(
            "Maximum adjacent luma step was $maxAdjacentStep, expected at or below 2",
            maxAdjacentStep <= 2
        )
    }

    @Test
    fun `vivid colours still get depth and have lightness reduced`() {
        val pinnedSaturatedSamples = listOf(
            0xFF1E4E7A.toInt() to 0.80f,
            0xFFE02040.toInt() to 0.77f,
            0xFFEC213E.toInt() to 0.77f,
            0xFFFF46A2.toInt() to 0.77f
        )
        for ((color, expectedRatio) in pinnedSaturatedSamples) {
            val inLab = colorToOklab(color)
            val outLab = colorToOklab(AuroraRenderer.boostChromaColor(color, 4.5f))
            val ratio = outLab.l / inLab.l
            assertEquals(expectedRatio, ratio, 0.02f)
            assertTrue("Depth stage must reduce lightness for saturated sample %08X".format(color), ratio <= 0.82f)
        }
    }

    private fun invokeSmoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val method = AuroraRenderer::class.java.getDeclaredMethod(
            "smoothstep",
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType
        ).apply { isAccessible = true }
        return method.invoke(AuroraRenderer, edge0, edge1, x) as Float
    }

    private fun getPrivateFloatConst(name: String): Float {
        val field = AuroraRenderer::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }
        return field.getFloat(AuroraRenderer)
    }

    @Test
    fun `continuity of lightness multiplier across the chroma floor window`() {
        val floorLow = getPrivateFloatConst("DEPTH_CHROMA_FLOOR_LOW")
        val floorHigh = getPrivateFloatConst("DEPTH_CHROMA_FLOOR_HIGH")
        val backgroundDepth = getPrivateFloatConst("BACKGROUND_DEPTH")
        val depthGateLow = getPrivateFloatConst("DEPTH_GATE_LOW")
        val depthGateHigh = getPrivateFloatConst("DEPTH_GATE_HIGH")

        assertEquals(0.010f, floorLow, 1e-6f)
        assertEquals(0.030f, floorHigh, 1e-6f)

        // For saturated colours with sourceRatio above DEPTH_GATE_HIGH, the ratio gate is 1.0.
        val sourceRatio = depthGateHigh + 0.1f
        val ratioGate = invokeSmoothstep(depthGateLow, depthGateHigh, sourceRatio)
        assertEquals(1.0f, ratioGate, 1e-6f)

        val step = 0.0002f
        val startChroma = floorLow - 0.002f
        val endChroma = floorHigh + 0.002f
        val maxStepBound = 0.008f

        var prevMultiplier: Float? = null
        var chroma = startChroma
        var stepCount = 0

        while (chroma <= endChroma + 1e-6f) {
            val chromaFloorGate = invokeSmoothstep(floorLow, floorHigh, chroma)
            val depthGate = ratioGate * chromaFloorGate
            val multiplier = 1f - backgroundDepth * depthGate

            if (chroma <= floorLow) {
                assertEquals(1.0f, multiplier, 1e-6f)
            }
            if (chroma >= floorHigh) {
                assertEquals(1f - backgroundDepth, multiplier, 1e-6f)
            }

            prevMultiplier?.let { prev ->
                assertTrue(
                    "Multiplier must be monotonically non-increasing, but went from $prev to $multiplier at chroma=$chroma",
                    multiplier <= prev + 1e-6f
                )
                val stepChange = Math.abs(multiplier - prev)
                assertTrue(
                    "Step change $stepChange at chroma $chroma exceeded bound $maxStepBound",
                    stepChange <= maxStepBound
                )
            }

            prevMultiplier = multiplier
            chroma += step
            stepCount++
        }

        assertTrue("Expected over 100 evaluation steps across the window", stepCount > 100)
    }
}
