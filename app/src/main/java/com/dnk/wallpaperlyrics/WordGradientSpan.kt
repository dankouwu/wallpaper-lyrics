package com.dnk.wallpaperlyrics

import android.graphics.*
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance

data class AuroraPalette(
    val accent: Int,
    val base: Int,
    val mid: Int,
    val highlight: Int
)

class WordGradientSpan(
    private val left: Float,
    private val right: Float
) : CharacterStyle(), UpdateAppearance {
    var progress: Float = 0f
    var activeAlpha: Int = 230
    var inactiveAlpha: Int = 80

    override fun updateDrawState(tp: TextPaint) {
        if (progress <= 0f) {
            tp.color = Color.argb(inactiveAlpha, 255, 255, 255)
            tp.shader = null
        } else if (progress >= 1f) {
            tp.color = Color.argb(activeAlpha, 255, 255, 255)
            tp.shader = null
        } else {
            val width = right - left
            if (width <= 0f) {
                tp.color = Color.argb(inactiveAlpha, 255, 255, 255)
                tp.shader = null
                return
            }

            // To avoid any sudden visual jump at progress 0 or 1, the gradient transition region
            // of width transitionWidth starts before the word (to the left of `left`) and moves
            // to the right of `right`.
            // At progress = 0: xTransition = left, so the entire word is in the inactive zone.
            // At progress = 1: xTransition = right + transitionWidth, so the entire word is in the active zone.
            val transitionWidth = 80f
            val xTransition = left + (width + transitionWidth) * progress

            val p1Uncoerced = (xTransition - transitionWidth - left) / width
            val p2Uncoerced = (xTransition - left) / width

            // Use 5 stops across the transition region to build a smooth ease (smoothstep)
            val pos0 = p1Uncoerced.coerceIn(0f, 1f)
            val pos1 = (p1Uncoerced + 0.25f * (p2Uncoerced - p1Uncoerced)).coerceIn(0f, 1f)
            val pos2 = (p1Uncoerced + 0.50f * (p2Uncoerced - p1Uncoerced)).coerceIn(0f, 1f)
            val pos3 = (p1Uncoerced + 0.75f * (p2Uncoerced - p1Uncoerced)).coerceIn(0f, 1f)
            val pos4 = p2Uncoerced.coerceIn(0f, 1f)

            val diff = (activeAlpha - inactiveAlpha).toFloat()
            // smoothstep values for 0.25, 0.50, 0.75 are 0.15625f, 0.50f, 0.84375f respectively
            val alpha1 = (activeAlpha - diff * 0.15625f).toInt()
            val alpha2 = (activeAlpha - diff * 0.5f).toInt()
            val alpha3 = (activeAlpha - diff * 0.84375f).toInt()

            val colors = intArrayOf(
                Color.argb(activeAlpha, 255, 255, 255),
                Color.argb(activeAlpha, 255, 255, 255),
                Color.argb(alpha1, 255, 255, 255),
                Color.argb(alpha2, 255, 255, 255),
                Color.argb(alpha3, 255, 255, 255),
                Color.argb(inactiveAlpha, 255, 255, 255),
                Color.argb(inactiveAlpha, 255, 255, 255)
            )

            val positions = floatArrayOf(
                0f,
                pos0,
                pos1,
                pos2,
                pos3,
                pos4,
                1f
            )

            tp.shader = LinearGradient(
                left, 0f, right, 0f,
                colors,
                positions,
                Shader.TileMode.CLAMP
            )
        }
    }
}
