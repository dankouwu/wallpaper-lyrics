package com.example.wallpaperlyrics

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import okhttp3.*
import java.io.File
import java.io.IOException
import java.net.URLEncoder

data class LyricLine(
    val startTime: Long, // in milliseconds
    val content: String
)

data class LyricsResponse(
    val plainLyrics: String?,
    val syncedLyrics: String?
)

class LyricsManager(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val cacheDir = File(context.cacheDir, "lyrics_cache").apply { mkdirs() }
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    fun fetchLyrics(title: String, artist: String, attempt: Int = 1, callback: (List<LyricLine>?) -> Unit) {
        val cacheKey = "${title}_${artist}".hashCode().toString()
        val cacheFile = File(cacheDir, "$cacheKey.json")
        
        // Try local cache first (Only on first attempt)
        if (attempt == 1 && cacheFile.exists()) {
            try {
                val json = cacheFile.readText()
                val lines = gson.fromJson(json, Array<LyricLine>::class.java).toList()
                callback(lines)
                return
            } catch (e: Exception) {
                cacheFile.delete()
            }
        }

        val query = "track_name=${URLEncoder.encode(title, "UTF-8")}&artist_name=${URLEncoder.encode(artist, "UTF-8")}"
        val url = "https://lrclib.net/api/get?$query"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "WallpaperLyricsApp/1.0 (Android)") // Help prevent generic rate limits
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                retryOrNull(title, artist, attempt, callback)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    try {
                        val lyricsRes = gson.fromJson(body, LyricsResponse::class.java)
                        val lines = parseLyrics(lyricsRes)
                        if (lines != null) {
                            cacheFile.writeText(gson.toJson(lines))
                        }
                        callback(lines)
                    } catch (e: Exception) {
                        retryOrNull(title, artist, attempt, callback)
                    }
                } else if (response.code == 429 || response.code >= 500) {
                    // Rate limit or server error - definitely retry
                    retryOrNull(title, artist, attempt, callback)
                } else {
                    callback(null)
                }
            }
        })
    }

    private fun retryOrNull(title: String, artist: String, attempt: Int, callback: (List<LyricLine>?) -> Unit) {
        if (attempt < 3) {
            val delay = attempt * 2000L // 2s, 4s
            handler.postDelayed({
                fetchLyrics(title, artist, attempt + 1, callback)
            }, delay)
        } else {
            callback(null)
        }
    }

    private fun parseLyrics(res: LyricsResponse): List<LyricLine>? {
        val rawLines = mutableListOf<LyricLine>()
        if (!res.syncedLyrics.isNullOrBlank()) {
            val regex = Regex("\\[(\\d+):(\\d+)\\.(\\d+)\\](.*)")
            res.syncedLyrics.lines().forEach { line ->
                val match = regex.find(line)
                if (match != null) {
                    val min = match.groupValues[1].toLong()
                    val sec = match.groupValues[2].toLong()
                    val ms = match.groupValues[3].padEnd(3, '0').take(3).toLong()
                    val content = match.groupValues[4].trim()
                    val startTime = (min * 60 + sec) * 1000 + ms
                    if (content.isNotBlank()) rawLines.add(LyricLine(startTime, content))
                }
            }
        } else if (!res.plainLyrics.isNullOrBlank()) {
            rawLines.addAll(res.plainLyrics.lines()
                .filter { it.isNotBlank() }
                .mapIndexed { index, content -> LyricLine(index * 4000L, content) })
        }

        if (rawLines.isEmpty()) return null

        // Inject Instrumental Markers (The "Apple Music" Dots)
        val processedLines = mutableListOf<LyricLine>()
        
        // 1. Check for Intro Gap (Singer starts late)
        if (rawLines[0].startTime > 8000) {
            processedLines.add(LyricLine(3000, "♪"))
        }

        // 2. Iterate and check for bridges/solos using character-aware estimation
        for (i in 0 until rawLines.size) {
            processedLines.add(rawLines[i])
            if (i < rawLines.size - 1) {
                val currentLine = rawLines[i]
                val nextLine = rawLines[i+1]
                
                val timeGap = nextLine.startTime - currentLine.startTime
                // Estimate singing duration: ~250ms per character, capped at 8 seconds
                val estimatedSingingDuration = (currentLine.content.length * 250L).coerceAtMost(8000L)
                val trueSilence = timeGap - estimatedSingingDuration
                
                // Only inject a note if there's more than 12 seconds of true silence
                if (trueSilence > 12000) {
                    // Place the note 2 seconds after the estimated end of the lyric
                    processedLines.add(LyricLine(currentLine.startTime + estimatedSingingDuration + 2000, "♪"))
                }
            }
        }
        
        return processedLines
    }
}
