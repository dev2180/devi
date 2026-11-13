package com.devi.overlay

import kotlin.math.min
import kotlin.math.roundToInt

/** How to decode an image for display: a power-of-two sample size, or (memory fallback) an exact fitted size. */
data class DecodePlan(val sampleSize: Int, val targetW: Int? = null, val targetH: Int? = null)

/**
 * Display-only decode sizing. The decoded bitmap is never smaller than the picture's fitted
 * (whole-picture) size on screen, and never so big that drawing it crashes. Stored files are untouched.
 */
object DisplaySampling {
    fun plan(srcW: Int, srcH: Int, maxW: Int, maxH: Int, maxBytes: Long = 64L * 1024 * 1024): DecodePlan {
        if (srcW <= 0 || srcH <= 0 || maxW <= 0 || maxH <= 0) return DecodePlan(1)
        val fit = min(maxW.toDouble() / srcW, maxH.toDouble() / srcH)
        var sample = 1
        while (sample * 2 * fit <= 1.0) sample *= 2

        val bytes = (srcW.toLong() / sample) * (srcH.toLong() / sample) * 4
        if (bytes > maxBytes) {
            val scale = min(fit, 1.0)
            return DecodePlan(
                sampleSize = 1,
                targetW = (srcW * scale).roundToInt().coerceAtLeast(1),
                targetH = (srcH * scale).roundToInt().coerceAtLeast(1),
            )
        }
        return DecodePlan(sample)
    }
}
