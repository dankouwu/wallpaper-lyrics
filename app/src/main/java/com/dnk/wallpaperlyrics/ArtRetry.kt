package com.dnk.wallpaperlyrics

object ArtRetry {

    fun delayAfterFailure(failedAttempts: Int): Long? = when (failedAttempts) {
        1 -> 2000L
        2 -> 5000L
        else -> null
    }
}
