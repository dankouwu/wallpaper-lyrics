package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class BgHandoffTest {

    @Test
    fun `returns START_FRESH when current background is null`() {
        val decision = BgHandoff.decide(
            hasCurrentBg = false,
            isTransitioning = false,
            currentGen = 0,
            incomingGen = 1
        )
        assertEquals(BgHandoffDecision.START_FRESH, decision)
    }

    @Test
    fun `returns START_FRESH when current background is null even if transitioning flag is set`() {
        val decision = BgHandoff.decide(
            hasCurrentBg = false,
            isTransitioning = true,
            currentGen = 1,
            incomingGen = 1
        )
        assertEquals(BgHandoffDecision.START_FRESH, decision)
    }

    @Test
    fun `returns REPLACE_TARGET when transitioning and generations match`() {
        val decision = BgHandoff.decide(
            hasCurrentBg = true,
            isTransitioning = true,
            currentGen = 2,
            incomingGen = 2
        )
        assertEquals(BgHandoffDecision.REPLACE_TARGET, decision)
    }

    @Test
    fun `returns ADVANCE_AND_START when transitioning and generations differ`() {
        val decision = BgHandoff.decide(
            hasCurrentBg = true,
            isTransitioning = true,
            currentGen = 2,
            incomingGen = 3
        )
        assertEquals(BgHandoffDecision.ADVANCE_AND_START, decision)
    }

    @Test
    fun `returns START_TRANSITION when not transitioning and generations match`() {
        val decision = BgHandoff.decide(
            hasCurrentBg = true,
            isTransitioning = false,
            currentGen = 2,
            incomingGen = 2
        )
        assertEquals(BgHandoffDecision.START_TRANSITION, decision)
    }

    @Test
    fun `returns START_TRANSITION when not transitioning and generations differ`() {
        val decision = BgHandoff.decide(
            hasCurrentBg = true,
            isTransitioning = false,
            currentGen = 2,
            incomingGen = 3
        )
        assertEquals(BgHandoffDecision.START_TRANSITION, decision)
    }

    @Test
    fun `returns DROP_STALE when incomingGen is less than currentGen while not transitioning`() {
        val decision = BgHandoff.decide(
            hasCurrentBg = true,
            isTransitioning = false,
            currentGen = 1,
            incomingGen = 0
        )
        assertEquals(BgHandoffDecision.DROP_STALE, decision)
    }

    @Test
    fun `returns DROP_STALE when incomingGen is less than currentGen while transitioning`() {
        val decision = BgHandoff.decide(
            hasCurrentBg = true,
            isTransitioning = true,
            currentGen = 2,
            incomingGen = 1
        )
        assertEquals(BgHandoffDecision.DROP_STALE, decision)
    }

    @Test
    fun `startup sequence drops late idle background and keeps track background`() {
        val step1 = BgHandoff.decide(
            hasCurrentBg = false,
            isTransitioning = false,
            currentGen = -1,
            incomingGen = 1
        )
        assertEquals(BgHandoffDecision.START_FRESH, step1)

        val step2 = BgHandoff.decide(
            hasCurrentBg = true,
            isTransitioning = false,
            currentGen = 1,
            incomingGen = 0
        )
        assertEquals(BgHandoffDecision.DROP_STALE, step2)

        val step3 = BgHandoff.decide(
            hasCurrentBg = true,
            isTransitioning = false,
            currentGen = 1,
            incomingGen = 1
        )
        assertEquals(BgHandoffDecision.START_TRANSITION, step3)
    }
}
