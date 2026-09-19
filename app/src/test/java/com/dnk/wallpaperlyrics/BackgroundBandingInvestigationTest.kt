package com.dnk.wallpaperlyrics

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.PrintWriter
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class BackgroundBandingInvestigationTest {

    private data class Lab(val l: Float, val a: Float, val b: Float, val chroma: Float, val hueDeg: Float)

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

        val chroma = hypot(a, bOut)
        var deg = Math.toDegrees(atan2(bOut.toDouble(), a.toDouble())).toFloat()
        if (deg < 0f) deg += 360f

        return Lab(L, a, bOut, chroma, deg)
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

    private class LoadedImage(val width: Int, val height: Int, val pixels: IntArray)

    private fun loadCover(name: String): LoadedImage {
        val imageIOClass = Class.forName("javax.imageio.ImageIO")
        val readStreamMethod = imageIOClass.getMethod("read", InputStream::class.java)
        val readFileMethod = imageIOClass.getMethod("read", File::class.java)

        val stream = javaClass.classLoader?.getResourceAsStream("covers/$name")
            ?: javaClass.classLoader?.getResourceAsStream("$name")
        val bufferedImage = if (stream != null) {
            readStreamMethod.invoke(null, stream)
        } else {
            val candidatePaths = listOf(
                File("src/test/resources/covers/$name"),
                File("app/src/test/resources/covers/$name"),
                File("/home/dnk/projects/Android/wallpaper-lyrics/app/src/test/resources/covers/$name")
            )
            val existing = candidatePaths.firstOrNull { it.exists() }
                ?: throw IllegalStateException("Cover image $name not found in candidates")
            readFileMethod.invoke(null, existing)
        } ?: throw IllegalStateException("Failed to decode cover image $name")

        val getWidthMethod = bufferedImage.javaClass.getMethod("getWidth")
        val getHeightMethod = bufferedImage.javaClass.getMethod("getHeight")
        val getRgbMethod = bufferedImage.javaClass.getMethod(
            "getRGB",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            IntArray::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )

        val width = getWidthMethod.invoke(bufferedImage) as Int
        val height = getHeightMethod.invoke(bufferedImage) as Int
        val pixels = IntArray(width * height)
        getRgbMethod.invoke(bufferedImage, 0, 0, width, height, pixels, 0, width)
        return LoadedImage(width, height, pixels)
    }

    @Test
    fun executeAllInvestigations() {
        val reportFile = File("build/banding_investigation_results.txt")
        reportFile.parentFile?.mkdirs()
        val out = PrintWriter(FileOutputStream(reportFile))

        try {
            out.println("=== BACKGROUND BANDING INVESTIGATION REPORT ===")
            out.println()

            runItem1(out)
            runItem2(out)
            runItem3And4(out)
            runItem5(out)
            runItem6(out)
            runItem7(out)

            out.println("=== END OF REPORT ===")
        } finally {
            out.flush()
            out.close()
        }
    }

    private fun runItem1(out: PrintWriter) {
        out.println("--------------------------------------------------------------------------------")
        out.println("ITEM 1: Chroma pinning across hue (24 hue steps x 4 lightness levels)")
        out.println("--------------------------------------------------------------------------------")
        out.println("Measured using exact AuroraRenderer.maxChromaAt and AuroraRenderer.boostChromaColor")
        out.println()

        val lightnessLevels = listOf(0.25f, 0.40f, 0.55f, 0.70f)
        val hueSteps = 24

        for (L in lightnessLevels) {
            out.println("### Lightness L = $L (Input chroma Cin = 0.40 * ceiling)")
            out.println(String.format("%-8s | %-16s | %-8s | %-8s | %-8s | %-8s | %-10s",
                "Hue(deg)", "Hue Family", "Cin", "Cout", "Ceiling", "Cap(98%)", "% of Cap"))
            out.println("---------+------------------+----------+----------+----------+----------+-----------")

            for (step in 0 until hueSteps) {
                val hueDeg = step * (360f / hueSteps)
                val rad = Math.toRadians(hueDeg.toDouble())
                val hueA = cos(rad).toFloat()
                val hueB = sin(rad).toFloat()

                val ceiling = AuroraRenderer.maxChromaAt(L, hueA, hueB)
                val cap = ceiling * 0.98f
                val Cin = 0.40f * ceiling

                val inColor = oklabToColor(L, Cin * hueA, Cin * hueB)
                val outColor = AuroraRenderer.boostChromaColor(inColor, AuroraRenderer.DEFAULT_CHROMA_EXPONENT)
                val outLab = colorToOklab(outColor)
                val Cout = outLab.chroma
                val pctOfCap = if (cap > 0f) (Cout / cap) * 100f else 100f

                val family = getHueFamily(hueDeg)
                out.println(String.format("%-8.1f | %-16s | %-8.4f | %-8.4f | %-8.4f | %-8.4f | %-9.2f%%",
                    hueDeg, family, Cin, Cout, ceiling, cap, pctOfCap))
            }
            out.println()
        }

        // Also sweep with fixed absolute Cin = 0.05
        out.println("### Lightness L = 0.40 (Fixed absolute input chroma Cin = 0.0500)")
        out.println(String.format("%-8s | %-16s | %-8s | %-8s | %-8s | %-8s | %-10s",
            "Hue(deg)", "Hue Family", "Cin", "Cout", "Ceiling", "Cap(98%)", "% of Cap"))
        out.println("---------+------------------+----------+----------+----------+----------+-----------")
        for (step in 0 until hueSteps) {
            val hueDeg = step * (360f / hueSteps)
            val rad = Math.toRadians(hueDeg.toDouble())
            val hueA = cos(rad).toFloat()
            val hueB = sin(rad).toFloat()

            val ceiling = AuroraRenderer.maxChromaAt(0.40f, hueA, hueB)
            val cap = ceiling * 0.98f
            val Cin = 0.05f

            val inColor = oklabToColor(0.40f, Cin * hueA, Cin * hueB)
            val outColor = AuroraRenderer.boostChromaColor(inColor, AuroraRenderer.DEFAULT_CHROMA_EXPONENT)
            val outLab = colorToOklab(outColor)
            val Cout = outLab.chroma
            val pctOfCap = if (cap > 0f) (Cout / cap) * 100f else 100f

            val family = getHueFamily(hueDeg)
            out.println(String.format("%-8.1f | %-16s | %-8.4f | %-8.4f | %-8.4f | %-8.4f | %-9.2f%%",
                hueDeg, family, Cin, Cout, ceiling, cap, pctOfCap))
        }
        out.println()
    }

    private fun runItem2(out: PrintWriter) {
        out.println("--------------------------------------------------------------------------------")
        out.println("ITEM 2: Loss of input dependence across hue families")
        out.println("--------------------------------------------------------------------------------")

        val families = listOf(
            Triple("Reddish Brown", 35f, 0.40f),
            Triple("Crimson Red", 25f, 0.45f),
            Triple("Amber/Orange", 55f, 0.50f),
            Triple("Yellow", 95f, 0.65f),
            Triple("Green", 145f, 0.50f),
            Triple("Cyan", 200f, 0.55f),
            Triple("Blue", 265f, 0.40f),
            Triple("Magenta", 325f, 0.45f)
        )

        for ((name, hueDeg, L) in families) {
            val rad = Math.toRadians(hueDeg.toDouble())
            val hueA = cos(rad).toFloat()
            val hueB = sin(rad).toFloat()
            val ceiling = AuroraRenderer.maxChromaAt(L, hueA, hueB)
            val cap = ceiling * 0.98f

            out.println("### Hue family: $name (Hue=$hueDeg deg, L=$L, Ceiling=$ceiling, Cap=$cap)")

            var thresholdCin99 = -1f
            var thresholdCinFlat = -1f
            var prevCout = -1f

            val steps = 50
            out.println(String.format("%-8s | %-12s | %-8s | %-8s | %-10s",
                "Cin", "Cin/Ceiling", "Cout", "dCout/dCin", "% of Cap"))
            out.println("---------+--------------+----------+------------+-----------")

            for (i in 1..steps) {
                val Cin = (i.toFloat() / steps.toFloat()) * ceiling
                val inColor = oklabToColor(L, Cin * hueA, Cin * hueB)
                val outColor = AuroraRenderer.boostChromaColor(inColor, 4.5f)
                val outLab = colorToOklab(outColor)
                val Cout = outLab.chroma
                val pct = if (cap > 0f) (Cout / cap) * 100f else 100f
                val dCout = if (prevCout >= 0f) (Cout - prevCout) / (ceiling / steps.toFloat()) else 4.5f

                if (i % 5 == 0 || i == 1 || (pct >= 99f && thresholdCin99 < 0f)) {
                    out.println(String.format("%-8.4f | %-12.2f%% | %-8.4f | %-10.4f | %-9.2f%%",
                        Cin, (Cin / ceiling) * 100f, Cout, dCout, pct))
                }

                if (pct >= 99.0f && thresholdCin99 < 0f) {
                    thresholdCin99 = Cin
                }
                if (dCout < 0.02f && thresholdCinFlat < 0f && i > 5) {
                    thresholdCinFlat = Cin
                }
                prevCout = Cout
            }

            out.println(String.format("--> Threshold 99%% of Cap: Cin = %.4f (%.1f%% of ceiling)",
                thresholdCin99, (thresholdCin99 / ceiling) * 100f))
            out.println(String.format("--> Threshold flat (dCout/dCin < 0.02): Cin = %.4f (%.1f%% of ceiling)",
                thresholdCinFlat, (thresholdCinFlat / ceiling) * 100f))
            out.println()
        }
    }

    private fun runItem3And4(out: PrintWriter) {
        out.println("--------------------------------------------------------------------------------")
        out.println("ITEMS 3 & 4: Distinct output levels and Contour jump locations")
        out.println("--------------------------------------------------------------------------------")

        val nSteps = 256
        val redGradient = IntArray(nSteps) { i ->
            val t = i.toFloat() / (nSteps - 1).toFloat()
            val r = Math.round(40 + (154 - 40) * t).coerceIn(0, 255)
            val g = Math.round(18 + (70 - 18) * t).coerceIn(0, 255)
            val b = Math.round(10 + (32 - 10) * t).coerceIn(0, 255)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        val blueGradient = IntArray(nSteps) { i ->
            val t = i.toFloat() / (nSteps - 1).toFloat()
            val r = Math.round(10 + (32 - 10) * t).coerceIn(0, 255)
            val g = Math.round(24 + (70 - 24) * t).coerceIn(0, 255)
            val b = Math.round(40 + (154 - 40) * t).coerceIn(0, 255)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        analyzeGradient("Reddish Brown Gradient (RGB ramp 40,18,10 -> 154,70,32)", redGradient, out)
        analyzeGradient("Blue Control Gradient (RGB ramp 10,24,40 -> 32,70,154)", blueGradient, out)

        val redOklabRamp = IntArray(nSteps) { i ->
            val t = i.toFloat() / (nSteps - 1).toFloat()
            val L = 0.22f + (0.55f - 0.22f) * t
            val C = 0.05f + (0.12f - 0.05f) * t
            val rad = Math.toRadians(35.0)
            oklabToColor(L, C * cos(rad).toFloat(), C * sin(rad).toFloat())
        }
        val blueOklabRamp = IntArray(nSteps) { i ->
            val t = i.toFloat() / (nSteps - 1).toFloat()
            val L = 0.22f + (0.55f - 0.22f) * t
            val C = 0.05f + (0.12f - 0.05f) * t
            val rad = Math.toRadians(265.0)
            oklabToColor(L, C * cos(rad).toFloat(), C * sin(rad).toFloat())
        }

        analyzeGradient("Reddish Brown OkLab Ramp (Hue=35 deg, L=0.22..0.55, C=0.05..0.12)", redOklabRamp, out)
        analyzeGradient("Blue OkLab Ramp (Hue=265 deg, L=0.22..0.55, C=0.05..0.12)", blueOklabRamp, out)
    }

    private fun analyzeGradient(name: String, ramp: IntArray, out: PrintWriter) {
        out.println("### Analysis for: $name")
        val n = ramp.size
        val distinctInput = ramp.distinct().size

        val afterBoost = IntArray(n) { i -> AuroraRenderer.boostChromaColor(ramp[i], 4.5f) }
        val distinctAfterBoost = afterBoost.distinct().size

        val afterCap = IntArray(n) { i -> AuroraRenderer.capLightnessColor(afterBoost[i]) }
        val distinctAfterCap = afterCap.distinct().size

        out.println("Distinct colors: Input=$distinctInput, After Boost=$distinctAfterBoost, After Cap=$distinctAfterCap")

        var jumpsOver1Lsb = 0
        var maxJump = 0
        var totalJump = 0
        val jumpLocations = mutableListOf<String>()

        for (i in 0 until n - 1) {
            val c1 = afterCap[i]
            val c2 = afterCap[i + 1]
            val dr = Math.abs(((c2 shr 16) and 0xFF) - ((c1 shr 16) and 0xFF))
            val dg = Math.abs(((c2 shr 8) and 0xFF) - ((c1 shr 8) and 0xFF))
            val db = Math.abs((c2 and 0xFF) - (c1 and 0xFF))
            val maxDelta = maxOf(dr, dg, db)
            totalJump += maxDelta
            if (maxDelta > maxJump) maxJump = maxDelta

            if (maxDelta > 1) {
                jumpsOver1Lsb++
                if (jumpLocations.size < 15) {
                    jumpLocations.add(String.format("Step %3d->%3d: dR=%d, dG=%d, dB=%d (maxDelta=%d LSB) from #%06X to #%06X",
                        i, i + 1, dr, dg, db, maxDelta, c1 and 0xFFFFFF, c2 and 0xFFFFFF))
                }
            }
        }

        val avgJump = totalJump.toFloat() / (n - 1).toFloat()
        out.println("Step jump stats (>1 LSB): count=$jumpsOver1Lsb / ${n - 1} steps (${jumpsOver1Lsb * 100f / (n - 1).toFloat()}%), maxJump=$maxJump LSB, avgStep=$avgJump LSB")
        out.println("Sample jump locations (>1 LSB):")
        for (loc in jumpLocations) {
            out.println("  $loc")
        }
        out.println()
    }

    private fun runItem5(out: PrintWriter) {
        out.println("--------------------------------------------------------------------------------")
        out.println("ITEM 5: Dither Adequacy Analysis")
        out.println("--------------------------------------------------------------------------------")
        out.println("CPU Bayer 8x8 Dither Amplitude:")
        out.println("  Formula: (index - 31.5) / 64 -> range [-31.5/64, +31.5/64] LSB = [-0.4921875, +0.4921875] LSB")
        out.println("  Maximum span: < 1.0 LSB total span (+/- 0.492 LSB).")
        out.println("  Mathematical limit: When input step causes underlying output channel change >= 2.0 LSB,")
        out.println("  the rounding threshold cannot be dither-modulated into the intermediate range,")
        out.println("  leaving an unmitigated sharp boundary.")
        out.println()
        out.println("AGSL Shader Dither Amplitude:")
        out.println("  Formula: u_dithering * 0.5 where u_dithering = 0.0118")
        out.println("  In 8-bit LSB units: 0.0118 * 0.5 * 255 = 1.5045 LSB peak")
        out.println("  Effective triangular dither span: +/- 1.50 LSB.")
        out.println("  Evaluation against measured jumps:")
        out.println("  For jumps of 2 LSB, 1.5 LSB dither partially blends the boundary.")
        out.println("  For jumps >= 3 LSB, 1.5 LSB dither leaves visible contour edges.")
        out.println()
    }

    private fun runItem6(out: PrintWriter) {
        out.println("--------------------------------------------------------------------------------")
        out.println("ITEM 6: Real Cover Art Analysis")
        out.println("--------------------------------------------------------------------------------")

        val covers = listOf(
            Pair("The Highlights (The Weeknd)", "highlights.jpg"),
            Pair("My Dear Melancholy (The Weeknd)", "melancholy.jpg"),
            Pair("Random Access Memories (Daft Punk - Control Blue)", "control_blue.jpg")
        )

        for ((title, filename) in covers) {
            val img = loadCover(filename)
            val w = img.width
            val h = img.height
            val pixels = img.pixels

            out.println("================================================================================")
            out.println("Cover: $title ($filename) - Dimensions: ${w}x${h}")
            out.println("================================================================================")

            var totalPixels = 0
            var pinnedCount99 = 0
            var pinnedCount98 = 0
            var pinnedCount95 = 0
            var sumInputChroma = 0.0
            var sumOutputChroma = 0.0
            var sumCap = 0.0
            var sumPctCap = 0.0

            for (color in pixels) {
                val lab = colorToOklab(color)
                if (lab.chroma < 0.001f) continue
                totalPixels++
                val hueA = lab.a / lab.chroma
                val hueB = lab.b / lab.chroma
                val ceiling = AuroraRenderer.maxChromaAt(lab.l, hueA, hueB)
                val cap = ceiling * 0.98f

                val outColor = AuroraRenderer.boostChromaColor(color, 4.5f)
                val outLab = colorToOklab(outColor)
                val Cout = outLab.chroma
                val pct = if (cap > 1e-6f) (Cout / cap) * 100f else 100f

                sumInputChroma += lab.chroma
                sumOutputChroma += Cout
                sumCap += cap
                sumPctCap += pct

                if (pct >= 99.0f) pinnedCount99++
                if (pct >= 98.0f) pinnedCount98++
                if (pct >= 95.0f) pinnedCount95++
            }

            out.println(String.format("Global Chroma Pinning: Total non-grey pixels = %d", totalPixels))
            out.println(String.format("  Mean Cin: %.4f | Mean Cout: %.4f | Mean Cap: %.4f",
                sumInputChroma / totalPixels, sumOutputChroma / totalPixels, sumCap / totalPixels))
            out.println(String.format("  Mean %% of Cap: %.2f%%", sumPctCap / totalPixels))
            out.println(String.format("  Pixels >= 99%% of Cap: %d (%.2f%%)", pinnedCount99, pinnedCount99 * 100f / totalPixels.toFloat()))
            out.println(String.format("  Pixels >= 98%% of Cap: %d (%.2f%%)", pinnedCount98, pinnedCount98 * 100f / totalPixels.toFloat()))
            out.println(String.format("  Pixels >= 95%% of Cap: %d (%.2f%%)", pinnedCount95, pinnedCount95 * 100f / totalPixels.toFloat()))
            out.println()

            // Smooth background scanlines
            // For melancholy: upper background Y=40, Y=80, left edge X=40, X=80
            // For control_blue: upper background Y=40, Y=80, left edge X=40, X=80
            // For highlights: portrait shadow Y=200, Y=300, vertical X=160, X=200
            val testY = listOf(40, 80, 120, 160, 200, 300, 400, 500)
            val testX = listOf(40, 80, 120, 160, 200, 300, 400, 500)

            out.println("Scanline Analysis (Horizontal Y scanlines, width=640):")
            out.println(String.format("%-6s | %-12s | %-12s | %-12s | %-10s | %-10s | %-10s",
                "Y", "In Distinct", "Boost Dist", "Cap Dist", ">1 LSB Jump", "Max Jump", "Pinned>=99%"))
            out.println("-------+--------------+--------------+--------------+------------+------------+-----------")

            for (y in testY) {
                val row = IntArray(w) { x -> pixels[y * w + x] }
                val rowBoost = IntArray(w) { x -> AuroraRenderer.boostChromaColor(row[x], 4.5f) }
                val rowCap = IntArray(w) { x -> AuroraRenderer.capLightnessColor(rowBoost[x]) }

                val inDistinct = row.distinct().size
                val boostDistinct = rowBoost.distinct().size
                val capDistinct = rowCap.distinct().size

                var jumpsOver1 = 0
                var maxJump = 0
                var pinnedCount = 0

                for (x in 0 until w) {
                    val lab = colorToOklab(row[x])
                    if (lab.chroma >= 0.001f) {
                        val ceiling = AuroraRenderer.maxChromaAt(lab.l, lab.a / lab.chroma, lab.b / lab.chroma)
                        val cap = ceiling * 0.98f
                        val outLab = colorToOklab(rowBoost[x])
                        if (cap > 1e-6f && (outLab.chroma / cap) >= 0.99f) pinnedCount++
                    }
                    if (x < w - 1) {
                        val c1 = rowCap[x]
                        val c2 = rowCap[x + 1]
                        val dr = Math.abs(((c2 shr 16) and 0xFF) - ((c1 shr 16) and 0xFF))
                        val dg = Math.abs(((c2 shr 8) and 0xFF) - ((c1 shr 8) and 0xFF))
                        val db = Math.abs((c2 and 0xFF) - (c1 and 0xFF))
                        val d = maxOf(dr, dg, db)
                        if (d > maxJump) maxJump = d
                        if (d > 1) jumpsOver1++
                    }
                }

                out.println(String.format("%-6d | %-12d | %-12d | %-12d | %-10d | %-10d | %-9.1f%%",
                    y, inDistinct, boostDistinct, capDistinct, jumpsOver1, maxJump, pinnedCount * 100f / w.toFloat()))
            }
            out.println()

            out.println("Scanline Analysis (Vertical X scanlines, height=640):")
            out.println(String.format("%-6s | %-12s | %-12s | %-12s | %-10s | %-10s | %-10s",
                "X", "In Distinct", "Boost Dist", "Cap Dist", ">1 LSB Jump", "Max Jump", "Pinned>=99%"))
            out.println("-------+--------------+--------------+--------------+------------+------------+-----------")

            for (x in testX) {
                val col = IntArray(h) { y -> pixels[y * w + x] }
                val colBoost = IntArray(h) { y -> AuroraRenderer.boostChromaColor(col[y], 4.5f) }
                val colCap = IntArray(h) { y -> AuroraRenderer.capLightnessColor(colBoost[y]) }

                val inDistinct = col.distinct().size
                val boostDistinct = colBoost.distinct().size
                val capDistinct = colCap.distinct().size

                var jumpsOver1 = 0
                var maxJump = 0
                var pinnedCount = 0

                for (y in 0 until h) {
                    val lab = colorToOklab(col[y])
                    if (lab.chroma >= 0.001f) {
                        val ceiling = AuroraRenderer.maxChromaAt(lab.l, lab.a / lab.chroma, lab.b / lab.chroma)
                        val cap = ceiling * 0.98f
                        val outLab = colorToOklab(colBoost[y])
                        if (cap > 1e-6f && (outLab.chroma / cap) >= 0.99f) pinnedCount++
                    }
                    if (y < h - 1) {
                        val c1 = colCap[y]
                        val c2 = colCap[y + 1]
                        val dr = Math.abs(((c2 shr 16) and 0xFF) - ((c1 shr 16) and 0xFF))
                        val dg = Math.abs(((c2 shr 8) and 0xFF) - ((c1 shr 8) and 0xFF))
                        val db = Math.abs((c2 and 0xFF) - (c1 and 0xFF))
                        val d = maxOf(dr, dg, db)
                        if (d > maxJump) maxJump = d
                        if (d > 1) jumpsOver1++
                    }
                }

                out.println(String.format("%-6d | %-12d | %-12d | %-12d | %-10d | %-10d | %-9.1f%%",
                    x, inDistinct, boostDistinct, capDistinct, jumpsOver1, maxJump, pinnedCount * 100f / h.toFloat()))
            }
            out.println()
        }
    }

    private fun runItem7(out: PrintWriter) {
        out.println("--------------------------------------------------------------------------------")
        out.println("ITEM 7: Counterfactual boosts (boost = 4.5, 3.0, 2.0 and Gamut-relative)")
        out.println("--------------------------------------------------------------------------------")

        val nSteps = 256
        val redGradient = IntArray(nSteps) { i ->
            val t = i.toFloat() / (nSteps - 1).toFloat()
            val r = Math.round(40 + (154 - 40) * t).coerceIn(0, 255)
            val g = Math.round(18 + (70 - 18) * t).coerceIn(0, 255)
            val b = Math.round(10 + (32 - 10) * t).coerceIn(0, 255)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val blueGradient = IntArray(nSteps) { i ->
            val t = i.toFloat() / (nSteps - 1).toFloat()
            val r = Math.round(10 + (32 - 10) * t).coerceIn(0, 255)
            val g = Math.round(24 + (70 - 24) * t).coerceIn(0, 255)
            val b = Math.round(40 + (154 - 40) * t).coerceIn(0, 255)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        val redOklabRamp = IntArray(nSteps) { i ->
            val t = i.toFloat() / (nSteps - 1).toFloat()
            val L = 0.22f + (0.55f - 0.22f) * t
            val C = 0.05f + (0.12f - 0.05f) * t
            val rad = Math.toRadians(35.0)
            oklabToColor(L, C * cos(rad).toFloat(), C * sin(rad).toFloat())
        }
        val blueOklabRamp = IntArray(nSteps) { i ->
            val t = i.toFloat() / (nSteps - 1).toFloat()
            val L = 0.22f + (0.55f - 0.22f) * t
            val C = 0.05f + (0.12f - 0.05f) * t
            val rad = Math.toRadians(265.0)
            oklabToColor(L, C * cos(rad).toFloat(), C * sin(rad).toFloat())
        }

        // Real cover smooth scanlines (Y=80)
        val melancholyImg = loadCover("melancholy.jpg")
        val melancholyY80 = IntArray(640) { x -> melancholyImg.pixels[80 * 640 + x] }

        val controlBlueImg = loadCover("control_blue.jpg")
        val controlBlueY80 = IntArray(640) { x -> controlBlueImg.pixels[80 * 640 + x] }

        val rules = listOf(
            Pair("Boost 4.5 (Shipped)", { c: Int -> AuroraRenderer.boostChromaColor(c, 4.5f) }),
            Pair("Boost 3.0", { c: Int -> AuroraRenderer.boostChromaColor(c, 3.0f) }),
            Pair("Boost 2.0", { c: Int -> AuroraRenderer.boostChromaColor(c, 2.0f) }),
            Pair("Gamut-Relative Power (Cin/Ceiling^0.6 * cap)", { c: Int -> boostGamutRelative(c) }),
            Pair("Gamut-Relative Headroom (Cin + 0.5*(cap - Cin))", { c: Int -> boostGamutHeadroom(c) })
        )

        for ((name, fn) in rules) {
            out.println("### Rule: $name")
            evalRuleOnRamp("Reddish Brown RGB Ramp", redGradient, fn, out)
            evalRuleOnRamp("Blue Control RGB Ramp", blueGradient, fn, out)
            evalRuleOnRamp("Reddish Brown OkLab Ramp", redOklabRamp, fn, out)
            evalRuleOnRamp("Blue Control OkLab Ramp", blueOklabRamp, fn, out)
            evalRuleOnRamp("Melancholy Scanline Y=80", melancholyY80, fn, out)
            evalRuleOnRamp("Control Blue Scanline Y=80", controlBlueY80, fn, out)
            out.println()
        }
    }

    private fun boostGamutRelative(color: Int): Int {
        val lab = colorToOklab(color)
        if (lab.chroma < 0.001f) return color
        val hueA = lab.a / lab.chroma
        val hueB = lab.b / lab.chroma
        val ceiling = AuroraRenderer.maxChromaAt(lab.l, hueA, hueB)
        val cap = ceiling * 0.98f
        val ratio = (lab.chroma / ceiling).coerceIn(0f, 1f)
        val targetRatio = Math.pow(ratio.toDouble(), 0.6).toFloat()
        val targetChroma = targetRatio * cap

        return oklabToColor(lab.l, targetChroma * hueA, targetChroma * hueB)
    }

    private fun boostGamutHeadroom(color: Int): Int {
        val lab = colorToOklab(color)
        if (lab.chroma < 0.001f) return color
        val hueA = lab.a / lab.chroma
        val hueB = lab.b / lab.chroma
        val ceiling = AuroraRenderer.maxChromaAt(lab.l, hueA, hueB)
        val cap = ceiling * 0.98f
        // Preserves derivative: adds a fraction of available headroom without saturating
        val targetChroma = lab.chroma + 0.5f * (cap - lab.chroma).coerceAtLeast(0f)

        return oklabToColor(lab.l, targetChroma * hueA, targetChroma * hueB)
    }

    private fun evalRuleOnRamp(rampName: String, ramp: IntArray, fn: (Int) -> Int, out: PrintWriter) {
        val n = ramp.size
        val afterBoost = IntArray(n) { i -> fn(ramp[i]) }
        val afterCap = IntArray(n) { i -> AuroraRenderer.capLightnessColor(afterBoost[i]) }

        val distinctBoost = afterBoost.distinct().size
        val distinctCap = afterCap.distinct().size

        var jumpsOver1 = 0
        var maxJump = 0

        for (i in 0 until n - 1) {
            val c1 = afterCap[i]
            val c2 = afterCap[i + 1]
            val dr = Math.abs(((c2 shr 16) and 0xFF) - ((c1 shr 16) and 0xFF))
            val dg = Math.abs(((c2 shr 8) and 0xFF) - ((c1 shr 8) and 0xFF))
            val db = Math.abs((c2 and 0xFF) - (c1 and 0xFF))
            val d = maxOf(dr, dg, db)
            if (d > maxJump) maxJump = d
            if (d > 1) jumpsOver1++
        }

        out.println(String.format("  %-15s -> Distinct Boost: %3d | Distinct Cap: %3d | Jumps >1 LSB: %3d | Max Jump: %2d LSB",
            rampName, distinctBoost, distinctCap, jumpsOver1, maxJump))
    }

    private fun getHueFamily(deg: Float): String {
        return when (deg) {
            in 345f..360f, in 0f..20f -> "Red"
            in 20f..50f -> "Reddish Brown"
            in 50f..80f -> "Orange/Amber"
            in 80f..115f -> "Yellow"
            in 115f..165f -> "Green"
            in 165f..215f -> "Cyan"
            in 215f..285f -> "Blue"
            in 285f..320f -> "Violet/Purple"
            else -> "Magenta"
        }
    }

    private fun applyOldKnee(scaled: Float, cap: Float): Float {
        val knee = cap * 0.70f
        val range = cap - knee
        return if (cap <= 1e-6f || range <= 0f || scaled <= knee) {
            Math.min(scaled, cap)
        } else {
            val unclipped = knee + range * (1f - Math.exp(-((scaled - knee) / range).toDouble()).toFloat())
            Math.min(unclipped, cap)
        }
    }

    private fun oldPipelineBoostColor(color: Int): Int {
        val alpha = color and 0xFF000000.toInt()
        val rByte = (color shr 16) and 0xFF
        val gByte = (color shr 8) and 0xFF
        val bByte = color and 0xFF

        val rLin = srgbToLinearTable[rByte]
        val gLin = srgbToLinearTable[gByte]
        val bLin = srgbToLinearTable[bByte]

        val l = 0.4122214708f * rLin + 0.5363325363f * gLin + 0.0514459929f * bLin
        val m = 0.2119034982f * rLin + 0.6806995451f * gLin + 0.1073969566f * bLin
        val s = 0.0883024619f * rLin + 0.2817188376f * gLin + 0.6299787005f * bLin

        val l_ = Math.cbrt(l.toDouble()).toFloat()
        val m_ = Math.cbrt(m.toDouble()).toFloat()
        val s_ = Math.cbrt(s.toDouble()).toFloat()

        val L = 0.2104542553f * l_ + 0.7936177850f * m_ - 0.0040720468f * s_
        val a = 1.9779984951f * l_ - 2.4285922050f * m_ + 0.4505937099f * s_
        val b = 0.0259040371f * l_ + 0.7827717662f * m_ - 0.8086757660f * s_

        val chroma = hypot(a, b)
        if (chroma < 0.001f) return color

        val hueA = a / chroma
        val hueB = b / chroma

        val ceilingAtSource = AuroraRenderer.maxChromaAt(L, hueA, hueB)
        val depthLightness: Float
        val ceiling: Float
        if (ceilingAtSource < 1e-6f) {
            depthLightness = L
            ceiling = ceilingAtSource
        } else {
            val sourceRatio = chroma / ceilingAtSource
            val chromaFloorGate = ((chroma - 0.010f) / (0.030f - 0.010f)).coerceIn(0f, 1f)
            val smoothChromaFloor = chromaFloorGate * chromaFloorGate * (3f - 2f * chromaFloorGate)
            val gateRatio = ((sourceRatio - 0.55f) / (0.85f - 0.55f)).coerceIn(0f, 1f)
            val smoothGate = gateRatio * gateRatio * (3f - 2f * gateRatio)
            val depthGate = smoothGate * smoothChromaFloor
            depthLightness = L * (1f - 0.23f * depthGate)
            ceiling = if (depthGate == 0f) ceilingAtSource else AuroraRenderer.maxChromaAt(depthLightness, hueA, hueB)
        }

        val cap = ceiling * 0.98f
        val scaled = chroma * 4.5f
        val finalChroma = applyOldKnee(scaled, cap)

        val finalA = finalChroma * hueA
        val finalB = finalChroma * hueB

        val finalL_ = depthLightness + 0.3963377774f * finalA + 0.2158037573f * finalB
        val finalM_ = depthLightness - 0.1055613458f * finalA - 0.0638541728f * finalB
        val finalS_ = depthLightness - 0.0894841775f * finalA - 1.2914855480f * finalB

        val finalL = finalL_ * finalL_ * finalL_
        val finalM = finalM_ * finalM_ * finalM_
        val finalS = finalS_ * finalS_ * finalS_

        val rOutLin = +4.0767416621f * finalL - 3.3077115913f * finalM + 0.2309699292f * finalS
        val gOutLin = -1.2684380046f * finalL + 2.6097574011f * finalM - 0.3413193965f * finalS
        val bOutLin = -0.0041960863f * finalL - 0.7034186147f * finalM + 1.7076147010f * finalS

        val outR = Math.round(linearToSrgb(rOutLin) * 255f).coerceIn(0, 255)
        val outG = Math.round(linearToSrgb(gOutLin) * 255f).coerceIn(0, 255)
        val outB = Math.round(linearToSrgb(bOutLin) * 255f).coerceIn(0, 255)

        return alpha or (outR shl 16) or (outG shl 8) or outB
    }

    @Test
    fun testBandingImprovementAndSaturationPreservation() {
        val nSteps = 256
        val redOklabRamp = IntArray(nSteps) { i ->
            val t = i.toFloat() / (nSteps - 1).toFloat()
            val L = 0.22f + (0.45f - 0.22f) * t
            val C = 0.03f + (0.10f - 0.03f) * t
            val rad = Math.toRadians(35.0)
            oklabToColor(L, C * cos(rad).toFloat(), C * sin(rad).toFloat())
        }

        val oldOutputs = IntArray(nSteps) { i -> oldPipelineBoostColor(redOklabRamp[i]) }
        val newOutputs = IntArray(nSteps) { i -> AuroraRenderer.boostChromaColor(redOklabRamp[i], AuroraRenderer.DEFAULT_CHROMA_EXPONENT) }

        var oldJumpsOver1 = 0
        var oldMaxJump = 0
        var newJumpsOver1 = 0
        var newMaxJump = 0

        for (i in 0 until nSteps - 1) {
            val c1Old = oldOutputs[i]
            val c2Old = oldOutputs[i + 1]
            val dOld = maxOf(
                abs(((c2Old shr 16) and 0xFF) - ((c1Old shr 16) and 0xFF)),
                abs(((c2Old shr 8) and 0xFF) - ((c1Old shr 8) and 0xFF)),
                abs((c2Old and 0xFF) - (c1Old and 0xFF))
            )
            if (dOld > oldMaxJump) oldMaxJump = dOld
            if (dOld > 1) oldJumpsOver1++

            val c1New = newOutputs[i]
            val c2New = newOutputs[i + 1]
            val dNew = maxOf(
                abs(((c2New shr 16) and 0xFF) - ((c1New shr 16) and 0xFF)),
                abs(((c2New shr 8) and 0xFF) - ((c1New shr 8) and 0xFF)),
                abs((c2New and 0xFF) - (c1New and 0xFF))
            )
            if (dNew > newMaxJump) newMaxJump = dNew
            if (dNew > 1) newJumpsOver1++
        }

        assertTrue("New jumps above 1 LSB ($newJumpsOver1) must be fewer than old pipeline ($oldJumpsOver1)", newJumpsOver1 < oldJumpsOver1)
        assertTrue("New max jump ($newMaxJump) must be no worse than old max jump ($oldMaxJump)", newMaxJump <= oldMaxJump)

        val coverNames = listOf("melancholy.jpg", "highlights.jpg", "late_registration.jpg", "control_blue.jpg")
        for (coverName in coverNames) {
            val img = loadCover(coverName)
            var sumOldChroma = 0.0
            var sumNewChroma = 0.0
            val pixels = img.pixels
            for (color in pixels) {
                val oldOut = oldPipelineBoostColor(color)
                val newOut = AuroraRenderer.boostChromaColor(color, AuroraRenderer.DEFAULT_CHROMA_EXPONENT)
                sumOldChroma += colorToOklab(oldOut).chroma
                sumNewChroma += colorToOklab(newOut).chroma
            }
            val meanOld = (sumOldChroma / pixels.size).toFloat()
            val meanNew = (sumNewChroma / pixels.size).toFloat()
            val diffPercent = abs(meanNew - meanOld) / meanOld
            assertTrue(
                "Cover $coverName mean chroma diff percent ($diffPercent) must be <= 0.20 (old=$meanOld, new=$meanNew)",
                diffPercent <= 0.20f
            )
        }
    }

    private fun collapsedCeilingBoostColor(color: Int): Int {
        val alpha = color and 0xFF000000.toInt()
        val rByte = (color shr 16) and 0xFF
        val gByte = (color shr 8) and 0xFF
        val bByte = color and 0xFF

        val rLin = srgbToLinearTable[rByte]
        val gLin = srgbToLinearTable[gByte]
        val bLin = srgbToLinearTable[bByte]

        val l = 0.4122214708f * rLin + 0.5363325363f * gLin + 0.0514459929f * bLin
        val m = 0.2119034982f * rLin + 0.6806995451f * gLin + 0.1073969566f * bLin
        val s = 0.0883024619f * rLin + 0.2817188376f * gLin + 0.6299787005f * bLin

        val l_ = Math.cbrt(l.toDouble()).toFloat()
        val m_ = Math.cbrt(m.toDouble()).toFloat()
        val s_ = Math.cbrt(s.toDouble()).toFloat()

        val L = 0.2104542553f * l_ + 0.7936177850f * m_ - 0.0040720468f * s_
        val a = 1.9779984951f * l_ - 2.4285922050f * m_ + 0.4505937099f * s_
        val b = 0.0259040371f * l_ + 0.7827717662f * m_ - 0.8086757660f * s_

        val chroma = hypot(a, b)
        if (chroma < 0.001f) return color

        val hueA = a / chroma
        val hueB = b / chroma

        val ceilingAtSource = AuroraRenderer.maxChromaAt(L, hueA, hueB)
        if (ceilingAtSource < 1e-6f) return color

        val sourceRatio = (chroma / ceilingAtSource).coerceIn(0f, 1f)
        val chromaFloorGate = ((chroma - 0.010f) / (0.030f - 0.010f)).coerceIn(0f, 1f)
        val smoothChromaFloor = chromaFloorGate * chromaFloorGate * (3f - 2f * chromaFloorGate)
        val gateRatio = ((sourceRatio - 0.55f) / (0.85f - 0.55f)).coerceIn(0f, 1f)
        val smoothGate = gateRatio * gateRatio * (3f - 2f * gateRatio)
        val depthGate = smoothGate * smoothChromaFloor
        val depthLightness = L * (1f - 0.23f * depthGate)

        // Pathological collapsed ceiling: re-evaluating at darkened depthLightness
        val ceiling = if (depthGate == 0f) ceilingAtSource else AuroraRenderer.maxChromaAt(depthLightness, hueA, hueB)
        val finalChroma = AuroraRenderer.gamutRelativeChroma(chroma, ceiling, AuroraRenderer.DEFAULT_CHROMA_EXPONENT)

        val finalA = finalChroma * hueA
        val finalB = finalChroma * hueB

        val finalL_ = depthLightness + 0.3963377774f * finalA + 0.2158037573f * finalB
        val finalM_ = depthLightness - 0.1055613458f * finalA - 0.0638541728f * finalB
        val finalS_ = depthLightness - 0.0894841775f * finalA - 1.2914855480f * finalB

        val finalL = finalL_ * finalL_ * finalL_
        val finalM = finalM_ * finalM_ * finalM_
        val finalS = finalS_ * finalS_ * finalS_

        val rOutLin = +4.0767416621f * finalL - 3.3077115913f * finalM + 0.2309699292f * finalS
        val gOutLin = -1.2684380046f * finalL + 2.6097574011f * finalM - 0.3413193965f * finalS
        val bOutLin = -0.0041960863f * finalL - 0.7034186147f * finalM + 1.7076147010f * finalS

        val outR = Math.round(linearToSrgb(rOutLin) * 255f).coerceIn(0, 255)
        val outG = Math.round(linearToSrgb(gOutLin) * 255f).coerceIn(0, 255)
        val outB = Math.round(linearToSrgb(bOutLin) * 255f).coerceIn(0, 255)

        return alpha or (outR shl 16) or (outG shl 8) or outB
    }

    @Test
    fun testBandingPreservedAndDepthRestoresSaturationOnReds() {
        val nSteps = 256
        val redOklabRamp = IntArray(nSteps) { i ->
            val t = i.toFloat() / (nSteps - 1).toFloat()
            val L = 0.22f + (0.45f - 0.22f) * t
            val C = 0.03f + (0.10f - 0.03f) * t
            val rad = Math.toRadians(35.0)
            oklabToColor(L, C * cos(rad).toFloat(), C * sin(rad).toFloat())
        }

        val oldOutputs = IntArray(nSteps) { i -> oldPipelineBoostColor(redOklabRamp[i]) }
        val newOutputs = IntArray(nSteps) { i -> AuroraRenderer.boostChromaColor(redOklabRamp[i]) }

        var oldJumpsOver1 = 0
        var oldMaxJump = 0
        var newJumpsOver1 = 0
        var newMaxJump = 0

        for (i in 0 until nSteps - 1) {
            val c1Old = oldOutputs[i]
            val c2Old = oldOutputs[i + 1]
            val dOld = maxOf(
                abs(((c2Old shr 16) and 0xFF) - ((c1Old shr 16) and 0xFF)),
                abs(((c2Old shr 8) and 0xFF) - ((c1Old shr 8) and 0xFF)),
                abs((c2Old and 0xFF) - (c1Old and 0xFF))
            )
            if (dOld > oldMaxJump) oldMaxJump = dOld
            if (dOld > 1) oldJumpsOver1++

            val c1New = newOutputs[i]
            val c2New = newOutputs[i + 1]
            val dNew = maxOf(
                abs(((c2New shr 16) and 0xFF) - ((c1New shr 16) and 0xFF)),
                abs(((c2New shr 8) and 0xFF) - ((c1New shr 8) and 0xFF)),
                abs((c2New and 0xFF) - (c1New and 0xFF))
            )
            if (dNew > newMaxJump) newMaxJump = dNew
            if (dNew > 1) newJumpsOver1++
        }

        assertTrue("Ramp jumps above 1 LSB must be <= 43 with both fixes at defaults (got $newJumpsOver1)", newJumpsOver1 <= 43)
        assertTrue("Max ramp jump must be <= 3 with both fixes at defaults (got $newMaxJump)", newMaxJump <= 3)
        assertTrue("Ramp jumps above 1 LSB ($newJumpsOver1) must be significantly lower than old pipeline ($oldJumpsOver1)", newJumpsOver1 < oldJumpsOver1)

        val melancholy = loadCover("melancholy.jpg")
        val highlights = loadCover("highlights.jpg")
        val lateReg = loadCover("late_registration.jpg")
        val controlBlue = loadCover("control_blue.jpg")

        fun meanChroma(img: LoadedImage, boostFn: (Int) -> Int): Float {
            var sum = 0.0
            for (color in img.pixels) {
                val outColor = boostFn(color)
                sum += colorToOklab(outColor).chroma
            }
            return (sum / img.pixels.size).toFloat()
        }

        val melancholyNew = meanChroma(melancholy) { AuroraRenderer.boostChromaColor(it) }
        val melancholyCollapsed = meanChroma(melancholy) { collapsedCeilingBoostColor(it) }
        val melancholyRise = (melancholyNew - melancholyCollapsed) / melancholyCollapsed

        val highlightsNew = meanChroma(highlights) { AuroraRenderer.boostChromaColor(it) }
        val highlightsCollapsed = meanChroma(highlights) { collapsedCeilingBoostColor(it) }
        val highlightsRise = (highlightsNew - highlightsCollapsed) / highlightsCollapsed

        val lateRegNew = meanChroma(lateReg) { AuroraRenderer.boostChromaColor(it) }
        val lateRegCollapsed = meanChroma(lateReg) { collapsedCeilingBoostColor(it) }
        val lateRegRise = (lateRegNew - lateRegCollapsed) / lateRegCollapsed

        val blueNew = meanChroma(controlBlue) { AuroraRenderer.boostChromaColor(it) }
        val blueCollapsed = meanChroma(controlBlue) { collapsedCeilingBoostColor(it) }
        val blueDiff = abs(blueNew - blueCollapsed) / blueCollapsed

        // Assert saturation rises on Weeknd fixtures relative to collapsed ceiling
        assertTrue("Melancholy chroma rise ($melancholyRise) must be around +8.2% (> 0.06)", melancholyRise > 0.06f)
        assertTrue("Highlights chroma rise ($highlightsRise) must be around +7.8% (> 0.06)", highlightsRise > 0.06f)
        assertTrue("Late Registration chroma rise ($lateRegRise) must be around +4.4% (> 0.03)", lateRegRise > 0.03f)

        // Assert blue control moves by less than 2%
        assertTrue("Blue control move ($blueDiff) must be < 0.02 (less than 2%)", blueDiff < 0.02f)
    }
}
