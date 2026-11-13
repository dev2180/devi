package com.devi.overlay

import kotlin.math.min

enum class FitNote { LOW_RESOLUTION, SMALL_DISPLAY }

/**
 * Add-time notes about how a picture will look when shown whole (never cropped) on the screen.
 * The notes only inform; the picture is always kept unaltered.
 */
object FitCheck {
    private const val LOW_RES_WIDTH = 1080
    private const val MIN_COVERAGE = 0.5

    fun evaluate(imageW: Int, imageH: Int, screenW: Int = 1440, screenH: Int = 3216): Set<FitNote> {
        if (imageW <= 0 || imageH <= 0 || screenW <= 0 || screenH <= 0) return emptySet()
        val scale = min(screenW.toDouble() / imageW, screenH.toDouble() / imageH)
        val coverage = (imageW * scale) * (imageH * scale) / (screenW.toDouble() * screenH)

        val notes = mutableSetOf<FitNote>()
        if (imageW < LOW_RES_WIDTH && scale > 1.0) notes += FitNote.LOW_RESOLUTION
        if (coverage < MIN_COVERAGE) notes += FitNote.SMALL_DISPLAY
        return notes
    }

    fun message(note: FitNote): String = when (note) {
        FitNote.LOW_RESOLUTION ->
            "This picture is small for your screen, so it may look a little soft. If you have the original, add that instead."
        FitNote.SMALL_DISPLAY ->
            "Because of its shape, this picture will fill less than half of your screen. It is still shown whole, never cropped."
    }
}
