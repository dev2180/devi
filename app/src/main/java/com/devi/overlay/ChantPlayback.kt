package com.devi.overlay

/** How a chant starts and ends. */
object ChantPlayback {
    /** A video you chose to watch starts at full volume; anything that comes with her still rises softly. */
    fun instant(isVideo: Boolean, byViewer: Boolean) = isVideo && !byViewer

    /** Chants loop; a video plays to its end. */
    fun loops(isVideo: Boolean) = !isVideo
}

/** How often the playback clock wakes: quickly only while a fade is moving, otherwise once a second. */
fun ChantPlayback.tickMs(fading: Boolean) = if (fading) 50L else 1_000L

/** A fade is moving if a voice is leaving, or the current one is still rising or falling. */
fun ChantPlayback.fading(paused: Boolean, instant: Boolean, gain: Float, fadingOut: Boolean, outgoing: Int) =
    outgoing > 0 || (!paused && (fadingOut || (!instant && gain < 0.999f)))
