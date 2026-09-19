package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class GamutRelativeChromaTest {

    private data class Lab(val l: Float, val a: Float, val b: Float, val chroma: Float)

    private val srgbToLinearTable = FloatArray(256) { i ->
        val c = i / 255f
        if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    }

    private fun linearToSrgb(c: Float): Float {
        val clamped = c.coerceIn(0f, 1f)
        return if (clamped <= 0.0031308f) {
            12.92f * clamped
        } else {
            1.055f * Math.pow(clamped.toDouble(), 1.0 / 2.4).toFloat() - 0.055f
        }
    }

    private fun colorToOklab(color: Int): Lab {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF

        val lr = srgbToLinearTable[r]
        val lg = srgbToLinearTable[g]
        val lb = srgbToLinearTable[b]

        val l = 0.4122214708f * lr + 0.5363325363f * lg + 0.0514459929f * lb
        val m = 0.2119034982f * lr + 0.6806995451f * lg + 0.1073969566f * lb
        val s = 0.0883024619f * lr + 0.2817188376f * lg + 0.6299787005f * lb

        val l_ = Math.cbrt(l.toDouble()).toFloat()
        val m_ = Math.cbrt(m.toDouble()).toFloat()
        val s_ = Math.cbrt(s.toDouble()).toFloat()

        val L = 0.2104542553f * l_ + 0.7936177850f * m_ - 0.0040720468f * s_
        val a = 1.9779984951f * l_ - 2.4285922050f * m_ + 0.4505937099f * s_
        val bOut = 0.0259040371f * l_ + 0.7827717662f * m_ - 0.8086757660f * s_
        return Lab(L, a, bOut, hypot(a, bOut))
    }

    private fun oklabToColor(L: Float, a: Float, b: Float): Int {
        val l_ = L + 0.3963377774f * a + 0.2158037573f * b
        val m_ = L - 0.1055613458f * a - 0.0638541728f * b
        val s_ = L - 0.0894841775f * a - 1.2914855480f * b

        val l = l_ * l_ * l_
        val m = m_ * m_ * m_
        val s = s_ * s_ * s_

        val rLin = +4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s
        val gLin = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s
        val bLin = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s

        val rByte = Math.round(linearToSrgb(rLin) * 255f).coerceIn(0, 255)
        val gByte = Math.round(linearToSrgb(gLin) * 255f).coerceIn(0, 255)
        val bByte = Math.round(linearToSrgb(bLin) * 255f).coerceIn(0, 255)

        return (0xFF shl 24) or (rByte shl 16) or (gByte shl 8) or bByte
    }

    @Test
    fun `output chroma is strictly increasing across fine sweep of input chroma`() {
        val hues = listOf(0.0, 35.0, 120.0, 240.0, 300.0)
        val lightnesses = listOf(0.35f, 0.50f, 0.70f)
        val exponents = listOf(0.15f, 0.30f, 1.0f)

        for (exp in exponents) {
            for (deg in hues) {
                val rad = Math.toRadians(deg)
                val hueA = cos(rad).toFloat()
                val hueB = sin(rad).toFloat()

                for (L in lightnesses) {
                    val ceiling = AuroraRenderer.maxChromaAt(L, hueA, hueB)
                    val steps = 100
                    var prevOutput = -1f

                    for (i in 1..steps) {
                        val cin = (i.toFloat() / steps.toFloat()) * ceiling
                        val out = AuroraRenderer.gamutRelativeChroma(cin, ceiling, exp)
                        if (prevOutput >= 0f) {
                            assertTrue(
                                "Monotonicity violated at L=$L, hue=$deg deg, exp=$exp, cin=$cin: out=$out <= prev=$prevOutput",
                                out > prevOutput
                            )
                        }
                        prevOutput = out
                    }
                }
            }
        }
    }

    @Test
    fun `derivative stays above zero near ceiling with no pinning`() {
        val exponents = listOf(0.15f, 0.30f, 1.0f)
        val L = 0.40f
        val rad = Math.toRadians(35.0)
        val hueA = cos(rad).toFloat()
        val hueB = sin(rad).toFloat()
        val ceiling = AuroraRenderer.maxChromaAt(L, hueA, hueB)

        for (exp in exponents) {
            val c1 = 0.98f * ceiling
            val c2 = 0.99f * ceiling
            val out1 = AuroraRenderer.gamutRelativeChroma(c1, ceiling, exp)
            val out2 = AuroraRenderer.gamutRelativeChroma(c2, ceiling, exp)

            val derivative = (out2 - out1) / (c2 - c1)
            assertTrue("Derivative must be strictly positive near ceiling for exp=$exp, but was $derivative", derivative > 0f)
            assertTrue("Outputs 1% apart near ceiling must be distinct for exp=$exp", out2 > out1)
        }
    }

    @Test
    fun `bounds output is zero at input zero and exactly cap at ceiling`() {
        val exponents = listOf(0.15f, 0.30f, 0.575f, 1.0f)
        val ceiling = 0.15f
        val expectedCap = ceiling * 0.98f

        for (exp in exponents) {
            val outAtZero = AuroraRenderer.gamutRelativeChroma(0f, ceiling, exp)
            assertEquals("Output must be 0 at input 0 for exp=$exp", 0f, outAtZero, 1e-6f)

            val outAtCeiling = AuroraRenderer.gamutRelativeChroma(ceiling, ceiling, exp)
            assertEquals("Output must be exactly cap at input ceiling for exp=$exp", expectedCap, outAtCeiling, 1e-6f)
        }
    }

    @Test
    fun `exponent one is identity on fraction of ceiling`() {
        val ceiling = 0.16f
        val cap = ceiling * 0.98f
        val testInputs = listOf(0.02f, 0.05f, 0.08f, 0.12f, 0.15f)

        for (cin in testInputs) {
            val expected = cap * (cin / ceiling)
            val actual = AuroraRenderer.gamutRelativeChroma(cin, ceiling, 1.0f)
            assertEquals("Exponent 1.0 must be linear on fraction", expected, actual, 1e-6f)
        }
    }

    @Test
    fun `greys below point zero zero one are returned unchanged at every exponent`() {
        val exponents = listOf(0.15f, 0.30f, 0.575f, 1.0f)
        val greys = listOf(
            0xFF000000.toInt(),
            0xFF121212.toInt(),
            0xFF6E6E6E.toInt(),
            0xFF808080.toInt(),
            0xFFFFFFFF.toInt()
        )

        for (exp in exponents) {
            for (color in greys) {
                val out = AuroraRenderer.boostChromaColor(color, exp)
                assertEquals("Grey color must be unchanged at exp=$exp", color, out)
            }
        }
    }

    @Test
    fun `slider direction maps rightward movement to lower exponent and higher output chroma`() {
        val leftPos = 0.0f
        val midPos = 0.5f
        val rightPos = 1.0f

        val expLeft = AuroraRenderer.sliderPositionToExponent(leftPos)
        val expMid = AuroraRenderer.sliderPositionToExponent(midPos)
        val expRight = AuroraRenderer.sliderPositionToExponent(rightPos)

        assertEquals("Left slider position must map to exponent 1.0", 1.0f, expLeft, 1e-6f)
        assertEquals("Midpoint slider position must map to exponent 0.575", 0.575f, expMid, 1e-6f)
        assertEquals("Right slider position must map to exponent 0.15", 0.15f, expRight, 1e-6f)

        assertEquals("Exponent 1.0 must map to left slider position 0.0", 0.0f, AuroraRenderer.exponentToSliderPosition(1.0f), 1e-6f)
        assertEquals("Exponent 0.575 must map to mid slider position 0.5", 0.5f, AuroraRenderer.exponentToSliderPosition(0.575f), 1e-6f)
        assertEquals("Exponent 0.15 must map to right slider position 1.0", 1.0f, AuroraRenderer.exponentToSliderPosition(0.15f), 1e-6f)

        val L = 0.40f
        val rad = Math.toRadians(35.0)
        val inColor = oklabToColor(L, 0.04f * cos(rad).toFloat(), 0.04f * sin(rad).toFloat())

        val outColorLeft = AuroraRenderer.boostChromaColor(inColor, expLeft)
        val outColorMid = AuroraRenderer.boostChromaColor(inColor, expMid)
        val outColorRight = AuroraRenderer.boostChromaColor(inColor, expRight)

        val chromaLeft = colorToOklab(outColorLeft).chroma
        val chromaMid = colorToOklab(outColorMid).chroma
        val chromaRight = colorToOklab(outColorRight).chroma

        assertTrue("Dragging right must produce higher output chroma: mid ($chromaMid) > left ($chromaLeft)", chromaMid > chromaLeft)
        assertTrue("Dragging right must produce higher output chroma: right ($chromaRight) > mid ($chromaMid)", chromaRight > chromaMid)
    }

    @Test
    fun `near neutral is no longer amplified and matches linear boost`() {
        val ceiling = 0.15f
        val exp = 0.30f
        val linearBoost = 4.5f
        val fractions = listOf(0.0005f, 0.001f, 0.01f)

        for (f in fractions) {
            val cin = f * ceiling
            val actual = AuroraRenderer.gamutRelativeChroma(cin, ceiling, exp)
            val expectedLinear = cin * linearBoost
            assertEquals(
                "At fraction $f, output chroma must equal linear result (cin * 4.5)",
                expectedLinear,
                actual,
                1e-6f
            )
            // Assert against section 8 table: output fraction must be within 1% of f * 4.5
            val outputFraction = actual / ceiling
            assertEquals(
                "At fraction $f, output fraction of ceiling must match table in section 8",
                f * linearBoost,
                outputFraction,
                1e-5f
            )
        }
    }

    @Test
    fun `high chroma is unchanged and matches power curve`() {
        val ceiling = 0.15f
        val cap = ceiling * 0.98f
        val exp = 0.30f
        val fractions = listOf(0.30f, 0.60f, 1.0f)

        for (f in fractions) {
            val cin = f * ceiling
            val actual = AuroraRenderer.gamutRelativeChroma(cin, ceiling, exp)
            val expectedPower = cap * Math.pow(f.toDouble(), exp.toDouble()).toFloat()
            assertEquals(
                "At fraction $f, output chroma must match power curve result",
                expectedPower,
                actual,
                1e-6f
            )
        }
    }

    @Test
    fun `crossover between linear and power curve sits near point one one five`() {
        val ceiling = 0.20f
        val exp = 0.30f
        val linearBoost = 4.5f
        val cap = ceiling * 0.98f

        // Theoretical crossover: 4.5 * f = 0.98 * f^0.30 => f^0.70 = 0.98 / 4.5 => f ~= 0.11475
        val belowCrossover = 0.10f
        val aboveCrossover = 0.13f

        val outBelow = AuroraRenderer.gamutRelativeChroma(belowCrossover * ceiling, ceiling, exp)
        val expectedBelowLinear = belowCrossover * ceiling * linearBoost
        val expectedBelowPower = cap * Math.pow(belowCrossover.toDouble(), exp.toDouble()).toFloat()
        assertEquals(expectedBelowLinear, outBelow, 1e-6f)
        assertTrue(outBelow < expectedBelowPower)

        val outAbove = AuroraRenderer.gamutRelativeChroma(aboveCrossover * ceiling, ceiling, exp)
        val expectedAboveLinear = aboveCrossover * ceiling * linearBoost
        val expectedAbovePower = cap * Math.pow(aboveCrossover.toDouble(), exp.toDouble()).toFloat()
        assertEquals(expectedAbovePower, outAbove, 1e-6f)
        assertTrue(outAbove < expectedAboveLinear)
    }

    @Test
    fun `depth has no inversion across fine sweep of input chroma`() {
        val hues = listOf(0.0, 35.0, 120.0, 240.0, 300.0)
        val lightnesses = listOf(0.35f, 0.50f, 0.70f)
        val exp = 0.30f

        for (deg in hues) {
            val rad = Math.toRadians(deg)
            val hueA = cos(rad).toFloat()
            val hueB = sin(rad).toFloat()

            for (L in lightnesses) {
                val ceiling = AuroraRenderer.maxChromaAt(L, hueA, hueB)
                val steps = 200
                var prevOutput = -1f

                for (i in 1..steps) {
                    val cin = (i.toFloat() / steps.toFloat()) * ceiling
                    val out = AuroraRenderer.gamutRelativeChroma(cin, ceiling, exp)
                    if (prevOutput >= 0f) {
                        assertTrue(
                            "Depth/chroma curve inversion detected at L=$L, hue=$deg deg, cin=$cin: out=$out <= prev=$prevOutput",
                            out > prevOutput
                        )
                    }
                    prevOutput = out
                }
            }
        }
    }
}
