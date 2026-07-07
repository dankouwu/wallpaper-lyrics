package com.dnk.wallpaperlyrics

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import okhttp3.*
import java.io.File
import java.io.IOException
import java.net.URLEncoder

data class LyricLine(
    val startTime: Long, 
    val endTime: Long,
    val content: String,
    val isInstrumental: Boolean = false
)

data class LyricsResponse(
    val plainLyrics: String?,
    val syncedLyrics: String?,
    val enhancedSyncedLyrics: String?,
    val duration: Double?
)

data class SearchResult(
    val trackName: String?,
    val artistName: String?,
    val duration: Double?,
    val instrumental: Boolean?,
    val plainLyrics: String?,
    val syncedLyrics: String?
)

class LyricsManager(private val context: Context) {
    val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val cacheDir = File(context.cacheDir, "lyrics_cache").apply { mkdirs() }

    fun deleteCacheFor(title: String, artist: String) {
        val cacheKey = "${title}_${artist}".hashCode().toString()
        val cacheFile = File(cacheDir, "$cacheKey.json")
        val missFile = File(cacheDir, "$cacheKey.miss")
        try {
            if (cacheFile.exists()) cacheFile.delete()
            if (missFile.exists()) missFile.delete()
        } catch (e: Exception) {
            Log.e("LyricsManager", "Failed to delete cache for $title - $artist", e)
        }
    }

    companion object {
        private const val USER_AGENT = "WallpaperLyricsApp/1.0 (Android)"
        private const val MISS_TTL_MS = 24 * 60 * 60 * 1000L
    }

    fun fetchBitmap(url: String, callback: (android.graphics.Bitmap?) -> Unit) {
        if (url.startsWith("content://")) {
            kotlin.concurrent.thread(start = true) {
                try {
                    val uri = android.net.Uri.parse(url)
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    callback(bitmap)
                } catch (e: Exception) {
                    Log.e("LyricsManager", "Failed to fetch content URI", e)
                    callback(null)
                }
            }
            return
        }

        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.use { body ->
                    try {
                        val bytes = body.bytes()
                        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        callback(bitmap)
                    } catch (e: Exception) {
                        callback(null)
                    }
                } ?: callback(null)
            }
        })
    }

    /**
     * Query ladder: cache -> /api/get exact -> /api/search with sanitized variants ->
     * /api/search free-text. Stops at the first accepted hit.
     *
     * The callback's second parameter is true when the result is definitive (lyrics
     * found, or LRCLIB conclusively has nothing for this track) and false on transient
     * failures (offline, 429, 5xx) where the caller's watchdog may retry later.
     */
    fun fetchLyrics(title: String, artist: String, durationMs: Long, callback: (List<LyricLine>?, Boolean) -> Unit) {
        val cacheKey = "${title}_${artist}".hashCode().toString()
        val cacheFile = File(cacheDir, "$cacheKey.json")
        val missFile = File(cacheDir, "$cacheKey.miss")

        if (cacheFile.exists()) {
            try {
                val json = cacheFile.readText()
                val lines = gson.fromJson(json, Array<LyricLine>::class.java).toList()
                callback(lines, true)
                return
            } catch (e: Exception) {
                cacheFile.delete()
            }
        }

        if (missFile.exists()) {
            val stamp = try { missFile.readText().toLongOrNull() } catch (e: Exception) { null } ?: 0L
            if (System.currentTimeMillis() - stamp < MISS_TTL_MS) {
                callback(null, true)
                return
            }
            missFile.delete()
        }

        val candidates = TrackQuery.buildQueries(title, artist)
        if (candidates.isEmpty()) {
            callback(null, true)
            return
        }
        val wantedDurationSec = if (durationMs > 0) durationMs / 1000.0 else null

        val steps = mutableListOf<Step>()
        steps.add(Step(getUrl(candidates.first()), isSearch = false))
        candidates.drop(1).forEach { steps.add(Step(searchUrl(it), isSearch = true)) }
        // Free-text last resort with the cleaned full title (variant 1).
        val freeText = candidates.getOrNull(1) ?: candidates.first()
        steps.add(Step(qUrl(freeText.title), isSearch = true))

        val deduped = steps.distinctBy { it.url }
        runStep(deduped, 0, candidates, wantedDurationSec, cacheFile, missFile, callback)
    }

    private data class Step(val url: String, val isSearch: Boolean)

    private fun getUrl(c: QueryCandidate) =
        "https://lrclib.net/api/get?track_name=${URLEncoder.encode(c.title, "UTF-8")}&artist_name=${URLEncoder.encode(c.artist, "UTF-8")}"

    private fun searchUrl(c: QueryCandidate) = if (c.artist.isBlank()) qUrl(c.title) else
        "https://lrclib.net/api/search?track_name=${URLEncoder.encode(c.title, "UTF-8")}&artist_name=${URLEncoder.encode(c.artist, "UTF-8")}"

    private fun qUrl(q: String) =
        "https://lrclib.net/api/search?q=${URLEncoder.encode(q, "UTF-8")}"

    private fun runStep(
        steps: List<Step>,
        index: Int,
        candidates: List<QueryCandidate>,
        wantedDurationSec: Double?,
        cacheFile: File,
        missFile: File,
        callback: (List<LyricLine>?, Boolean) -> Unit
    ) {
        if (index >= steps.size) {
            try { missFile.writeText(System.currentTimeMillis().toString()) } catch (e: Exception) {}
            callback(null, true)
            return
        }

        val step = steps[index]
        val request = Request.Builder()
            .url(step.url)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Transient: abort the whole ladder, no negative cache. The service
                // watchdog is the retry layer.
                callback(null, false)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.code == 429 || response.code >= 500) {
                    callback(null, false)
                    return
                }
                // 404 and other 4xx are definitive for this step: advance the ladder.
                val lines = if (response.isSuccessful && body != null) {
                    try {
                        if (step.isSearch) pickFromSearch(body, candidates, wantedDurationSec)
                        else parseLyrics(gson.fromJson(body, LyricsResponse::class.java))
                    } catch (e: Exception) {
                        Log.e("LyricsManager", "Parse failure for ${step.url}", e)
                        null
                    }
                } else null

                if (lines != null) {
                    try { cacheFile.writeText(gson.toJson(lines)) } catch (e: Exception) {}
                    callback(lines, true)
                } else {
                    runStep(steps, index + 1, candidates, wantedDurationSec, cacheFile, missFile, callback)
                }
            }
        })
    }

    private fun pickFromSearch(
        body: String,
        candidates: List<QueryCandidate>,
        wantedDurationSec: Double?
    ): List<LyricLine>? {
        val results = gson.fromJson(body, Array<SearchResult>::class.java) ?: return null
        
        // Take the first 10 results from the search
        val topResults = results.take(10)
        
        // Calculate matching scores for all top results
        val scoredResults = topResults.map { result ->
            val score = candidates.maxOf { wanted ->
                TrackQuery.scoreCandidate(
                    result.trackName.orEmpty(),
                    result.artistName.orEmpty(),
                    result.duration,
                    wanted,
                    wantedDurationSec
                )
            }
            result to score
        }

        // Prioritize the first result that has synced lyrics and passes the threshold
        val firstSynced = scoredResults.firstOrNull { (result, score) ->
            !result.syncedLyrics.isNullOrBlank() && score >= TrackQuery.ACCEPT_THRESHOLD
        }
        
        if (firstSynced != null) {
            val (r, score) = firstSynced
            Log.d("LyricsManager", "Picked synced search match: ${r.trackName} - ${r.artistName} (score $score)")
            return parseLyrics(LyricsResponse(r.plainLyrics, r.syncedLyrics, null, r.duration))
        }

        return null
    }

    private fun parseLyrics(res: LyricsResponse): List<LyricLine>? {
        val rawLines = mutableListOf<LyricLine>()
        
        // Priority: syncedLyrics (ELRC support can be added by parsing res.enhancedSyncedLyrics)
        if (!res.syncedLyrics.isNullOrBlank()) {
            val regex = Regex("\\[(\\d+):(\\d+)\\.(\\d+)\\](.*)")
            res.syncedLyrics.lines().forEach { line ->
                val match = regex.find(line)
                if (match != null) {
                    val min = match.groupValues[1].toLong()
                    val sec = match.groupValues[2].toLong()
                    val ms = match.groupValues[3].padEnd(3, '0').take(3).toLong()
                    var content = match.groupValues[4].trim()
                    
                    val isMarker = content.contains("♪") || 
                                 content.contains("(Instrumental)", true) || 
                                 content.contains("[Instrumental]", true)
                    
                    if (isMarker) content = "♪"
                    
                    val startTime = (min * 60 + sec) * 1000 + ms
                    // Convert empty content into a marker if it's likely a pause
                    val finalContent = if (content.isEmpty()) "♪" else content
                    rawLines.add(LyricLine(startTime, 0, finalContent, finalContent == "♪"))
                }
            }
        }

        if (rawLines.isEmpty()) return null

        val songDurationMs = (res.duration?.times(1000)?.toLong()) ?: (rawLines.last().startTime + 10000)
        val lineDensity = rawLines.size.toFloat() / (songDurationMs / 1000f)
        
        val gapThreshold = when {
            lineDensity > 0.5 -> 4000L  
            lineDensity < 0.2 -> 10000L 
            else -> 7000L               
        }

        val processedLines = mutableListOf<LyricLine>()
        
        for (i in 0 until rawLines.size) {
            val currentRaw = rawLines[i]
            val nextRaw = if (i < rawLines.size - 1) rawLines[i + 1] else null
            
            // If this line is a marker, its duration is until the next line starts
            val estimatedDuration = if (currentRaw.isInstrumental) {
                nextRaw?.let { it.startTime - currentRaw.startTime } ?: (songDurationMs - currentRaw.startTime)
            } else {
                (currentRaw.content.length * 100L + 500L).coerceIn(2000L, 8000L)
            }
            
            var endTime = nextRaw?.startTime?.let { Math.min(currentRaw.startTime + estimatedDuration, it - 200L) } 
                          ?: (currentRaw.startTime + estimatedDuration)
            
            // If it's the last line and it's a note '♪', or just the last line, extend it to song end
            if (nextRaw == null) {
                endTime = songDurationMs
            }

            val currentLine = currentRaw.copy(endTime = endTime)
            processedLines.add(currentLine)

            // Step B: Dynamic Gap Heuristic (only if next line isn't already a marker)
            if (nextRaw != null && !nextRaw.isInstrumental) {
                val trueSilence = nextRaw.startTime - currentLine.endTime
                if (trueSilence > gapThreshold) {
                    processedLines.add(LyricLine(
                        startTime = currentLine.endTime + 200L,
                        endTime = nextRaw.startTime - 200L,
                        content = "♪",
                        isInstrumental = true
                    ))
                }
            }
        }
        
        // Final sanity check: if the very last processed line doesn't reach the end, stretch it
        if (processedLines.isNotEmpty() && processedLines.last().endTime < songDurationMs) {
            val last = processedLines.last()
            processedLines[processedLines.size - 1] = last.copy(endTime = songDurationMs)
        }
        
        if (processedLines.first().startTime > 5000) {
            processedLines.add(0, LyricLine(
                startTime = 0,
                endTime = processedLines.first().startTime - 500L,
                content = "♪",
                isInstrumental = true
            ))
        }

        return processedLines
    }
}
