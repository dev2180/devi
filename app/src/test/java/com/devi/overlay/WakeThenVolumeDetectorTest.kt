package com.devi.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeThenVolumeDetectorTest {
    @Test fun wakeThenVolumeWithinWindowFires() {
        val d = WakeThenVolumeDetector()
        d.onScreenOff(0); d.onScreenOn(1000)
        assertTrue(d.onVolumeKeyDown(2500, isRepeat = false, inCall = false))
    }

    @Test fun volumeTooLateDoesNotFire() {
        val d = WakeThenVolumeDetector()
        d.onScreenOff(0); d.onScreenOn(1000)
        assertFalse(d.onVolumeKeyDown(2501, isRepeat = false, inCall = false))
    }

    @Test fun volumeBeforeLateScreenOnBroadcastFires() {
        val d = WakeThenVolumeDetector()
        d.onScreenOff(0) // screen is on again (we get keys) but the ON broadcast hasn't arrived yet
        assertTrue(d.onVolumeKeyDown(500, isRepeat = false, inCall = false))
    }

    @Test fun volumeWithoutScreenEventsDoesNotFire() =
        assertFalse(WakeThenVolumeDetector().onVolumeKeyDown(100, isRepeat = false, inCall = false))

    @Test fun firesOncePerWake() {
        val d = WakeThenVolumeDetector()
        d.onScreenOff(0); d.onScreenOn(1000)
        assertTrue(d.onVolumeKeyDown(1200, isRepeat = false, inCall = false))
        assertFalse(d.onVolumeKeyDown(1300, isRepeat = false, inCall = false))
    }

    @Test fun lateBroadcastAfterFiringDoesNotReArm() {
        val d = WakeThenVolumeDetector()
        d.onScreenOff(0)
        assertTrue(d.onVolumeKeyDown(300, isRepeat = false, inCall = false))
        d.onScreenOn(400) // the delayed broadcast for the same wake
        assertFalse(d.onVolumeKeyDown(600, isRepeat = false, inCall = false))
    }

    @Test fun ignoredDuringCall() {
        val d = WakeThenVolumeDetector()
        d.onScreenOff(0); d.onScreenOn(1000)
        assertFalse(d.onVolumeKeyDown(1200, isRepeat = false, inCall = true))
    }

    @Test fun heldKeyIgnored() {
        val d = WakeThenVolumeDetector()
        d.onScreenOff(0); d.onScreenOn(1000)
        assertFalse(d.onVolumeKeyDown(1200, isRepeat = true, inCall = false))
    }
}
