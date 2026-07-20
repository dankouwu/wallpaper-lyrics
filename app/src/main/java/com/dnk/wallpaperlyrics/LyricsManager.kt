package com.dnk.wallpaperlyrics

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.IOException
import java.net.URLEncoder

data class LyricWord(
    val startTime: Long,
    val endTime: Long,
    val text: String,
    val startIndex: Int,
    val endIndex: Int,
    val left: Float = 0f,
    val right: Float = 0f,
    val lineNum: Int = 0,
    @Transient var spanRef: Any? = null,
    val fullStartTime: Long = startTime,
    val fullEndTime: Long = endTime,
    val partStartProp: Float = 0f,
    val partEndProp: Float = 1f,
    val isEstimated: Boolean = false
)

data class LyricLine(
    val startTime: Long, 
    val endTime: Long,
    val content: String,
    val isInstrumental: Boolean = false,
    val words: List<LyricWord>? = null
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
        private const val MUSIXMATCH_ROOT = "https://apic-desktop.musixmatch.com/ws/1.1/"

        fun formatTime(timeMs: Long): String {
            val totalSeconds = timeMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val milliseconds = timeMs % 1000
            val centiseconds = milliseconds / 10
            return String.format("%02d:%02d.%02d", minutes, seconds, centiseconds)
        }

        fun parseLrcText(lrcText: String, durationMs: Long? = null): List<LyricLine>? {
            val rawLines = mutableListOf<LyricLine>()
            val lineRegex = Regex("\\[(\\d+):(\\d+)\\.(\\d+)\\](.*)")
            
            lrcText.lines().forEach { line ->
                val match = lineRegex.find(line)
                if (match != null) {
                    val min = match.groupValues[1].toLongOrNull() ?: 0L
                    val sec = match.groupValues[2].toLongOrNull() ?: 0L
                    val ms = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                    val remainingPart = match.groupValues[4]
                    
                    val isMarker = remainingPart.contains("♪") || 
                                 remainingPart.contains("(Instrumental)", true) || 
                                 remainingPart.contains("[Instrumental]", true)
                    
                    val startTime = (min * 60 + sec) * 1000 + ms
                    
                    if (isMarker) {
                        rawLines.add(LyricLine(startTime, 0L, "♪", isInstrumental = true, words = null))
                    } else {
                        val tagRegex = Regex("<(\\d+):(\\d+)\\.(\\d+)>")
                        val tagMatches = tagRegex.findAll(remainingPart).toList()
                        
                        if (tagMatches.isNotEmpty()) {
                            val parsedWords = mutableListOf<LyricWord>()
                            
                            val firstTagMatch = tagMatches.first()
                            if (firstTagMatch.range.first > 0) {
                                val leadText = remainingPart.substring(0, firstTagMatch.range.first)
                                parsedWords.add(LyricWord(
                                    startTime = startTime,
                                    endTime = 0L,
                                    text = leadText,
                                    startIndex = 0,
                                    endIndex = 0
                                ))
                            }
                            
                            for (idx in tagMatches.indices) {
                                val currentTag = tagMatches[idx]
                                val nextTag = if (idx < tagMatches.size - 1) tagMatches[idx + 1] else null
                                
                                val startIdx = currentTag.range.last + 1
                                val endIdx = nextTag?.range?.first ?: remainingPart.length
                                val wordText = remainingPart.substring(startIdx, endIdx)
                                
                                val wMin = currentTag.groupValues[1].toLongOrNull() ?: 0L
                                val wSec = currentTag.groupValues[2].toLongOrNull() ?: 0L
                                val wMs = currentTag.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                                val wordStartTime = (wMin * 60 + wSec) * 1000 + wMs
                                
                                parsedWords.add(LyricWord(
                                    startTime = wordStartTime,
                                    endTime = 0L,
                                    text = wordText,
                                    startIndex = 0,
                                    endIndex = 0
                                ))
                            }
                            
                            var lastWordEndTime = 0L
                            if (parsedWords.isNotEmpty() && parsedWords.last().text.isBlank()) {
                                lastWordEndTime = parsedWords.last().startTime
                                parsedWords.removeAt(parsedWords.size - 1)
                            }
                            
                            // Filter out purely blank words and resolve their durations/endTimes
                            val cleanWords = mutableListOf<LyricWord>()
                            for (index in parsedWords.indices) {
                                val word = parsedWords[index]
                                if (word.text.trim().isEmpty()) {
                                    continue
                                }
                                
                                val nextWord = if (index < parsedWords.size - 1) parsedWords[index + 1] else null
                                val wordEndTime = nextWord?.startTime ?: lastWordEndTime
                                
                                cleanWords.add(LyricWord(
                                    startTime = word.startTime,
                                    endTime = wordEndTime,
                                    text = word.text,
                                    startIndex = 0,
                                    endIndex = 0
                                ))
                            }
                            
                            if (cleanWords.isNotEmpty()) {
                                val words = mutableListOf<LyricWord>()
                                val sb = StringBuilder()
                                
                                for (index in cleanWords.indices) {
                                    val word = cleanWords[index]
                                    if (sb.isNotEmpty()) {
                                        sb.append(" ")
                                    }
                                    
                                    val startIndex = sb.length
                                    val trimmedText = word.text.trim()
                                    sb.append(trimmedText)
                                    val endIndex = sb.length
                                    
                                    val wordDuration = word.endTime - word.startTime
                                    val estimatedWordDuration = (trimmedText.length * 120L + 150L).coerceIn(200L, 800L)
                                    val finalWordEndTime = if (wordDuration > estimatedWordDuration && word.endTime > word.startTime) {
                                        word.startTime + estimatedWordDuration
                                    } else {
                                        word.endTime
                                    }
                                    
                                    words.add(LyricWord(
                                        startTime = word.startTime,
                                        endTime = finalWordEndTime,
                                        text = trimmedText,
                                        startIndex = startIndex,
                                        endIndex = endIndex
                                    ))
                                }
                                
                                val content = sb.toString()
                                rawLines.add(LyricLine(startTime, 0L, content, isInstrumental = false, words = words))
                            } else {
                                val content = remainingPart.trim()
                                val finalContent = if (content.isEmpty()) "♪" else content
                                rawLines.add(LyricLine(startTime, 0L, finalContent, isInstrumental = (finalContent == "♪"), words = null))
                            }
                        } else {
                            val content = remainingPart.trim()
                            val finalContent = if (content.isEmpty()) "♪" else content
                            rawLines.add(LyricLine(startTime, 0L, finalContent, isInstrumental = (finalContent == "♪"), words = null))
                        }
                    }
                }
            }
            
            if (rawLines.isEmpty()) return null
            
            // Sort by start time to ensure chronological order
            rawLines.sortBy { it.startTime }

            // Resolve overlapping start times
            for (i in 1 until rawLines.size) {
                val prev = rawLines[i - 1]
                val curr = rawLines[i]
                
                // Determine the end time/vocal end time of the previous line
                val prevEnd = if (prev.words != null && prev.words.isNotEmpty()) {
                    prev.words.maxOf { Math.max(it.startTime, it.endTime) }
                } else {
                    val minDur = (prev.content.length * 100L + 500L).coerceIn(1500L, 4000L)
                    prev.startTime + minDur
                }
                
                val minGap = 100L // 100ms minimum gap between lines
                val requiredStart = prevEnd + minGap
                
                if (curr.startTime < requiredStart) {
                    val delta = requiredStart - curr.startTime
                    val newStartTime = requiredStart
                    
                    val shiftedWords = curr.words?.map { word ->
                        word.copy(
                            startTime = word.startTime + delta,
                            endTime = if (word.endTime > 0L) word.endTime + delta else 0L,
                            fullStartTime = if (word.fullStartTime > 0L) word.fullStartTime + delta else 0L,
                            fullEndTime = if (word.fullEndTime > 0L) word.fullEndTime + delta else 0L
                        )
                    }
                    
                    rawLines[i] = curr.copy(
                        startTime = newStartTime,
                        words = shiftedWords
                    )
                }
            }
            
            val songDurationMs = durationMs ?: (rawLines.last().startTime + 10000)
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
                
                val estimatedDuration = if (currentRaw.isInstrumental) {
                    nextRaw?.let { it.startTime - currentRaw.startTime } ?: (songDurationMs - currentRaw.startTime)
                } else {
                    (currentRaw.content.length * 100L + 500L).coerceIn(2000L, 8000L)
                }
                
                var endTime = nextRaw?.startTime?.let { Math.min(currentRaw.startTime + estimatedDuration, it - 200L) } 
                              ?: (currentRaw.startTime + estimatedDuration)
                
                if (nextRaw == null) {
                    if (currentRaw.words != null && currentRaw.words.isNotEmpty()) {
                        val lastWord = currentRaw.words.last()
                        val lastWordEnd = if (lastWord.endTime > 0L) lastWord.endTime else (lastWord.startTime + 1500L)
                        endTime = Math.min(lastWordEnd + 1500L, songDurationMs)
                    } else {
                        endTime = Math.min(currentRaw.startTime + estimatedDuration, songDurationMs)
                    }
                }
                
                // If the line has explicit words, make sure endTime is at least the last word's end time
                val lastWordEnd = currentRaw.words?.lastOrNull()?.let { Math.max(it.startTime, it.endTime) } ?: 0L
                val finalEndTime = if (lastWordEnd > 0L) Math.max(endTime, lastWordEnd) else endTime
                
                val currentLine = currentRaw.copy(endTime = finalEndTime)
                
                // Resolve word end times or estimate words if they don't exist
                val lineWithEndTimes = if (currentLine.words != null && currentLine.words.isNotEmpty()) {
                    val wordsWithEndTimes = currentLine.words.mapIndexed { index, word ->
                        if (index == currentLine.words.size - 1 && (word.endTime == 0L || word.fullEndTime == 0L)) {
                            word.copy(
                                endTime = currentLine.endTime,
                                fullEndTime = currentLine.endTime
                            )
                        } else {
                            word
                        }
                    }
                    currentLine.copy(words = wordsWithEndTimes)
                } else if (!currentLine.isInstrumental && currentLine.content != "♪") {
                    val content = currentLine.content
                    val wordMatches = Regex("(\\S+\\s*)").findAll(content).toList()
                    if (wordMatches.isNotEmpty()) {
                        val totalLen = content.length.toFloat()
                        val lineDuration = currentLine.endTime - currentLine.startTime
                        val words = mutableListOf<LyricWord>()
                        var currentWordStart = currentLine.startTime
                        
                        for (index in wordMatches.indices) {
                            val match = wordMatches[index]
                            val wordText = match.value
                            val startIndex = match.range.first
                            val endIndex = match.range.last + 1
                            
                            val wordDuration = if (totalLen > 0) {
                                (lineDuration * (wordText.length / totalLen)).toLong()
                            } else {
                                0L
                            }
                            
                            val wordEnd = if (index == wordMatches.size - 1) {
                                currentLine.endTime
                            } else {
                                currentWordStart + wordDuration
                            }
                            
                            words.add(LyricWord(
                                startTime = currentWordStart,
                                endTime = wordEnd,
                                text = wordText,
                                startIndex = startIndex,
                                endIndex = endIndex,
                                fullStartTime = currentWordStart,
                                fullEndTime = wordEnd,
                                isEstimated = true
                            ))
                            
                            currentWordStart = wordEnd
                        }
                        currentLine.copy(words = words)
                    } else {
                        currentLine
                    }
                } else {
                    currentLine
                }
                
                processedLines.add(lineWithEndTimes)
                
                // Dynamic Gap Heuristic (only if next line isn't already a marker)
                if (nextRaw != null) {
                    if (!nextRaw.isInstrumental) {
                        val trueSilence = nextRaw.startTime - lineWithEndTimes.endTime
                        if (trueSilence > gapThreshold) {
                            processedLines.add(LyricLine(
                                startTime = lineWithEndTimes.endTime + 200L,
                                endTime = nextRaw.startTime - 200L,
                                content = "♪",
                                isInstrumental = true,
                                words = null
                            ))
                        }
                    }
                } else {
                    // Trailing Gap at the end of the song!
                    val trueSilence = songDurationMs - lineWithEndTimes.endTime
                    if (trueSilence > 3000L) {
                        processedLines.add(LyricLine(
                            startTime = lineWithEndTimes.endTime + 200L,
                            endTime = songDurationMs,
                            content = "♪",
                            isInstrumental = true,
                            words = null
                        ))
                    }
                }
            }
            
            // Final sanity check: stretch last processed line to song end if needed
            if (processedLines.isNotEmpty() && processedLines.last().endTime < songDurationMs) {
                val last = processedLines.last()
                processedLines[processedLines.size - 1] = last.copy(endTime = songDurationMs)
            }
            
            // If the first lyric starts late, insert initial instrumental marker
            if (processedLines.first().startTime > 5000) {
                processedLines.add(0, LyricLine(
                    startTime = 0,
                    endTime = processedLines.first().startTime - 500L,
                    content = "♪",
                    isInstrumental = true,
                    words = null
                ))
            }
            
            return processedLines
        }
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

        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val customEnabled = prefs.getBoolean("custom_lyrics_enabled", false)
        if (customEnabled) {
            var endpoint = prefs.getString("custom_lyrics_endpoint", "http://10.0.2.2:8000/api/lyrics") ?: ""
            if (endpoint.isNotEmpty()) {
                if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
                    endpoint = "http://$endpoint"
                }
                val format = prefs.getString("custom_lyrics_format", "LRC") ?: "LRC"
                val timeoutSec = prefs.getFloat("custom_lyrics_timeout", 60f).toLong()
                
                val urlBuilder = endpoint.toHttpUrlOrNull()?.newBuilder()
                if (urlBuilder != null) {
                    urlBuilder.addQueryParameter("artist", artist)
                    urlBuilder.addQueryParameter("title", title)
                    urlBuilder.addQueryParameter("duration", (durationMs / 1000.0).toString())
                    urlBuilder.addQueryParameter("format", format)
                    val fullUrl = urlBuilder.build().toString()
                    
                    showToast("Connecting to custom provider...")
                    
                    val customClient = client.newBuilder()
                        .connectTimeout(timeoutSec, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(timeoutSec, java.util.concurrent.TimeUnit.SECONDS)
                        .writeTimeout(timeoutSec, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                        
                    val request = Request.Builder()
                        .url(fullUrl)
                        .header("User-Agent", USER_AGENT)
                        .build()
                        
                    customClient.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            Log.e("LyricsManager", "Custom provider failed, falling back to Musixmatch: ${e.message}")
                            showToast("Custom provider failed. Trying Musixmatch...")
                            fetchMusixmatchLyrics(title, artist, durationMs, cacheFile, missFile, candidates, wantedDurationSec, callback)
                        }
                        
                        override fun onResponse(call: Call, response: Response) {
                            val body = response.body?.string()
                            if (response.isSuccessful && body != null) {
                                try {
                                    val lyricsText = if (format.equals("JSON", ignoreCase = true)) {
                                        val jsonObj = gson.fromJson(body, com.google.gson.JsonObject::class.java)
                                        jsonObj.get("lyrics")?.asString ?: body
                                    } else {
                                        body
                                    }
                                    
                                    val parsedLines = parseLrcText(lyricsText, durationMs)
                                    if (parsedLines != null) {
                                        try { cacheFile.writeText(gson.toJson(parsedLines)) } catch (ex: Exception) {}
                                        showToast("Custom provider lyrics loaded!")
                                        callback(parsedLines, true)
                                        return
                                    }
                                } catch (e: Exception) {
                                    Log.e("LyricsManager", "Failed to parse custom provider lyrics: ${e.message}")
                                }
                            }
                            Log.d("LyricsManager", "Custom provider response failed or empty, falling back to Musixmatch")
                            showToast("Custom provider empty response. Trying Musixmatch...")
                            fetchMusixmatchLyrics(title, artist, durationMs, cacheFile, missFile, candidates, wantedDurationSec, callback)
                        }
                    })
                    return
                } else {
                    showToast("Invalid custom provider URL: $endpoint")
                }
            }
        }

        fetchMusixmatchLyrics(title, artist, durationMs, cacheFile, missFile, candidates, wantedDurationSec, callback)
    }

    private fun getMusixmatchToken(callback: (String?) -> Unit) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val cachedToken = prefs.getString("musixmatch_token", null)
        val expireTime = prefs.getLong("musixmatch_token_expire", 0L)
        val now = System.currentTimeMillis()
        
        if (cachedToken != null && now < expireTime) {
            callback(cachedToken)
            return
        }
        
        val url = "${MUSIXMATCH_ROOT}token.get?app_id=web-desktop-app-v1.0&user_language=en&t=$now"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
            
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("LyricsManager", "Musixmatch token fetch failed: ${e.message}")
                callback(null)
            }
            
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    try {
                        val json = gson.fromJson(body, com.google.gson.JsonObject::class.java)
                        val header = json.getAsJsonObject("message")?.getAsJsonObject("header")
                        val statusCode = header?.get("status_code")?.asInt ?: 0
                        if (statusCode == 200) {
                            val token = json.getAsJsonObject("message")
                                ?.getAsJsonObject("body")
                                ?.get("user_token")?.asString
                            if (token != null) {
                                prefs.edit()
                                    .putString("musixmatch_token", token)
                                    .putLong("musixmatch_token_expire", now + 600 * 1000L) // 10 minutes
                                    .apply()
                                callback(token)
                                return
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("LyricsManager", "Error parsing Musixmatch token response", e)
                    }
                }
                callback(null)
            }
        })
    }

    private fun fetchMusixmatchLyrics(
        title: String,
        artist: String,
        durationMs: Long,
        cacheFile: File,
        missFile: File,
        candidates: List<QueryCandidate>,
        wantedDurationSec: Double?,
        callback: (List<LyricLine>?, Boolean) -> Unit
    ) {
        getMusixmatchToken { token ->
            if (token == null) {
                Log.d("LyricsManager", "Musixmatch token is null, falling back to LRCLIB")
                fetchStandardLrc(cacheFile, missFile, candidates, wantedDurationSec, callback)
                return@getMusixmatchToken
            }
            
            val urlBuilder = "${MUSIXMATCH_ROOT}track.search".toHttpUrlOrNull()?.newBuilder()
            if (urlBuilder == null) {
                fetchStandardLrc(cacheFile, missFile, candidates, wantedDurationSec, callback)
                return@getMusixmatchToken
            }
            
            urlBuilder.addQueryParameter("q", "$artist $title")
            urlBuilder.addQueryParameter("page_size", "5")
            urlBuilder.addQueryParameter("page", "1")
            urlBuilder.addQueryParameter("app_id", "web-desktop-app-v1.0")
            urlBuilder.addQueryParameter("usertoken", token)
            urlBuilder.addQueryParameter("t", System.currentTimeMillis().toString())
            val url = urlBuilder.build().toString()
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
                
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("LyricsManager", "Musixmatch search failed: ${e.message}, falling back to LRCLIB")
                    fetchStandardLrc(cacheFile, missFile, candidates, wantedDurationSec, callback)
                }
                
                override fun onResponse(call: Call, response: Response) {
                    val bodyStr = response.body?.string()
                    if (response.isSuccessful && bodyStr != null) {
                        try {
                            val json = gson.fromJson(bodyStr, com.google.gson.JsonObject::class.java)
                            val message = json.getAsJsonObject("message")
                            val header = message?.getAsJsonObject("header")
                            val statusCode = header?.get("status_code")?.asInt ?: 0
                            if (statusCode == 200) {
                                val trackList = message?.getAsJsonObject("body")?.getAsJsonArray("track_list")
                                if (trackList != null && trackList.size() > 0) {
                                    val trackObj = trackList.get(0).asJsonObject.getAsJsonObject("track")
                                    val trackId = trackObj.get("track_id").asInt
                                    
                                    tryMusixmatchRichsync(trackId, token, durationMs, cacheFile, missFile, candidates, wantedDurationSec, callback)
                                    return
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("LyricsManager", "Error parsing Musixmatch search response", e)
                        }
                    }
                    Log.d("LyricsManager", "Musixmatch search response failed or empty, falling back to LRCLIB")
                    fetchStandardLrc(cacheFile, missFile, candidates, wantedDurationSec, callback)
                }
            })
        }
    }

    private fun tryMusixmatchRichsync(
        trackId: Int,
        token: String,
        durationMs: Long,
        cacheFile: File,
        missFile: File,
        candidates: List<QueryCandidate>,
        wantedDurationSec: Double?,
        callback: (List<LyricLine>?, Boolean) -> Unit
    ) {
        val now = System.currentTimeMillis()
        val url = "${MUSIXMATCH_ROOT}track.richsync.get?track_id=$trackId&app_id=web-desktop-app-v1.0&usertoken=$token&t=$now"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
            
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d("LyricsManager", "Musixmatch richsync failed: ${e.message}, trying subtitle fallback")
                tryMusixmatchSubtitle(trackId, token, durationMs, cacheFile, missFile, candidates, wantedDurationSec, callback)
            }
            
            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (response.isSuccessful && bodyStr != null) {
                    try {
                        val json = gson.fromJson(bodyStr, com.google.gson.JsonObject::class.java)
                        val message = json.getAsJsonObject("message")
                        val header = message?.getAsJsonObject("header")
                        val statusCode = header?.get("status_code")?.asInt ?: 0
                        if (statusCode == 200) {
                            val richsyncObj = message?.getAsJsonObject("body")?.getAsJsonObject("richsync")
                            val richsyncBody = richsyncObj?.get("richsync_body")?.asString
                            if (richsyncBody != null) {
                                val parsedRichsync = parseRichsync(richsyncBody)
                                if (parsedRichsync != null) {
                                    val parsedLines = parseLrcText(parsedRichsync, durationMs)
                                    if (parsedLines != null) {
                                        try { cacheFile.writeText(gson.toJson(parsedLines)) } catch (ex: Exception) {}
                                        showToast("Musixmatch word-synced lyrics loaded!")
                                        callback(parsedLines, true)
                                        return
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("LyricsManager", "Error parsing richsync response", e)
                    }
                }
                Log.d("LyricsManager", "Musixmatch richsync unavailable or empty, trying subtitle fallback")
                tryMusixmatchSubtitle(trackId, token, durationMs, cacheFile, missFile, candidates, wantedDurationSec, callback)
            }
        })
    }

    private fun tryMusixmatchSubtitle(
        trackId: Int,
        token: String,
        durationMs: Long,
        cacheFile: File,
        missFile: File,
        candidates: List<QueryCandidate>,
        wantedDurationSec: Double?,
        callback: (List<LyricLine>?, Boolean) -> Unit
    ) {
        val now = System.currentTimeMillis()
        val url = "${MUSIXMATCH_ROOT}track.subtitle.get?track_id=$trackId&subtitle_format=lrc&app_id=web-desktop-app-v1.0&usertoken=$token&t=$now"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
            
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d("LyricsManager", "Musixmatch subtitle failed: ${e.message}, falling back to LRCLIB")
                fetchStandardLrc(cacheFile, missFile, candidates, wantedDurationSec, callback)
            }
            
            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (response.isSuccessful && bodyStr != null) {
                    try {
                        val json = gson.fromJson(bodyStr, com.google.gson.JsonObject::class.java)
                        val message = json.getAsJsonObject("message")
                        val header = message?.getAsJsonObject("header")
                        val statusCode = header?.get("status_code")?.asInt ?: 0
                        if (statusCode == 200) {
                            val subtitleObj = message?.getAsJsonObject("body")?.getAsJsonObject("subtitle")
                            val subtitleBody = subtitleObj?.get("subtitle_body")?.asString
                            if (subtitleBody != null) {
                                val parsedLines = parseLrcText(subtitleBody, durationMs)
                                if (parsedLines != null) {
                                    try { cacheFile.writeText(gson.toJson(parsedLines)) } catch (ex: Exception) {}
                                    showToast("Musixmatch lyrics loaded!")
                                    callback(parsedLines, true)
                                    return
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("LyricsManager", "Error parsing subtitle response", e)
                    }
                }
                Log.d("LyricsManager", "Musixmatch subtitle failed or empty, falling back to LRCLIB")
                fetchStandardLrc(cacheFile, missFile, candidates, wantedDurationSec, callback)
            }
        })
    }

    private fun parseRichsync(richsyncBody: String): String? {
        try {
            val array = gson.fromJson(richsyncBody, com.google.gson.JsonArray::class.java)
            val sb = StringBuilder()
            for (item in array) {
                val obj = item.asJsonObject
                val ts = obj.get("ts")?.asDouble ?: continue
                val lArray = obj.getAsJsonArray("l") ?: continue
                
                sb.append("[").append(formatLrcTime(ts)).append("]")
                for (wordItem in lArray) {
                    val wordObj = wordItem.asJsonObject
                    val offset = wordObj.get("o")?.asDouble ?: 0.0
                    val text = wordObj.get("c")?.asString ?: ""
                    val wordTime = ts + offset
                    sb.append("<").append(formatLrcTime(wordTime)).append(">").append(text).append(" ")
                }
                sb.append("\n")
            }
            return sb.toString()
        } catch (e: Exception) {
            Log.e("LyricsManager", "Error parsing richsync json", e)
            return null
        }
    }

    private fun formatLrcTime(seconds: Double): String {
        val totalMs = (seconds * 1000).toLong()
        val totalSeconds = totalMs / 1000
        val minutes = totalSeconds / 60
        val secs = totalSeconds % 60
        val milliseconds = totalMs % 1000
        val centiseconds = milliseconds / 10
        return String.format("%02d:%02d.%02d", minutes, secs, centiseconds)
    }

    private fun fetchStandardLrc(
        cacheFile: File,
        missFile: File,
        candidates: List<QueryCandidate>,
        wantedDurationSec: Double?,
        callback: (List<LyricLine>?, Boolean) -> Unit
    ) {
        val primary = candidates.first()
        val url = "https://lrclib.net/api/search?track_name=${URLEncoder.encode(primary.title, "UTF-8")}&artist_name=${URLEncoder.encode(primary.artist, "UTF-8")}"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, false)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.code == 429 || response.code >= 500) {
                    callback(null, false)
                    return
                }

                val lines = if (response.isSuccessful && body != null) {
                    try {
                        pickFromSearch(body, candidates, wantedDurationSec)
                    } catch (e: Exception) {
                        Log.e("LyricsManager", "Parse failure for $url", e)
                        null
                    }
                } else null

                if (lines != null) {
                    try { cacheFile.writeText(gson.toJson(lines)) } catch (e: Exception) {}
                    callback(lines, true)
                } else {
                    try { missFile.writeText(System.currentTimeMillis().toString()) } catch (e: Exception) {}
                    callback(null, true)
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
        if (res.syncedLyrics.isNullOrBlank()) return null
        val songDurationMs = res.duration?.times(1000)?.toLong()
        return parseLrcText(res.syncedLyrics, songDurationMs)
    }

    private fun showToast(message: String) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
