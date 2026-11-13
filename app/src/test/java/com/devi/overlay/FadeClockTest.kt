package com.devi.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class FadeClockTest {
    @Test fun elapsedRunsFromStart() = assertEquals(3_000L, FadeClock(1_000).elapsed(4_000))

    @Test fun pausedTimeDoesNotCount() {
        val c = FadeClock(0)
        c.pause(3_000)              // gain was mid-rise at 3 s
        c.resume(63_000)            // a one-minute call
        assertEquals(3_000L, c.elapsed(63_000))
        assertEquals(4_000L, c.elapsed(64_000))
    }

    @Test fun elapsedFrozenWhilePaused() {
        val c = FadeClock(0)
        c.pause(2_000)
        assertEquals(2_000L, c.elapsed(9_000))
    }

    @Test fun doublePauseAndResumeAreHarmless() {
        val c = FadeClock(0)
        c.pause(1_000); c.pause(5_000)
        c.resume(6_000); c.resume(8_000)
        assertEquals(2_000L, c.elapsed(7_000))
    }
}
