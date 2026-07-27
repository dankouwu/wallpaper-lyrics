package com.dnk.wallpaperlyrics

import java.util.concurrent.ConcurrentHashMap

object SyllableAnimator {

    class SyllableInfo(
        val syllableCount: Int,
        val bounds: FloatArray // size syllableCount + 1, from 0f to 1f
    )

    private val cache = ConcurrentHashMap<String, SyllableInfo>()

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

    fun easeSyllable(x: Float): Float {
        // A custom fast-slow-fast curve to mimic natural pronunciation:
        // quick consonants at the start/end, and lingering vowels in the middle.
        // We use a cubic function centered at 0.5: f(x) = 4 * (x - 0.5)^3 + 0.5
        // and blend it with a linear component (15% linear / 85% cubic) to ensure
        // a smooth, non-zero speed while keeping the transition highly eased and dramatic.
        val cx = x - 0.5f
        val cubic = 4f * cx * cx * cx + 0.5f
        return 0.15f * x + 0.85f * cubic
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
            // Single syllable: use easeSyllable for a smooth transition
            return easeSyllable(p)
        }

        // Multi-syllable word
        val r = info.bounds
        val t = FloatArray(n + 1)
        t[0] = 0f
        t[n] = 1f
        for (i in 1 until n) {
            // Blend equal time intervals (70%) and character length proportions (30%)
            t[i] = 0.7f * (i.toFloat() / n) + 0.3f * r[i]
        }

        // Find which syllable we are currently in
        var syllableIdx = 0
        for (i in 0 until n) {
            if (p >= t[i] && p <= t[i + 1]) {
                syllableIdx = i
                break
            }
        }

        val tStart = t[syllableIdx]
        val tEnd = t[syllableIdx + 1]
        val rStart = r[syllableIdx]
        val rEnd = r[syllableIdx + 1]

        // Local progress within the current syllable (0.0 to 1.0)
        val u = if (tEnd > tStart) (p - tStart) / (tEnd - tStart) else 0f
        val uEased = easeSyllable(u)

        // Map back to the word's character space bounds
        return rStart + uEased * (rEnd - rStart)
    }
}

