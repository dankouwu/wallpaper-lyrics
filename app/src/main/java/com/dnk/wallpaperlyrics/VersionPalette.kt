package com.dnk.wallpaperlyrics

import kotlin.math.cos
import kotlin.math.sin

object VersionPalette {

    private val PINNED_VERSIONS: Map<String, IntArray> = mapOf(
        "2.2.0" to intArrayOf(
            0xFF805D93.toInt(),
            0xFFD31277.toInt(),
            0xFF56BD54.toInt(),
            0xFF00DFFF.toInt()
        )
    )

    private val SCHEMES = arrayOf(
        floatArrayOf(0f, 35f, -35f, 70f),
        floatArrayOf(0f, 120f, 240f, 60f),
        floatArrayOf(0f, 150f, 210f, 30f),
        floatArrayOf(0f, 180f, 30f, 210f)
    )

    // Targets derived from the pinned 2.2.0 palette.
    private val TARGET_L = floatArrayOf(0.5355f, 0.5682f, 0.7135f, 0.8300f)
    private val TARGET_C = floatArrayOf(0.0913f, 0.2246f, 0.1731f, 0.1452f)

    fun schemeForVersion(versionName: String): String {
        val trimmed = versionName.trim()
        if (PINNED_VERSIONS.containsKey(trimmed)) {
            return "Pinned"
        }
        val seed = fnv1a32(trimmed)
        val rng = XorShift32(seed)
        rng.nextFloat()
        return when (rng.nextInt(4)) {
            0 -> "Analogous"
            1 -> "Triadic"
            2 -> "Split complementary"
            3 -> "Complementary"
            else -> "Unknown"
        }
    }

    fun forVersion(versionName: String): IntArray {
        val trimmed = versionName.trim()
        val pinned = PINNED_VERSIONS[trimmed]
        if (pinned != null) {
            return pinned.clone()
        }

        val seed = fnv1a32(trimmed)
        val rng = XorShift32(seed)
        val h0 = rng.nextFloat() * 360f
        val scheme = SCHEMES[rng.nextInt(4)]

        val result = IntArray(4)
        for (role in 0 until 4) {
            val hueJitter = rng.nextFloat(-8f, 8f)
            val hueDeg = (h0 + scheme[role] + hueJitter) % 360f
            val normalizedHue = if (hueDeg < 0f) hueDeg + 360f else hueDeg

            val lJitter = rng.nextFloat(-0.03f, 0.03f)
            val targetL = TARGET_L[role] + lJitter

            val cJitter = rng.nextFloat(-0.10f, 0.10f)
            var chroma = TARGET_C[role] * (1f + cJitter)

            val hueRad = Math.toRadians(normalizedHue.toDouble()).toFloat()
            val cosH = cos(hueRad)
            val sinH = sin(hueRad)

            var a = chroma * cosH
            var b = chroma * sinH

            if (!inGamut(targetL, a, b)) {
                var low = 0f
                var high = chroma
                for (iter in 0 until 20) {
                    val mid = (low + high) * 0.5f
                    if (inGamut(targetL, mid * cosH, mid * sinH)) {
                        low = mid
                    } else {
                        high = mid
                    }
                }
                chroma = low
                a = chroma * cosH
                b = chroma * sinH
            }

            result[role] = oklabToSrgb(targetL, a, b)
        }

        return result
    }

    private fun fnv1a32(text: String): Int {
        var hash = 0x811C9DC5.toInt()
        val bytes = text.toByteArray(Charsets.UTF_8)
        for (b in bytes) {
            hash = hash xor (b.toInt() and 0xFF)
            hash *= 0x01000193
        }
        return hash
    }

    private fun inGamut(L: Float, a: Float, b: Float): Boolean {
        val l_ = L + 0.3963377774f * a + 0.2158037573f * b
        val m_ = L - 0.1055613458f * a - 0.0638541728f * b
        val s_ = L - 0.0894841775f * a - 1.2914855480f * b

        val l = l_ * l_ * l_
        val m = m_ * m_ * m_
        val s = s_ * s_ * s_

        val rLin = +4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s
        val gLin = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s
        val bLin = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s

        return rLin in 0f..1f && gLin in 0f..1f && bLin in 0f..1f
    }

    private fun oklabToSrgb(L: Float, a: Float, b: Float): Int {
        val l_ = L + 0.3963377774f * a + 0.2158037573f * b
        val m_ = L - 0.1055613458f * a - 0.0638541728f * b
        val s_ = L - 0.0894841775f * a - 1.2914855480f * b

        val l = l_ * l_ * l_
        val m = m_ * m_ * m_
        val s = s_ * s_ * s_

        val rLin = +4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s
        val gLin = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s
        val bLin = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s

        val outR = Math.round(linearToSrgb(rLin) * 255f).coerceIn(0, 255)
        val outG = Math.round(linearToSrgb(gLin) * 255f).coerceIn(0, 255)
        val outB = Math.round(linearToSrgb(bLin) * 255f).coerceIn(0, 255)

        return (0xFF shl 24) or (outR shl 16) or (outG shl 8) or outB
    }

    private fun linearToSrgb(c: Float): Float {
        val clamped = c.coerceIn(0f, 1f)
        return if (clamped <= 0.0031308f) {
            12.92f * clamped
        } else {
            1.055f * Math.pow(clamped.toDouble(), 1.0 / 2.4).toFloat() - 0.055f
        }
    }

    private class XorShift32(seed: Int) {
        private var state = if (seed == 0) 0x6D2B79F5.toInt() else seed

        fun nextInt(): Int {
            var x = state
            x = x xor (x shl 13)
            x = x xor (x ushr 17)
            x = x xor (x shl 5)
            state = x
            return x
        }

        fun nextInt(bound: Int): Int {
            val raw = nextInt() ushr 1
            return raw % bound
        }

        fun nextFloat(): Float {
            return (nextInt() ushr 8) / 16777216f
        }

        fun nextFloat(min: Float, max: Float): Float {
            return min + nextFloat() * (max - min)
        }
    }
}
