package com.dnk.wallpaperlyrics

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest

sealed class LyricsResolution {
    data class Override(val lines: List<LyricLine>) : LyricsResolution()
    data class Cached(val lines: List<LyricLine>) : LyricsResolution()
    object Miss : LyricsResolution()
    object NeedsFetch : LyricsResolution()
}

/**
 * Manages storage and lookup for hand-edited overrides and fetched lyrics.
 * Overrides live in filesDir/lyrics_overrides so they survive cache clearing and OS storage pressure.
 * Fetched lyrics and negative lookup markers live in filesDir/lyrics_cache to survive Android cache clearing.
 */
class LyricsStorage(
    val overridesDir: File,
    val fetchedDir: File,
    val oldCacheDir: File? = null,
    val maxFetchedEntries: Int = DEFAULT_MAX_FETCHED_ENTRIES
) {
    private val gson = Gson()

    fun cacheKey(title: String, artist: String): String = Companion.cacheKey(title, artist)

    fun getOverrideFile(title: String, artist: String): File {
        return File(overridesDir, "${cacheKey(title, artist)}.json")
    }

    fun getCacheFile(title: String, artist: String): File {
        return File(fetchedDir, "${cacheKey(title, artist)}.json")
    }

    fun getMissFile(title: String, artist: String): File {
        return File(fetchedDir, "${cacheKey(title, artist)}.miss")
    }

    fun getOverride(title: String, artist: String): List<LyricLine>? {
        return try {
            val file = getOverrideFile(title, artist)
            if (!file.exists() || !file.isFile) return null
            val json = file.readText()
            gson.fromJson(json, Array<LyricLine>::class.java)?.toList()
        } catch (e: Exception) {
            null
        }
    }

    fun getCache(title: String, artist: String): List<LyricLine>? {
        val file = getCacheFile(title, artist)
        return try {
            if (!file.exists() || !file.isFile) return null
            val json = file.readText()
            file.setLastModified(System.currentTimeMillis())
            gson.fromJson(json, Array<LyricLine>::class.java)?.toList()
        } catch (e: Exception) {
            try { file.delete() } catch (ignored: Exception) {}
            null
        }
    }

    fun hasValidMiss(title: String, artist: String, currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        val file = getMissFile(title, artist)
        return try {
            if (!file.exists() || !file.isFile) return false
            val stamp = file.readText().trim().toLongOrNull() ?: 0L
            if (currentTimeMs - stamp < MISS_TTL_MS) {
                true
            } else {
                file.delete()
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun resolveLyrics(title: String, artist: String, currentTimeMs: Long = System.currentTimeMillis()): LyricsResolution {
        val override = getOverride(title, artist)
        if (override != null) {
            return LyricsResolution.Override(override)
        }
        val cached = getCache(title, artist)
        if (cached != null) {
            return LyricsResolution.Cached(cached)
        }
        if (hasValidMiss(title, artist, currentTimeMs)) {
            return LyricsResolution.Miss
        }
        return LyricsResolution.NeedsFetch
    }

    fun saveOverride(title: String, artist: String, lines: List<LyricLine>): Boolean {
        return try {
            if (!overridesDir.exists()) {
                overridesDir.mkdirs()
            }
            val file = getOverrideFile(title, artist)
            file.writeText(gson.toJson(lines))
            val missFile = getMissFile(title, artist)
            if (missFile.exists()) {
                missFile.delete()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun clearOverride(title: String, artist: String): Boolean {
        return try {
            val file = getOverrideFile(title, artist)
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun clearAllOverrides(): Boolean {
        return try {
            if (overridesDir.exists()) {
                overridesDir.deleteRecursively()
                overridesDir.mkdirs()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun saveCache(title: String, artist: String, lines: List<LyricLine>): Boolean {
        return try {
            if (!fetchedDir.exists()) {
                fetchedDir.mkdirs()
            }
            val file = getCacheFile(title, artist)
            file.writeText(gson.toJson(lines))
            file.setLastModified(System.currentTimeMillis())
            evictOldEntries()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun recordMiss(title: String, artist: String, timestampMs: Long = System.currentTimeMillis()): Boolean {
        return try {
            if (!fetchedDir.exists()) {
                fetchedDir.mkdirs()
            }
            val file = getMissFile(title, artist)
            file.writeText(timestampMs.toString())
            cleanupExpiredMisses(timestampMs)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun deleteCacheFor(title: String, artist: String): Boolean {
        return try {
            val cacheFile = getCacheFile(title, artist)
            val missFile = getMissFile(title, artist)
            if (cacheFile.exists()) cacheFile.delete()
            if (missFile.exists()) missFile.delete()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun clearCache(): Boolean {
        return try {
            if (fetchedDir.exists()) {
                fetchedDir.deleteRecursively()
                fetchedDir.mkdirs()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun migrateOldCacheIfNeeded(): Boolean {
        val source = oldCacheDir ?: return true
        return migrateOldCache(source, fetchedDir)
    }

    fun migrateOldCache(
        sourceDir: File? = oldCacheDir,
        destinationDir: File = fetchedDir
    ): Boolean {
        val source = sourceDir ?: return false
        return try {
            if (!source.exists() || !source.isDirectory) return true
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }
            val files = source.listFiles() ?: return true
            for (file in files) {
                if (file.isDirectory) continue
                val destFile = File(destinationDir, file.name)
                if (destFile.exists()) {
                    file.delete()
                } else {
                    val renamed = file.renameTo(destFile)
                    if (!renamed) {
                        try {
                            file.copyTo(destFile, overwrite = false)
                            file.delete()
                        } catch (e: Exception) {
                            // Leave source file intact if copy fails
                        }
                    }
                }
            }
            val remaining = source.listFiles()
            if (remaining == null || remaining.isEmpty()) {
                source.delete()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun evictOldEntries(cap: Int = maxFetchedEntries) {
        try {
            if (!fetchedDir.exists() || !fetchedDir.isDirectory) return
            val jsonFiles = fetchedDir.listFiles { file ->
                file.isFile && file.name.endsWith(".json")
            } ?: return

            if (jsonFiles.size > cap) {
                val sortedFiles = jsonFiles.sortedBy { it.lastModified() }
                val numToEvict = jsonFiles.size - cap
                for (i in 0 until numToEvict) {
                    try {
                        sortedFiles[i].delete()
                    } catch (e: Exception) {
                        // Silently ignore individual file deletion error
                    }
                }
            }
        } catch (e: Exception) {
            // Silently handle unwritable or missing directory
        }
    }

    fun cleanupExpiredMisses(currentTimeMs: Long = System.currentTimeMillis()) {
        try {
            if (!fetchedDir.exists() || !fetchedDir.isDirectory) return
            val missFiles = fetchedDir.listFiles { file ->
                file.isFile && file.name.endsWith(".miss")
            } ?: return

            for (file in missFiles) {
                try {
                    val stamp = file.readText().trim().toLongOrNull() ?: 0L
                    if (currentTimeMs - stamp >= MISS_TTL_MS) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    try { file.delete() } catch (ignored: Exception) {}
                }
            }
        } catch (e: Exception) {
            // Silently handle unwritable or missing directory
        }
    }

    fun getLyricsForEdit(title: String, artist: String): String {
        val overrideLines = getOverride(title, artist)
        if (overrideLines != null) {
            return LyricsManager.renderLrc(overrideLines)
        }
        val cachedLines = getCache(title, artist)
        if (cachedLines != null) {
            return LyricsManager.renderLrc(cachedLines)
        }
        return ""
    }

    companion object {
        const val MISS_TTL_MS = 24 * 60 * 60 * 1000L
        const val OVERRIDES_DIR_NAME = "lyrics_overrides"
        const val FETCHED_DIR_NAME = "lyrics_cache"
        const val CACHE_DIR_NAME = FETCHED_DIR_NAME
        const val OLD_CACHE_DIR_NAME = "lyrics_cache"
        const val DEFAULT_MAX_FETCHED_ENTRIES = 2000

        fun create(filesDir: File, cacheDir: File? = null): LyricsStorage {
            val overrides = File(filesDir, OVERRIDES_DIR_NAME).apply { mkdirs() }
            val fetched = File(filesDir, FETCHED_DIR_NAME).apply { mkdirs() }
            val oldCache = cacheDir?.let { File(it, OLD_CACHE_DIR_NAME) }
            return LyricsStorage(overrides, fetched, oldCache)
        }

        fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

        fun cacheKey(title: String, artist: String): String = sha256("${title}_$artist")

        fun forContext(context: Context): LyricsStorage {
            val storage = create(context.filesDir, context.cacheDir)
            if (storage.oldCacheDir?.exists() == true) {
                CoroutineScope(Dispatchers.IO).launch {
                    storage.migrateOldCacheIfNeeded()
                }
            }
            return storage
        }
    }
}
