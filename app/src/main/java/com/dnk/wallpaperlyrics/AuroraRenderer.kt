package com.dnk.wallpaperlyrics

import android.graphics.*
import android.os.Build
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.math.hypot
import kotlin.math.min
import androidx.palette.graphics.Palette

/**
 * Handles all aurora background rendering, color extraction, and image preprocessing.
 * All state is passed in from the engine so nothing here is mutable or shared.
 */
object AuroraRenderer {

    const val BACKGROUND_WORK_RESOLUTION = 512
    const val BACKGROUND_CHROMA_BOOST = 4.5f
    private const val BACKGROUND_DEPTH = 0.23f
    private const val DEPTH_GATE_LOW = 0.55f
    private const val DEPTH_GATE_HIGH = 0.85f
    private const val DEPTH_CHROMA_FLOOR_LOW = 0.010f
    private const val DEPTH_CHROMA_FLOOR_HIGH = 0.030f
    private const val CHROMA_KNEE = 0.70f
    private const val LIGHTNESS_CAP_KNEE = 0.62f
    private const val LIGHTNESS_CAP_CEILING = 0.76f
    private const val LIGHTNESS_CAP_STRENGTH = 0.5f

    fun drawAurora(
        canvas: Canvas,
        runtimeShader: android.graphics.RuntimeShader?,
        shaderPaint: Paint,
        currentBgArt: Bitmap?,
        nextBgArt: Bitmap?,
        blendProgress: Float,
        isTransitioning: Boolean,
        accumulatedTime: Float,
        nextAccumulatedTime: Float,
        currentSeedX: Float,
        currentSeedY: Float,
        nextSeedX: Float,
        nextSeedY: Float,
        currentColors: IntArray,
        auroraPaints: List<Paint>,
        staticBg: Boolean = false
    ) {
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && runtimeShader != null
            && currentBgArt != null
            && !currentBgArt.isRecycled
        ) {
            val shader = runtimeShader
            val currentBmp = currentBgArt
            val currentShader = BitmapShader(currentBmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            currentShader.setFilterMode(BitmapShader.FILTER_MODE_LINEAR)

            val scaleX = width / currentBmp.width
            val scaleY = height / currentBmp.height
            val scale = Math.max(scaleX, scaleY) * 1.3f
            val dx = (width - currentBmp.width * scale) / 2f
            val dy = (height - currentBmp.height * scale) / 2f

            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(dx, dy)
            }
            currentShader.setLocalMatrix(matrix)
            shader.setInputShader("u_texture", currentShader)

            val nextBmp = nextBgArt
            if (nextBmp != null && !nextBmp.isRecycled) {
                val nextShader = BitmapShader(nextBmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                nextShader.setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
                val nextMatrix = Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(dx, dy)
                }
                nextShader.setLocalMatrix(nextMatrix)
                shader.setInputShader("u_texture_next", nextShader)
            } else {
                shader.setInputShader("u_texture_next", currentShader)
            }

            shader.setFloatUniform("u_tex_scale", scale)
            shader.setFloatUniform("u_tex_offset", dx, dy)
            shader.setFloatUniform("u_blend", blendProgress)
            shader.setFloatUniform("u_resolution", width, height)
            shader.setFloatUniform("u_time", accumulatedTime)
            shader.setFloatUniform("u_time_next", if (isTransitioning) nextAccumulatedTime else accumulatedTime)
            shader.setFloatUniform("u_seed", currentSeedX, currentSeedY)
            shader.setFloatUniform("u_seed_next", if (isTransitioning) nextSeedX else currentSeedX, if (isTransitioning) nextSeedY else currentSeedY)
            shader.setFloatUniform("u_intensity", 1.0f)
            // Dither is applied as the final operation immediately before 8-bit quantization with no
            // downstream gain stages. Per-channel triangular noise over (-1, 1) scaled by 0.5 with
            // u_dithering = 0.0118f gives 0.0118 * 0.5 * 255 = 1.5 LSB peak amplitude, breaking shallow
            // gradient contours across independent color channels.
            shader.setFloatUniform("u_dithering", 0.0118f)
            shader.setFloatUniform("u_scale", 1.0f)
            shader.setFloatUniform("u_static_bg", if (staticBg) 1.0f else 0.0f)

            shaderPaint.shader = shader

            if (android.os.Build.VERSION.SDK_INT >= 31) {
                try {
                    val method = shaderPaint.javaClass.getMethod("setRenderEffect", android.graphics.RenderEffect::class.java)
                    method.invoke(shaderPaint, null)
                } catch (e: Exception) {}
            }

            canvas.drawRect(0f, 0f, width, height, shaderPaint)
        } else {
            // Fallback for API < 33: Spicy Lyrics Mesh Gradient (Radial Blobs)
            // Tint the black base with the palette's base color so dark patches
            // between blobs blend into the scene instead of looking like holes.
            val tintedBase = AuroraRenderer.tintBlack(base = currentColors.getOrElse(1) { 0xFF0A0A0A.toInt() })
            canvas.drawColor(tintedBase)

            val fallbackTime = if (staticBg) 0f else accumulatedTime * 2.4f

            for (index in currentColors.indices) {
                val paint = auroraPaints.getOrNull(index) ?: break
                val t = fallbackTime * 0.12f
                val phase = index * 1.5f
                val x = width * (0.5f + 0.4f * sin(t * (0.6f + index * 0.1f) + phase).toFloat())
                val y = height * (0.5f + 0.4f * cos(t * (0.5f + index * 0.15f) + phase + 1f).toFloat())

                val radius = width * 1.6f

                val color = currentColors[index]
                val transparentColor = Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))

                var innerOffset = 0.15f
                val uvY = y / height
                val blurRegion = 0.22f
                if (uvY < blurRegion) {
                    innerOffset += (blurRegion - uvY) * 0.8f
                } else if (uvY > (1.0f - blurRegion)) {
                    innerOffset += (uvY - (1.0f - blurRegion)) * 0.8f
                }

                val gradient = RadialGradient(
                    x, y, radius,
                    intArrayOf(color, transparentColor),
                    floatArrayOf(innerOffset.coerceAtMost(0.85f), 0.96f),
                    Shader.TileMode.CLAMP
                )

                paint.shader = gradient
                canvas.drawCircle(x, y, radius, paint)
            }
        }

        // Subtle darkening overlay for text legibility
        canvas.drawColor(Color.argb(46, 0, 0, 0))
    }

    fun extractPalette(sourceBitmap: Bitmap): AuroraPalette {
        val scaledBitmap = Bitmap.createScaledBitmap(sourceBitmap, 128, 128, true)

        val w = scaledBitmap.width - 1
        val h = scaledBitmap.height - 1
        val corners = listOf(
            scaledBitmap.getPixel(0, 0),
            scaledBitmap.getPixel(w, 0),
            scaledBitmap.getPixel(0, h),
            scaledBitmap.getPixel(w, h)
        )
        var base = determineConsensusColor(corners) ?: 0xFF0A0A0A.toInt()

        val p = Palette.from(scaledBitmap).generate()
        val swatches = p.swatches
        if (swatches.isEmpty()) return AuroraPalette(base, base, base, base)

        val maxPopulation = swatches.maxOf { it.population }.toFloat()

        val scoredSwatches = swatches.mapNotNull { swatch ->
            val distance = calculateColorDistance(swatch.rgb, base)

            if (distance < 30.0f) return@mapNotNull null

            val popRatio = swatch.population / maxPopulation
            val popWeight = Math.sqrt(popRatio.toDouble()).toFloat()
            val saturation = swatch.hsl[1]

            val score = popWeight * (saturation + 0.3f) * (distance / 441f)

            Pair(swatch, score)
        }.sortedByDescending { it.second }

        var accent = scoredSwatches.getOrNull(0)?.first?.rgb ?: shiftHue(base, 180f)
        var mid = scoredSwatches.getOrNull(1)?.first?.rgb ?: shiftHue(base, 90f)
        var highlight = scoredSwatches.getOrNull(2)?.first?.rgb ?: p.lightVibrantSwatch?.rgb ?: 0xFFFFFFFF.toInt()

        val sortedForeground = listOf(accent, mid, highlight).sortedBy { luminance(it) }

        accent = sortedForeground[0]
        mid = sortedForeground[1]
        highlight = sortedForeground[2]

        if (isMonochromatic(accent, base, mid, highlight)) {
            accent = 0xFF1A1A1A.toInt()
            base = 0xFF0A0A0A.toInt()
            mid = 0xFFEAEAEA.toInt()
            highlight = 0xFFFFFFFF.toInt()
        }

        return AuroraPalette(accent, base, mid, highlight)
    }

    fun interpolateColor(from: Int, to: Int, fraction: Float): Int {
        val a = (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * fraction).toInt()
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * fraction).toInt()
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * fraction).toInt()
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * fraction).toInt()
        return Color.argb(a, r, g, b)
    }

    fun preprocessArt(source: Bitmap, tintColor: Int, tintIntensity: Float): Bitmap {
        val lowRes = Bitmap.createScaledBitmap(source, BACKGROUND_WORK_RESOLUTION, BACKGROUND_WORK_RESOLUTION, true)
        val w = lowRes.width
        val h = lowRes.height
        val pixels = IntArray(w * h)
        lowRes.getPixels(pixels, 0, w, 0, 0, w, h)

        val tintR = Color.red(tintColor) / 255f
        val tintG = Color.green(tintColor) / 255f
        val tintB = Color.blue(tintColor) / 255f

        for (i in pixels.indices) {
            val color = pixels[i]
            val r = ((color shr 16) and 0xff) / 255f
            val g = ((color shr 8) and 0xff) / 255f
            val b = (color and 0xff) / 255f

            val luma = 0.299f * r + 0.587f * g + 0.114f * b
            val x = (luma / 0.5f).coerceIn(0f, 1f)
            val smooth = x * x * (3f - 2f * x)
            val darkMask = 1.0f - smooth

            val factor = darkMask * tintIntensity
            val newR = (r + (tintR - r) * factor).coerceIn(0f, 1f)
            val newG = (g + (tintG - g) * factor).coerceIn(0f, 1f)
            val newB = (b + (tintB - b) * factor).coerceIn(0f, 1f)

            pixels[i] = (0xff000000.toInt() or
                        (Math.round(newR * 255f).coerceIn(0, 255) shl 16) or
                        (Math.round(newG * 255f).coerceIn(0, 255) shl 8) or
                        Math.round(newB * 255f).coerceIn(0, 255))
        }

        val tinted = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        tinted.setPixels(pixels, 0, w, 0, 0, w, h)
        lowRes.recycle()
        return tinted
    }

    fun createIdleMesh(colors: IntArray): Bitmap {
        val w = BACKGROUND_WORK_RESOLUTION
        val h = BACKGROUND_WORK_RESOLUTION
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)

        val c00 = colors.getOrElse(0) { 0xFFFF0055.toInt() }
        val c10 = colors.getOrElse(1) { 0xFF0A0B1A.toInt() }
        val c01 = colors.getOrElse(2) { 0xFF7A22FF.toInt() }
        val c11 = colors.getOrElse(3) { 0xFFD6C7FF.toInt() }

        for (y in 0 until h) {
            val yf = y.toFloat() / (h - 1)
            for (x in 0 until w) {
                val xf = x.toFloat() / (w - 1)

                val r = Math.round(Color.red(c00) * (1f - xf) * (1f - yf) +
                         Color.red(c10) * xf * (1f - yf) +
                         Color.red(c01) * (1f - xf) * yf +
                         Color.red(c11) * xf * yf).coerceIn(0, 255)

                val g = Math.round(Color.green(c00) * (1f - xf) * (1f - yf) +
                         Color.green(c10) * xf * (1f - yf) +
                         Color.green(c01) * (1f - xf) * yf +
                         Color.green(c11) * xf * yf).coerceIn(0, 255)

                val b = Math.round(Color.blue(c00) * (1f - xf) * (1f - yf) +
                         Color.blue(c10) * xf * (1f - yf) +
                         Color.blue(c01) * (1f - xf) * yf +
                         Color.blue(c11) * xf * yf).coerceIn(0, 255)

                pixels[y * w + x] = 0xff000000.toInt() or (r shl 16) or (g shl 8) or b
            }
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap
    }

    fun blurBitmap(sentBitmap: Bitmap, radius: Int): Bitmap {
        val bitmap = sentBitmap.copy(sentBitmap.config, true)
        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int

        val vmin = IntArray(Math.max(w, h))

        // Horizontal pass
        yw = 0
        yi = 0
        for (y in 0 until h) {
            rsum = 0
            gsum = 0
            bsum = 0
            for (i in -radius..radius) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))]
                rsum += (p shr 16) and 0xff
                gsum += (p shr 8) and 0xff
                bsum += p and 0xff
            }
            for (x in 0 until w) {
                r[yi] = (rsum + div / 2) / div
                g[yi] = (gsum + div / 2) / div
                b[yi] = (bsum + div / 2) / div

                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm)
                }
                val p1 = pix[yw + vmin[x]]
                val p2 = pix[yw + Math.max(x - radius, 0)]

                rsum += ((p1 shr 16) and 0xff) - ((p2 shr 16) and 0xff)
                gsum += ((p1 shr 8) and 0xff) - ((p2 shr 8) and 0xff)
                bsum += (p1 and 0xff) - (p2 and 0xff)
                yi++
            }
            yw += w
        }

        // Vertical pass
        for (x in 0 until w) {
            rsum = 0
            gsum = 0
            bsum = 0
            yp = -radius * w
            for (i in -radius..radius) {
                yi = Math.max(0, yp) + x
                rsum += r[yi]
                gsum += g[yi]
                bsum += b[yi]
                yp += w
            }
            yi = x
            for (y in 0 until h) {
                pix[yi] = (0xff000000.toInt() or (((rsum + div / 2) / div) shl 16) or (((gsum + div / 2) / div) shl 8) or ((bsum + div / 2) / div))
                if (x == 0) {
                    vmin[y] = Math.min(y + radius + 1, hm) * w
                }
                val p1 = x + vmin[y]
                val p2 = x + Math.max(0, y - radius) * w

                rsum += r[p1] - r[p2]
                gsum += g[p1] - g[p2]
                bsum += b[p1] - b[p2]
                yi += w
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }

    private fun determineConsensusColor(samples: List<Int>): Int? {
        return samples.groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
    }

    private fun luminance(color: Int): Float {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private fun isMonochromatic(vararg colors: Int): Boolean {
        val hsv = FloatArray(3)
        var totalSaturation = 0f
        for (color in colors) {
            Color.colorToHSV(color, hsv)
            totalSaturation += hsv[1]
        }
        return (totalSaturation / colors.size) < 0.08f
    }

    private fun calculateColorDistance(c1: Int, c2: Int): Float {
        val rDiff = (Color.red(c1) - Color.red(c2)) / 255f
        val gDiff = (Color.green(c1) - Color.green(c2)) / 255f
        val bDiff = (Color.blue(c1) - Color.blue(c2)) / 255f
        return sqrt(rDiff * rDiff + gDiff * gDiff + bDiff * bDiff) * 255f
    }

    private fun shiftHue(color: Int, degrees: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[0] = (hsv[0] + degrees) % 360f
        hsv[1] = hsv[1].coerceAtLeast(0.7f)
        return Color.HSVToColor(hsv)
    }

    /** Tints a black background with a base color so dark patches blend into the scene. */
    fun tintBlack(base: Int): Int {
        val r = (Color.red(base) * 0.15f).toInt().coerceIn(0, 255)
        val g = (Color.green(base) * 0.15f).toInt().coerceIn(0, 255)
        val b = (Color.blue(base) * 0.15f).toInt().coerceIn(0, 255)
        return Color.argb(255, r, g, b)
    }

    // srgbToLinear is only ever fed byteValue / 255f, so 256 entries cover every input it
    // can receive and the table is exact rather than an approximation. The conversion is
    // three of the six pow calls in boostChromaColor, which runs on a quarter of a million
    // pixels per track.
    private val srgbToLinearTable = FloatArray(256) { i ->
        val c = i / 255f
        if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    }

    // Centering the 8x8 Bayer matrix at -31.5 / 64 preserves the mean color across the dither pattern.
    private val BAYER_8X8 = floatArrayOf(
        (0f - 31.5f) / 64f, (32f - 31.5f) / 64f, (8f - 31.5f) / 64f, (40f - 31.5f) / 64f,
        (2f - 31.5f) / 64f, (34f - 31.5f) / 64f, (10f - 31.5f) / 64f, (42f - 31.5f) / 64f,
        (48f - 31.5f) / 64f, (16f - 31.5f) / 64f, (56f - 31.5f) / 64f, (24f - 31.5f) / 64f,
        (50f - 31.5f) / 64f, (18f - 31.5f) / 64f, (58f - 31.5f) / 64f, (26f - 31.5f) / 64f,
        (12f - 31.5f) / 64f, (44f - 31.5f) / 64f, (4f - 31.5f) / 64f, (36f - 31.5f) / 64f,
        (14f - 31.5f) / 64f, (46f - 31.5f) / 64f, (6f - 31.5f) / 64f, (38f - 31.5f) / 64f,
        (60f - 31.5f) / 64f, (28f - 31.5f) / 64f, (52f - 31.5f) / 64f, (20f - 31.5f) / 64f,
        (62f - 31.5f) / 64f, (30f - 31.5f) / 64f, (54f - 31.5f) / 64f, (22f - 31.5f) / 64f,
        (3f - 31.5f) / 64f, (35f - 31.5f) / 64f, (11f - 31.5f) / 64f, (43f - 31.5f) / 64f,
        (1f - 31.5f) / 64f, (33f - 31.5f) / 64f, (9f - 31.5f) / 64f, (41f - 31.5f) / 64f,
        (51f - 31.5f) / 64f, (19f - 31.5f) / 64f, (59f - 31.5f) / 64f, (27f - 31.5f) / 64f,
        (49f - 31.5f) / 64f, (17f - 31.5f) / 64f, (57f - 31.5f) / 64f, (25f - 31.5f) / 64f,
        (15f - 31.5f) / 64f, (47f - 31.5f) / 64f, (7f - 31.5f) / 64f, (39f - 31.5f) / 64f,
        (13f - 31.5f) / 64f, (45f - 31.5f) / 64f, (5f - 31.5f) / 64f, (37f - 31.5f) / 64f,
        (63f - 31.5f) / 64f, (31f - 31.5f) / 64f, (55f - 31.5f) / 64f, (23f - 31.5f) / 64f,
        (61f - 31.5f) / 64f, (29f - 31.5f) / 64f, (53f - 31.5f) / 64f, (21f - 31.5f) / 64f
    )

    fun boostChromaColor(color: Int, boost: Float): Int = boostChromaColor(color, boost, 0f)

    private fun boostChromaColor(color: Int, boost: Float, dither: Float): Int {
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
        // Greys stay grey to prevent rounding noise from blowing out to arbitrary hues.
        if (chroma < 0.001f) {
            return color
        }

        val hueA = a / chroma
        val hueB = b / chroma

        val ceilingAtSource = maxChromaAt(L, hueA, hueB)
        val depthLightness: Float
        val ceiling: Float
        if (ceilingAtSource < 1e-6f) {
            depthLightness = L
            ceiling = ceilingAtSource
        } else {
            val sourceRatio = chroma / ceilingAtSource
            // Near the neutral axis, the gamut ceiling collapses toward zero and sourceRatio
            // can reach full saturation from sub-LSB noise. Gating by absolute chroma prevents
            // darkening near-white and grey pixels.
            val chromaFloorGate = smoothstep(DEPTH_CHROMA_FLOOR_LOW, DEPTH_CHROMA_FLOOR_HIGH, chroma)
            val depthGate = smoothstep(DEPTH_GATE_LOW, DEPTH_GATE_HIGH, sourceRatio) * chromaFloorGate
            depthLightness = L * (1f - BACKGROUND_DEPTH * depthGate)
            ceiling = if (depthGate == 0f) ceilingAtSource else maxChromaAt(depthLightness, hueA, hueB)
        }

        // The 0.98 ceiling matches saturated album art while avoiding gamut boundary clipping.
        val cap = ceiling * 0.98f
        val scaled = chroma * boost
        val finalChroma = rollOffChroma(scaled, cap)

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

        val outR = Math.round(linearToSrgb(rOutLin) * 255f + dither).coerceIn(0, 255)
        val outG = Math.round(linearToSrgb(gOutLin) * 255f + dither).coerceIn(0, 255)
        val outB = Math.round(linearToSrgb(bOutLin) * 255f + dither).coerceIn(0, 255)

        return alpha or (outR shl 16) or (outG shl 8) or outB
    }

    fun boostChroma(target: Bitmap, boost: Float) {
        val w = target.width
        val h = target.height
        val pixels = IntArray(w * h)
        target.getPixels(pixels, 0, w, 0, 0, w, h)
        boostChroma(pixels, w, h, boost)
        target.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    fun boostChroma(pixels: IntArray, width: Int, height: Int, boost: Float) {
        for (y in 0 until height) {
            val rowOffset = y * width
            val bayerRow = (y and 7) shl 3
            for (x in 0 until width) {
                val dither = BAYER_8X8[bayerRow or (x and 7)]
                val index = rowOffset + x
                pixels[index] = boostChromaColor(pixels[index], boost, dither)
            }
        }
    }

    // Caps bright backgrounds so white lyrics stay legible against white covers without clipping contrast in darker scenes.
    // Uses a soft exponential knee above 0.62 to prevent harsh luminance boundaries on gradients.
    fun capLightness(target: Bitmap) {
        val w = target.width
        val h = target.height
        val pixels = IntArray(w * h)
        target.getPixels(pixels, 0, w, 0, 0, w, h)
        capLightness(pixels, w, h)
        target.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    internal fun capLightness(pixels: IntArray, width: Int, height: Int) {
        for (y in 0 until height) {
            val rowOffset = y * width
            val bayerRow = (y and 7) shl 3
            for (x in 0 until width) {
                val dither = BAYER_8X8[bayerRow or (x and 7)]
                val index = rowOffset + x
                pixels[index] = capLightnessColor(pixels[index], dither)
            }
        }
    }

    internal fun capLightnessColor(color: Int): Int = capLightnessColor(color, 0f)

    internal fun capLightnessColor(color: Int, dither: Float): Int {
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
        if (L <= LIGHTNESS_CAP_KNEE) {
            return color
        }

        val a = 1.9779984951f * l_ - 2.4285922050f * m_ + 0.4505937099f * s_
        val b = 0.0259040371f * l_ + 0.7827717662f * m_ - 0.8086757660f * s_

        val range = LIGHTNESS_CAP_CEILING - LIGHTNESS_CAP_KNEE
        val cappedL = LIGHTNESS_CAP_KNEE + range * (1f - Math.exp(-((L - LIGHTNESS_CAP_KNEE) / range).toDouble()).toFloat())
        val blendedL = L - LIGHTNESS_CAP_STRENGTH * (L - cappedL)

        val finalL_ = blendedL + 0.3963377774f * a + 0.2158037573f * b
        val finalM_ = blendedL - 0.1055613458f * a - 0.0638541728f * b
        val finalS_ = blendedL - 0.0894841775f * a - 1.2914855480f * b

        val finalL = finalL_ * finalL_ * finalL_
        val finalM = finalM_ * finalM_ * finalM_
        val finalS = finalS_ * finalS_ * finalS_

        val rOutLin = +4.0767416621f * finalL - 3.3077115913f * finalM + 0.2309699292f * finalS
        val gOutLin = -1.2684380046f * finalL + 2.6097574011f * finalM - 0.3413193965f * finalS
        val bOutLin = -0.0041960863f * finalL - 0.7034186147f * finalM + 1.7076147010f * finalS

        val outR = Math.round(linearToSrgb(rOutLin) * 255f + dither).coerceIn(0, 255)
        val outG = Math.round(linearToSrgb(gOutLin) * 255f + dither).coerceIn(0, 255)
        val outB = Math.round(linearToSrgb(bOutLin) * 255f + dither).coerceIn(0, 255)

        return alpha or (outR shl 16) or (outG shl 8) or outB
    }

    private fun linearToSrgb(c: Float): Float {
        val clamped = c.coerceIn(0f, 1f)
        return if (clamped <= 0.0031308f) {
            12.92f * clamped
        } else {
            1.055f * Math.pow(clamped.toDouble(), 1.0 / 2.4).toFloat() - 0.055f
        }
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    internal fun rollOffChroma(scaled: Float, cap: Float): Float {
        val knee = cap * CHROMA_KNEE
        val range = cap - knee
        return if (cap <= 1e-6f || cap.isNaN() || scaled.isNaN() || range <= 0f || scaled <= knee) {
            min(scaled, cap)
        } else {
            val unclipped = knee + range * (1f - Math.exp(-((scaled - knee) / range).toDouble()).toFloat())
            min(unclipped, cap)
        }
    }

    internal fun maxChromaAt(lightness: Float, hueA: Float, hueB: Float): Float {
        // In sRGB, no realisable colour exceeds chroma 0.45 at any lightness level.
        var lo = 0f
        var hi = 0.45f
        for (i in 0 until 12) {
            val mid = (lo + hi) * 0.5f
            val testA = mid * hueA
            val testB = mid * hueB

            val testL_ = lightness + 0.3963377774f * testA + 0.2158037573f * testB
            val testM_ = lightness - 0.1055613458f * testA - 0.0638541728f * testB
            val testS_ = lightness - 0.0894841775f * testA - 1.2914855480f * testB

            val testL = testL_ * testL_ * testL_
            val testM = testM_ * testM_ * testM_
            val testS = testS_ * testS_ * testS_

            val rTest = +4.0767416621f * testL - 3.3077115913f * testM + 0.2309699292f * testS
            val gTest = -1.2684380046f * testL + 2.6097574011f * testM - 0.3413193965f * testS
            val bTest = -0.0041960863f * testL - 0.7034186147f * testM + 1.7076147010f * testS

            if (rTest in -0.0001f..1.0001f && gTest in -0.0001f..1.0001f && bTest in -0.0001f..1.0001f) {
                lo = mid
            } else {
                hi = mid
            }
        }
        return lo
    }
}
