package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackQueryTest {

    // --- buildQueries: sanitization ---

    @Test
    fun `local file with everything in title produces split variant`() {
        val queries = TrackQuery.buildQueries("Sam Carter - ZA!P (Official Video).mp3", "")
        assertEquals(QueryCandidate("Sam Carter - ZA!P (Official Video).mp3", ""), queries[0])
        assertTrue(queries.contains(QueryCandidate("Sam Carter - ZA!P", "")))
        assertTrue(queries.contains(QueryCandidate("ZA!P", "Sam Carter")))
    }

    @Test
    fun `noise brackets are stripped`() {
        val queries = TrackQuery.buildQueries("Song D [Official Audio] (4K)", "Artist J")
        assertTrue(queries.contains(QueryCandidate("Song D", "Artist J")))
    }

    @Test
    fun `feat clause is stripped from title`() {
        val queries = TrackQuery.buildQueries("ZA!P (feat. Guest Singer)", "Sam Carter")
        assertTrue(queries.contains(QueryCandidate("ZA!P", "Sam Carter")))
    }

    @Test
    fun `track number and underscores are stripped`() {
        val queries = TrackQuery.buildQueries("01. song_name", "Some Artist")
        assertTrue(queries.contains(QueryCandidate("song name", "Some Artist")))
    }

    @Test
    fun `unknown artist is treated as blank and triggers split`() {
        val queries = TrackQuery.buildQueries("Artist J - Song D", "Unknown Artist")
        assertTrue(queries.contains(QueryCandidate("Song D", "Artist J")))
        assertFalse(queries.any { it.artist.equals("Unknown Artist", ignoreCase = true) && it != queries[0] })
    }

    @Test
    fun `youtube topic suffix is stripped from artist`() {
        val queries = TrackQuery.buildQueries("Some Song", "Some Artist - Topic")
        assertTrue(queries.contains(QueryCandidate("Some Song", "Some Artist")))
    }

    @Test
    fun `clean input passes through as a single raw variant`() {
        val queries = TrackQuery.buildQueries("Song L", "Artist W")
        assertEquals(listOf(QueryCandidate("Song L", "Artist W")), queries)
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

    private val wanted = QueryCandidate("ZA!P", "Sam Carter")

    @Test
    fun `exact match with close duration is accepted`() {
        val score = TrackQuery.scoreCandidate("ZA!P", "Sam Carter", 192.0, wanted, 190.0)
        assertTrue(score >= TrackQuery.ACCEPT_THRESHOLD)
    }

    @Test
    fun `same title and artist but wrong duration is hard rejected`() {
        // Weighted score alone would be 0.70 and pass; the >=15s hard reject must catch it.
        val score = TrackQuery.scoreCandidate("ZA!P", "Sam Carter", 435.0, wanted, 214.0)
        assertEquals(0.0, score, 0.0001)
    }

    @Test
    fun `unrelated title is hard rejected`() {
        val score = TrackQuery.scoreCandidate("Long Song", "Band Q", 190.0, wanted, 190.0)
        assertEquals(0.0, score, 0.0001)
    }

    @Test
    fun `blank wanted artist scores neutral`() {
        val noArtist = QueryCandidate("ZA!P", "")
        val score = TrackQuery.scoreCandidate("ZA!P", "Sam Carter", 190.0, noArtist, 190.0)
        // 0.5*1.0 + 0.2*0.5 + 0.3*1.0 = 0.90
        assertEquals(0.90, score, 0.0001)
    }

    @Test
    fun `unknown durations score neutral instead of zero`() {
        val score = TrackQuery.scoreCandidate("ZA!P", "Sam Carter", null, wanted, null)
        // 0.5*1.0 + 0.2*1.0 + 0.3*0.5 = 0.85
        assertEquals(0.85, score, 0.0001)
        assertTrue(score >= TrackQuery.ACCEPT_THRESHOLD)
    }

    @Test
    fun `near miss duration degrades linearly but can still pass on strong text`() {
        val score = TrackQuery.scoreCandidate("ZA!P", "Sam Carter", 200.0, wanted, 190.0)
        // diff 10s -> durationScore = 1 - (10-4)/(15-4) = 0.4545...; total ~0.836
        assertTrue(score >= TrackQuery.ACCEPT_THRESHOLD)
        val far = TrackQuery.scoreCandidate("ZAIP", "Sam", 204.9, wanted, 190.0)
        assertTrue(far < score)
    }

    @Test
    fun `normalization strips diacritics punctuation and case`() {
        assertEquals("renee", TrackQuery.normalize("Renée"))
        assertEquals("za p", TrackQuery.normalize("ZA!P"))
        assertEquals(1.0, TrackQuery.levenshteinRatio(
            TrackQuery.normalize("Café Olé"), TrackQuery.normalize("cafe ole")), 0.0001)
    }
}
