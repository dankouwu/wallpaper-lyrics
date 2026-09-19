package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LyricsManagerTest {

    @Test
    fun `parseLrcText preserves close authoritative line start timestamps`() {
        val parsed = LyricsManager.parseLrcText(
            """
                [00:38.81]A deliberately lengthy first lyric line
                [00:41.14]A deliberately lengthy second lyric line
                [00:44.59]A deliberately lengthy third lyric line
            """.trimIndent(),
            durationMs = 60_000L
        )

        assertNotNull(parsed)
        val lyrics = parsed!!.filterNot { it.isInstrumental }
        assertEquals(listOf(38_810L, 41_140L, 44_590L), lyrics.map { it.startTime })
    }

    @Test
    fun `authoritative word duration over 800ms is preserved`() {
        val parsed = LyricsManager.parseLrcText(
            "[00:01.00]<00:01.00>slow<00:03.50>",
            isAuthoritative = true
        )

        assertNotNull(parsed)
        val word = parsed!!.first { !it.isInstrumental }.words!!.first()
        assertEquals(1_000L, word.startTime)
        assertEquals(3_500L, word.endTime)
    }

    @Test
    fun `word duration below estimate is unchanged either way`() {
        val lrc = "[00:01.00]<00:01.00>fast<00:01.30>"
        val defaultParsed = LyricsManager.parseLrcText(lrc, isAuthoritative = false)
        val authoritativeParsed = LyricsManager.parseLrcText(lrc, isAuthoritative = true)

        assertNotNull(defaultParsed)
        assertNotNull(authoritativeParsed)

        val defaultWord = defaultParsed!!.first { !it.isInstrumental }.words!!.first()
        val authoritativeWord = authoritativeParsed!!.first { !it.isInstrumental }.words!!.first()

        assertEquals(1_300L, defaultWord.endTime)
        assertEquals(1_300L, authoritativeWord.endTime)
    }

    @Test
    fun `fetched lyrics keep duration estimate on default path`() {
        val parsed = LyricsManager.parseLrcText(
            "[00:01.00]<00:01.00>slow<00:03.50>"
        )

        assertNotNull(parsed)
        val word = parsed!!.first { !it.isInstrumental }.words!!.first()
        assertEquals(1_630L, word.endTime)
    }

    @Test
    fun `round trip stability preserves identical word start and end times`() {
        val originalLrc = "[00:01.00]<00:01.00>held<00:03.80><00:04.20>second<00:04.90>third<00:05.50>"
        val firstPass = LyricsManager.parseLrcText(originalLrc, isAuthoritative = true)
        assertNotNull(firstPass)

        val renderedLrc = LyricsManager.renderLrc(firstPass!!)
        val secondPass = LyricsManager.parseLrcText(renderedLrc, isAuthoritative = true)
        assertNotNull(secondPass)

        val firstWords = firstPass.first { !it.isInstrumental }.words!!
        val secondWords = secondPass!!.first { !it.isInstrumental }.words!!

        assertEquals(firstWords.size, secondWords.size)
        for (i in firstWords.indices) {
            assertEquals(firstWords[i].startTime, secondWords[i].startTime)
            assertEquals(firstWords[i].endTime, secondWords[i].endTime)
            assertEquals(firstWords[i].text, secondWords[i].text)
        }
    }

    @Test
    fun `centisecond precision round trips exactly`() {
        val timesMs = listOf(50L, 990L, 1_050L, 12_990L, 65_430L)
        for (timeMs in timesMs) {
            val formatted = LyricsManager.formatTime(timeMs)
            val lrc = "[$formatted] Test"
            val parsed = LyricsManager.parseLrcText(lrc)
            assertNotNull(parsed)
            val line = parsed!!.first { !it.isInstrumental }
            assertEquals(timeMs, line.startTime)
        }

        val wordLrc = "[00:01.05]<00:01.05>alpha<00:02.99>"
        val wordParsed = LyricsManager.parseLrcText(wordLrc, isAuthoritative = true)
        assertNotNull(wordParsed)
        val word = wordParsed!!.first { !it.isInstrumental }.words!!.first()
        assertEquals(1_050L, word.startTime)
        assertEquals(2_990L, word.endTime)
    }

    @Test
    fun `line start times set by hand survive parsing including first and last line`() {
        val lrc = """
            [00:08.50]First line starting after five seconds
            [00:15.20]Middle line
            [00:25.80]Last line of the song
        """.trimIndent()

        val parsed = LyricsManager.parseLrcText(lrc, isAuthoritative = true)
        assertNotNull(parsed)

        val lyricLines = parsed!!.filterNot { it.isInstrumental }
        assertEquals(listOf(8_500L, 15_200L, 25_800L), lyricLines.map { it.startTime })
    }

    @Test
    fun `renderLrc preserves intermediate word end times when gap exists`() {
        val lrc = "[00:01.00]<00:01.00>gap<00:02.00><00:03.00>word<00:04.00>"
        val parsed = LyricsManager.parseLrcText(lrc, isAuthoritative = true)
        assertNotNull(parsed)

        val words = parsed!!.first { !it.isInstrumental }.words!!
        assertEquals(2_000L, words[0].endTime)
        assertEquals(3_000L, words[1].startTime)

        val rendered = LyricsManager.renderLrc(parsed)
        org.junit.Assert.assertTrue(rendered.contains("<00:02.00>"))

        val secondParsed = LyricsManager.parseLrcText(rendered, isAuthoritative = true)
        assertNotNull(secondParsed)

        val roundTripWords = secondParsed!!.first { !it.isInstrumental }.words!!
        assertEquals(2_000L, roundTripWords[0].endTime)
        assertEquals(3_000L, roundTripWords[1].startTime)
        assertEquals(4_000L, roundTripWords[1].endTime)
    }

    @Test
    fun `override wins over cache without attempting fetch`() {
        val tempDir = Files.createTempDirectory("storage_test").toFile()
        try {
            val overridesDir = File(tempDir, "lyrics_overrides")
            val cacheDir = File(tempDir, "lyrics_cache")
            val storage = LyricsStorage(overridesDir, cacheDir)

            val overrideLines = listOf(LyricLine(1000L, 2500L, "Override Line"))
            val cachedLines = listOf(LyricLine(1000L, 1800L, "Cached Line"))

            storage.saveOverride("Song Title", "Artist Name", overrideLines)
            storage.saveCache("Song Title", "Artist Name", cachedLines)

            val resolution = storage.resolveLyrics("Song Title", "Artist Name")
            assertTrue(resolution is LyricsResolution.Override)
            assertEquals(overrideLines, (resolution as LyricsResolution.Override).lines)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `lookup order follows override then cache then miss within ttl then needs fetch`() {
        val tempDir = Files.createTempDirectory("storage_test").toFile()
        try {
            val overridesDir = File(tempDir, "lyrics_overrides")
            val cacheDir = File(tempDir, "lyrics_cache")
            val storage = LyricsStorage(overridesDir, cacheDir)

            val overrideLines = listOf(LyricLine(1000L, 2500L, "Override Line"))
            val cachedLines = listOf(LyricLine(1000L, 1800L, "Cached Line"))
            val now = 10_000_000L

            // Step 1: Override present -> Override returned
            storage.saveOverride("Song", "Artist", overrideLines)
            assertEquals(LyricsResolution.Override(overrideLines), storage.resolveLyrics("Song", "Artist", now))

            // Step 2: Override absent, Cache present -> Cached returned
            storage.clearAllOverrides()
            storage.saveCache("Song", "Artist", cachedLines)
            assertEquals(LyricsResolution.Cached(cachedLines), storage.resolveLyrics("Song", "Artist", now))

            // Step 3: Override and Cache absent, Miss within TTL -> Miss returned
            storage.clearCache()
            storage.recordMiss("Song", "Artist", timestampMs = now - 1000L)
            assertEquals(LyricsResolution.Miss, storage.resolveLyrics("Song", "Artist", now))

            // Step 4: Miss outside TTL -> NeedsFetch returned and miss cleared
            val expiredTime = now + 25 * 60 * 60 * 1000L
            assertEquals(LyricsResolution.NeedsFetch, storage.resolveLyrics("Song", "Artist", expiredTime))
            assertFalse(storage.getMissFile("Song", "Artist").exists())

            // Step 5: None present -> NeedsFetch returned
            assertEquals(LyricsResolution.NeedsFetch, storage.resolveLyrics("Unknown", "Artist", now))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `cache clear preserves overrides`() {
        val tempDir = Files.createTempDirectory("storage_test").toFile()
        try {
            val overridesDir = File(tempDir, "lyrics_overrides")
            val cacheDir = File(tempDir, "lyrics_cache")
            val storage = LyricsStorage(overridesDir, cacheDir)

            val overrideLines = listOf(LyricLine(1000L, 2000L, "Hand Edited"))
            val cachedLines = listOf(LyricLine(1000L, 2000L, "Fetched Cache"))

            storage.saveOverride("Song A", "Artist A", overrideLines)
            storage.saveCache("Song B", "Artist B", cachedLines)

            storage.clearCache()

            assertEquals(LyricsResolution.Override(overrideLines), storage.resolveLyrics("Song A", "Artist A"))
            assertEquals(LyricsResolution.NeedsFetch, storage.resolveLyrics("Song B", "Artist B"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `override clear removes overrides and preserves cached entries`() {
        val tempDir = Files.createTempDirectory("storage_test").toFile()
        try {
            val overridesDir = File(tempDir, "lyrics_overrides")
            val cacheDir = File(tempDir, "lyrics_cache")
            val storage = LyricsStorage(overridesDir, cacheDir)

            val overrideLines = listOf(LyricLine(1000L, 2000L, "Hand Edited"))
            val cachedLines = listOf(LyricLine(1000L, 2000L, "Fetched Cache"))

            storage.saveOverride("Song A", "Artist A", overrideLines)
            storage.saveCache("Song B", "Artist B", cachedLines)

            storage.clearAllOverrides()

            assertEquals(LyricsResolution.NeedsFetch, storage.resolveLyrics("Song A", "Artist A"))
            assertEquals(LyricsResolution.Cached(cachedLines), storage.resolveLyrics("Song B", "Artist B"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `force re-fetch preserves overrides while clearing cached entries`() {
        val tempDir = Files.createTempDirectory("storage_test").toFile()
        try {
            val overridesDir = File(tempDir, "lyrics_overrides")
            val cacheDir = File(tempDir, "lyrics_cache")
            val storage = LyricsStorage(overridesDir, cacheDir)

            val overrideLines = listOf(LyricLine(1000L, 2000L, "Override"))
            val cachedLines = listOf(LyricLine(1000L, 2000L, "Cache"))

            storage.saveOverride("Song A", "Artist A", overrideLines)
            storage.saveCache("Song A", "Artist A", cachedLines)
            storage.recordMiss("Song A", "Artist A")

            // deleteCacheFor is what FORCE_RELOAD_LYRICS calls
            storage.deleteCacheFor("Song A", "Artist A")

            assertFalse(storage.getCacheFile("Song A", "Artist A").exists())
            assertFalse(storage.getMissFile("Song A", "Artist A").exists())
            assertTrue(storage.getOverrideFile("Song A", "Artist A").exists())
            assertEquals(LyricsResolution.Override(overrideLines), storage.resolveLyrics("Song A", "Artist A"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `empty save clears one override without touching others`() {
        val tempDir = Files.createTempDirectory("storage_test").toFile()
        try {
            val overridesDir = File(tempDir, "lyrics_overrides")
            val cacheDir = File(tempDir, "lyrics_cache")
            val storage = LyricsStorage(overridesDir, cacheDir)

            val overrideA = listOf(LyricLine(1000L, 2000L, "Override A"))
            val overrideB = listOf(LyricLine(1000L, 2000L, "Override B"))

            storage.saveOverride("Song A", "Artist A", overrideA)
            storage.saveOverride("Song B", "Artist B", overrideB)

            // Clearing override for Song A (simulating empty save)
            storage.clearOverride("Song A", "Artist A")

            assertEquals(LyricsResolution.NeedsFetch, storage.resolveLyrics("Song A", "Artist A"))
            assertEquals(LyricsResolution.Override(overrideB), storage.resolveLyrics("Song B", "Artist B"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `missing or unwritable overrides directory handled without throwing`() {
        val tempDir = Files.createTempDirectory("storage_test").toFile()
        try {
            val missingDir = File(tempDir, "non_existent_subdir")
            val cacheDir = File(tempDir, "lyrics_cache")
            val storage = LyricsStorage(missingDir, cacheDir)

            // Reading from missing directory should return null without throwing
            assertNull(storage.getOverride("Song", "Artist"))
            assertEquals(LyricsResolution.NeedsFetch, storage.resolveLyrics("Song", "Artist"))

            // An unwritable directory (simulate by using a regular file as the directory path)
            val blockedFile = File(tempDir, "blocked_as_file")
            blockedFile.createNewFile()
            val unwritableStorage = LyricsStorage(blockedFile, cacheDir)

            assertNull(unwritableStorage.getOverride("Song", "Artist"))
            val saveResult = unwritableStorage.saveOverride("Song", "Artist", listOf(LyricLine(100L, 200L, "Test")))
            assertFalse(saveResult)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `storage layer constructs no path under cache directory`() {
        val tempDir = Files.createTempDirectory("storage_path_test").toFile()
        try {
            val filesDir = File(tempDir, "files").apply { mkdirs() }
            val cacheDir = File(tempDir, "cache").apply { mkdirs() }

            val storage = LyricsStorage.create(filesDir, cacheDir)

            val cacheFile = storage.getCacheFile("Song Title", "Artist Name")
            val missFile = storage.getMissFile("Song Title", "Artist Name")
            val overrideFile = storage.getOverrideFile("Song Title", "Artist Name")

            assertFalse(cacheFile.canonicalPath.startsWith(cacheDir.canonicalPath))
            assertFalse(missFile.canonicalPath.startsWith(cacheDir.canonicalPath))
            assertFalse(overrideFile.canonicalPath.startsWith(cacheDir.canonicalPath))
            assertFalse(storage.fetchedDir.canonicalPath.startsWith(cacheDir.canonicalPath))

            assertTrue(cacheFile.canonicalPath.startsWith(filesDir.canonicalPath))
            assertTrue(missFile.canonicalPath.startsWith(filesDir.canonicalPath))
            assertTrue(overrideFile.canonicalPath.startsWith(filesDir.canonicalPath))
            assertTrue(storage.fetchedDir.canonicalPath.startsWith(filesDir.canonicalPath))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `migration moves entries from old cache to new location, removes old dir, and is idempotent`() {
        val tempDir = Files.createTempDirectory("storage_migration_test").toFile()
        try {
            val filesDir = File(tempDir, "files").apply { mkdirs() }
            val oldCacheDir = File(tempDir, "old_cache").apply { mkdirs() }
            val fetchedDir = File(filesDir, LyricsStorage.FETCHED_DIR_NAME).apply { mkdirs() }
            val overridesDir = File(filesDir, LyricsStorage.OVERRIDES_DIR_NAME).apply { mkdirs() }

            val storage = LyricsStorage(overridesDir, fetchedDir)

            val legacyEntry1 = File(oldCacheDir, "entry1.json").apply { writeText("""[{"startTime":1000,"endTime":2000,"content":"One"}]""") }
            val legacyEntry2 = File(oldCacheDir, "entry2.json").apply { writeText("""[{"startTime":3000,"endTime":4000,"content":"Two"}]""") }
            val legacyMiss = File(oldCacheDir, "entry3.miss").apply { writeText("123456789") }

            val result1 = storage.migrateOldCache(oldCacheDir, fetchedDir)
            assertTrue(result1)

            val destEntry1 = File(fetchedDir, "entry1.json")
            val destEntry2 = File(fetchedDir, "entry2.json")
            val destMiss = File(fetchedDir, "entry3.miss")

            assertTrue(destEntry1.exists())
            assertTrue(destEntry2.exists())
            assertTrue(destMiss.exists())
            assertEquals(legacyEntry1.name, destEntry1.name)
            assertEquals(legacyEntry2.name, destEntry2.name)
            assertEquals(legacyMiss.name, destMiss.name)
            assertEquals("""[{"startTime":1000,"endTime":2000,"content":"One"}]""", destEntry1.readText())
            assertEquals("""[{"startTime":3000,"endTime":4000,"content":"Two"}]""", destEntry2.readText())
            assertEquals("123456789", destMiss.readText())

            assertFalse(oldCacheDir.exists())

            val result2 = storage.migrateOldCache(oldCacheDir, fetchedDir)
            assertTrue(result2)
            assertTrue(destEntry1.exists())
            assertTrue(destEntry2.exists())
            assertTrue(destMiss.exists())
            assertFalse(oldCacheDir.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `migration conflict keeps destination copy and discards stale source copy`() {
        val tempDir = Files.createTempDirectory("storage_conflict_test").toFile()
        try {
            val filesDir = File(tempDir, "files").apply { mkdirs() }
            val oldCacheDir = File(tempDir, "old_cache").apply { mkdirs() }
            val fetchedDir = File(filesDir, LyricsStorage.FETCHED_DIR_NAME).apply { mkdirs() }
            val overridesDir = File(filesDir, LyricsStorage.OVERRIDES_DIR_NAME).apply { mkdirs() }

            val storage = LyricsStorage(overridesDir, fetchedDir)

            val conflictFileInOld = File(oldCacheDir, "song.json").apply { writeText("OLD_STALE_CONTENT") }
            val conflictFileInDest = File(fetchedDir, "song.json").apply { writeText("NEW_DESTINATION_CONTENT") }

            val result = storage.migrateOldCache(oldCacheDir, fetchedDir)
            assertTrue(result)

            assertEquals("NEW_DESTINATION_CONTENT", conflictFileInDest.readText())
            assertFalse(conflictFileInOld.exists())
            assertFalse(oldCacheDir.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `interrupted migration loses nothing and completes on subsequent run`() {
        val tempDir = Files.createTempDirectory("storage_interrupted_test").toFile()
        try {
            val filesDir = File(tempDir, "files").apply { mkdirs() }
            val oldCacheDir = File(tempDir, "old_cache").apply { mkdirs() }
            val fetchedDir = File(filesDir, LyricsStorage.FETCHED_DIR_NAME).apply { mkdirs() }
            val overridesDir = File(filesDir, LyricsStorage.OVERRIDES_DIR_NAME).apply { mkdirs() }

            val storage = LyricsStorage(overridesDir, fetchedDir)

            val dest1 = File(fetchedDir, "song1.json").apply { writeText("CONTENT_1") }
            File(oldCacheDir, "song2.json").writeText("CONTENT_2")

            val result = storage.migrateOldCache(oldCacheDir, fetchedDir)
            assertTrue(result)

            val dest2 = File(fetchedDir, "song2.json")
            assertTrue(dest1.exists())
            assertTrue(dest2.exists())
            assertEquals("CONTENT_1", dest1.readText())
            assertEquals("CONTENT_2", dest2.readText())
            assertFalse(oldCacheDir.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `eviction respects cap, removes least recently used first, and never removes overrides`() {
        val tempDir = Files.createTempDirectory("storage_eviction_test").toFile()
        try {
            val overridesDir = File(tempDir, "lyrics_overrides").apply { mkdirs() }
            val fetchedDir = File(tempDir, "lyrics_cache").apply { mkdirs() }
            val storage = LyricsStorage(overridesDir, fetchedDir)

            val overrideLine = listOf(LyricLine(100L, 200L, "Override"))
            storage.saveOverride("KeepOverride1", "Artist", overrideLine)
            storage.saveOverride("KeepOverride2", "Artist", overrideLine)

            val baseTime = 1_000_000L
            val songs = listOf("Song1", "Song2", "Song3", "Song4", "Song5")
            for (i in songs.indices) {
                val song = songs[i]
                storage.saveCache(song, "Artist", listOf(LyricLine(100L, 200L, song)))
                val file = storage.getCacheFile(song, "Artist")
                file.setLastModified(baseTime + (i * 10_000L))
            }

            storage.evictOldEntries(cap = 3)

            assertFalse("Song1 should be evicted", storage.getCacheFile("Song1", "Artist").exists())
            assertFalse("Song2 should be evicted", storage.getCacheFile("Song2", "Artist").exists())

            assertTrue("Song3 should survive", storage.getCacheFile("Song3", "Artist").exists())
            assertTrue("Song4 should survive", storage.getCacheFile("Song4", "Artist").exists())
            assertTrue("Song5 should survive", storage.getCacheFile("Song5", "Artist").exists())

            assertTrue("Override 1 must survive eviction", storage.getOverrideFile("KeepOverride1", "Artist").exists())
            assertTrue("Override 2 must survive eviction", storage.getOverrideFile("KeepOverride2", "Artist").exists())
            assertNotNull(storage.getOverride("KeepOverride1", "Artist"))
            assertNotNull(storage.getOverride("KeepOverride2", "Artist"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `cleanup removes expired miss markers and preserves unexpired ones`() {
        val tempDir = Files.createTempDirectory("storage_miss_cleanup_test").toFile()
        try {
            val overridesDir = File(tempDir, "lyrics_overrides").apply { mkdirs() }
            val fetchedDir = File(tempDir, "lyrics_cache").apply { mkdirs() }
            val storage = LyricsStorage(overridesDir, fetchedDir)

            val now = 100_000_000L
            val expiredTime = now - (25 * 60 * 60 * 1000L)
            val freshTime = now - (1 * 60 * 60 * 1000L)

            val expiredMissFile = storage.getMissFile("ExpiredSong", "Artist").apply { writeText(expiredTime.toString()) }
            val freshMissFile = storage.getMissFile("FreshSong", "Artist").apply { writeText(freshTime.toString()) }

            assertTrue(expiredMissFile.exists())
            assertTrue(freshMissFile.exists())

            storage.cleanupExpiredMisses(currentTimeMs = now)

            assertFalse("Expired miss marker must be deleted", expiredMissFile.exists())
            assertTrue("Fresh miss marker must be retained", freshMissFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `in app cache clear empties fetched store in its new location and leaves overrides intact`() {
        val tempDir = Files.createTempDirectory("storage_clear_test").toFile()
        try {
            val filesDir = File(tempDir, "files").apply { mkdirs() }
            val cacheDir = File(tempDir, "cache").apply { mkdirs() }
            val storage = LyricsStorage.create(filesDir, cacheDir)

            val overrideLines = listOf(LyricLine(1000L, 2000L, "Keep Me"))
            val fetchedLines = listOf(LyricLine(1000L, 2000L, "Delete Me"))

            storage.saveOverride("Song A", "Artist A", overrideLines)
            storage.saveCache("Song B", "Artist B", fetchedLines)
            storage.recordMiss("Song C", "Artist C")

            assertTrue(storage.getOverrideFile("Song A", "Artist A").exists())
            assertTrue(storage.getCacheFile("Song B", "Artist B").exists())
            assertTrue(storage.getMissFile("Song C", "Artist C").exists())

            storage.clearCache()

            // Fetched store in filesDir must be emptied
            assertFalse(storage.getCacheFile("Song B", "Artist B").exists())
            assertFalse(storage.getMissFile("Song C", "Artist C").exists())

            // Overrides in filesDir must remain intact
            assertTrue(storage.getOverrideFile("Song A", "Artist A").exists())
            assertEquals(LyricsResolution.Override(overrideLines), storage.resolveLyrics("Song A", "Artist A"))

            // cacheDir remains untouched
            val cacheFiles = cacheDir.listFiles()
            assertTrue(cacheFiles == null || cacheFiles.isEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `missing and unwritable directories handled safely during migration and eviction`() {
        val tempDir = Files.createTempDirectory("storage_error_test").toFile()
        try {
            val missingDir = File(tempDir, "non_existent_subdir")
            val blockedFile = File(tempDir, "blocked_as_file").apply { createNewFile() }

            val storage = LyricsStorage(blockedFile, missingDir)

            // Eviction on missing or blocked directory must not throw
            storage.evictOldEntries()
            storage.cleanupExpiredMisses()

            // Migration from or to missing/blocked directory must not throw
            storage.migrateOldCache(missingDir, blockedFile)
            storage.migrateOldCache(blockedFile, missingDir)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
