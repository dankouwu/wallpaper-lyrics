package com.dnk.wallpaperlyrics

import android.graphics.*
import android.os.Build
import android.os.SystemClock
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

const val INACTIVE_LYRIC_ALPHA = 80

data class WordEdge(
    var transitionWidth: Float = 0f,
    var edgeLeft: Float = 0f,
    var edgeRight: Float = 0f
)

fun computeWordEdge(
    wordLeft: Float,
    wordWidth: Float,
    progress: Float,
    out: WordEdge = WordEdge()
): WordEdge {
    val transitionWidth = (wordWidth * 0.3f).coerceAtLeast(40f)
    val edgeRight = wordLeft + (wordWidth + transitionWidth) * progress
    val edgeLeft = edgeRight - transitionWidth
    out.transitionWidth = transitionWidth
    out.edgeLeft = edgeLeft
    out.edgeRight = edgeRight
    return out
}

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
    var linearProgress: Float = 0f
    var wholeWordLinearProgress: Float = Float.NaN
    var motionProgress: Float = 0f
    var motionWindowMs: Long = 0L
    var sweepStartOffsetMs: Long = 0L
    var sweepDurationMs: Long = 0L
    var partStartProp: Float = 0f
    var partEndProp: Float = 1f
    var wordText: String = ""
    var activeAlpha: Int = 230
    var inactiveAlpha: Int = INACTIVE_LYRIC_ALPHA
    var bakeNeutral: Boolean = false
    var exitFade: Float = 0f

    // Cached shader state: avoids allocating a new LinearGradient on every draw call.
    // At 60 FPS with a 10-word active line, this eliminates ~600 heap allocations/second.
    private var cachedShader: LinearGradient? = null
    private var lastProgress = -1f
    private var lastActiveAlpha = -1
    private var lastInactiveAlpha = -1
    private var lastLeft = Float.NaN
    private var lastRight = Float.NaN
    private val wordEdge = WordEdge()

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

                val edge = computeWordEdge(left, width, progress, wordEdge)

                val p1Uncoerced = (edge.edgeLeft - left) / width
                val p2Uncoerced = (edge.edgeRight - left) / width

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
    private val wordDurationMs: Long,
    private val relativeXs: FloatArray,
    private val measuredAdvance: Int,
    textSize: Float = 0f,
    private val isRtl: Boolean = false,
    private val totalAdvance: Float = measuredAdvance.toFloat()
) : ReplacementSpan() {

    private var currentBlurRadius: Float = computeBlurRadius(textSize)
    private var glowMaskFilter = BlurMaskFilter(currentBlurRadius, BlurMaskFilter.Blur.NORMAL)
    private var letterUnitShader: LinearGradient? = null
    private var lastShaderActiveAlpha = -1
    private var lastShaderInactiveAlpha = -1
    private val renderNode: RenderNode? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RenderNode("WordGlow").apply {
                setUseCompositingLayer(true, renderNodePaint)
                setRenderEffect(
                    RenderEffect.createBlurEffect(
                        currentBlurRadius,
                        currentBlurRadius,
                        Shader.TileMode.CLAMP
                    )
                )
            }
        } else {
            null
        }
    } catch (t: Throwable) {
        null
    }

    companion object {
        private val layerBounds = RectF()
        private val layerPaint = Paint()
        private val wordGradMatrix = Matrix()
        private val letterTransformMatrix = Matrix()
        private val letterInverseMatrix = Matrix()
        private val letterShaderMatrix = Matrix()
        private val mode2WordEdge = WordEdge()
        private val maskPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        private val glowPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        }
        private val renderNodePaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        }
        private val glowLetterPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }

        fun computeBlurRadius(textSize: Float, blurFraction: Float = Tuning.glowBlurRadiusFraction): Float {
            return Math.max(1f, if (textSize > 0f) textSize * blurFraction else 9.6f)
        }

        fun computeHorizontalPadding(
            measuredAdvance: Int,
            textSize: Float,
            blurRadius: Float = computeBlurRadius(textSize)
        ): Float {
            return measuredAdvance * 0.02f + 3f * blurRadius
        }

        fun computeBlurredDrawCount(
            hasGlow: Boolean,
            isHeld: Boolean,
            codePointCount: Int,
            letterAnimation: Int = Tuning.letterAnimation
        ): Int {
            if (!hasGlow || codePointCount <= 0 || (isHeld && letterAnimation == 2)) return 0
            return if (isHeld) 1 else 1
        }

        fun computeTopPadding(
            textSize: Float,
            blurRadius: Float = computeBlurRadius(textSize)
        ): Float {
            return textSize * 0.15f + 3f * blurRadius
        }

        fun computeBottomPadding(
            textSize: Float,
            blurRadius: Float = computeBlurRadius(textSize)
        ): Float {
            return 3f * blurRadius
        }

        fun computeWordLayerBounds(
            x: Float,
            top: Int,
            bottom: Int,
            measuredAdvance: Int,
            textSize: Float,
            outRect: RectF,
            blurRadius: Float = computeBlurRadius(textSize)
        ) {
            val horizPad = computeHorizontalPadding(measuredAdvance, textSize, blurRadius)
            val topPad = computeTopPadding(textSize, blurRadius)
            val bottomPad = computeBottomPadding(textSize, blurRadius)
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
        if (progress <= 0f) {
            canvas.drawTextRun(text, start, end, start, end, x, y.toFloat(), isRtl, paint)
            return
        }
        if (progress >= 1f) {
            // A word that has not started sits at the rest scale, which is the line frame itself,
            // so it can be drawn untransformed. A settled word sits above the frame and cannot.
            val settled = SyllableAnimator.toLineRelativeScale(
                Tuning.wordScaleSettle,
                Tuning.wordScaleStart,
                wordSpan.exitFade
            )
            canvas.save()
            canvas.scale(settled, settled, x + totalAdvance / 2f, y.toFloat())
            canvas.drawTextRun(text, start, end, start, end, x, y.toFloat(), isRtl, paint)
            canvas.restore()
            return
        }

        // Read tuning values once per drawGlyphs pass, never per glyph
        val letterAnimation = Tuning.letterAnimation
        val isSequential = Tuning.isSequentialLetterAnimation
        val letterOverlap = Tuning.letterOverlap
        val glowAlphaMult = Tuning.wordGlowAlphaMultiplier
        val blurFraction = Tuning.glowBlurRadiusFraction
        val scaleStart = Tuning.wordScaleStart
        val scalePeak = Tuning.wordScalePeak
        val scalePeakPos = Tuning.wordScalePeakPosition
        val easeInFraction = Tuning.wordScaleEaseInFraction
        val rippleEaseInFraction = Tuning.rippleEaseInFraction
        val riseDurationMs = Tuning.wordRiseDurationMs
        val settleDurationMs = Tuning.wordSettleDurationMs
        val scaleSettle = Tuning.wordScaleSettle
        val motionWindowMs = wordSpan.motionWindowMs
        val sungProgress = wordSpan.linearProgress
        val liftFraction = Tuning.wordLiftPeakFraction
        val liftPeakPos = Tuning.wordLiftPeakPosition
        val glowRiseEnd = Tuning.wordGlowRiseEnd
        val glowHoldEnd = Tuning.wordGlowHoldEnd
        val heldThreshold = Tuning.heldWordMinDurationMs
        val heldScalePeak = Tuning.heldWordLetterScalePeak
        val heldLiftFraction = Tuning.heldWordLetterLiftFraction
        val falloffPower = Tuning.letterFalloffPower
        val motionDurationMinMs = Tuning.wordMotionMinDurationMs
        val motionDurationMaxMs = Tuning.wordMotionMaxDurationMs
        val motionMinAmplitude = Tuning.wordMotionMinAmplitude
        val amplitude = SyllableAnimator.getMotionAmplitude(
            wordDurationMs,
            motionDurationMinMs,
            motionDurationMaxMs,
            motionMinAmplitude
        )

        val desiredBlurRadius = computeBlurRadius(paint.textSize, blurFraction)
        if (currentBlurRadius != desiredBlurRadius) {
            currentBlurRadius = desiredBlurRadius
            glowMaskFilter = BlurMaskFilter(desiredBlurRadius, BlurMaskFilter.Blur.NORMAL)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    renderNode?.setRenderEffect(
                        RenderEffect.createBlurEffect(
                            desiredBlurRadius,
                            desiredBlurRadius,
                            Shader.TileMode.CLAMP
                        )
                    )
                } catch (t: Throwable) {}
            }
        }

        val glow = SyllableAnimator.getWordGlow(progress, glowRiseEnd, glowHoldEnd)
        val hasGlow = glow > 0f
        val baseGlowAlpha = if (hasGlow) (glow * glowAlphaMult).toInt().coerceIn(0, 255) else 0

        val isHeld = SyllableAnimator.isHeldWord(wordDurationMs, heldThreshold)
        val useGpuGlow = hasGlow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canvas.isHardwareAccelerated && renderNode != null

        val effectivePeak = scaleSettle + (scalePeak - scaleSettle) * amplitude
        val absoluteScale = SyllableAnimator.getWordMotionScale(
            progress,
            motionWindowMs,
            riseDurationMs,
            scaleStart,
            effectivePeak,
            scalePeakPos,
            easeInFraction,
            settleDurationMs,
            scaleSettle
        )
        val scale = SyllableAnimator.toLineRelativeScale(absoluteScale, scaleStart, wordSpan.exitFade)
        val lift = SyllableAnimator.getWordLift(progress, paint.textSize, liftFraction, liftPeakPos) * amplitude

        if (isHeld) {
            val baseLift = lift
            val heldLiftPeak = liftFraction * paint.textSize * amplitude
            val effectiveLetterPeak = 1f + (heldScalePeak - 1f) * amplitude
            val numGlyphs = codePointStarts.size

            if (letterAnimation != 2) {
                if (useGpuGlow) {
                    val intLeft = layerBounds.left.toInt()
                    val intTop = layerBounds.top.toInt()
                    val intRight = Math.ceil(layerBounds.right.toDouble()).toInt()
                    val intBottom = Math.ceil(layerBounds.bottom.toDouble()).toInt()
                    val intWidth = intRight - intLeft
                    val intHeight = intBottom - intTop
                    if (intWidth > 0 && intHeight > 0) {
                        renderNode!!.setPosition(intLeft, intTop, intRight, intBottom)
                        val recordingCanvas = renderNode.beginRecording(intWidth, intHeight)
                        recordingCanvas.translate(-intLeft.toFloat(), -intTop.toFloat())
                        glowLetterPaint.textSize = paint.textSize
                        glowLetterPaint.typeface = paint.typeface
                        recordingCanvas.save()
                        recordingCanvas.scale(scale, scale, x + totalAdvance / 2f, y.toFloat())
                        if (isSequential) {
                            for (i in 0 until numGlyphs) {
                                val focus = SyllableAnimator.getSequentialLetterFocus(sungProgress, i, numGlyphs, letterOverlap)
                                val letterLift = heldLiftPeak * focus
                                val letterScale = 1f + (effectiveLetterPeak - 1f) * focus
                                val letterLeft = x + relativeXs[i]
                                val letterRight = if (i + 1 < numGlyphs) x + relativeXs[i + 1] else x + totalAdvance
                                val letterCenterX = (letterLeft + letterRight) * 0.5f

                                recordingCanvas.save()
                                recordingCanvas.scale(scale * letterScale, scale * letterScale, letterCenterX, y.toFloat())
                                glowLetterPaint.alpha = (baseGlowAlpha * focus).toInt().coerceIn(0, 255)
                                recordingCanvas.drawTextRun(
                                    text,
                                    codePointStarts[i],
                                    codePointEnds[i],
                                    start,
                                    end,
                                    letterLeft,
                                    y.toFloat() - letterLift,
                                    isRtl,
                                    glowLetterPaint
                                )
                                recordingCanvas.restore()
                            }
                        } else {
                            val activePos = SyllableAnimator.getActiveLetterPosition(progress, codePointCount)
                            val rippleEnvelope = SyllableAnimator.getRippleEnvelope(progress, rippleEaseInFraction)
                            for (i in 0 until numGlyphs) {
                                val globalIndex = codePointIndices[i]
                                val distance = globalIndex.toFloat() - activePos
                                val falloff = SyllableAnimator.getRippleFalloff(distance, falloffPower)
                                val emphasis = falloff * rippleEnvelope
                                val letterLift = heldLiftPeak * emphasis
                                val letterScale = SyllableAnimator.getHeldWordLetterScale(
                                    progress,
                                    distance,
                                    scaleStart,
                                    heldScalePeak,
                                    scalePeakPos,
                                    falloffPower,
                                    amplitude,
                                    motionWindowMs,
                                    riseDurationMs,
                                    easeInFraction,
                                    settleDurationMs,
                                    rippleEaseInFraction
                                )
                                val letterLeft = x + relativeXs[i]
                                val letterRight = if (i + 1 < numGlyphs) x + relativeXs[i + 1] else x + totalAdvance
                                val letterCenterX = (letterLeft + letterRight) * 0.5f

                                recordingCanvas.save()
                                recordingCanvas.scale(scale * letterScale, scale * letterScale, letterCenterX, y.toFloat())
                                glowLetterPaint.alpha = (baseGlowAlpha * emphasis).toInt().coerceIn(0, 255)
                                recordingCanvas.drawTextRun(
                                    text,
                                    codePointStarts[i],
                                    codePointEnds[i],
                                    start,
                                    end,
                                    letterLeft,
                                    y.toFloat() - letterLift,
                                    isRtl,
                                    glowLetterPaint
                                )
                                recordingCanvas.restore()
                            }
                        }
                        recordingCanvas.restore()
                        renderNode.endRecording()
                        canvas.drawRenderNode(renderNode)
                    }
                } else if (hasGlow) {
                    glowPaint.alpha = baseGlowAlpha
                    glowPaint.textSize = paint.textSize
                    glowPaint.typeface = paint.typeface
                    glowPaint.maskFilter = glowMaskFilter
                    canvas.save()
                    canvas.scale(scale, scale, x + totalAdvance / 2f, y.toFloat())
                    canvas.drawTextRun(text, start, end, start, end, x, y.toFloat() - baseLift, isRtl, glowPaint)
                    canvas.restore()
                }
            }

            if (letterAnimation == 2) {
                val prevShader = paint.shader
                val prevColor = paint.color
                try {
                    val activeAlpha = wordSpan.activeAlpha
                    val inactiveAlpha = wordSpan.inactiveAlpha

                    if (letterUnitShader == null || lastShaderActiveAlpha != activeAlpha || lastShaderInactiveAlpha != inactiveAlpha) {
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
                        val positions = floatArrayOf(0f, 0f, 0.25f, 0.5f, 0.75f, 1f, 1f)

                        letterUnitShader = LinearGradient(
                            0f, 0f, 1f, 0f,
                            colors,
                            positions,
                            Shader.TileMode.CLAMP
                        )
                        lastShaderActiveAlpha = activeAlpha
                        lastShaderInactiveAlpha = inactiveAlpha
                    }

                    val sweepProg = wordSpan.progress
                    val wordLeft = wordSpan.left
                    val wordWidth = wordSpan.right - wordSpan.left
                    val hasActiveSweep = sweepProg > 0f && sweepProg < 1f && wordWidth > 0f

                    if (hasActiveSweep) {
                        computeWordEdge(wordLeft, wordWidth, sweepProg, mode2WordEdge)
                        wordGradMatrix.setScale(mode2WordEdge.transitionWidth, 1f)
                        wordGradMatrix.postTranslate(mode2WordEdge.edgeLeft, 0f)
                    }

                    val edgeLeft = if (hasActiveSweep) mode2WordEdge.edgeLeft else 0f
                    val edgeRight = if (hasActiveSweep) mode2WordEdge.edgeRight else 0f

                    val wordCenterX = x + totalAdvance / 2f
                    val startProp = wordSpan.partStartProp
                    val endProp = if (wordSpan.partEndProp == 0f) 1f else wordSpan.partEndProp
                    val propRange = (endProp - startProp).coerceAtLeast(0f)
                    val sweepStartOffsetMs = wordSpan.sweepStartOffsetMs
                    val sweepDurationMs = wordSpan.sweepDurationMs
                    val wordText = wordSpan.wordText
                    val overlapMs = Tuning.heldWordLetterOverlapMs
                    val elapsedMs = progress * motionWindowMs.toFloat()
                    val measuredAdvF = totalAdvance

                    for (i in 0 until numGlyphs) {
                        val letterLeft = x + relativeXs[i]
                        val letterRight = if (i + 1 < numGlyphs) x + relativeXs[i + 1] else x + totalAdvance
                        val letterCenterX = (letterLeft + letterRight) * 0.5f

                        val xLeft = if (measuredAdvF > 0f) relativeXs[i] / measuredAdvF else 0f
                        val xRight = if (measuredAdvF > 0f) (letterRight - x) / measuredAdvF else 0f

                        val fLeft = (startProp + xLeft * propRange).coerceIn(0f, 1f)
                        val fRight = (startProp + xRight * propRange).coerceIn(0f, 1f)

                        val letterProgress = SyllableAnimator.getLetterMotionProgress(
                            timeMs = elapsedMs,
                            motionWindowMs = motionWindowMs,
                            sweepStartOffsetMs = sweepStartOffsetMs,
                            sweepDurationMs = sweepDurationMs,
                            fractionLeft = fLeft,
                            fractionRight = fRight,
                            wordText = wordText,
                            overlapMs = overlapMs,
                            peakPosition = liftPeakPos
                        )

                        val letterAbsScale = SyllableAnimator.getWordMotionScale(
                            letterProgress,
                            startScale = scaleStart,
                            peakScale = effectivePeak,
                            peakPosition = scalePeakPos,
                            easeInFraction = easeInFraction,
                            settleScale = scaleSettle
                        )
                        val letterScale = SyllableAnimator.toLineRelativeScale(
                            letterAbsScale,
                            scaleStart,
                            wordSpan.exitFade
                        )
                        val letterLift = SyllableAnimator.getWordLift(
                            letterProgress,
                            paint.textSize,
                            heldLiftFraction,
                            liftPeakPos
                        ) * amplitude

                        val targetCenterX = wordCenterX + (letterCenterX - wordCenterX) * letterScale

                        val halfWidth = (letterRight - letterLeft) * 0.5f * letterScale
                        val transformedLeft = targetCenterX - halfWidth
                        val transformedRight = targetCenterX + halfWidth

                        when {
                            sweepProg >= 1f || (hasActiveSweep && transformedRight <= edgeLeft) -> {
                                paint.shader = null
                                paint.color = Color.WHITE
                                paint.alpha = activeAlpha
                            }
                            sweepProg <= 0f || !hasActiveSweep || transformedLeft >= edgeRight -> {
                                paint.shader = null
                                paint.color = Color.WHITE
                                paint.alpha = inactiveAlpha
                            }
                            else -> {
                                letterTransformMatrix.setTranslate(targetCenterX - letterCenterX, -letterLift)
                                letterTransformMatrix.preScale(letterScale, letterScale, letterCenterX, y.toFloat())
                                if (letterTransformMatrix.invert(letterInverseMatrix)) {
                                    letterShaderMatrix.set(letterInverseMatrix)
                                    letterShaderMatrix.preConcat(wordGradMatrix)
                                } else {
                                    letterShaderMatrix.set(wordGradMatrix)
                                }
                                letterUnitShader!!.setLocalMatrix(letterShaderMatrix)
                                paint.color = Color.WHITE
                                paint.shader = letterUnitShader
                            }
                        }

                        canvas.save()
                        canvas.translate(targetCenterX - letterCenterX, -letterLift)
                        canvas.scale(letterScale, letterScale, letterCenterX, y.toFloat())
                        canvas.drawTextRun(
                            text,
                            codePointStarts[i],
                            codePointEnds[i],
                            start,
                            end,
                            letterLeft,
                            y.toFloat(),
                            isRtl,
                            paint
                        )
                        canvas.restore()
                    }
                } finally {
                    paint.shader = prevShader
                    paint.color = prevColor
                }
            } else if (isSequential) {
                val prevShader = paint.shader
                val prevColor = paint.color
                try {
                    paint.shader = null
                    val inactiveAlpha = wordSpan.inactiveAlpha
                    val activeAlpha = wordSpan.activeAlpha
                    val alphaDiff = (activeAlpha - inactiveAlpha).toFloat()

                    canvas.save()
                    canvas.scale(scale, scale, x + totalAdvance / 2f, y.toFloat())
                    for (i in 0 until numGlyphs) {
                        val focus = SyllableAnimator.getSequentialLetterFocus(sungProgress, i, numGlyphs, letterOverlap)
                        val fill = SyllableAnimator.getSequentialLetterFill(sungProgress, i, numGlyphs, letterOverlap)
                        val letterScale = 1f + (effectiveLetterPeak - 1f) * focus
                        val letterLift = heldLiftPeak * focus
                        val letterAlpha = (inactiveAlpha + alphaDiff * fill).toInt().coerceIn(0, 255)

                        val letterLeft = x + relativeXs[i]
                        val letterRight = if (i + 1 < numGlyphs) x + relativeXs[i + 1] else x + totalAdvance
                        val letterCenterX = (letterLeft + letterRight) * 0.5f

                        paint.color = Color.WHITE
                        paint.alpha = letterAlpha

                        canvas.save()
                        canvas.scale(scale * letterScale, scale * letterScale, letterCenterX, y.toFloat())
                        canvas.drawTextRun(
                            text,
                            codePointStarts[i],
                            codePointEnds[i],
                            start,
                            end,
                            letterLeft,
                            y.toFloat() - letterLift,
                            isRtl,
                            paint
                        )
                        canvas.restore()
                    }
                    canvas.restore()
                } finally {
                    paint.shader = prevShader
                    paint.color = prevColor
                }
            } else {
                val activePos = SyllableAnimator.getActiveLetterPosition(progress, codePointCount)
                val rippleEnvelope = SyllableAnimator.getRippleEnvelope(progress, rippleEaseInFraction)
                canvas.save()
                // The word swell belongs to the word and the ripple belongs to the letter.
                canvas.scale(scale, scale, x + totalAdvance / 2f, y.toFloat())
                for (i in 0 until numGlyphs) {
                    val globalIndex = codePointIndices[i]
                    val distance = globalIndex.toFloat() - activePos
                    val falloff = SyllableAnimator.getRippleFalloff(distance, falloffPower)
                    val emphasis = falloff * rippleEnvelope
                    val letterLift = heldLiftPeak * emphasis
                    val letterScale = SyllableAnimator.getHeldWordLetterScale(
                        progress,
                        distance,
                        scaleStart,
                        heldScalePeak,
                        scalePeakPos,
                        falloffPower,
                        amplitude,
                        motionWindowMs,
                        riseDurationMs,
                        easeInFraction,
                        settleDurationMs,
                        rippleEaseInFraction
                    )

                    val letterLeft = x + relativeXs[i]
                    val letterRight = if (i + 1 < numGlyphs) x + relativeXs[i + 1] else x + totalAdvance
                    val letterCenterX = (letterLeft + letterRight) * 0.5f

                    canvas.save()
                    canvas.scale(scale * letterScale, scale * letterScale, letterCenterX, y.toFloat())
                    canvas.drawTextRun(
                        text,
                        codePointStarts[i],
                        codePointEnds[i],
                        start,
                        end,
                        letterLeft,
                        y.toFloat() - letterLift,
                        isRtl,
                        paint
                    )
                    canvas.restore()
                }
                canvas.restore()
            }
        } else {
            if (useGpuGlow) {
                val intLeft = layerBounds.left.toInt()
                val intTop = layerBounds.top.toInt()
                val intRight = Math.ceil(layerBounds.right.toDouble()).toInt()
                val intBottom = Math.ceil(layerBounds.bottom.toDouble()).toInt()
                val intWidth = intRight - intLeft
                val intHeight = intBottom - intTop
                if (intWidth > 0 && intHeight > 0) {
                    renderNode!!.setPosition(intLeft, intTop, intRight, intBottom)
                    val recordingCanvas = renderNode.beginRecording(intWidth, intHeight)
                    recordingCanvas.translate(-intLeft.toFloat(), -intTop.toFloat())
                    glowLetterPaint.textSize = paint.textSize
                    glowLetterPaint.typeface = paint.typeface
                    glowLetterPaint.alpha = baseGlowAlpha
                    recordingCanvas.save()
                    recordingCanvas.scale(scale, scale, x + totalAdvance / 2f, y.toFloat())
                    recordingCanvas.drawTextRun(text, start, end, start, end, x, y.toFloat() - lift, isRtl, glowLetterPaint)
                    recordingCanvas.restore()
                    renderNode.endRecording()
                    canvas.drawRenderNode(renderNode)
                }
            } else if (hasGlow) {
                glowPaint.alpha = baseGlowAlpha
                glowPaint.textSize = paint.textSize
                glowPaint.typeface = paint.typeface
                glowPaint.maskFilter = glowMaskFilter
                canvas.save()
                canvas.scale(scale, scale, x + totalAdvance / 2f, y.toFloat())
                canvas.drawTextRun(text, start, end, start, end, x, y.toFloat() - lift, isRtl, glowPaint)
                canvas.restore()
            }

            canvas.save()
            canvas.scale(scale, scale, x + totalAdvance / 2f, y.toFloat())
            canvas.drawTextRun(text, start, end, start, end, x, y.toFloat() - lift, isRtl, paint)
            canvas.restore()
        }
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

            if (wordSpan.bakeNeutral) {
                if (shader != null) {
                    computeWordLayerBounds(x, top, bottom, measuredAdvance, paint.textSize, layerBounds)
                    layerPaint.alpha = 255
                    canvas.saveLayer(layerBounds, layerPaint)
                    paint.color = Color.WHITE
                    paint.shader = null
                    canvas.drawTextRun(text, start, end, start, end, x, y.toFloat(), isRtl, paint)
                    maskPaint.shader = shader
                    canvas.drawRect(layerBounds, maskPaint)
                    maskPaint.shader = null
                    canvas.restore()
                } else if (paint.alpha > 0) {
                    canvas.drawTextRun(text, start, end, start, end, x, y.toFloat(), isRtl, paint)
                }
                return
            }

            val motionProg = wordSpan.motionProgress

            computeWordLayerBounds(x, top, bottom, measuredAdvance, paint.textSize, layerBounds)

            val isHeldSequentialOrSpring = SyllableAnimator.isHeldWord(wordDurationMs, Tuning.heldWordMinDurationMs) &&
                (Tuning.isSequentialLetterAnimation || Tuning.isSpringLetterAnimation)

            if (isHeldSequentialOrSpring) {
                layerPaint.alpha = 255
                canvas.saveLayer(layerBounds, layerPaint)
                paint.color = if (motionProg >= 1f) {
                    Color.argb(wordSpan.activeAlpha, 255, 255, 255)
                } else if (motionProg <= 0f) {
                    Color.argb(wordSpan.inactiveAlpha, 255, 255, 255)
                } else {
                    Color.WHITE
                }
                paint.shader = null
                drawGlyphs(canvas, text, start, end, x, y, paint, motionProg)
                canvas.restore()
            } else if (shader != null) {
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
