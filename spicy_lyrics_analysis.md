# Spicy Lyrics Integration Analysis (Compact)

This document outlines the core steps, data structures, and Kotlin implementations required to integrate the **Spicy Lyrics** API into the Android Live Wallpaper app.

---

## 1. Network Request & API

- **Endpoint:** `POST https://api.spicylyrics.org/query`
- **Headers:**
  - `Content-Type: application/json`
  - `SpicyLyrics-Version: 1.58.62`
  - `SpicyLyrics-WebAuth: Bearer <SpotifyAccessToken>`
- **Request Body:**
  ```json
  {
    "queries": [
      {
        "operation": "lyrics",
        "variables": { "id": "<spotify_track_id>", "auth": "SpicyLyrics-WebAuth" }
      }
    ],
    "client": { "version": "1.58.62" }
  }
  ```
- **Asynchronous Queue (HTTP 503):** If a song is uncached, the server returns `503 Service Unavailable`. The client must enter a polling retry loop (using exponential backoff) until it returns `200 OK`.

---

## 2. SLObjPack Decoding (Kotlin)

Spicy Lyrics returns a packed structure of the form `[valuesList, stream]`. Use this decoder to unpack it:

```kotlin
package com.dnk.wallpaperlyrics

import com.google.gson.*

class SLObjPack {
    private val forbiddenKeys = setOf("__proto__", "constructor", "prototype")

    fun unpack(packed: JsonArray): JsonElement {
        val valuesList = packed.get(0).asJsonArray
        val stream = packed.get(1).asJsonArray
        var cursor = 0

        fun readStream() = stream.get(cursor++).asInt
        fun resolvePointer(ptr: Int) = valuesList.get(ptr)
        
        fun readKey(): String {
            val key = resolvePointer(readStream()).asString
            if (forbiddenKeys.contains(key)) throw IllegalArgumentException("Forbidden key: $key")
            return key
        }

        fun decode(depth: Int): JsonElement {
            if (depth > 512) throw IllegalStateException("Max depth exceeded")
            val op = readStream()
            if (op >= 0) return resolvePointer(op)

            return when (op) {
                -1 -> { // Object
                    val numKeys = readStream()
                    val keys = Array(numKeys) { readKey() }
                    JsonObject().apply { for (k in keys) add(k, decode(depth + 1)) }
                }
                -2 -> { // Array
                    val numItems = readStream()
                    JsonArray().apply { for (i in 0 until numItems) add(decode(depth + 1)) }
                }
                -3 -> { // Schema Array (Array of objects sharing keys)
                    val numItems = readStream()
                    val numKeys = readStream()
                    val keys = Array(numKeys) { readKey() }
                    JsonArray().apply {
                        for (i in 0 until numItems) {
                            add(JsonObject().apply { for (k in keys) add(k, decode(depth + 1)) })
                        }
                    }
                }
                -4 -> JsonArray()
                -5 -> JsonArray().apply { add(decode(depth + 1)) }
                -6 -> JsonObject()
                else -> throw IllegalStateException("Unknown opcode $op")
            }
        }
        return decode(0)
    }
}
```

---

## 3. Mapping JSON to App Models (Kotlin)

Timings are in **seconds** (float/double) and must be converted to milliseconds (`* 1000`).

```kotlin
fun parseSpicyLyrics(lyricsJson: JsonObject): List<LyricLine>? {
    val type = lyricsJson.get("Type")?.asString ?: return null
    val result = mutableListOf<LyricLine>()

    when (type) {
        "Syllable" -> {
            val contentArray = lyricsJson.getAsJsonArray("Content") ?: return null
            for (vocalElement in contentArray) {
                val vocalObj = vocalElement.asJsonObject
                if (vocalObj.get("Type")?.asString != "Vocal") continue

                val lead = vocalObj.getAsJsonObject("Lead") ?: continue
                val syllables = lead.getAsJsonArray("Syllables") ?: continue
                val wordsList = mutableListOf<LyricWord>()
                val lineTextBuilder = StringBuilder()

                // 1. Lead Syllables
                for (i in 0 until syllables.size()) {
                    val syllableObj = syllables.get(i).asJsonObject
                    val text = syllableObj.get("Text")?.asString ?: ""
                    val isPartOfWord = syllableObj.get("IsPartOfWord")?.asBoolean ?: false
                    val startTimeMs = ((syllableObj.get("StartTime")?.asDouble ?: 0.0) * 1000).toLong()
                    val endTimeMs = ((syllableObj.get("EndTime")?.asDouble ?: 0.0) * 1000).toLong()

                    val startIndex = lineTextBuilder.length
                    lineTextBuilder.append(text)
                    val endIndex = lineTextBuilder.length
                    if (i < syllables.size() - 1 && !isPartOfWord) lineTextBuilder.append(" ")

                    wordsList.add(LyricWord(startTime = startTimeMs, endTime = endTimeMs, text = text.trim(), startIndex = startIndex, endIndex = endIndex))
                }

                // 2. Background Syllables (Append in parentheses)
                vocalObj.getAsJsonArray("Background")?.let { bgArray ->
                    if (bgArray.size() > 0) {
                        bgArray.get(0).asJsonObject.getAsJsonArray("Syllables")?.let { bgSyllables ->
                            lineTextBuilder.append(" (")
                            for (i in 0 until bgSyllables.size()) {
                                val syllableObj = bgSyllables.get(i).asJsonObject
                                val text = syllableObj.get("Text")?.asString ?: ""
                                val isPartOfWord = syllableObj.get("IsPartOfWord")?.asBoolean ?: false
                                val startTimeMs = ((syllableObj.get("StartTime")?.asDouble ?: 0.0) * 1000).toLong()
                                val endTimeMs = ((syllableObj.get("EndTime")?.asDouble ?: 0.0) * 1000).toLong()

                                val startIndex = lineTextBuilder.length
                                lineTextBuilder.append(text)
                                val endIndex = lineTextBuilder.length
                                if (i < bgSyllables.size() - 1 && !isPartOfWord) lineTextBuilder.append(" ")

                                wordsList.add(LyricWord(startTime = startTimeMs, endTime = endTimeMs, text = text.trim(), startIndex = startIndex, endIndex = endIndex))
                            }
                            lineTextBuilder.append(")")
                        }
                    }
                }

                val lineStartTime = lead.get("StartTime")?.asDouble?.times(1000)?.toLong() ?: wordsList.firstOrNull()?.startTime ?: 0L
                val lineEndTime = lead.get("EndTime")?.asDouble?.times(1000)?.toLong() ?: wordsList.lastOrNull()?.endTime ?: 0L

                result.add(LyricLine(startTime = lineStartTime, endTime = lineEndTime, content = lineTextBuilder.toString(), words = wordsList))
            }
        }
        "Line" -> {
            lyricsJson.getAsJsonArray("Content")?.forEach {
                val lineObj = it.asJsonObject
                if (lineObj.get("Type")?.asString == "Vocal") {
                    result.add(LyricLine(
                        startTime = ((lineObj.get("StartTime")?.asDouble ?: 0.0) * 1000).toLong(),
                        endTime = ((lineObj.get("EndTime")?.asDouble ?: 0.0) * 1000).toLong(),
                        content = lineObj.get("Text")?.asString ?: "",
                        words = null // Estimated by existing code
                    ))
                }
            }
        }
        "Static" -> {
            lyricsJson.getAsJsonArray("Lines")?.forEach {
                result.add(LyricLine(startTime = 0L, endTime = 0L, content = it.asJsonObject.get("Text")?.asString ?: "", words = null))
            }
        }
    }
    return if (result.isEmpty()) null else result
}
```

---

## 4. Key Integration Steps

1. **Get Spotify Access Token:**
   - **Option A (No-Auth):** Test if server returns `200 OK` with dummy tokens on cached songs.
   - **Option B (Official Flow):** Add Spotify PKCE login in Settings to save a persistent token refresh loop.
   - **Option C (Xposed Hook):** If Spicy EX is installed, retrieve token via ContentProvider.
2. **Execute Queries:** Extract track ID from `MediaMetadata.METADATA_KEY_MEDIA_ID` -> POST query to `api.spicylyrics.org/query`.
3. **Handle 503 Retry:** If response code is `503`, retry every 3–5 seconds (up to a timeout limit).
4. **Decode and Map:** Decompress via `SLObjPack.unpack()`, map to `LyricLine` lists using `parseSpicyLyrics()`, and cache.
