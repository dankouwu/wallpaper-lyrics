package com.dnk.wallpaperlyrics

import java.util.Collections
import java.util.LinkedHashMap

object SyllableAnimator {

    const val WORD_OVERLAP_MS = 50L
    const val WORD_MOTION_TRAIL_FRACTION = 0.35f
    const val WORD_MOTION_TRAIL_MIN_MS = 120L
    const val WORD_MOTION_TRAIL_MAX_MS = 250L
    const val WORD_MIN_ANIMATION_MS = 200L
    const val BASE_GLIDE_MS = 200f
    const val REFERENCE_DISTANCE_PX = 158f
    const val PRE_ROLL_MAX_LIFT = 50
    const val PRE_ROLL_SETTLE_MS = 150L

    class SyllableInfo(
        val syllableCount: Int,
        val bounds: FloatArray // size syllableCount + 1, from 0f to 1f
    )

    /** A listening session rarely goes past a few hundred distinct words. */
    private const val MAX_CACHE_ENTRIES = 512
    private val cache: MutableMap<String, SyllableInfo> = Collections.synchronizedMap(
        object : LinkedHashMap<String, SyllableInfo>(MAX_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SyllableInfo>?): Boolean =
                size > MAX_CACHE_ENTRIES
        }
    )

    private fun isVowel(c: Char, index: Int): Boolean {
        val lc = c.lowercaseChar()
        return lc == 'a' || lc == 'e' || lc == 'i' || lc == 'o' || lc == 'u' || (lc == 'y' && index > 0)
    }

    private data class VowelGroup(val start: Int, val end: Int, val text: String)

    fun getSyllableInfo(word: String): SyllableInfo {
        return cache.computeIfAbsent(word) { computeSyllableInfo(it) }
    }

    private fun computeSyllableInfo(origWord: String): SyllableInfo {
        if (origWord.isBlank()) {
            return SyllableInfo(0, floatArrayOf(0f, 1f))
        }

        // Map alphabetical characters to their original indices
        val indexMap = mutableListOf<Int>()
        val cleanSb = StringBuilder()
        for (i in origWord.indices) {
            val c = origWord[i]
            if (c.isLetter()) {
                cleanSb.append(c)
                indexMap.add(i)
            }
        }
        val cleanWord = cleanSb.toString()

        if (cleanWord.isEmpty()) {
            // No letters (e.g. punctuation, symbols like "♪")
            return SyllableInfo(0, floatArrayOf(0f, 1f))
        }

        // Find initial vowel groups
        val rawGroups = mutableListOf<VowelGroup>()
        var inGroup = false
        var start = 0
        for (i in cleanWord.indices) {
            val isV = isVowel(cleanWord[i], i)
            if (isV && !inGroup) {
                start = i
                inGroup = true
            } else if (!isV && inGroup) {
                rawGroups.add(VowelGroup(start, i, cleanWord.substring(start, i)))
                inGroup = false
            }
        }
        if (inGroup) {
            rawGroups.add(VowelGroup(start, cleanWord.length, cleanWord.substring(start)))
        }

        // Filter out silent vowels
        val groups = filterVowelGroups(cleanWord, rawGroups)
        val n = groups.size

        if (n <= 1) {
            return SyllableInfo(1, floatArrayOf(0f, 1f))
        }

        // Calculate split indices in cleanWord
        val splits = IntArray(n + 1)
        splits[0] = 0
        splits[n] = cleanWord.length

        for (i in 0 until n - 1) {
            val endA = groups[i].end
            val startB = groups[i + 1].start
            val consonantsBetween = startB - endA

            val splitIdx = when {
                consonantsBetween <= 0 -> endA
                consonantsBetween == 1 -> endA
                consonantsBetween == 2 -> endA + 1
                else -> endA + 1 // split after the first consonant for 3+ consonants
            }
            splits[i + 1] = splitIdx
        }

        // Map split indices in cleanWord back to origWord
        val origSplits = FloatArray(n + 1)
        origSplits[0] = 0f
        origSplits[n] = 1f

        val origLen = origWord.length.toFloat()
        for (i in 1 until n) {
            val cleanIdx = splits[i]
            val origIdx = if (cleanIdx < indexMap.size) {
                indexMap[cleanIdx]
            } else {
                origWord.length
            }
            origSplits[i] = origIdx.toFloat() / origLen
        }

        return SyllableInfo(n, origSplits)
    }

    private fun filterVowelGroups(word: String, groups: List<VowelGroup>): List<VowelGroup> {
        if (groups.size <= 1) return groups
        val result = groups.toMutableList()
        val last = result.last()

        // Rule 1: Silent 'e' at the end
        if (last.text == "e" && last.end == word.length) {
            val prevChar = if (last.start > 0) word[last.start - 1] else ' '
            if (prevChar != 'l') {
                result.removeAt(result.size - 1)
            }
        }
        // Rule 2: Silent 'e' in "es" ending
        else if (last.text == "e" && last.end == word.length - 1 && word.endsWith("es")) {
            val prevChar = if (last.start > 0) word[last.start - 1] else ' '
            val prevPrevChar = if (last.start > 1) word[last.start - 2] else ' '
            val isSibilant = prevChar == 's' || prevChar == 'z' || prevChar == 'x' ||
                             prevChar == 'g' || prevChar == 'c' ||
                             (prevPrevChar == 'c' && prevChar == 'h') ||
                             (prevPrevChar == 's' && prevChar == 'h')
            if (!isSibilant) {
                result.removeAt(result.size - 1)
            }
        }
        // Rule 3: Silent 'e' in "ed" ending
        else if (last.text == "e" && last.end == word.length - 1 && word.endsWith("ed")) {
            val prevChar = if (last.start > 0) word[last.start - 1] else ' '
            if (prevChar != 't' && prevChar != 'd') {
                result.removeAt(result.size - 1)
            }
        }

        return if (result.isEmpty()) groups else result
    }

    // Easing curves
    fun easeOutGlide(x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        val inv = 1f - x
        return 1f - Math.pow(inv.toDouble(), 1.5).toFloat()
    }

    fun glideDurationMs(
        distancePx: Float,
        baseGlideMs: Float = Tuning.baseGlideMs,
        referenceDistancePx: Float = Tuning.referenceDistancePx
    ): Float {
        if (distancePx <= 0f || distancePx.isNaN()) return baseGlideMs
        val factor = Math.sqrt((distancePx / referenceDistancePx).toDouble()).toFloat()
        return (baseGlideMs * factor).coerceIn(baseGlideMs, baseGlideMs * 1.6f)
    }

    fun easeOutExpo(x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        return 1f - Math.pow(2.0, -10.0 * x.toDouble()).toFloat()
    }

    fun easeOutCubic(x: Float): Float {
        val inv = 1f - x
        return 1f - inv * inv * inv
    }

    fun easeInOutCubic(x: Float): Float {
        return if (x < 0.5f) {
            4f * x * x * x
        } else {
            val inv = -2f * x + 2f
            1f - inv * inv * inv / 2f
        }
    }

    const val SYLLABLE_EASE_POWER = 2.2f
    const val SYLLABLE_LINEAR_BLEND = 0.25f

    fun easeSyllableOut(x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        val decay = 1f - Math.pow((1f - x).toDouble(), SYLLABLE_EASE_POWER.toDouble()).toFloat()
        return SYLLABLE_LINEAR_BLEND * x + (1f - SYLLABLE_LINEAR_BLEND) * decay
    }

    /**
     * Maps linear progress (0f to 1f) of a word to its syllable-aware eased progress.
     */
    fun getEasedProgress(linearProgress: Float, wordText: String): Float {
        val p = linearProgress.coerceIn(0f, 1f)
        if (p <= 0f) return 0f
        if (p >= 1f) return 1f

        val info = getSyllableInfo(wordText)
        val n = info.syllableCount

        if (n <= 1) {
            return easeSyllableOut(p)
        }

        // Multi-syllable word
        val r = info.bounds
        var tStart = 0f
        var tEnd = 1f
        var syllableIdx = 0

        for (i in 0 until n) {
            tEnd = if (i + 1 < n) {
                0.7f * ((i + 1).toFloat() / n) + 0.3f * r[i + 1]
            } else {
                1f
            }
            if (p <= tEnd || i == n - 1) {
                syllableIdx = i
                break
            }
            tStart = tEnd
        }

        val rStart = r[syllableIdx]
        val rEnd = r[syllableIdx + 1]
        val u = if (tEnd > tStart) (p - tStart) / (tEnd - tStart) else 0f
        val uEased = easeSyllableOut(u)

        return rStart + uEased * (rEnd - rStart)
    }

    const val HELD_WORD_MIN_DURATION_MS = 575L

    fun isHeldWord(
        wordDurationMs: Long,
        thresholdMs: Long = Tuning.heldWordMinDurationMs
    ): Boolean = wordDurationMs >= thresholdMs

    private fun evalScaleRise(
        u: Float,
        startScale: Float,
        peakScale: Float,
        easeInFraction: Float
    ): Float {
        val clampedU = u.coerceIn(0f, 1f)
        val k = easeInFraction.coerceIn(0.01f, 0.99f)
        return if (clampedU <= k) {
            startScale
        } else {
            val w = (clampedU - k) / (1.0f - k)
            startScale + (peakScale - startScale) * (w * w * (3f - 2f * w))
        }
    }

    private fun evalScaleCubic(
        p: Float,
        s0: Float,
        sPeak: Float,
        xPeak: Float,
        settleScale: Float = Tuning.wordScaleSettle
    ): Float {
        val x = xPeak.coerceIn(0.01f, 0.99f)
        val d1 = settleScale - s0
        val dp = sPeak - s0
        val denomD = x * x * (1f - x) * (1f - x)
        val d = if (denomD > 0f) (x * x * d1 - (2f * x - 1f) * dp) / denomD else 0f
        val c = (dp - x * d1) / (x * (x - 1f)) - (x + 1f) * d
        val b = d1 - c - d
        return s0 + p * (b + p * (c + p * d))
    }

    fun getMotionAmplitude(
        wordDurationMs: Long,
        minDurationMs: Long = Tuning.wordMotionMinDurationMs,
        maxDurationMs: Long = Tuning.wordMotionMaxDurationMs,
        minAmplitude: Float = Tuning.wordMotionMinAmplitude
    ): Float {
        if (wordDurationMs <= 0L) return 1f
        if (wordDurationMs >= maxDurationMs) return 1f
        if (wordDurationMs <= minDurationMs) return minAmplitude
        val t = (wordDurationMs - minDurationMs).toFloat() / (maxDurationMs - minDurationMs).toFloat()
        val smoothT = t * t * (3f - 2f * t)
        return minAmplitude + (1f - minAmplitude) * smoothT
    }

    fun getWordMotionScale(
        linearProgress: Float,
        startScale: Float = Tuning.wordScaleStart,
        peakScale: Float = Tuning.wordScalePeak,
        peakPosition: Float = Tuning.wordScalePeakPosition,
        easeInFraction: Float = Tuning.wordScaleEaseInFraction,
        settleScale: Float = Tuning.wordScaleSettle
    ): Float {
        if (linearProgress.isNaN()) return settleScale
        val p = linearProgress.coerceIn(0f, 1f)
        val x = peakPosition.coerceIn(0.01f, 0.99f)
        return if (p <= x) {
            val u = p / x
            evalScaleRise(u, startScale, peakScale, easeInFraction)
        } else {
            val v = ((p - x) / (1f - x)).coerceIn(0f, 1f)
            val denom = peakScale - settleScale
            val settleNorm = if (denom > 0f) {
                (evalScaleCubic(x + (1f - x) * v, startScale, peakScale, x, settleScale) - settleScale) / denom
            } else {
                1f - v
            }
            settleScale + (peakScale - settleScale) * settleNorm
        }
    }

    fun getWordMotionScale(
        linearProgress: Float,
        motionWindowMs: Long,
        riseDurationMs: Long = Tuning.wordRiseDurationMs,
        startScale: Float = Tuning.wordScaleStart,
        peakScale: Float = Tuning.wordScalePeak,
        peakPosition: Float = Tuning.wordScalePeakPosition,
        easeInFraction: Float = Tuning.wordScaleEaseInFraction,
        settleDurationMs: Long = Tuning.wordSettleDurationMs,
        settleScale: Float = Tuning.wordScaleSettle
    ): Float {
        if (linearProgress.isNaN()) return settleScale
        val p = linearProgress.coerceIn(0f, 1f)
        if (motionWindowMs <= 0L || riseDurationMs <= 0L) {
            return getWordMotionScale(p, startScale, peakScale, peakPosition, easeInFraction, settleScale)
        }

        val windowF = motionWindowMs.toFloat()
        val riseF = riseDurationMs.toFloat()
        val pMax = 0.85f
        val pPeak = Math.min(riseF / windowF, pMax).coerceIn(0.01f, 0.99f)

        val tPeak = pPeak * windowF
        val uPeak = Math.min(1.0f, tPeak / riseF)
        val sPeakActual = evalScaleRise(uPeak, startScale, peakScale, easeInFraction)

        return if (p <= pPeak) {
            val t = p * windowF
            val u = Math.min(1.0f, t / riseF)
            evalScaleRise(u, startScale, peakScale, easeInFraction)
        } else {
            val t = p * windowF
            val settleF = if (settleDurationMs > 0L) settleDurationMs.toFloat() else (windowF - tPeak)
            val availableSettle = windowF - tPeak
            val effectiveSettleF = if (availableSettle > 0f) Math.min(settleF, availableSettle) else settleF
            val v = if (effectiveSettleF > 0f) {
                ((t - tPeak) / effectiveSettleF).coerceIn(0f, 1f)
            } else {
                1.0f
            }
            val denom = peakScale - settleScale
            val settleNorm = if (denom > 0f) {
                (evalScaleCubic(peakPosition + (1.0f - peakPosition) * v, startScale, peakScale, peakPosition, settleScale) - settleScale) / denom
            } else {
                1.0f - v
            }
            settleScale + (sPeakActual - settleScale) * settleNorm
        }
    }

    fun getWordMotionScale(
        linearProgress: Float,
        wordDurationMs: Long,
        settleScale: Float = Tuning.wordScaleSettle
    ): Float {
        val amp = getMotionAmplitude(wordDurationMs)
        val effectivePeak = settleScale + (Tuning.wordScalePeak - settleScale) * amp
        return getWordMotionScale(linearProgress, peakScale = effectivePeak, settleScale = settleScale)
    }

    fun getWordMotionScale(
        linearProgress: Float,
        wordDurationMs: Long,
        motionWindowMs: Long,
        riseDurationMs: Long = Tuning.wordRiseDurationMs,
        settleDurationMs: Long = Tuning.wordSettleDurationMs,
        settleScale: Float = Tuning.wordScaleSettle
    ): Float {
        val amp = getMotionAmplitude(wordDurationMs)
        val effectivePeak = settleScale + (Tuning.wordScalePeak - settleScale) * amp
        return getWordMotionScale(
            linearProgress = linearProgress,
            motionWindowMs = motionWindowMs,
            riseDurationMs = riseDurationMs,
            peakScale = effectivePeak,
            settleDurationMs = settleDurationMs,
            settleScale = settleScale
        )
    }

    fun toLineRelativeScale(absoluteScale: Float, restScale: Float, exitFade: Float = 0f): Float {
        val relativeScale = if (restScale <= 0f) 1f else absoluteScale / restScale
        return 1f + (relativeScale - 1f) * (1f - exitFade)
    }

    // Two smoothstep halves replace the old cubic whose third root entered
    // the word window outside roughly 0.34..0.66, swinging negative.
    fun getWordLift(
        linearProgress: Float,
        textSize: Float,
        peakFraction: Float = Tuning.wordLiftPeakFraction,
        peakPosition: Float = Tuning.wordLiftPeakPosition
    ): Float {
        if (linearProgress.isNaN() || textSize <= 0f) return 0f
        val p = linearProgress.coerceIn(0f, 1f)
        val x = peakPosition.coerceIn(0.01f, 0.99f)
        val peak = peakFraction * textSize
        return if (p <= x) {
            val u = p / x
            peak * u * u * (3f - 2f * u)
        } else {
            val v = (p - x) / (1f - x)
            peak * (1f - v * v * (3f - 2f * v))
        }
    }

    fun getWordLift(
        linearProgress: Float,
        textSize: Float,
        wordDurationMs: Long
    ): Float = getWordLift(linearProgress, textSize) * getMotionAmplitude(wordDurationMs)

    fun getWordGlow(
        linearProgress: Float,
        riseEnd: Float = Tuning.wordGlowRiseEnd,
        holdEnd: Float = Tuning.wordGlowHoldEnd
    ): Float {
        if (linearProgress.isNaN()) return 0f
        val p = linearProgress.coerceIn(0f, 1f)
        val rEnd = riseEnd.coerceIn(0.01f, 0.98f)
        val hEnd = holdEnd.coerceIn(rEnd + 0.001f, 0.99f)
        return when {
            p <= 0f || p >= 1f -> 0f
            p < rEnd -> p / rEnd
            p <= hEnd -> 1f
            else -> (1f - p) / (1f - hEnd)
        }
    }

    fun getActiveLetterIndex(linearProgress: Float, codePointCount: Int): Int {
        if (codePointCount <= 1) return 0
        if (linearProgress.isNaN()) return 0
        val p = linearProgress.coerceIn(0f, 1f)
        val raw = (p * codePointCount).toInt()
        return raw.coerceIn(0, codePointCount - 1)
    }

    fun getActiveLetterPosition(linearProgress: Float, codePointCount: Int): Float {
        if (codePointCount <= 0) return 0f
        if (linearProgress.isNaN()) return 0f
        val p = linearProgress.coerceIn(0f, 1f)
        return p * codePointCount - 0.5f
    }

    fun getRippleEnvelope(
        linearProgress: Float,
        easeInFraction: Float = Tuning.rippleEaseInFraction
    ): Float {
        if (linearProgress.isNaN()) return 0f
        val p = linearProgress.coerceIn(0f, 1f)
        val k = easeInFraction.coerceIn(0.001f, 0.5f)
        return when {
            p < k -> {
                val u = p / k
                u * u * (3f - 2f * u)
            }
            p > 1f - k -> {
                val v = (1f - p) / k
                v * v * (3f - 2f * v)
            }
            else -> 1f
        }
    }

    fun getRippleEmphasis(
        linearProgress: Float,
        distance: Float,
        falloffPower: Float = Tuning.letterFalloffPower,
        easeInFraction: Float = Tuning.rippleEaseInFraction
    ): Float {
        val envelope = getRippleEnvelope(linearProgress, easeInFraction)
        if (envelope <= 0f) return 0f
        val falloff = getRippleFalloff(distance, falloffPower)
        return falloff * envelope
    }

    fun getRippleEmphasis(
        linearProgress: Float,
        codePointIndex: Int,
        codePointCount: Int,
        falloffPower: Float = Tuning.letterFalloffPower,
        easeInFraction: Float = Tuning.rippleEaseInFraction
    ): Float {
        if (codePointCount <= 0) return 0f
        val activePos = getActiveLetterPosition(linearProgress, codePointCount)
        val distance = codePointIndex.toFloat() - activePos
        return getRippleEmphasis(linearProgress, distance, falloffPower, easeInFraction)
    }

    fun getRippleFalloff(distance: Float, power: Float = Tuning.letterFalloffPower): Float {
        val absD = Math.abs(distance)
        val dPowered = if (power == 3f) {
            absD * absD * absD
        } else {
            Math.pow(absD.toDouble(), power.toDouble()).toFloat()
        }
        return 1f / (1f + dPowered)
    }

    fun getRippleFalloff(distance: Int, power: Float = Tuning.letterFalloffPower): Float =
        getRippleFalloff(distance.toFloat(), power)

    @Suppress("UNUSED_PARAMETER")
    fun getHeldWordLetterScale(
        linearProgress: Float,
        distance: Float,
        startScale: Float = Tuning.wordScaleStart,
        peakScale: Float = Tuning.heldWordLetterScalePeak,
        peakPosition: Float = Tuning.wordScalePeakPosition,
        falloffPower: Float = Tuning.letterFalloffPower,
        amplitude: Float = 1f,
        motionWindowMs: Long = 0L,
        riseDurationMs: Long = Tuning.wordRiseDurationMs,
        easeInFraction: Float = Tuning.wordScaleEaseInFraction,
        settleDurationMs: Long = Tuning.wordSettleDurationMs,
        rippleEaseInFraction: Float = Tuning.rippleEaseInFraction
    ): Float {
        if (linearProgress.isNaN()) return 1f
        val p = linearProgress.coerceIn(0f, 1f)
        val emphasis = getRippleEmphasis(p, distance, falloffPower, rippleEaseInFraction)
        val effectivePeak = 1f + (peakScale - 1f) * amplitude
        return 1f + (effectivePeak - 1f) * emphasis
    }

    fun getHeldWordLetterScale(
        linearProgress: Float,
        distance: Int,
        startScale: Float = Tuning.wordScaleStart,
        peakScale: Float = Tuning.heldWordLetterScalePeak,
        peakPosition: Float = Tuning.wordScalePeakPosition,
        falloffPower: Float = Tuning.letterFalloffPower,
        amplitude: Float = 1f,
        motionWindowMs: Long = 0L,
        riseDurationMs: Long = Tuning.wordRiseDurationMs,
        easeInFraction: Float = Tuning.wordScaleEaseInFraction,
        settleDurationMs: Long = Tuning.wordSettleDurationMs,
        rippleEaseInFraction: Float = Tuning.rippleEaseInFraction
    ): Float = getHeldWordLetterScale(
        linearProgress,
        distance.toFloat(),
        startScale,
        peakScale,
        peakPosition,
        falloffPower,
        amplitude,
        motionWindowMs,
        riseDurationMs,
        easeInFraction,
        settleDurationMs,
        rippleEaseInFraction
    )

    fun getLetterScale(
        linearProgress: Float,
        codePointIndex: Int,
        codePointCount: Int,
        durationMs: Long = 0L,
        thresholdMs: Long = Tuning.heldWordMinDurationMs,
        startScale: Float = Tuning.wordScaleStart,
        peakScale: Float = Tuning.heldWordLetterScalePeak,
        peakPosition: Float = Tuning.wordScalePeakPosition,
        falloffPower: Float = Tuning.letterFalloffPower,
        motionWindowMs: Long = 0L,
        riseDurationMs: Long = Tuning.wordRiseDurationMs,
        easeInFraction: Float = Tuning.wordScaleEaseInFraction,
        settleDurationMs: Long = Tuning.wordSettleDurationMs,
        rippleEaseInFraction: Float = Tuning.rippleEaseInFraction,
        settleScale: Float = Tuning.wordScaleSettle
    ): Float {
        val amp = getMotionAmplitude(durationMs)
        val wordScale = if (motionWindowMs > 0L) {
            getWordMotionScale(
                linearProgress = linearProgress,
                wordDurationMs = durationMs,
                motionWindowMs = motionWindowMs,
                riseDurationMs = riseDurationMs,
                settleDurationMs = settleDurationMs,
                settleScale = settleScale
            )
        } else {
            getWordMotionScale(linearProgress, durationMs, settleScale)
        }
        if (!isHeldWord(durationMs, thresholdMs) || codePointCount <= 1) {
            return wordScale
        }
        val activePos = getActiveLetterPosition(linearProgress, codePointCount)
        val distance = codePointIndex.toFloat() - activePos
        val letterRippleScale = getHeldWordLetterScale(
            linearProgress,
            distance,
            startScale,
            peakScale,
            peakPosition,
            falloffPower,
            amp,
            motionWindowMs,
            riseDurationMs,
            easeInFraction,
            settleDurationMs,
            rippleEaseInFraction
        )
        // The word swell belongs to the word and the ripple belongs to the letter.
        return wordScale * letterRippleScale
    }

    fun getLetterLift(
        linearProgress: Float,
        codePointIndex: Int,
        codePointCount: Int,
        textSize: Float,
        durationMs: Long = 0L,
        thresholdMs: Long = Tuning.heldWordMinDurationMs,
        peakFraction: Float = Tuning.wordLiftPeakFraction,
        peakPosition: Float = Tuning.wordLiftPeakPosition,
        falloffPower: Float = Tuning.letterFalloffPower,
        rippleEaseInFraction: Float = Tuning.rippleEaseInFraction
    ): Float {
        val amp = getMotionAmplitude(durationMs)
        if (!isHeldWord(durationMs, thresholdMs) || codePointCount <= 1) {
            return getWordLift(linearProgress, textSize, peakFraction, peakPosition) * amp
        }
        val emphasis = getRippleEmphasis(linearProgress, codePointIndex, codePointCount, falloffPower, rippleEaseInFraction)
        val maxLift = peakFraction * textSize * amp
        return maxLift * emphasis
    }

    /**
     * Extends a word animation window to at least [WORD_MIN_ANIMATION_MS] or its original duration plus
     * [WORD_OVERLAP_MS], clamped to [lineEndMs] so the final word of a line completes before the line
     * goes inactive, and bounded below by [endMs].
     */
    fun getExtendedWordEnd(
        startMs: Long,
        endMs: Long,
        lineEndMs: Long,
        overlapMs: Long = Tuning.wordOverlapMs,
        minAnimationMs: Long = Tuning.wordMinAnimationMs
    ): Long {
        val duration = endMs - startMs
        val animated = if (duration <= 0L) {
            minAnimationMs
        } else {
            Math.max(duration + overlapMs, minAnimationMs)
        }
        val clamped = Math.min(startMs + animated, lineEndMs)
        return Math.max(endMs, clamped)
    }

    /**
     * Shifts word motion start earlier by [leadInMs] so that the rise peak lands closer
     * to word onset, clamped so motion never begins before [lineStartMs] or before the
     * previous word has finished being sung at [prevWordEndMs].
     */
    fun getMotionWordStart(
        startMs: Long,
        lineStartMs: Long,
        prevWordEndMs: Long = lineStartMs,
        leadInMs: Long = Tuning.wordLeadInMs
    ): Long {
        if (leadInMs <= 0L) return startMs
        val earliestAllowed = Math.max(lineStartMs, prevWordEndMs)
        val candidate = startMs - leadInMs
        return Math.min(startMs, Math.max(candidate, earliestAllowed))
    }

    fun getEffectiveMotionFloor(
        floorMs: Long = Tuning.wordMotionDurationFloorMs,
        leadInMs: Long = Tuning.wordLeadInMs,
        riseMs: Long = Tuning.wordRiseDurationMs,
        settleMs: Long = Tuning.wordSettleDurationMs
    ): Long = Math.max(floorMs, leadInMs + riseMs + settleMs)

    /**
     * Extends a word motion window beyond its sweep end with a trail that scales with the word so that every
     * word hands over at a similar point in its lift regardless of how long it was sung, bounded at both ends
     * by [WORD_MOTION_TRAIL_MIN_MS] and [WORD_MOTION_TRAIL_MAX_MS], clamped so motion does not extend beyond
     * [lineEndMs] plus [maxOverrunMs] into the fade, and bounded below by the sweep end and effective floor.
     */
    fun getMotionWordEnd(
        startMs: Long,
        endMs: Long,
        lineEndMs: Long,
        trailFraction: Float = Tuning.wordMotionTrailFraction,
        trailMinMs: Long = Tuning.wordMotionTrailMinMs,
        trailMaxMs: Long = Tuning.wordMotionTrailMaxMs,
        overlapMs: Long = Tuning.wordOverlapMs,
        minAnimationMs: Long = Tuning.wordMinAnimationMs,
        motionDurationFloorMs: Long = Tuning.wordMotionDurationFloorMs,
        maxOverrunMs: Long = 0L,
        leadInMs: Long = 0L,
        riseDurationMs: Long = 0L,
        settleDurationMs: Long = 0L
    ): Long {
        val sweepEnd = getExtendedWordEnd(startMs, endMs, lineEndMs, overlapMs, minAnimationMs)
        val shapeDuration = leadInMs + riseDurationMs + settleDurationMs
        val effectiveFloor = if (shapeDuration > 0L) Math.max(motionDurationFloorMs, shapeDuration) else motionDurationFloorMs
        val maxMotionEnd = lineEndMs + maxOverrunMs
        val scaled = ((sweepEnd - startMs) * trailFraction).toLong()
        val trail = scaled.coerceIn(trailMinMs, trailMaxMs)
        val candidate = Math.max(sweepEnd, Math.min(sweepEnd + trail, maxMotionEnd))
        val floored = Math.min(startMs + effectiveFloor, maxMotionEnd)
        return Math.max(candidate, Math.max(sweepEnd, floored))
    }

    fun getPreRollInactiveAlpha(
        currentPos: Long,
        lineStartTime: Long,
        firstWordOnset: Long,
        settleDurationMs: Long = Tuning.preRollSettleMs,
        maxLift: Int = Tuning.preRollMaxLift
    ): Int {
        val preRollGap = firstWordOnset - lineStartTime
        if (preRollGap <= 0L || currentPos < lineStartTime) {
            return INACTIVE_LYRIC_ALPHA
        }

        return if (currentPos < firstWordOnset) {
            val progress = ((currentPos - lineStartTime).toFloat() / preRollGap.toFloat()).coerceIn(0f, 1f)
            (INACTIVE_LYRIC_ALPHA + (maxLift * progress)).toInt().coerceIn(0, 255)
        } else {
            if (settleDurationMs <= 0L) {
                return INACTIVE_LYRIC_ALPHA
            }
            val elapsed = currentPos - firstWordOnset
            if (elapsed >= settleDurationMs) {
                return INACTIVE_LYRIC_ALPHA
            }
            val settleProgress = (elapsed.toFloat() / settleDurationMs.toFloat()).coerceIn(0f, 1f)
            val decay = 1f - easeInOutCubic(settleProgress)
            (INACTIVE_LYRIC_ALPHA + (maxLift * decay)).toInt().coerceIn(0, 255)
        }
    }
}
