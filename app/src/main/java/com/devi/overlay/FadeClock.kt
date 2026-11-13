package com.devi.overlay

/** Time since a chant began rising, not counting time it spent paused (e.g. during a call). */
class FadeClock(private var startMs: Long) {
    private var pausedAt: Long? = null

    fun pause(nowMs: Long) {
        if (pausedAt == null) pausedAt = nowMs
    }

    fun resume(nowMs: Long) {
        val p = pausedAt ?: return
        startMs += nowMs - p
        pausedAt = null
    }

    fun elapsed(nowMs: Long): Long = (pausedAt ?: nowMs) - startMs
}
