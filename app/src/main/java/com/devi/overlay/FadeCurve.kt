package com.devi.overlay

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Equal-power fades for chants. Gains are 0..1 of the phone's media volume, never above it. */
object FadeCurve {
    fun fadeIn(elapsedMs: Long, durationMs: Long = 10_000): Float {
        val t = (elapsedMs.toDouble() / durationMs).coerceIn(0.0, 1.0)
        return sin(t * PI / 2).toFloat()
    }

    /** Fades from wherever the gain is now (e.g. mid fade-in) down to silence. */
    fun fadeOut(elapsedMs: Long, fromGain: Float, durationMs: Long = 1_500): Float {
        val t = (elapsedMs.toDouble() / durationMs).coerceIn(0.0, 1.0)
        return (fromGain * cos(t * PI / 2)).toFloat().coerceIn(0f, 1f)
    }
}
