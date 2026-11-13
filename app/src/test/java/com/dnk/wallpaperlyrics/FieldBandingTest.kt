package com.dnk.wallpaperlyrics

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class FieldBandingTest {

    private val n = AuroraRenderer.BACKGROUND_WORK_RESOLUTION

    private fun twoTone(top: Int, bottom: Int, diagonal: Boolean) = IntArray(n * n) { i ->
        val x = i % n
        val y = i / n
        val useTop = if (diagonal) x + y < n else y < n / 2
        (if (useTop) top else bottom) or (0xFF shl 24)
    }

    private fun blobs(bg: Int, vararg spots: Pair<Int, FloatArray>) = IntArray(n * n) { i ->
        val x = (i % n).toFloat() / n
        val y = (i / n).toFloat() / n
        var c = bg
        for ((color, p) in spots) if (hypot(x - p[0], y - p[1]) < p[2]) c = color
        c or (0xFF shl 24)
    }

    private fun lowPass(c: FloatArray, r: Int): FloatArray {
        val tmp = FloatArray(c.size)
        val out = FloatArray(c.size)
        for (y in 0 until n) for (x in 0 until n) {
            var s = 0f
            for (i in max(0, x - r)..min(n - 1, x + r)) s += c[y * n + i]
            tmp[y * n + x] = s / (min(n - 1, x + r) - max(0, x - r) + 1)
        }
        for (y in 0 until n) for (x in 0 until n) {
            var s = 0f
            for (i in max(0, y - r)..min(n - 1, y + r)) s += tmp[i * n + x]
            out[y * n + x] = s / (min(n - 1, y + r) - max(0, y - r) + 1)
        }
        return out
    }

    // A band is a low frequency error: what is left of the 8-bit field minus its float source
    // once the dither grain is averaged away. Before the float pipeline these fields were off by
    // 1.4 to 13 LSB.
    private fun worstBandLsb(input: IntArray): Float {
        val (r, g, b) = AuroraRenderer.processFieldFloat(input.copyOf(), n, n, 80)
        val out = input.copyOf()
        AuroraRenderer.processField(out, n, n, 80)
        var worst = 0f
        for ((shift, ref) in listOf(16 to r, 8 to g, 0 to b)) {
            val err = FloatArray(n * n) { ((out[it] shr shift) and 0xFF) - ref[it] }
            for (v in lowPass(err, 7)) worst = max(worst, abs(v))
        }
        return worst
    }

    @Test
    fun `dark fields that used to band stay within half a level of the float field`() {
        val fields = mapOf(
            "github dark" to twoTone(0x0d1117, 0x1f2a3a, diagonal = true),
            "navy to black" to twoTone(0x1a2a5a, 0x000000, diagonal = false),
            "plum" to blobs(0x120a14, 0x5a2a55 to floatArrayOf(0.3f, 0.3f, 0.25f), 0x22304a to floatArrayOf(0.7f, 0.65f, 0.3f)),
            "warm brown" to twoTone(0x281209, 0x6a3a1c, diagonal = true)
        )
        for ((name, field) in fields) {
            val worst = worstBandLsb(field)
            assertTrue("$name is off by $worst LSB", worst < 0.5f)
        }
    }

    // The old path rounded between boost and cap, so a level or two apart is expected.
    @Test
    fun `float boost and cap match the 8-bit boost then cap they replace`() {
        val random = java.util.Random(7)
        val pixels = IntArray(4096) { random.nextInt() or (0xFF shl 24) }
        val (r, g, b) = AuroraRenderer.processFieldFloat(pixels.copyOf(), 64, 64, 0)
        var worst = 0f
        for (i in pixels.indices) {
            val boosted = AuroraRenderer.boostChromaColor(pixels[i], AuroraRenderer.DEFAULT_CHROMA_EXPONENT, dither = 0f)
            val old = AuroraRenderer.capLightnessColor(boosted, dither = 0f)
            worst = max(worst, abs(((old shr 16) and 0xFF) - r[i]))
            worst = max(worst, abs(((old shr 8) and 0xFF) - g[i]))
            worst = max(worst, abs((old and 0xFF) - b[i]))
        }
        assertTrue("off by $worst levels", worst <= 2f)
    }
}
