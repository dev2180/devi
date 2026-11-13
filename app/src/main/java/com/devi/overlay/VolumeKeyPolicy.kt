package com.devi.overlay

/**
 * Decides whether a volume key press should be swallowed (no volume change, no volume panel).
 * Only on the plain lock screen (or over our own viewer), with nothing playing and no call/ringing,
 * so the system volume panel never draws over the picture there.
 */
object VolumeKeyPolicy {
    private val quietPackages = setOf("com.android.systemui", "com.devi.overlay")

    fun shouldConsume(
        keyguardLocked: Boolean,
        musicActive: Boolean,
        audioModeNormal: Boolean,
        activePackage: String?,
        protectedAudioActive: Boolean = false,
        chantPlaying: Boolean = false,
    ): Boolean = keyguardLocked && !musicActive && audioModeNormal && !protectedAudioActive && !chantPlaying &&
        activePackage in quietPackages
}
