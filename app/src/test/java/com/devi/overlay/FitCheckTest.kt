package com.devi.overlay

import com.devi.overlay.FitNote.LOW_RESOLUTION
import com.devi.overlay.FitNote.SMALL_DISPLAY
import org.junit.Assert.assertEquals
import org.junit.Test

class FitCheckTest {
    @Test fun ownersMainPictureIsLowResOnly() =
        assertEquals(setOf(LOW_RESOLUTION), FitCheck.evaluate(514, 913))

    @Test fun fullResPortraitHasNoNotes() =
        assertEquals(emptySet<FitNote>(), FitCheck.evaluate(1440, 3216))

    @Test fun bigPhoneShapedPhotoHasNoNotes() =
        assertEquals(emptySet<FitNote>(), FitCheck.evaluate(3000, 4000))

    @Test fun widePanoramaIsSmallDisplay() =
        assertEquals(setOf(SMALL_DISPLAY), FitCheck.evaluate(6000, 2000))

    @Test fun tinyWideImageGetsBoth() =
        assertEquals(setOf(LOW_RESOLUTION, SMALL_DISPLAY), FitCheck.evaluate(800, 400))

    @Test fun zeroSizeIsSafe() =
        assertEquals(emptySet<FitNote>(), FitCheck.evaluate(0, 0))
}
