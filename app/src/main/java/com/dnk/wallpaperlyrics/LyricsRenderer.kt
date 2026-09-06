package com.dnk.wallpaperlyrics

import android.graphics.*
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.graphics.text.LineBreaker
import androidx.core.content.res.ResourcesCompat

/**
 * Handles all lyrics-related drawing: synced lyrics, fade gradients,
 * metadata view, instrumental progress dots, and layout construction.
 */
object LyricsRenderer {

    // Cached paint for instrumental countdown dots: created once, never re-allocated
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    /**
     * Build lyric layouts, bitmaps, and measured word spans for all lines.
     * Returns a Triple of (layouts, bitmaps, updatedLines).
     */
    fun buildLyricLayouts(
        lines: List<LyricLine>,
        activePaint: TextPaint,
        maxTextWidth: Int
    ): Triple<List<StaticLayout>, List<Bitmap>, List<LyricLine>> {
        val layouts = mutableListOf<StaticLayout>()
        val bitmaps = mutableListOf<Bitmap>()
        val linesWithMeasuredWords = mutableListOf<LyricLine>()

        lines.forEach { line ->
            val isInstrumental = line.content == "♪"

            val linePaint = TextPaint(activePaint).apply {
                if (isInstrumental) textSize = 120f
                alpha = 255
            }

            // 1. Build temporary layout with plain text to measure word coordinates
            val tempLayout = StaticLayout.Builder.obtain(line.content, 0, line.content.length, linePaint, maxTextWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.15f)
                .build()

            // 2. Measure coordinates and create spans
            val spannedText = SpannableStringBuilder(line.content)
            val hasRealWordSync = line.words != null && line.words.isNotEmpty() && !line.words.any { it.isEstimated }
            val measuredWords = if (hasRealWordSync) {
                line.words!!.flatMap { word ->
                    val startLine = tempLayout.getLineForOffset(word.startIndex)
                    val endLine = tempLayout.getLineForOffset(Math.max(word.startIndex, word.endIndex - 1))

                    if (startLine == endLine) {
                        val h1 = tempLayout.getPrimaryHorizontal(word.startIndex)
                        val h2 = if (tempLayout.getLineForOffset(word.endIndex) == startLine) {
                            tempLayout.getPrimaryHorizontal(word.endIndex)
                        } else {
                            tempLayout.getLineRight(startLine)
                        }
                        val left = Math.min(h1, h2)
                        val right = Math.max(h1, h2)
                        val span = WordGradientSpan(left, right)

                        spannedText.setSpan(
                            span,
                            word.startIndex,
                            word.endIndex,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        addWordMotionSpan(
                            spannedText,
                            line.content,
                            linePaint,
                            word.startIndex,
                            word.endIndex,
                            word.startIndex,
                            word.endIndex,
                            word.endTime - word.startTime,
                            span
                        )

                        listOf(word.copy(
                            left = left,
                            right = right,
                            lineNum = startLine,
                            spanRef = span
                        ))
                    } else {
                        val totalChars = word.endIndex - word.startIndex
                        val parts = mutableListOf<LyricWord>()

                        for (l in startLine..endLine) {
                            val partStart = Math.max(word.startIndex, tempLayout.getLineStart(l))
                            val partEnd = Math.min(word.endIndex, tempLayout.getLineEnd(l))
                            if (partStart >= partEnd) continue

                            val h1 = tempLayout.getPrimaryHorizontal(partStart)
                            val h2 = if (tempLayout.getLineForOffset(partEnd) == l) {
                                tempLayout.getPrimaryHorizontal(partEnd)
                            } else {
                                tempLayout.getLineRight(l)
                            }
                            val left = Math.min(h1, h2)
                            val right = Math.max(h1, h2)

                            val span = WordGradientSpan(left, right)
                            spannedText.setSpan(
                                span,
                                partStart,
                                partEnd,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            addWordMotionSpan(
                                spannedText,
                                line.content,
                                linePaint,
                                word.startIndex,
                                word.endIndex,
                                partStart,
                                partEnd,
                                word.endTime - word.startTime,
                                span
                            )

                            val duration = word.endTime - word.startTime
                            val startProp = (partStart - word.startIndex).toFloat() / totalChars
                            val endProp = (partEnd - word.startIndex).toFloat() / totalChars

                            val partStartMs = word.startTime + (duration * startProp).toLong()
                            val partEndMs = word.startTime + (duration * endProp).toLong()

                            parts.add(word.copy(
                                startIndex = partStart,
                                endIndex = partEnd,
                                startTime = partStartMs,
                                endTime = partEndMs,
                                left = left,
                                right = right,
                                lineNum = l,
                                spanRef = span,
                                fullStartTime = word.startTime,
                                fullEndTime = word.endTime,
                                partStartProp = startProp,
                                partEndProp = endProp
                            ))
                        }
                        parts
                    }
                }
            } else null

            // 3. Build the final layout using the spannedText
            val textToUse: CharSequence = if (measuredWords != null) spannedText else line.content
            val layout = StaticLayout.Builder.obtain(textToUse, 0, textToUse.length, linePaint, maxTextWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.15f)
                .build()
            layouts.add(layout)

            // Re-derive word geometry from the layout that is actually drawn
            val updatedWords = measuredWords?.map { word ->
                val lineNum = layout.getLineForOffset(word.startIndex)
                val h1 = layout.getPrimaryHorizontal(word.startIndex)
                val h2 = if (layout.getLineForOffset(word.endIndex) == lineNum) {
                    layout.getPrimaryHorizontal(word.endIndex)
                } else {
                    layout.getLineRight(lineNum)
                }
                val left = Math.min(h1, h2)
                val right = Math.max(h1, h2)

                (word.spanRef as? WordGradientSpan)?.let { span ->
                    span.left = left
                    span.right = right
                }

                word.copy(
                    left = left,
                    right = right,
                    lineNum = lineNum
                )
            }

            linesWithMeasuredWords.add(line.copy(words = updatedWords))

            val spans = updatedWords?.mapNotNull { it.spanRef as? WordGradientSpan }
            spans?.forEach { span ->
                span.progress = 0f
                span.motionProgress = 0f
                span.inactiveAlpha = 255
            }

            // Bake inactive bitmap from layout (spanned layout with neutral opaque spans: fully opaque white)
            val bmp = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
            val bmpCanvas = Canvas(bmp)
            layout.draw(bmpCanvas)
            bitmaps.add(bmp)

            spans?.forEach { span ->
                span.inactiveAlpha = 80
            }
        }

        return Triple(layouts, bitmaps, linesWithMeasuredWords)
    }

    private fun addWordMotionSpan(
        text: SpannableStringBuilder,
        content: String,
        paint: TextPaint,
        wordStart: Int,
        wordEnd: Int,
        partStart: Int,
        partEnd: Int,
        wordDurationMs: Long,
        wordSpan: WordGradientSpan
    ) {
        var codePointCount = 0
        var offset = wordStart
        while (offset < wordEnd) {
            val nextOffset = offset + Character.charCount(Character.codePointAt(content, offset))
            if (nextOffset > wordEnd) break
            codePointCount++
            offset = nextOffset
        }
        if (codePointCount == 0) return

        var partCodePointCount = 0
        offset = wordStart
        while (offset < wordEnd) {
            val nextOffset = offset + Character.charCount(Character.codePointAt(content, offset))
            if (nextOffset > wordEnd) break
            if (offset >= partStart && nextOffset <= partEnd) partCodePointCount++
            offset = nextOffset
        }
        if (partCodePointCount == 0) return

        val codePointStarts = IntArray(partCodePointCount)
        val codePointEnds = IntArray(partCodePointCount)
        val codePointIndices = IntArray(partCodePointCount)
        val relativeXs = FloatArray(partCodePointCount)
        val measuredAdvance = Math.round(paint.measureText(content, partStart, partEnd))
        var relativeX = 0f
        var partCodePointIndex = 0
        var codePointIndex = 0
        offset = wordStart
        while (offset < wordEnd) {
            val nextOffset = offset + Character.charCount(Character.codePointAt(content, offset))
            if (nextOffset > wordEnd) break
            if (offset >= partStart && nextOffset <= partEnd) {
                codePointStarts[partCodePointIndex] = offset
                codePointEnds[partCodePointIndex] = nextOffset
                codePointIndices[partCodePointIndex] = codePointIndex
                relativeXs[partCodePointIndex] = relativeX
                relativeX += paint.measureText(content, offset, nextOffset)
                partCodePointIndex++
            }
            codePointIndex++
            offset = nextOffset
        }

        if (relativeX > 0f) {
            val advanceScale = measuredAdvance / relativeX
            for (i in relativeXs.indices) relativeXs[i] *= advanceScale
        }

        text.setSpan(
            WordMotionSpan(
                wordSpan,
                codePointStarts,
                codePointEnds,
                codePointIndices,
                codePointCount,
                SyllableAnimator.usesPerLetterMotion(wordDurationMs, codePointCount),
                relativeXs,
                measuredAdvance
            ),
            partStart,
            partEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    fun drawFadeGradients(
        canvas: Canvas,
        width: Float,
        height: Float,
        alpha: Float,
        fadePaint: Paint,
        topFadeShader: LinearGradient?,
        bottomFadeShader: LinearGradient?
    ) {
        if (alpha <= 0f) return

        val fadeHeight = height * 0.25f

        fadePaint.alpha = (alpha * 255).toInt()

        // Top fade
        fadePaint.shader = topFadeShader
        canvas.drawRect(0f, 0f, width, fadeHeight, fadePaint)

        // Bottom fade
        fadePaint.shader = bottomFadeShader
        canvas.drawRect(0f, height - fadeHeight, width, height, fadePaint)
    }

    /**
     * Creates or updates the fade gradient shaders if dimensions changed.
     * Returns a Pair of (topShader, bottomShader).
     */
    fun createFadeShaders(height: Float): Pair<LinearGradient, LinearGradient> {
        val fadeHeight = height * 0.25f

        val topColors = intArrayOf(
            Color.argb(255, 0, 0, 0),
            Color.argb(131, 0, 0, 0),
            Color.argb(55, 0, 0, 0),
            Color.argb(16, 0, 0, 0),
            Color.argb(2, 0, 0, 0),
            Color.argb(0, 0, 0, 0)
        )
        val bottomColors = intArrayOf(
            Color.argb(0, 0, 0, 0),
            Color.argb(2, 0, 0, 0),
            Color.argb(16, 0, 0, 0),
            Color.argb(55, 0, 0, 0),
            Color.argb(131, 0, 0, 0),
            Color.argb(255, 0, 0, 0)
        )
        val positions = floatArrayOf(0.0f, 0.2f, 0.4f, 0.6f, 0.8f, 1.0f)

        val topShader = LinearGradient(0f, 0f, 0f, fadeHeight,
            topColors, positions, Shader.TileMode.CLAMP)

        val bottomShader = LinearGradient(0f, height - fadeHeight, 0f, height,
            bottomColors, positions, Shader.TileMode.CLAMP)

        return Pair(topShader, bottomShader)
    }

    fun drawInstrumentalProgress(
        canvas: Canvas,
        layout: StaticLayout,
        progress: Float,
        position: Long,
        line: LyricLine
    ) {
        val dotCount = 3
        val dotSpacing = 36f
        val dotRadius = 7f
        val totalWidth = (dotCount - 1) * dotSpacing
        val startX = layout.width / 2f - totalWidth / 2f
        val dotY = layout.height - 10f

        val entryAlpha = ((position - line.startTime) / 300f).coerceIn(0f, 1f)
        val exitAlpha = ((line.endTime - position) / 300f).coerceIn(0f, 1f)
        val groupAlpha = Math.min(entryAlpha, exitAlpha)

        val paint = dotPaint

        for (i in 0 until dotCount) {
            val centerProgress = (i + 1).toFloat() / (dotCount + 1)
            val dist = Math.abs(progress - centerProgress) * (dotCount + 1)
            val focus = (1.0f - dist).coerceIn(0.0f, 1.0f)
            val alpha = (100 + (155 * focus)).toInt()
            val scale = 1.0f + (0.4f * focus)

            paint.alpha = (alpha * groupAlpha).toInt()
            canvas.drawCircle(startX + i * dotSpacing, dotY, dotRadius * scale, paint)
        }
    }

    fun drawSimpleLayout(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x - layout.width / 2f, y - (layout.height / 2f))
        layout.draw(canvas)
        canvas.restore()
    }

    fun cleanTitle(title: String): String {
        val regex = Regex("(?i)\\s*(?:\\(|\\[)?\\b(?:feat|ft)\\b\\.?.*")
        var clean = title.replace(regex, "").trim()
        if (clean.endsWith("(") || clean.endsWith("[")) {
            clean = clean.substring(0, clean.length - 1).trim()
        }
        return clean
    }
}
