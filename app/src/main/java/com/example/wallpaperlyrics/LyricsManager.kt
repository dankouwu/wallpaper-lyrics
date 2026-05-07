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
    private val client = OkHttpClient()
    private val gson = Gson()
    private val cacheDir = File(context.cacheDir, "lyrics_cache").apply { mkdirs() }

    fun fetchLyrics(title: String, artist: String, callback: (List<LyricLine>?) -> Unit) {
        val cacheKey = "${title}_${artist}".hashCode().toString()
        val cacheFile = File(cacheDir, "$cacheKey.json")
        
        // Try local cache first
        if (cacheFile.exists()) {
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

        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null)
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
                        callback(null)
                    }
                } else {
                    callback(null)
                }
            }
        })
    }

    private fun parseLyrics(res: LyricsResponse): List<LyricLine>? {
        if (!res.syncedLyrics.isNullOrBlank()) {
            val lines = mutableListOf<LyricLine>()
            val regex = Regex("\\[(\\d+):(\\d+)\\.(\\d+)\\](.*)")
            res.syncedLyrics.lines().forEach { line ->
                val match = regex.find(line)
                if (match != null) {
                    val min = match.groupValues[1].toLong()
                    val sec = match.groupValues[2].toLong()
                    val ms = match.groupValues[3].padEnd(3, '0').take(3).toLong()
                    val content = match.groupValues[4].trim()
                    val startTime = (min * 60 + sec) * 1000 + ms
                    if (content.isNotBlank()) lines.add(LyricLine(startTime, content))
                }
            }
            if (lines.isNotEmpty()) return lines
        }
        if (!res.plainLyrics.isNullOrBlank()) {
            return res.plainLyrics.lines()
                .filter { it.isNotBlank() }
                .mapIndexed { index, content -> LyricLine(index * 4000L, content) }
        }
        return null
    }
}
