package com.dnk.wallpaperlyrics

import android.graphics.*
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.ReplacementSpan
import android.text.style.UpdateAppearance

data class AuroraPalette(
    val accent: Int,
    val base: Int,
    val mid: Int,
    val highlight: Int
)

class WordGradientSpan(
    left: Float,
    right: Float
) : CharacterStyle(), UpdateAppearance {
    var left: Float = left
        set(value) {
            if (field != value) {
                field = value
                cachedShader = null
            }
        }
    var right: Float = right
        set(value) {
            if (field != value) {
                field = value
                cachedShader = null
            }
        }
    var progress: Float = 0f
    var motionProgress: Float = 0f
    var activeAlpha: Int = 230
    var inactiveAlpha: Int = 80

    // Cached shader state: avoids allocating a new LinearGradient on every draw call.
    // At 60 FPS with a 10-word active line, this eliminates ~600 heap allocations/second.
    private var cachedShader: LinearGradient? = null
    private var lastProgress = -1f
    private var lastActiveAlpha = -1
    private var lastInactiveAlpha = -1
    private var lastLeft = Float.NaN
    private var lastRight = Float.NaN

    override fun updateDrawState(tp: TextPaint) {
        applyDrawState(tp)
    }

    fun applyDrawState(paint: Paint) {
        if (progress <= 0f) {
            paint.color = Color.argb(inactiveAlpha, 255, 255, 255)
            paint.shader = null
        } else if (progress >= 1f) {
            paint.color = Color.argb(activeAlpha, 255, 255, 255)
            paint.shader = null
        } else {
            val width = right - left
            if (width <= 0f) {
                paint.color = Color.argb(inactiveAlpha, 255, 255, 255)
                paint.shader = null
                return
            }

            // Only rebuild the gradient when state has meaningfully changed.
            val needsRebuild = cachedShader == null
                || Math.abs(progress - lastProgress) > 0.002f
                || activeAlpha != lastActiveAlpha
                || inactiveAlpha != lastInactiveAlpha
                || left != lastLeft
                || right != lastRight

            if (needsRebuild) {
                lastProgress = progress
                lastActiveAlpha = activeAlpha
                lastInactiveAlpha = inactiveAlpha
                lastLeft = left
                lastRight = right

                // Transition width = 30% of word width, minimum 40px.
                // Fixed 80px bled across entire short words (2-3 chars); proportional
                // width scales correctly so all word sizes sweep uniformly.
                val transitionWidth = (width * 0.3f).coerceAtLeast(40f)
                val xTransition = left + (width + transitionWidth) * progress

                val p1Uncoerced = (xTransition - transitionWidth - left) / width
                val p2Uncoerced = (xTransition - left) / width

                // 5-stop smoothstep gradient across the transition region
                val pos0 = p1Uncoerced.coerceIn(0f, 1f)
                val pos1 = (p1Uncoerced + 0.25f * (p2Uncoerced - p1Uncoerced)).coerceIn(0f, 1f)
                val pos2 = (p1Uncoerced + 0.50f * (p2Uncoerced - p1Uncoerced)).coerceIn(0f, 1f)
                val pos3 = (p1Uncoerced + 0.75f * (p2Uncoerced - p1Uncoerced)).coerceIn(0f, 1f)
                val pos4 = p2Uncoerced.coerceIn(0f, 1f)

                val diff = (activeAlpha - inactiveAlpha).toFloat()
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

                val positions = floatArrayOf(0f, pos0, pos1, pos2, pos3, pos4, 1f)

                cachedShader = LinearGradient(
                    left, 0f, right, 0f,
                    colors,
                    positions,
                    Shader.TileMode.CLAMP
                )
            }

            paint.color = Color.WHITE
            paint.shader = cachedShader
        }
    }
}

class WordMotionSpan(
    private val wordSpan: WordGradientSpan,
    private val codePointStarts: IntArray,
    private val codePointEnds: IntArray,
    private val codePointIndices: IntArray,
    private val codePointCount: Int,
    private val usesPerLetterMotion: Boolean,
    private val relativeXs: FloatArray,
    private val measuredAdvance: Int
) : ReplacementSpan() {

    companion object {
        private val layerBounds = RectF()
        private val layerPaint = Paint()
        private val maskPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }

        fun computeLayerBoundsValues(
            x: Float,
            top: Int,
            bottom: Int,
            measuredAdvance: Int,
            textSize: Float
        ): FloatArray {
            val horizPad = measuredAdvance * 0.02f + textSize * 0.10f
            val topPad = textSize * 0.15f
            val bottomPad = textSize * 0.08f
            return floatArrayOf(
                x - horizPad,
                top.toFloat() - topPad,
                x + measuredAdvance.toFloat() + horizPad,
                bottom.toFloat() + bottomPad
            )
        }

        fun computeWordLayerBounds(
            x: Float,
            top: Int,
            bottom: Int,
            measuredAdvance: Int,
            textSize: Float,
            outRect: RectF
        ) {
            val horizPad = measuredAdvance * 0.02f + textSize * 0.10f
            val topPad = textSize * 0.15f
            val bottomPad = textSize * 0.08f
            outRect.set(
                x - horizPad,
                top.toFloat() - topPad,
                x + measuredAdvance.toFloat() + horizPad,
                bottom.toFloat() + bottomPad
            )
        }
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fontMetrics: Paint.FontMetricsInt?
    ): Int = measuredAdvance

    private fun drawGlyphs(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        y: Int,
        paint: Paint,
        progress: Float
    ) {
        if (progress <= 0f || progress >= 1f) {
            canvas.drawText(text, start, end, x, y.toFloat(), paint)
            return
        }

        val scale = SyllableAnimator.getWordMotionScale(progress)
        canvas.save()
        canvas.scale(scale, scale, x + measuredAdvance / 2f, y.toFloat())
        if (usesPerLetterMotion) {
            for (i in codePointStarts.indices) {
                val lift = SyllableAnimator.getLetterLift(
                    progress,
                    codePointIndices[i],
                    codePointCount,
                    paint.textSize
                )
                canvas.drawText(
                    text,
                    codePointStarts[i],
                    codePointEnds[i],
                    x + relativeXs[i],
                    y - lift,
                    paint
                )
            }
        } else {
            canvas.drawText(
                text,
                start,
                end,
                x,
                y - SyllableAnimator.getWholeWordLift(progress, paint.textSize),
                paint
            )
        }
        canvas.restore()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val originalColor = paint.color
        val originalShader = paint.shader
        try {
            wordSpan.applyDrawState(paint)
            val shader = paint.shader
            val motionProg = wordSpan.motionProgress

            computeWordLayerBounds(x, top, bottom, measuredAdvance, paint.textSize, layerBounds)

            if (shader != null) {
                // Mid-sweep word: draw opaque into bounded layer, then apply gradient via DST_IN mask
                layerPaint.alpha = 255
                canvas.saveLayer(layerBounds, layerPaint)
                paint.color = Color.WHITE
                paint.shader = null
                drawGlyphs(canvas, text, start, end, x, y, paint, motionProg)
                maskPaint.shader = shader
                canvas.drawRect(layerBounds, maskPaint)
                maskPaint.shader = null
                canvas.restore()
            } else {
                // Uniform-alpha word: draw opaque into bounded layer, composite at target alpha
                val targetAlpha = paint.alpha
                if (targetAlpha > 0) {
                    layerPaint.alpha = targetAlpha
                    canvas.saveLayer(layerBounds, layerPaint)
                    paint.color = Color.WHITE
                    drawGlyphs(canvas, text, start, end, x, y, paint, motionProg)
                    canvas.restore()
                }
            }
        } finally {
            paint.color = originalColor
            paint.shader = originalShader
        }
    }
}
