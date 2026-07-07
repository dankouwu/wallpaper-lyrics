package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackQueryTest {

    // --- buildQueries: sanitization ---

    @Test
    fun `local file with everything in title produces split variant`() {
        val queries = TrackQuery.buildQueries("Travis Scott - FE!N (Official Video).mp3", "")
        assertEquals(QueryCandidate("Travis Scott - FE!N (Official Video).mp3", ""), queries[0])
        assertTrue(queries.contains(QueryCandidate("Travis Scott - FE!N", "")))
        assertTrue(queries.contains(QueryCandidate("FE!N", "Travis Scott")))
    }

    @Test
    fun `noise brackets are stripped`() {
        val queries = TrackQuery.buildQueries("Lucid Dreams [Official Audio] (4K)", "Juice WRLD")
        assertTrue(queries.contains(QueryCandidate("Lucid Dreams", "Juice WRLD")))
    }

    @Test
    fun `feat clause is stripped from title`() {
        val queries = TrackQuery.buildQueries("FE!N (feat. Playboi Carti)", "Travis Scott")
        assertTrue(queries.contains(QueryCandidate("FE!N", "Travis Scott")))
    }

    @Test
    fun `track number and underscores are stripped`() {
        val queries = TrackQuery.buildQueries("01. song_name", "Some Artist")
        assertTrue(queries.contains(QueryCandidate("song name", "Some Artist")))
    }

    @Test
    fun `unknown artist is treated as blank and triggers split`() {
        val queries = TrackQuery.buildQueries("Juice WRLD - Lucid Dreams", "Unknown Artist")
        assertTrue(queries.contains(QueryCandidate("Lucid Dreams", "Juice WRLD")))
        assertFalse(queries.any { it.artist.equals("Unknown Artist", ignoreCase = true) && it != queries[0] })
    }

    @Test
    fun `youtube topic suffix is stripped from artist`() {
        val queries = TrackQuery.buildQueries("Some Song", "Some Artist - Topic")
        assertTrue(queries.contains(QueryCandidate("Some Song", "Some Artist")))
    }

    @Test
    fun `clean input passes through as a single raw variant`() {
        val queries = TrackQuery.buildQueries("Blinding Lights", "The Weeknd")
        assertEquals(listOf(QueryCandidate("Blinding Lights", "The Weeknd")), queries)
    }

    @Test
    fun `variants are deduped and capped`() {
        val queries = TrackQuery.buildQueries("A - B (Official Video) (Lyrics) [HD].flac", "")
        assertEquals(queries, queries.distinct())
        assertTrue(queries.size <= 4)
        assertTrue(queries.all { it.title.isNotBlank() })
    }

    @Test
    fun `title that is all noise falls back to original`() {
        val queries = TrackQuery.buildQueries("(Official Video)", "X")
        assertTrue(queries.all { it.title.isNotBlank() })
    }

    // --- scoreCandidate: the balanced dial ---

    private val wanted = QueryCandidate("FE!N", "Travis Scott")

    @Test
    fun `exact match with close duration is accepted`() {
        val score = TrackQuery.scoreCandidate("FE!N", "Travis Scott", 192.0, wanted, 190.0)
        assertTrue(score >= TrackQuery.ACCEPT_THRESHOLD)
    }

    @Test
    fun `same title and artist but wrong duration is hard rejected`() {
        // Weighted score alone would be 0.70 and pass; the >=15s hard reject must catch it.
        val score = TrackQuery.scoreCandidate("FE!N", "Travis Scott", 435.0, wanted, 214.0)
        assertEquals(0.0, score, 0.0001)
    }

    @Test
    fun `unrelated title is hard rejected`() {
        val score = TrackQuery.scoreCandidate("Bohemian Rhapsody", "Queen", 190.0, wanted, 190.0)
        assertEquals(0.0, score, 0.0001)
    }

    @Test
    fun `blank wanted artist scores neutral`() {
        val noArtist = QueryCandidate("FE!N", "")
        val score = TrackQuery.scoreCandidate("FE!N", "Travis Scott", 190.0, noArtist, 190.0)
        // 0.5*1.0 + 0.2*0.5 + 0.3*1.0 = 0.90
        assertEquals(0.90, score, 0.0001)
    }

    @Test
    fun `unknown durations score neutral instead of zero`() {
        val score = TrackQuery.scoreCandidate("FE!N", "Travis Scott", null, wanted, null)
        // 0.5*1.0 + 0.2*1.0 + 0.3*0.5 = 0.85
        assertEquals(0.85, score, 0.0001)
        assertTrue(score >= TrackQuery.ACCEPT_THRESHOLD)
    }

    @Test
    fun `near miss duration degrades linearly but can still pass on strong text`() {
        val score = TrackQuery.scoreCandidate("FE!N", "Travis Scott", 200.0, wanted, 190.0)
        // diff 10s -> durationScore = 1 - (10-4)/(15-4) = 0.4545...; total ~0.836
        assertTrue(score >= TrackQuery.ACCEPT_THRESHOLD)
        val far = TrackQuery.scoreCandidate("FEIN", "Travis", 204.9, wanted, 190.0)
        assertTrue(far < score)
    }

    @Test
    fun `normalization strips diacritics punctuation and case`() {
        assertEquals("beyonce", TrackQuery.normalize("Beyoncé"))
        assertEquals("fe n", TrackQuery.normalize("FE!N"))
        assertEquals(1.0, TrackQuery.levenshteinRatio(
            TrackQuery.normalize("Déjà Vu"), TrackQuery.normalize("deja vu")), 0.0001)
    }
}
