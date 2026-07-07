package com.dnk.wallpaperlyrics

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max

data class QueryCandidate(val title: String, val artist: String)

/**
 * Pure metadata sanitization and LRCLIB candidate scoring.
 * No Android dependencies so it stays unit-testable on the JVM.
 */
object TrackQuery {
    // Single "balanced" dial: raise for stricter matching, lower for aggressive.
    const val ACCEPT_THRESHOLD = 0.65
    private const val DURATION_NEAR_SEC = 4.0
    private const val DURATION_REJECT_SEC = 15.0

    private val NOISE_BRACKETS = Regex(
        "[(\\[{][^()\\[\\]{}]*(official|video|audio|lyric|visuali[sz]er|hd|4k|m/?v|" +
        "remaster|explicit|clean|hq|full|slowed|reverb|sped ?up|nightcore|bass ?boost|8d|copyright|free)" +
        "[^()\\[\\]{}]*[)\\]}]",
        RegexOption.IGNORE_CASE
    )
    private val EMPTY_BRACKETS = Regex("[(\\[{]\\s*[)\\]}]")
    private val FEAT = Regex("\\b(feat\\.?|ft\\.?|featuring)\\s+[^()\\[\\]{}|]*", RegexOption.IGNORE_CASE)
    private val FILE_EXT = Regex("\\.(mp3|m4a|flac|wav|ogg|opus|wma)\\s*$", RegexOption.IGNORE_CASE)
    private val TRACK_NUMBER = Regex("^\\d{1,3}[\\s.\\-_]+")
    private val TOPIC_SUFFIX = Regex("\\s*-\\s*topic\\s*$", RegexOption.IGNORE_CASE)
    private val UNKNOWN_ARTIST = Regex("^unknown( artist)?$", RegexOption.IGNORE_CASE)
    private val SPLIT_DELIMITERS = Regex("\\s+[-–—|]\\s+")
    private val WHITESPACE = Regex("\\s+")
    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}\\s]")
    private val COMBINING_MARKS = Regex("\\p{Mn}+")

    /**
     * Ordered query variants, cleanest-guess last:
     * 0) raw metadata as-is (preserves the exact-match behavior and cache keys)
     * 1) cleaned (noise brackets, feat-clauses, file extensions, track numbers stripped)
     * 2) primary artist only (if multiple artists are listed)
     * 3) "Artist - Title" split of the cleaned title, only when the artist is missing/unknown
     */
    fun buildQueries(rawTitle: String, rawArtist: String?): List<QueryCandidate> {
        val title = rawTitle.trim()
        val artist = rawArtist?.trim().orEmpty()

        val candidates = mutableListOf(QueryCandidate(title, artist))

        val cleanedTitle = cleanTitle(title)
        val cleanedArtist = cleanArtist(artist)
        candidates.add(QueryCandidate(cleanedTitle, cleanedArtist))

        // Extract primary/first artist if multiple artists are detected
        val artistSeparators = Regex("[,;&]|\\s+(and|feat\\.?|ft\\.?|featuring)\\s+", RegexOption.IGNORE_CASE)
        val artistParts = artistSeparators.split(cleanedArtist)
        if (artistParts.size > 1) {
            val primaryArtist = artistParts[0].trim()
            if (primaryArtist.isNotBlank() && primaryArtist != cleanedArtist) {
                candidates.add(QueryCandidate(cleanedTitle, primaryArtist))
            }
        }

        if (cleanedArtist.isBlank()) {
            val parts = SPLIT_DELIMITERS.split(cleanedTitle, 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                candidates.add(QueryCandidate(parts[1].trim(), parts[0].trim()))
            }
        }

        return candidates.filter { it.title.isNotBlank() }.distinct().take(4)
    }

    /**
     * 0.0–1.0 match score for one LRCLIB search result against what is playing.
     * Hard rejects (returns 0.0): title similarity < 0.4, or both durations known
     * and differing by >= 15s — a perfect title+artist match with the wrong duration
     * would otherwise score 0.70 and pass the 0.65 threshold.
     * Pass null (never 0.0) for an unknown duration so it scores neutral.
     */
    fun scoreCandidate(
        resultTitle: String,
        resultArtist: String,
        resultDurationSec: Double?,
        wanted: QueryCandidate,
        wantedDurationSec: Double?
    ): Double {
        val titleSim = levenshteinRatio(normalize(resultTitle), normalize(wanted.title))
        if (titleSim < 0.4) return 0.0

        val durationDiff = if (resultDurationSec != null && wantedDurationSec != null) {
            abs(resultDurationSec - wantedDurationSec)
        } else null
        if (durationDiff != null && durationDiff >= DURATION_REJECT_SEC) return 0.0

        val artistSim = if (wanted.artist.isBlank()) 0.5
            else levenshteinRatio(normalize(resultArtist), normalize(wanted.artist))

        val durationScore = when {
            durationDiff == null -> 0.5
            durationDiff <= DURATION_NEAR_SEC -> 1.0
            else -> 1.0 - (durationDiff - DURATION_NEAR_SEC) / (DURATION_REJECT_SEC - DURATION_NEAR_SEC)
        }

        return 0.5 * titleSim + 0.2 * artistSim + 0.3 * durationScore
    }

    private fun cleanTitle(s: String): String {
        var out = FILE_EXT.replace(s, "")
        out = out.replace('_', ' ')
        out = NOISE_BRACKETS.replace(out, " ")
        out = FEAT.replace(out, " ")
        out = EMPTY_BRACKETS.replace(out, " ")
        out = TRACK_NUMBER.replace(out, "")
        out = WHITESPACE.replace(out, " ").trim().trim('-', '|', '–', '—').trim()
        return out.ifBlank { s }
    }

    private fun cleanArtist(s: String): String {
        var out = TOPIC_SUFFIX.replace(s, "")
        out = FEAT.replace(out, " ")
        out = WHITESPACE.replace(out, " ").trim()
        // Placeholder artists are worse than no artist: they poison artist_name queries.
        if (UNKNOWN_ARTIST.matches(out)) return ""
        return out
    }

    internal fun normalize(s: String): String {
        val decomposed = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        val stripped = NON_ALNUM.replace(COMBINING_MARKS.replace(decomposed, ""), " ")
        return WHITESPACE.replace(stripped, " ").trim()
    }

    internal fun levenshteinRatio(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val dist = levenshtein(a, b)
        return 1.0 - dist.toDouble() / max(a.length, b.length)
    }

    private fun levenshtein(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            curr.copyInto(prev)
        }
        return prev[b.length]
    }
}
