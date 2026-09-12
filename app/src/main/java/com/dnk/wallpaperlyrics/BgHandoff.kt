package com.dnk.wallpaperlyrics

enum class BgHandoffDecision {
    START_FRESH,
    REPLACE_TARGET,
    ADVANCE_AND_START,
    START_TRANSITION,
    DROP_STALE
}

object BgHandoff {

    fun decide(
        hasCurrentBg: Boolean,
        isTransitioning: Boolean,
        currentGen: Int,
        incomingGen: Int
    ): BgHandoffDecision {
        if (incomingGen < currentGen) {
            return BgHandoffDecision.DROP_STALE
        }
        if (!hasCurrentBg) {
            return BgHandoffDecision.START_FRESH
        }
        if (!isTransitioning) {
            return BgHandoffDecision.START_TRANSITION
        }
        return if (currentGen == incomingGen) {
            BgHandoffDecision.REPLACE_TARGET
        } else {
            BgHandoffDecision.ADVANCE_AND_START
        }
    }
}
