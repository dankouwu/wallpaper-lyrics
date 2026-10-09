package com.dnk.wallpaperlyrics

/**
 * No frame loop runs while the screen is off, so a frame composed then stays on the display and has to show the end state.
 */
object TransitionSettle {

    fun shouldSettle(isScreenOff: Boolean): Boolean {
        return isScreenOff
    }

    fun progressAt(elapsedMs: Long, isScreenOff: Boolean): Float {
        if (shouldSettle(isScreenOff)) return 1.0f
        return CardFade.progressAt(elapsedMs)
    }

    fun shouldFadeCard(currentArt: Any?, incomingArt: Any?, isScreenOff: Boolean): Boolean {
        if (shouldSettle(isScreenOff)) return false
        return CardFade.shouldFade(currentArt, incomingArt)
    }
}
