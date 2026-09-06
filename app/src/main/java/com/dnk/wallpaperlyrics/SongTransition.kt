package com.dnk.wallpaperlyrics

/**
 * The phase timeline for a track change: hold the old art, swap, hold the new.
 * Timestamps in, phase out, so the boundaries can be tested against a fake clock.
 */
object SongTransition {
    const val HOLD_OLD_MS = 1000L
    const val SWAP_MS = 500L
    const val HOLD_NEW_MS = 1000L
    const val TOTAL_MS = HOLD_OLD_MS + SWAP_MS + HOLD_NEW_MS

    enum class Phase { NONE, HOLD_OLD, SWAP, HOLD_NEW }

    fun phaseAt(elapsedMs: Long): Phase {
        if (elapsedMs < 0L) return Phase.NONE
        return when {
            elapsedMs < HOLD_OLD_MS -> Phase.HOLD_OLD
            elapsedMs < HOLD_OLD_MS + SWAP_MS -> Phase.SWAP
            elapsedMs < TOTAL_MS -> Phase.HOLD_NEW
            else -> Phase.NONE
        }
    }

    fun isActive(elapsedMs: Long): Boolean {
        return elapsedMs in 0 until TOTAL_MS
    }

    fun shouldCommit(elapsedMs: Long): Boolean {
        return elapsedMs >= HOLD_OLD_MS
    }
}
