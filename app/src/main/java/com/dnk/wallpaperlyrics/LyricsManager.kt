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

class LyricsManager(private val context: Context) {
    val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val cacheDir = File(context.cacheDir, "lyrics_cache").apply { mkdirs() }
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    fun fetchBitmap(url: String, callback: (android.graphics.Bitmap?) -> Unit) {
        if (url.startsWith("content://")) {
            try {
                val uri = android.net.Uri.parse(url)
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                callback(bitmap)
            } catch (e: Exception) {
                Log.e("LyricsManager", "Failed to fetch content URI", e)
                callback(null)
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

    fun fetchLyrics(title: String, artist: String, attempt: Int = 1, callback: (List<LyricLine>?) -> Unit) {
        val cacheKey = "${title}_${artist}".hashCode().toString()
        val cacheFile = File(cacheDir, "$cacheKey.json")
        
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
            .header("User-Agent", "WallpaperLyricsApp/1.0 (Android)") 
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
                    retryOrNull(title, artist, attempt, callback)
                } else {
                    callback(null)
                }
            }
        })
    }

    private fun retryOrNull(title: String, artist: String, attempt: Int, callback: (List<LyricLine>?) -> Unit) {
        if (attempt < 3) {
            val delay = attempt * 2000L 
            handler.postDelayed({
                fetchLyrics(title, artist, attempt + 1, callback)
            }, delay)
        } else {
            callback(null)
        }
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
