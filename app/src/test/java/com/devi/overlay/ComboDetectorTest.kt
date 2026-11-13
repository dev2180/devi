package com.devi.overlay

import com.devi.overlay.VolumeKey.DOWN
import com.devi.overlay.VolumeKey.UP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComboDetectorTest {
    @Test fun upDownUpWithinWindowFires() {
        val d = ComboDetector()
        assertFalse(d.onKeyDown(UP, 0, false))
        assertFalse(d.onKeyDown(DOWN, 300, false))
        assertTrue(d.onKeyDown(UP, 600, false))
    }

    @Test fun gapOver600Resets() {
        val d = ComboDetector()
        d.onKeyDown(UP, 0, false)
        d.onKeyDown(DOWN, 601, false)
        assertFalse(d.onKeyDown(UP, 900, false))
    }

    @Test fun wrongKeyResetsButUpRestarts() {
        val d = ComboDetector()
        d.onKeyDown(UP, 0, false)
        d.onKeyDown(UP, 100, false) // second UP starts a fresh sequence
        d.onKeyDown(DOWN, 200, false)
        assertTrue(d.onKeyDown(UP, 300, false))
    }

    @Test fun downFirstNeverFires() {
        val d = ComboDetector()
        d.onKeyDown(DOWN, 0, false)
        d.onKeyDown(UP, 100, false)
        assertFalse(d.onKeyDown(DOWN, 200, false))
    }

    @Test fun repeatsIgnored() {
        val d = ComboDetector()
        d.onKeyDown(UP, 0, false)
        assertFalse(d.onKeyDown(UP, 50, true))
        d.onKeyDown(DOWN, 100, false)
        assertTrue(d.onKeyDown(UP, 200, false))
    }

    @Test fun firesOnceThenNeedsFreshSequence() {
        val d = ComboDetector()
        d.onKeyDown(UP, 0, false)
        d.onKeyDown(DOWN, 100, false)
        assertTrue(d.onKeyDown(UP, 200, false))
        assertFalse(d.onKeyDown(DOWN, 300, false))
        assertFalse(d.onKeyDown(UP, 400, false))
    }

    @Test fun upUpDownFiresAfterExtraUp() {
        val d = ComboDetector(ComboPreset.UP_UP_DOWN.keys)
        d.onKeyDown(UP, 0, false); d.onKeyDown(UP, 100, false); d.onKeyDown(UP, 200, false)
        assertTrue(d.onKeyDown(DOWN, 300, false))
    }

    @Test fun downDownUpPreset() {
        val d = ComboDetector(ComboPreset.DOWN_DOWN_UP.keys)
        d.onKeyDown(DOWN, 0, false); d.onKeyDown(DOWN, 100, false)
        assertTrue(d.onKeyDown(UP, 200, false))
    }

    @Test fun fourPressPresetNeedsAllFour() {
        val d = ComboDetector(ComboPreset.UP_DOWN_UP_DOWN.keys)
        d.onKeyDown(UP, 0, false); d.onKeyDown(DOWN, 100, false)
        assertFalse(d.onKeyDown(UP, 200, false))
        assertTrue(d.onKeyDown(DOWN, 300, false))
    }

    @Test fun presetLookupFallsBackToDefault() {
        assertEquals(ComboPreset.DOWN_UP_DOWN, ComboPreset.fromId("DOWN_UP_DOWN"))
        assertEquals(ComboPreset.UP_DOWN_UP, ComboPreset.fromId("garbage"))
        assertEquals(ComboPreset.UP_DOWN_UP, ComboPreset.fromId(null))
    }
}
