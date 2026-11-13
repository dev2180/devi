package com.devi.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplaySamplingTest {
    @Test fun widePanoramaIsDownsampled() {
        val p = DisplaySampling.plan(12000, 2400, 1440, 3216)
        assertEquals(8, p.sampleSize); assertNull(p.targetW)
    }

    @Test fun fiftyMegapixelLandscapeIsDownsampled() =
        assertEquals(4, DisplaySampling.plan(8160, 6144, 1440, 3216).sampleSize)

    @Test fun smallPictureIsNotSampled() {
        val p = DisplaySampling.plan(514, 913, 1440, 3216)
        assertEquals(1, p.sampleSize); assertNull(p.targetW)
    }

    @Test fun sampledImageStaysAtLeastFittedSize() {
        val p = DisplaySampling.plan(4000, 9000, 1440, 3216)
        assertEquals(2, p.sampleSize)
    }

    @Test fun memoryCapFallsBackToFittedSize() {
        val p = DisplaySampling.plan(4000, 4000, 1440, 3216, maxBytes = 1_000_000)
        assertEquals(1440, p.targetW); assertEquals(1440, p.targetH)
    }

    @Test fun zeroSizeIsSafe() = assertEquals(1, DisplaySampling.plan(0, 0, 1440, 3216).sampleSize)
}
