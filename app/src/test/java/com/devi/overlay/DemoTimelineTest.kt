package com.devi.overlay

import com.devi.overlay.VolumeKey.DOWN
import com.devi.overlay.VolumeKey.UP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoTimelineTest {
    private val udu = listOf(UP, DOWN, UP)

    @Test fun volumeFirstPressOnUp() {
        val f = DemoTimeline.frame(DemoMode.VOLUME, udu, 960)
        assertEquals(0, f.pressedButton); assertEquals(37f, f.thumbX, 0.01f)
    }

    @Test fun secondPressOnDown() = assertEquals(1, DemoTimeline.frame(DemoMode.VOLUME, udu, 1560).pressedButton)

    @Test fun chipsCountUp() {
        assertEquals(1, DemoTimeline.frame(DemoMode.VOLUME, udu, 1000).litChips)
        assertEquals(2, DemoTimeline.frame(DemoMode.VOLUME, udu, 1600).litChips)
        assertEquals(3, DemoTimeline.frame(DemoMode.VOLUME, udu, 2200).litChips)
    }

    @Test fun herVisibleAfterSequence() = assertEquals(1f, DemoTimeline.frame(DemoMode.VOLUME, udu, 4000).herAlpha, 0.001f)

    @Test fun herHiddenBeforeLastPress() = assertEquals(0f, DemoTimeline.frame(DemoMode.VOLUME, udu, 1800).herAlpha, 0.001f)

    @Test fun loopWraps() = assertEquals(DemoTimeline.frame(DemoMode.VOLUME, udu, 0), DemoTimeline.frame(DemoMode.VOLUME, udu, 6000))

    @Test fun powerModePressesPowerFromRight() {
        val f = DemoTimeline.frame(DemoMode.POWER_THEN_VOLUME, udu, 960)
        assertEquals(2, f.pressedButton); assertTrue(f.fromRight)
    }

    @Test fun powerModeThenVolumeFromLeft() {
        val f = DemoTimeline.frame(DemoMode.POWER_THEN_VOLUME, udu, 2040)
        assertEquals(0, f.pressedButton); assertTrue(!f.fromRight)
    }

    @Test fun fourKeyPresetHasFourPresses() {
        val keys = listOf(UP, DOWN, UP, DOWN)
        val pressed = (0 until 6000 step 10).mapNotNull { t ->
            DemoTimeline.frame(DemoMode.VOLUME, keys, t.toLong()).pressedButton?.let { t / 300 }
        }.distinct()
        assertEquals(4, pressed.size)
        assertEquals(4, DemoTimeline.frame(DemoMode.VOLUME, keys, 2820).litChips)
    }
}
