package com.devi.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EyeClockTest {
    private val min = 60_000L

    @Test fun countsUnbrokenUse() {
        val c = EyeClock()
        c.onUnlockedScreen(0)
        assertFalse(c.due(19 * min, 20))
        assertTrue(c.due(20 * min, 20))
    }

    @Test fun shortGlanceAwayKeepsCounting() {
        val c = EyeClock()
        c.onUnlockedScreen(0)
        c.onScreenOff(10 * min)
        assertEquals(0, c.usedMs(10 * min + 5_000))
        c.onUnlockedScreen(10 * min + 30_000)
        assertTrue(c.due(20 * min, 20))
    }

    @Test fun realRestStartsAgain() {
        val c = EyeClock()
        c.onUnlockedScreen(0)
        c.onScreenOff(15 * min)
        c.onUnlockedScreen(17 * min)
        assertFalse(c.due(30 * min, 20))
        assertTrue(c.due(37 * min, 20))
    }

    @Test fun neverDueWhileOffOrBeforeUse() {
        val c = EyeClock()
        assertFalse(c.due(99 * min, 20))
        c.onUnlockedScreen(0); c.onScreenOff(1 * min)
        assertFalse(c.due(99 * min, 20))
    }

    @Test fun afterABreakCountsFromThen() {
        val c = EyeClock()
        c.onUnlockedScreen(0)
        c.restartAt(20 * min)
        assertFalse(c.due(39 * min, 20))
        assertTrue(c.due(40 * min, 20))
    }
}

class EyeBreakPolicyTest {
    @Test fun runsOnlyWhenNothingMatters() {
        assertTrue(EyeBreakPolicy.shouldRun(false, false, false, false))
        assertFalse(EyeBreakPolicy.shouldRun(callActive = true, otherAppSound = false, deviVideoPlaying = false, herScreenShowing = false))
        assertFalse(EyeBreakPolicy.shouldRun(false, otherAppSound = true, deviVideoPlaying = false, herScreenShowing = false))
        assertFalse(EyeBreakPolicy.shouldRun(false, false, deviVideoPlaying = true, herScreenShowing = false))
        assertFalse(EyeBreakPolicy.shouldRun(false, false, false, herScreenShowing = true))
    }
}

class CaptureGuardTest {
    @Test fun captureKeepsItOnWhileUnlockedButNeverOverPayments() {
        // While capturing, unlock alone doesn't drop it; a payment window always does.
        assertFalse(AccessGuardPolicy.mustDropNow(null, locked = false, screenOn = true, capturing = true))
        assertFalse(AccessGuardPolicy.mustDropNow("com.android.chrome", locked = false, screenOn = true, capturing = true))
        assertTrue(AccessGuardPolicy.mustDropNow("com.google.android.apps.nbu.paisa.user", locked = false, screenOn = true, capturing = true))
        assertTrue(AccessGuardPolicy.mustDropNow(null, locked = false, screenOn = true, capturing = false))
    }
}
