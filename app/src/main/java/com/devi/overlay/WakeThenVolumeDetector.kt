package com.devi.overlay

/**
 * "Power, then a volume button": the screen wakes, then a volume key is pressed within [windowMs].
 * Never needs more than one power press, so it can't collide with the 3-press Emergency SOS
 * that Indian phones are required to have. Fires at most once per wake; ignored during calls.
 */
class WakeThenVolumeDetector(private val windowMs: Long = 1500) {
    private var lastOnMs: Long? = null
    private var armed = false // set by a screen-off; consumed by firing

    fun onScreenOff(timeMs: Long) {
        armed = true
        lastOnMs = null
    }

    fun onScreenOn(timeMs: Long) {
        lastOnMs = timeMs
    }

    fun onVolumeKeyDown(timeMs: Long, isRepeat: Boolean, inCall: Boolean): Boolean {
        if (isRepeat || !armed) return false
        if (inCall) {
            armed = false
            return false
        }
        val on = lastOnMs
        // Keys only arrive while the screen is on, so with no ON broadcast yet the wake is just
        // happening (the broadcast is late) and counts as within the window.
        val withinWindow = on == null || timeMs - on in 0..windowMs
        if (!withinWindow) return false
        armed = false
        return true
    }
}
