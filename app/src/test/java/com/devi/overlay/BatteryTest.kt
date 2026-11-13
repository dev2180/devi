package com.devi.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TickTest {
    @Test fun fastOnlyWhileAFadeIsMoving() {
        assertEquals(50L, ChantPlayback.tickMs(fading = true))
        assertEquals(1_000L, ChantPlayback.tickMs(fading = false))
    }

    @Test fun fadingMeansRisingFallingOrOutgoing() {
        assertTrue(ChantPlayback.fading(paused = false, instant = false, gain = 0.5f, fadingOut = false, outgoing = 0))
        assertTrue(ChantPlayback.fading(paused = false, instant = false, gain = 1f, fadingOut = true, outgoing = 0))
        assertTrue(ChantPlayback.fading(paused = true, instant = false, gain = 1f, fadingOut = false, outgoing = 1))
        assertFalse(ChantPlayback.fading(paused = false, instant = false, gain = 1f, fadingOut = false, outgoing = 0))
        assertFalse(ChantPlayback.fading(paused = false, instant = true, gain = 1f, fadingOut = false, outgoing = 0))
        assertFalse(ChantPlayback.fading(paused = true, instant = false, gain = 0.3f, fadingOut = false, outgoing = 0))
    }
}

class LeanTest {
    @Test fun saverOrColourOffMeansLean() {
        assertTrue(LeanPolicy.lean(powerSave = true, greyscale = false))
        assertTrue(LeanPolicy.lean(powerSave = false, greyscale = true))
        assertFalse(LeanPolicy.lean(powerSave = false, greyscale = false))
    }

    @Test fun greyscaleFromEitherSetting() {
        assertTrue(LeanPolicy.greyscale(realmeGray = 1, daltonizerOn = 0, daltonizer = -1))
        assertTrue(LeanPolicy.greyscale(realmeGray = 0, daltonizerOn = 1, daltonizer = 0))
        assertFalse(LeanPolicy.greyscale(realmeGray = 0, daltonizerOn = 1, daltonizer = 12))
        assertFalse(LeanPolicy.greyscale(realmeGray = 0, daltonizerOn = 0, daltonizer = 0))
    }
}

class BoxBlurTest {
    @Test fun flatStaysFlatAndSpikeSpreads() {
        val flat = IntArray(25) { 0xFF808080.toInt() }
        BoxBlur.pass(flat, 5, 5, 1)
        assertTrue(flat.all { it == 0xFF808080.toInt() })
        val spike = IntArray(25) { 0xFF000000.toInt() }; spike[12] = 0xFFFFFFFF.toInt()
        BoxBlur.pass(spike, 5, 5, 1)
        assertEquals(0xFF1C1C1C.toInt(), spike[12]) // 255 / 9 = 28 = 0x1C
        assertEquals(0xFF000000.toInt(), spike[0])
    }
}
