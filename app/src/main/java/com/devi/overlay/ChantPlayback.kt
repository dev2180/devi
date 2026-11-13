package com.devi.overlay

/** How a chant starts and ends. */
object ChantPlayback {
    /** A video you chose to watch starts at full volume; anything that comes with her still rises softly. */
    fun instant(isVideo: Boolean, byViewer: Boolean) = isVideo && !byViewer

    /** Chants loop; a video plays to its end. */
    fun loops(isVideo: Boolean) = !isVideo
}
