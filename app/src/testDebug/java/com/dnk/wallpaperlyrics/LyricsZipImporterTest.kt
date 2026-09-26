package com.dnk.wallpaperlyrics

import com.google.gson.Gson
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LyricsZipImporterTest {

    private val gson = Gson()

    private fun createZip(
        zipFile: File,
        manifestJson: String,
        entries: Map<String, String>
    ) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val manifestEntry = ZipEntry("manifest.json")
            zos.putNextEntry(manifestEntry)
            zos.write(manifestJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            for ((path, content) in entries) {
                val fileEntry = ZipEntry(path)
                zos.putNextEntry(fileEntry)
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
    }

    private val sampleEnhancedLrc1 = """
        [00:01.00]<00:01.00>Never <00:01.40>gonna <00:01.80>give <00:02.10>you <00:02.50>up
        [00:03.00]<00:03.00>Never <00:03.40>gonna <00:03.80>let <00:04.10>you <00:04.50>down
    """.trimIndent()

    private val sampleEnhancedLrc2 = """
        [00:10.00]<00:10.00>I <00:10.20>wanna <00:10.50>run <00:10.80>away
        [00:12.00]<00:12.00>Anywhere <00:12.50>out <00:12.80>of <00:13.10>here
    """.trimIndent()

    @Test
    fun `check 1 two entries import under correct cache keys with parsed json`() {
        val tempDir = Files.createTempDirectory("importer_test1").toFile()
        try {
            val overridesDir = File(tempDir, "lyrics_overrides").apply { mkdirs() }
            val cacheDir = File(tempDir, "lyrics_cache").apply { mkdirs() }
            val undoDir = File(tempDir, "lyrics_import_undo")
            val storage = LyricsStorage(overridesDir, cacheDir)

            val manifest = """
                {
                  "format": 1,
                  "created": "2026-09-26T21:30:00+02:00",
                  "source": "aligner test",
                  "entries": [
                    {
                      "file": "lrc/0001.lrc",
                      "title": "Song One",
                      "artist": "Artist One",
                      "duration_ms": 180000
                    },
                    {
                      "file": "lrc/0002.lrc",
                      "title": "Song Two",
                      "artist": "Artist Two",
                      "duration_ms": 210000
                    }
                  ]
                }
            """.trimIndent()

            val zipFile = File(tempDir, "test.zip")
            createZip(
                zipFile,
                manifest,
                mapOf(
                    "lrc/0001.lrc" to sampleEnhancedLrc1,
                    "lrc/0002.lrc" to sampleEnhancedLrc2
                )
            )

            val importer = LyricsZipImporter(storage, undoDir)
            val result = importer.importZip(zipFile)

            assertEquals(2, result.importedNew)
            assertEquals(0, result.replaced)
            assertEquals(0, result.overrideActive)
            assertEquals(0, result.failed)
            assertTrue(result.failedTitles.isEmpty())

            val key1 = storage.cacheKey("Song One", "Artist One")
            val key2 = storage.cacheKey("Song Two", "Artist Two")

            val file1 = File(cacheDir, "$key1.json")
            val file2 = File(cacheDir, "$key2.json")
            assertTrue(file1.exists())
            assertTrue(file2.exists())

            val expectedLines1 = LyricsManager.parseLrcText(sampleEnhancedLrc1, 180000L, trustWordEnds = true)
            val expectedLines2 = LyricsManager.parseLrcText(sampleEnhancedLrc2, 210000L, trustWordEnds = true)

            assertEquals(gson.toJson(expectedLines1), file1.readText())
            assertEquals(gson.toJson(expectedLines2), file2.readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `check 2 existing cache file is replaced and counts say 1 new 1 replaced`() {
        val tempDir = Files.createTempDirectory("importer_test2").toFile()
        try {
            val overridesDir = File(tempDir, "lyrics_overrides").apply { mkdirs() }
            val cacheDir = File(tempDir, "lyrics_cache").apply { mkdirs() }
            val undoDir = File(tempDir, "lyrics_import_undo")
            val storage = LyricsStorage(overridesDir, cacheDir)

            val oldLines = listOf(LyricLine(500L, 1500L, "Old cached lyric"))
            storage.saveCache("Song One", "Artist One", oldLines)

            val manifest = """
                {
                  "format": 1,
                  "entries": [
                    {
                      "file": "lrc/0001.lrc",
                      "title": "Song One",
                      "artist": "Artist One",
                      "duration_ms": 180000
                    },
                    {
                      "file": "lrc/0002.lrc",
                      "title": "Song Two",
                      "artist": "Artist Two",
                      "duration_ms": 210000
                    }
                  ]
                }
            """.trimIndent()

            val zipFile = File(tempDir, "test.zip")
            createZip(
                zipFile,
                manifest,
                mapOf(
                    "lrc/0001.lrc" to sampleEnhancedLrc1,
                    "lrc/0002.lrc" to sampleEnhancedLrc2
                )
            )

            val importer = LyricsZipImporter(storage, undoDir)
            val result = importer.importZip(zipFile)

            assertEquals(1, result.importedNew)
            assertEquals(1, result.replaced)
            assertEquals(0, result.overrideActive)
            assertEquals(0, result.failed)

            val key1 = storage.cacheKey("Song One", "Artist One")
            val file1 = File(cacheDir, "$key1.json")
            val expectedLines1 = LyricsManager.parseLrcText(sampleEnhancedLrc1, 180000L, trustWordEnds = true)
            assertEquals(gson.toJson(expectedLines1), file1.readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `check 3 miss file for imported key is deleted`() {
        val tempDir = Files.createTempDirectory("importer_test3").toFile()
        try {
            val overridesDir = File(tempDir, "lyrics_overrides").apply { mkdirs() }
            val cacheDir = File(tempDir, "lyrics_cache").apply { mkdirs() }
            val undoDir = File(tempDir, "lyrics_import_undo")
            val storage = LyricsStorage(overridesDir, cacheDir)

            storage.recordMiss("Song One", "Artist One")
            val missFile = storage.getMissFile("Song One", "Artist One")
            assertTrue(missFile.exists())

            val manifest = """
                {
                  "format": 1,
                  "entries": [
                    {
                      "file": "lrc/0001.lrc",
                      "title": "Song One",
                      "artist": "Artist One",
                      "duration_ms": 180000
                    }
                  ]
                }
            """.trimIndent()

            val zipFile = File(tempDir, "test.zip")
            createZip(zipFile, manifest, mapOf("lrc/0001.lrc" to sampleEnhancedLrc1))

            val importer = LyricsZipImporter(storage, undoDir)
            val result = importer.importZip(zipFile)

            assertEquals(1, result.importedNew)
            assertFalse(missFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `check 4 entry with override is unchanged and counted as override active`() {
        val tempDir = Files.createTempDirectory("importer_test4").toFile()
        try {
            val overridesDir = File(tempDir, "lyrics_overrides").apply { mkdirs() }
            val cacheDir = File(tempDir, "lyrics_cache").apply { mkdirs() }
            val undoDir = File(tempDir, "lyrics_import_undo")
            val storage = LyricsStorage(overridesDir, cacheDir)

            val overrideLines = listOf(LyricLine(100L, 200L, "Hand edited override"))
            storage.saveOverride("Song One", "Artist One", overrideLines)

            val overrideFile = storage.getOverrideFile("Song One", "Artist One")
            val originalOverrideJson = overrideFile.readText()

            val manifest = """
                {
                  "format": 1,
                  "entries": [
                    {
                      "file": "lrc/0001.lrc",
                      "title": "Song One",
                      "artist": "Artist One",
                      "duration_ms": 180000
                    },
                    {
                      "file": "lrc/0002.lrc",
                      "title": "Song Two",
                      "artist": "Artist Two",
                      "duration_ms": 210000
                    }
                  ]
                }
            """.trimIndent()

            val zipFile = File(tempDir, "test.zip")
            createZip(
                zipFile,
                manifest,
                mapOf(
                    "lrc/0001.lrc" to sampleEnhancedLrc1,
                    "lrc/0002.lrc" to sampleEnhancedLrc2
                )
            )

            val importer = LyricsZipImporter(storage, undoDir)
            val result = importer.importZip(zipFile)

            assertEquals(1, result.importedNew)
            assertEquals(0, result.replaced)
            assertEquals(1, result.overrideActive)
            assertEquals(0, result.failed)

            assertEquals(originalOverrideJson, overrideFile.readText())

            val key1 = storage.cacheKey("Song One", "Artist One")
            val cacheFile1 = File(cacheDir, "$key1.json")
            assertTrue(cacheFile1.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `check 5 malformed lrc counts as failed and rest still imports`() {
        val tempDir = Files.createTempDirectory("importer_test5").toFile()
        try {
            val overridesDir = File(tempDir, "lyrics_overrides").apply { mkdirs() }
            val cacheDir = File(tempDir, "lyrics_cache").apply { mkdirs() }
            val undoDir = File(tempDir, "lyrics_import_undo")
            val storage = LyricsStorage(overridesDir, cacheDir)

            val manifest = """
                {
                  "format": 1,
                  "entries": [
                    {
                      "file": "lrc/0001.lrc",
                      "title": "Broken Track",
                      "artist": "Broken Artist",
                      "duration_ms": 180000
                    },
                    {
                      "file": "lrc/0002.lrc",
                      "title": "Good Track",
                      "artist": "Good Artist",
                      "duration_ms": 210000
                    }
                  ]
                }
            """.trimIndent()

            val zipFile = File(tempDir, "test.zip")
            createZip(
                zipFile,
                manifest,
                mapOf(
                    "lrc/0001.lrc" to "not lyrics at all",
                    "lrc/0002.lrc" to sampleEnhancedLrc2
                )
            )

            val importer = LyricsZipImporter(storage, undoDir)
            val result = importer.importZip(zipFile)

            assertEquals(1, result.importedNew)
            assertEquals(0, result.replaced)
            assertEquals(0, result.overrideActive)
            assertEquals(1, result.failed)
            assertEquals(listOf("Broken Track"), result.failedTitles)

            val goodKey = storage.cacheKey("Good Track", "Good Artist")
            assertTrue(File(cacheDir, "$goodKey.json").exists())

            val brokenKey = storage.cacheKey("Broken Track", "Broken Artist")
            assertFalse(File(cacheDir, "$brokenKey.json").exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `check 6 format 2 is refused and nothing is written`() {
        val tempDir = Files.createTempDirectory("importer_test6").toFile()
        try {
            val overridesDir = File(tempDir, "lyrics_overrides").apply { mkdirs() }
            val cacheDir = File(tempDir, "lyrics_cache").apply { mkdirs() }
            val undoDir = File(tempDir, "lyrics_import_undo")
            val storage = LyricsStorage(overridesDir, cacheDir)

            val manifest = """
                {
                  "format": 2,
                  "entries": [
                    {
                      "file": "lrc/0001.lrc",
                      "title": "Song One",
                      "artist": "Artist One",
                      "duration_ms": 180000
                    }
                  ]
                }
            """.trimIndent()

            val zipFile = File(tempDir, "test.zip")
            createZip(zipFile, manifest, mapOf("lrc/0001.lrc" to sampleEnhancedLrc1))

            val importer = LyricsZipImporter(storage, undoDir)
            val exception = assertThrows(IllegalArgumentException::class.java) {
                importer.importZip(zipFile)
            }

            assertTrue(exception.message?.contains("1") == true)
            assertEquals(0, cacheDir.listFiles()?.size ?: 0)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `check 7 slash and cjk titles import correctly without path derivation`() {
        val tempDir = Files.createTempDirectory("importer_test7").toFile()
        try {
            val overridesDir = File(tempDir, "lyrics_overrides").apply { mkdirs() }
            val cacheDir = File(tempDir, "lyrics_cache").apply { mkdirs() }
            val undoDir = File(tempDir, "lyrics_import_undo")
            val storage = LyricsStorage(overridesDir, cacheDir)

            val slashTitle = "AC/DC / Rock & Roll? *"
            val slashArtist = "Rock/Artist"
            val cjkTitle = "夜に駆ける"
            val cjkArtist = "YOASOBI"

            val cjkLrc = "[00:05.00]<00:05.00>夜に<00:05.80>駆ける<00:06.50>"

            val manifest = """
                {
                  "format": 1,
                  "entries": [
                    {
                      "file": "lrc/0001.lrc",
                      "title": "$slashTitle",
                      "artist": "$slashArtist",
                      "duration_ms": 180000
                    },
                    {
                      "file": "lrc/0002.lrc",
                      "title": "$cjkTitle",
                      "artist": "$cjkArtist",
                      "duration_ms": 210000
                    }
                  ]
                }
            """.trimIndent()

            val zipFile = File(tempDir, "test.zip")
            createZip(
                zipFile,
                manifest,
                mapOf(
                    "lrc/0001.lrc" to sampleEnhancedLrc1,
                    "lrc/0002.lrc" to cjkLrc
                )
            )

            val importer = LyricsZipImporter(storage, undoDir)
            val result = importer.importZip(zipFile)

            assertEquals(2, result.importedNew)
            assertEquals(0, result.failed)

            val slashKey = storage.cacheKey(slashTitle, slashArtist)
            val cjkKey = storage.cacheKey(cjkTitle, cjkArtist)

            val slashFile = File(cacheDir, "$slashKey.json")
            val cjkFile = File(cacheDir, "$cjkKey.json")

            assertTrue(slashFile.exists())
            assertTrue(cjkFile.exists())

            assertTrue(slashFile.name.matches(Regex("^[a-f0-9]{64}\\.json$")))
            assertTrue(cjkFile.name.matches(Regex("^[a-f0-9]{64}\\.json$")))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `check 8 undo restores replaced file byte for byte and deletes new files`() {
        val tempDir = Files.createTempDirectory("importer_test8").toFile()
        try {
            val overridesDir = File(tempDir, "lyrics_overrides").apply { mkdirs() }
            val cacheDir = File(tempDir, "lyrics_cache").apply { mkdirs() }
            val undoDir = File(tempDir, "lyrics_import_undo")
            val storage = LyricsStorage(overridesDir, cacheDir)

            val keyReplaced = storage.cacheKey("Song Replaced", "Artist A")
            val replacedFile = File(cacheDir, "$keyReplaced.json")
            val originalBytes = """[{"startTime":10,"endTime":20,"content":"Original Byte Exact"}]""".toByteArray(Charsets.UTF_8)
            replacedFile.writeBytes(originalBytes)

            val manifest = """
                {
                  "format": 1,
                  "entries": [
                    {
                      "file": "lrc/0001.lrc",
                      "title": "Song Replaced",
                      "artist": "Artist A",
                      "duration_ms": 180000
                    },
                    {
                      "file": "lrc/0002.lrc",
                      "title": "Song Brand New",
                      "artist": "Artist B",
                      "duration_ms": 210000
                    }
                  ]
                }
            """.trimIndent()

            val zipFile = File(tempDir, "test.zip")
            createZip(
                zipFile,
                manifest,
                mapOf(
                    "lrc/0001.lrc" to sampleEnhancedLrc1,
                    "lrc/0002.lrc" to sampleEnhancedLrc2
                )
            )

            val importer = LyricsZipImporter(storage, undoDir)
            val result = importer.importZip(zipFile)

            assertEquals(1, result.importedNew)
            assertEquals(1, result.replaced)

            val keyNew = storage.cacheKey("Song Brand New", "Artist B")
            val newFile = File(cacheDir, "$keyNew.json")
            assertTrue(newFile.exists())
            assertFalse(replacedFile.readBytes().contentEquals(originalBytes))

            val undone = importer.undoLastImport()
            assertTrue(undone)

            assertTrue(replacedFile.exists())
            assertArrayEquals(originalBytes, replacedFile.readBytes())

            assertFalse(newFile.exists())

            assertTrue(!undoDir.exists() || undoDir.listFiles().isNullOrEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `check 9 cache of 1957 files plus import of 2500 entries evicts nothing`() {
        val tempDir = Files.createTempDirectory("importer_test9").toFile()
        try {
            val overridesDir = File(tempDir, "lyrics_overrides").apply { mkdirs() }
            val cacheDir = File(tempDir, "lyrics_cache").apply { mkdirs() }
            val undoDir = File(tempDir, "lyrics_import_undo")
            val storage = LyricsStorage(overridesDir, cacheDir)

            for (i in 1..1957) {
                val dummyFile = File(cacheDir, "preexisting_%04d.json".format(i))
                dummyFile.writeText("[]")
                dummyFile.setLastModified(1000L + i)
            }
            assertEquals(1957, cacheDir.listFiles { f -> f.extension == "json" }?.size)

            val manifestSb = StringBuilder()
            manifestSb.append("""{"format":1,"entries":[""")
            for (i in 1..2500) {
                if (i > 1) manifestSb.append(",")
                manifestSb.append("""{"file":"lrc/shared.lrc","title":"Batch Track $i","artist":"Batch Artist $i","duration_ms":120000}""")
            }
            manifestSb.append("]}")

            val zipFile = File(tempDir, "large_import.zip")
            createZip(
                zipFile,
                manifestSb.toString(),
                mapOf("lrc/shared.lrc" to sampleEnhancedLrc1)
            )

            var lastReportedDone = 0
            var lastReportedTotal = 0
            val importer = LyricsZipImporter(storage, undoDir)
            val result = importer.importZip(zipFile) { done, total ->
                lastReportedDone = done
                lastReportedTotal = total
            }

            assertEquals(2500, result.importedNew)
            assertEquals(0, result.replaced)
            assertEquals(0, result.failed)
            assertEquals(2500, lastReportedDone)
            assertEquals(2500, lastReportedTotal)

            val totalFiles = cacheDir.listFiles { f -> f.extension == "json" }?.size ?: 0
            assertEquals(4457, totalFiles)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `check 10 title with trailing space imports under untrimmed cache key`() {
        val tempDir = Files.createTempDirectory("importer_test10").toFile()
        try {
            val overridesDir = File(tempDir, "lyrics_overrides").apply { mkdirs() }
            val cacheDir = File(tempDir, "lyrics_cache").apply { mkdirs() }
            val undoDir = File(tempDir, "lyrics_import_undo")
            val storage = LyricsStorage(overridesDir, cacheDir)

            val manifest = """
                {
                  "format": 1,
                  "entries": [
                    {
                      "file": "lrc/0001.lrc",
                      "title": "Song With Space ",
                      "artist": "Artist Name",
                      "duration_ms": 180000
                    }
                  ]
                }
            """.trimIndent()

            val zipFile = File(tempDir, "test.zip")
            createZip(zipFile, manifest, mapOf("lrc/0001.lrc" to sampleEnhancedLrc1))

            val importer = LyricsZipImporter(storage, undoDir)
            val result = importer.importZip(zipFile)

            assertEquals(1, result.importedNew)
            assertEquals(0, result.failed)

            val untrimmedKey = storage.cacheKey("Song With Space ", "Artist Name")
            val trimmedKey = storage.cacheKey("Song With Space", "Artist Name")

            assertTrue(File(cacheDir, "$untrimmedKey.json").exists())
            assertFalse(File(cacheDir, "$trimmedKey.json").exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `check 11 entry with no artist field imports under empty artist cache key`() {
        val tempDir = Files.createTempDirectory("importer_test11").toFile()
        try {
            val overridesDir = File(tempDir, "lyrics_overrides").apply { mkdirs() }
            val cacheDir = File(tempDir, "lyrics_cache").apply { mkdirs() }
            val undoDir = File(tempDir, "lyrics_import_undo")
            val storage = LyricsStorage(overridesDir, cacheDir)

            val manifest = """
                {
                  "format": 1,
                  "entries": [
                    {
                      "file": "lrc/0001.lrc",
                      "title": "Solo Track",
                      "duration_ms": 180000
                    }
                  ]
                }
            """.trimIndent()

            val zipFile = File(tempDir, "test.zip")
            createZip(zipFile, manifest, mapOf("lrc/0001.lrc" to sampleEnhancedLrc1))

            val importer = LyricsZipImporter(storage, undoDir)
            val result = importer.importZip(zipFile)

            assertEquals(1, result.importedNew)
            assertEquals(0, result.failed)

            val expectedKey = storage.cacheKey("Solo Track", "")
            val cacheFile = File(cacheDir, "$expectedKey.json")
            assertTrue(cacheFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `check 12 failed import writes created keys so undo deletes created file`() {
        val tempDir = Files.createTempDirectory("importer_test12").toFile()
        try {
            val overridesDir = File(tempDir, "lyrics_overrides").apply { mkdirs() }
            val cacheDir = File(tempDir, "lyrics_cache").apply { mkdirs() }
            val undoDir = File(tempDir, "lyrics_import_undo")
            val storage = LyricsStorage(overridesDir, cacheDir)

            val manifest = """
                {
                  "format": 1,
                  "entries": [
                    {
                      "file": "lrc/0001.lrc",
                      "title": "Song One",
                      "artist": "Artist One",
                      "duration_ms": 180000
                    },
                    {
                      "file": "lrc/0002.lrc",
                      "title": "Song Two",
                      "artist": "Artist Two",
                      "duration_ms": 210000
                    }
                  ]
                }
            """.trimIndent()

            val zipFile = File(tempDir, "test.zip")
            createZip(
                zipFile,
                manifest,
                mapOf(
                    "lrc/0001.lrc" to sampleEnhancedLrc1,
                    "lrc/0002.lrc" to sampleEnhancedLrc2
                )
            )

            val key1 = storage.cacheKey("Song One", "Artist One")
            val file1 = File(cacheDir, "$key1.json")

            val importer = LyricsZipImporter(storage, undoDir)
            assertThrows(RuntimeException::class.java) {
                importer.importZip(zipFile) { done, _ ->
                    if (done == 1) {
                        throw RuntimeException("Simulated error after first file")
                    }
                }
            }

            assertTrue(file1.exists())
            assertTrue(importer.hasUndo())

            val undone = importer.undoLastImport()
            assertTrue(undone)

            assertFalse(file1.exists())
            assertFalse(importer.hasUndo())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
