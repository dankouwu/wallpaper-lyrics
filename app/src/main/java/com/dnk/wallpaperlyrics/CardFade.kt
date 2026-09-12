package com.dnk.wallpaperlyrics

/**
 * Pure transition helper for the metadata album card fade.
 * Runs on the same 500 ms duration as the metadata text crossfade.
 */
object CardFade {
    const val DURATION_MS = 500L

    fun shouldFade(currentArt: Any?, incomingArt: Any?): Boolean {
        return incomingArt !== currentArt
    }

    fun progressAt(elapsedMs: Long): Float {
        if (elapsedMs <= 0L) return 0f
        if (elapsedMs >= DURATION_MS) return 1f
        return elapsedMs.toFloat() / DURATION_MS
    }

    fun isComplete(progress: Float): Boolean {
        return progress >= 1.0f
    }

    fun outgoingAlpha(progress: Float, rectanglesMatch: Boolean): Float {
        if (rectanglesMatch) return 1.0f
        return (1.0f - progress).coerceIn(0f, 1f)
    }

    fun incomingAlpha(progress: Float): Float {
        return progress.coerceIn(0f, 1f)
    }

    fun outgoingAlphaInt(progress: Float, rectanglesMatch: Boolean): Int {
        return (outgoingAlpha(progress, rectanglesMatch) * 255f).toInt()
    }

    fun incomingAlphaInt(progress: Float): Int {
        return (incomingAlpha(progress) * 255f).toInt()
    }
}
