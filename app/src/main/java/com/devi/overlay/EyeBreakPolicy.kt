package com.devi.overlay

/** How the eye break arrives: over the screen, or the screen itself breaking into stars. */
enum class EyeStyle(val label: String) { COVER("The sky covers the screen"), DISSOLVE("The screen dissolves into stars") }

/**
 * Counts unbroken screen time. Looking away for [REST_MS] (screen off) counts as a rest and
 * starts the count again; a shorter glance away does not.
 */
class EyeClock {
    private var usedFrom = -1L
    private var offAt = -1L

    fun onUnlockedScreen(now: Long) {
        if (usedFrom < 0 || (offAt >= 0 && now - offAt >= REST_MS)) usedFrom = now
        offAt = -1
    }

    fun onScreenOff(now: Long) { if (offAt < 0) offAt = now }

    fun usedMs(now: Long): Long = if (usedFrom < 0 || offAt >= 0) 0 else now - usedFrom

    fun due(now: Long, everyMinutes: Int) = usedMs(now) >= everyMinutes * 60_000L

    /** After a break (or one we had to skip and want to try again in a little while). */
    fun restartAt(now: Long) { usedFrom = now }

    companion object { const val REST_MS = 60_000L }
}

object EyeBreakPolicy {
    /** Never during a call, a video, her own screen, or sound from another app (a film, navigation, a game). */
    fun shouldRun(callActive: Boolean, otherAppSound: Boolean, deviVideoPlaying: Boolean, herScreenShowing: Boolean) =
        !callActive && !otherAppSound && !deviVideoPlaying && !herScreenShowing

    /** When a break can't happen now, try again soon rather than waiting a whole round. */
    const val RETRY_MS = 2 * 60_000L

    /** You may close it after this. */
    const val SKIP_AFTER_MS = 5_000L
}
