package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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
    fun `check 1 held word pipeline source trusted path preserves end time`() {
        val lrc = "[01:29.23]<01:29.34>We<01:31.40> <01:32.72>should <01:33.02>try<01:33.38>"
        val parsed = LyricsManager.parseLrcText(lrc, durationMs = 120_000L, trustWordEnds = true)
        assertNotNull(parsed)
        val words = parsed!!.first { !it.isInstrumental }.words!!
        val we = words.first { it.text == "We" }
        assertEquals(89_340L, we.startTime)
        assertEquals(91_400L, we.endTime)
    }

    @Test
    fun `check 2 held word into next word no end tag trusted path preserves end time`() {
        val lrc = "[00:01.00]<00:01.00>weee <00:03.50>go<00:04.00>"
        val parsed = LyricsManager.parseLrcText(lrc, durationMs = 10_000L, trustWordEnds = true)
        assertNotNull(parsed)
        val words = parsed!!.first { !it.isInstrumental }.words!!
        val weee = words.first { it.text == "weee" }
        assertEquals(1_000L, weee.startTime)
        assertEquals(3_500L, weee.endTime)
    }

    @Test
    fun `check 3 provider data keeps the cap through untrusted path`() {
        val lrc = "[00:01.00]<00:01.00>weee <00:03.50>go<00:04.00>"
        val parsed = LyricsManager.parseLrcText(lrc, durationMs = 10_000L, trustWordEnds = false)
        assertNotNull(parsed)
        val words = parsed!!.first { !it.isInstrumental }.words!!
        val weee = words.first { it.text == "weee" }
        assertEquals(1_000L, weee.startTime)
        assertEquals(1_630L, weee.endTime)
    }

    @Test
    fun `check 4 hyphen pieces joined without space with separate lyric words and exact slices`() {
        val lrc = "[00:17.52]<00:17.76>ooh-<00:18.40>ooh-<00:19.10>ooh <00:20.00>next<00:20.50>"
        val parsed = LyricsManager.parseLrcText(lrc, durationMs = 30_000L, trustWordEnds = true)
        assertNotNull(parsed)
        val line = parsed!!.first { !it.isInstrumental }
        assertEquals("ooh-ooh-ooh next", line.content)
        val words = line.words!!
        assertEquals(4, words.size)
        assertEquals(listOf(17_760L, 18_400L, 19_100L, 20_000L), words.map { it.startTime })
        assertEquals("ooh-", line.content.substring(words[0].startIndex, words[0].endIndex))
        assertEquals("ooh-", line.content.substring(words[1].startIndex, words[1].endIndex))
        assertEquals("ooh", line.content.substring(words[2].startIndex, words[2].endIndex))
        assertEquals("next", line.content.substring(words[3].startIndex, words[3].endIndex))
    }

    @Test
    fun `check 5 old style input is unchanged`() {
        val lrc = "[00:05.59]<00:05.69>What <00:07.00>do <00:07.62>you<00:07.78>"
        val parsed = LyricsManager.parseLrcText(lrc, durationMs = 15_000L)
        assertNotNull(parsed)
        val line = parsed!!.first { !it.isInstrumental }
        assertEquals("What do you", line.content)
    }

    @Test
    fun `check 6 space in lead or trail still separates words with single space`() {
        val lrc1 = "[00:01.00]<00:01.00>What<00:02.00> do"
        val parsed1 = LyricsManager.parseLrcText(lrc1, durationMs = 5_000L)
        assertNotNull(parsed1)
        assertEquals("What do", parsed1!!.first { !it.isInstrumental }.content)

        val lrc2 = "[00:01.00]<00:01.00> What <00:02.00> do"
        val parsed2 = LyricsManager.parseLrcText(lrc2, durationMs = 5_000L)
        assertNotNull(parsed2)
        assertEquals("What do", parsed2!!.first { !it.isInstrumental }.content)
    }

    @Test
    fun `check 7 round trip keeps content and word start and end times`() {
        val hyphenLrc = "[00:17.52]<00:17.76>ooh-<00:18.40>ooh-<00:19.10>ooh <00:20.00>next<00:20.50>"
        val hyphenParsed = LyricsManager.parseLrcText(hyphenLrc, durationMs = 30_000L, isAuthoritative = true)
        assertNotNull(hyphenParsed)
        val renderedHyphen = LyricsManager.renderLrc(hyphenParsed!!)
        val roundTripHyphen = LyricsManager.parseLrcText(renderedHyphen, durationMs = 30_000L, isAuthoritative = true)
        assertNotNull(roundTripHyphen)
        val origHyphenLine = hyphenParsed.first { !it.isInstrumental }
        val rtHyphenLine = roundTripHyphen!!.first { !it.isInstrumental }
        assertEquals(origHyphenLine.content, rtHyphenLine.content)
        assertEquals(
            origHyphenLine.words!!.map { Triple(it.startTime, it.endTime, it.text) },
            rtHyphenLine.words!!.map { Triple(it.startTime, it.endTime, it.text) }
        )

        val heldLrc = "[01:29.23]<01:29.34>We<01:31.40> <01:32.72>should <01:33.02>try<01:33.38>"
        val heldParsed = LyricsManager.parseLrcText(heldLrc, durationMs = 120_000L, isAuthoritative = true)
        assertNotNull(heldParsed)
        val renderedHeld = LyricsManager.renderLrc(heldParsed!!)
        val roundTripHeld = LyricsManager.parseLrcText(renderedHeld, durationMs = 120_000L, isAuthoritative = true)
        assertNotNull(roundTripHeld)
        val origHeldLine = heldParsed.first { !it.isInstrumental }
        val rtHeldLine = roundTripHeld!!.first { !it.isInstrumental }
        assertEquals(origHeldLine.content, rtHeldLine.content)
        assertEquals(
            origHeldLine.words!!.map { Triple(it.startTime, it.endTime, it.text) },
            rtHeldLine.words!!.map { Triple(it.startTime, it.endTime, it.text) }
        )
    }

    @Test
    fun `check 8 pause inside hyphenated word preserves pieces and sets end time without space`() {
        val lrc = "[00:29.00]<00:29.84>ooh-<00:30.42><00:32.32>ooh-<00:33.80>ooh<00:34.12>"
        val parsed = LyricsManager.parseLrcText(lrc, durationMs = 40_000L, trustWordEnds = true)
        assertNotNull(parsed)
        val line = parsed!!.first { !it.isInstrumental }
        assertEquals("ooh-ooh-ooh", line.content)
        val words = line.words!!
        assertEquals(3, words.size)
        assertEquals(29_840L, words[0].startTime)
        assertEquals(30_420L, words[0].endTime)
        assertEquals(32_320L, words[1].startTime)
        assertEquals(33_800L, words[2].startTime)
        assertEquals(34_120L, words[2].endTime)
    }

    @Test
    fun `check 9 cleanCachedLyrics preserves held word over 800ms when merging adjacent instrumentals`() {
        val heldLine = LyricLine(
            startTime = 10_000L,
            endTime = 15_000L,
            content = "Held word",
            words = listOf(
                LyricWord(10_000L, 12_500L, "Held", 0, 4),
                LyricWord(12_500L, 14_000L, "word", 5, 9)
            )
        )
        val dirtyCached = listOf(
            LyricLine(0L, 5_000L, "♪", isInstrumental = true),
            LyricLine(5_000L, 10_000L, "♪", isInstrumental = true),
            heldLine
        )
        assertTrue(LyricsManager.hasAdjacentInstrumentals(dirtyCached))
        val cleaned = LyricsManager.cleanCachedLyrics(dirtyCached, durationMs = 30_000L)
        assertFalse(LyricsManager.hasAdjacentInstrumentals(cleaned))
        val cleanedHeldLine = cleaned.first { !it.isInstrumental }
        val heldWord = cleanedHeldLine.words!!.first { it.text == "Held" }
        assertEquals(12_500L, heldWord.endTime)
    }

    @Test
    fun `check 10 old input unchanged across debug assets with no glued words`() {
        val assetsDir = listOf(File("src/debug/assets"), File("app/src/debug/assets")).firstOrNull { it.isDirectory }
        assumeTrue(assetsDir != null)
        val lrcFiles = assetsDir!!.listFiles { file -> file.extension == "lrc" || file.extension == "elrc" } ?: emptyArray()
        assertTrue("Should have debug asset lrc files", lrcFiles.isNotEmpty())

        val tagRegex = Regex("\\[.*?\\]|<.*?>")
        for (file in lrcFiles) {
            val text = file.readText()
            val parsed = LyricsManager.parseLrcText(text)
            assertNotNull("File ${file.name} should parse", parsed)
            val nonInstrumental = parsed!!.filterNot { it.isInstrumental }
            val rawNonEmpty = text.lines()
                .map { line -> tagRegex.replace(line, "").trim() }
                .filter { it.isNotEmpty() && it != "♪" && !it.equals("(Instrumental)", ignoreCase = true) && !it.equals("[Instrumental]", ignoreCase = true) }

            assertEquals("Line count should match for ${file.name}", rawNonEmpty.size, nonInstrumental.size)
            for (i in nonInstrumental.indices) {
                val expectedWords = rawNonEmpty[i].split(Regex("\\s+")).filter { it.isNotEmpty() }
                val actualWords = nonInstrumental[i].content.split(Regex("\\s+")).filter { it.isNotEmpty() }
                assertEquals(
                    "File ${file.name} at line index $i should have matching words",
                    expectedWords,
                    actualWords
                )
            }
        }
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

    @Test
    fun `fetched style LRC drops provider markers and blank lines and matches authoritative reparse`() {
        val lrc = """
            [00:06.00] ♪
            [00:10.00] (Instrumental)
            [00:12.00]
            [00:15.00] First lyric line
            [00:25.00] [Instrumental]
            [00:28.00] ♪
            [00:29.50]
            [00:30.00] Second lyric line
            [00:50.00] Third lyric line
        """.trimIndent()
        val durationMs = 65_000L

        val parsed = LyricsManager.parseLrcText(lrc, durationMs = durationMs, isAuthoritative = false)
        assertNotNull(parsed)

        for (i in 0 until parsed!!.size - 1) {
            val adjacent = parsed[i].isInstrumental && parsed[i + 1].isInstrumental
            assertFalse("Found adjacent instrumental lines at index $i and ${i + 1}", adjacent)
        }

        val rendered = LyricsManager.renderLrc(parsed)
        val secondPass = LyricsManager.parseLrcText(rendered, durationMs = durationMs, isAuthoritative = true)
        assertNotNull(secondPass)

        val parsedLyrics = parsed.filterNot { it.isInstrumental }
        val secondPassLyrics = secondPass!!.filterNot { it.isInstrumental }

        assertEquals(
            secondPassLyrics.map { it.startTime to it.content },
            parsedLyrics.map { it.startTime to it.content }
        )
    }

    @Test
    fun `authoritative input with user typed instrumental marker keeps it when not adjacent`() {
        val lrc = """
            [00:02.00] Intro line
            [00:04.00] ♪
            [00:10.00] Middle line
        """.trimIndent()
        val durationMs = 30_000L

        val parsed = LyricsManager.parseLrcText(lrc, durationMs = durationMs, isAuthoritative = true)
        assertNotNull(parsed)

        val instrumentalLine = parsed!!.find { it.isInstrumental && it.startTime == 4_000L }
        assertNotNull("User typed marker at 4000ms should be preserved", instrumentalLine)

        for (i in 0 until parsed.size - 1) {
            assertFalse(parsed[i].isInstrumental && parsed[i + 1].isInstrumental)
        }
    }

    @Test
    fun `real lyric line start times are unchanged versus input timestamps`() {
        val lrc = """
            [00:05.12] First line
            [00:12.34] Second line
            [00:25.78] Third line
        """.trimIndent()

        val expectedStarts = listOf(5_120L, 12_340L, 25_780L)

        val nonAuth = LyricsManager.parseLrcText(lrc, durationMs = 40_000L, isAuthoritative = false)
        assertNotNull(nonAuth)
        assertEquals(expectedStarts, nonAuth!!.filterNot { it.isInstrumental }.map { it.startTime })

        val auth = LyricsManager.parseLrcText(lrc, durationMs = 40_000L, isAuthoritative = true)
        assertNotNull(auth)
        assertEquals(expectedStarts, auth!!.filterNot { it.isInstrumental }.map { it.startTime })
    }

    @Test
    fun `authoritative input merges adjacent user typed instrumentals covering earliest start to latest end`() {
        val lrc = """
            [00:08.00] ♪
            [00:12.00] ♪
            [00:25.00] Real line
        """.trimIndent()

        val parsed = LyricsManager.parseLrcText(lrc, durationMs = 40_000L, isAuthoritative = true)
        assertNotNull(parsed)

        for (i in 0 until parsed!!.size - 1) {
            assertFalse(parsed[i].isInstrumental && parsed[i + 1].isInstrumental)
        }

        val initialInstrumental = parsed.first { it.isInstrumental }
        assertEquals(0L, initialInstrumental.startTime)
        assertTrue(initialInstrumental.endTime >= 12_000L)
    }

    @Test
    fun `cache clean up function cleans adjacent instrumentals and leaves clean lists intact`() {
        val dirtyCachedLines = listOf(
            LyricLine(0L, 5_000L, "♪", isInstrumental = true),
            LyricLine(5_000L, 10_000L, "♪", isInstrumental = true),
            LyricLine(12_000L, 16_000L, "Real line"),
            LyricLine(17_000L, 22_000L, "♪", isInstrumental = true),
            LyricLine(22_000L, 28_000L, "♪", isInstrumental = true),
            LyricLine(30_000L, 35_000L, "Another line")
        )

        assertTrue(LyricsManager.hasAdjacentInstrumentals(dirtyCachedLines))

        val cleaned = LyricsManager.cleanCachedLyrics(dirtyCachedLines, durationMs = 45_000L)
        assertFalse(LyricsManager.hasAdjacentInstrumentals(cleaned))

        for (i in 0 until cleaned.size - 1) {
            assertFalse(cleaned[i].isInstrumental && cleaned[i + 1].isInstrumental)
        }

        val originalLyrics = dirtyCachedLines.filterNot { it.isInstrumental }.map { it.startTime to it.content }
        val cleanedLyrics = cleaned.filterNot { it.isInstrumental }.map { it.startTime to it.content }
        assertEquals(originalLyrics, cleanedLyrics)

        val cleanAgain = LyricsManager.cleanCachedLyrics(cleaned, durationMs = 45_000L)
        assertEquals(cleaned, cleanAgain)
    }

    @Test
    fun `cached entry with adjacent instrumentals is cleaned and written back while overrides are untouched`() {
        val tempDir = Files.createTempDirectory("cache_rewrite_test").toFile()
        try {
            val overridesDir = File(tempDir, "lyrics_overrides").apply { mkdirs() }
            val cacheDir = File(tempDir, "lyrics_cache").apply { mkdirs() }
            val storage = LyricsStorage(overridesDir, cacheDir)

            val dirtyCached = listOf(
                LyricLine(0L, 5_000L, "♪", isInstrumental = true),
                LyricLine(5_000L, 10_000L, "♪", isInstrumental = true),
                LyricLine(12_000L, 16_000L, "Cached song line")
            )
            storage.saveCache("Song", "Artist", dirtyCached)

            val dirtyOverride = listOf(
                LyricLine(0L, 5_000L, "♪", isInstrumental = true),
                LyricLine(5_000L, 10_000L, "♪", isInstrumental = true),
                LyricLine(12_000L, 16_000L, "Override song line")
            )
            storage.saveOverride("OverrideSong", "OverrideArtist", dirtyOverride)

            val loadedCache = storage.getCache("Song", "Artist")!!
            assertTrue(LyricsManager.hasAdjacentInstrumentals(loadedCache))
            val cleaned = LyricsManager.cleanCachedLyrics(loadedCache, 30_000L)
            storage.saveCache("Song", "Artist", cleaned)

            val reloadedCache = storage.getCache("Song", "Artist")!!
            assertFalse(LyricsManager.hasAdjacentInstrumentals(reloadedCache))
            assertEquals(cleaned, reloadedCache)

            val loadedOverride = storage.getOverride("OverrideSong", "OverrideArtist")!!
            assertTrue(LyricsManager.hasAdjacentInstrumentals(loadedOverride))
            assertEquals(dirtyOverride, loadedOverride)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
