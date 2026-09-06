package com.dnk.wallpaperlyrics

import java.util.Collections
import java.util.LinkedHashMap

object SyllableAnimator {

    private const val LETTER_MOTION_MINIMUM_MS = 150L
    const val WORD_OVERLAP_MS = 50L
    const val WORD_MIN_ANIMATION_MS = 200L
    const val BASE_GLIDE_MS = 200f
    const val REFERENCE_DISTANCE_PX = 158f

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

    fun glideDurationMs(distancePx: Float): Float {
        if (distancePx <= 0f || distancePx.isNaN()) return BASE_GLIDE_MS
        val factor = Math.sqrt((distancePx / REFERENCE_DISTANCE_PX).toDouble()).toFloat()
        return (BASE_GLIDE_MS * factor).coerceIn(BASE_GLIDE_MS, BASE_GLIDE_MS * 1.6f)
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

    fun getWordMotionScale(linearProgress: Float): Float {
        val progress = linearProgress.coerceIn(0f, 1f)
        return 1f + 0.04f * Math.sin(Math.PI * progress).toFloat()
    }

    fun usesPerLetterMotion(durationMs: Long, codePointCount: Int): Boolean =
        codePointCount > 0 && durationMs >= codePointCount.toLong() * LETTER_MOTION_MINIMUM_MS

    fun getWholeWordLift(linearProgress: Float, textSize: Float): Float {
        val progress = linearProgress.coerceIn(0f, 1f)
        return textSize * 0.06f * Math.sin(Math.PI * progress).toFloat()
    }

    fun getLetterLift(
        linearProgress: Float,
        codePointIndex: Int,
        codePointCount: Int,
        textSize: Float
    ): Float {
        val progress = linearProgress.coerceIn(0f, 1f)
        if (progress <= 0f || progress >= 1f || codePointIndex !in 0 until codePointCount) return 0f

        val start = codePointIndex.toFloat() / codePointCount
        if (progress <= start) return 0f

        val localProgress = (progress - start) / (1f - start)
        return textSize * 0.06f * Math.sin(Math.PI * localProgress).toFloat()
    }

    /**
     * Extends a word animation window to at least [WORD_MIN_ANIMATION_MS] or its original duration plus
     * [WORD_OVERLAP_MS], clamped to [lineEndMs] so the final word of a line completes before the line
     * goes inactive, and bounded below by [endMs].
     */
    fun getExtendedWordEnd(startMs: Long, endMs: Long, lineEndMs: Long): Long {
        val duration = endMs - startMs
        val animated = if (duration <= 0L) {
            WORD_MIN_ANIMATION_MS
        } else {
            Math.max(duration + WORD_OVERLAP_MS, WORD_MIN_ANIMATION_MS)
        }
        val clamped = Math.min(startMs + animated, lineEndMs)
        return Math.max(endMs, clamped)
    }
}
