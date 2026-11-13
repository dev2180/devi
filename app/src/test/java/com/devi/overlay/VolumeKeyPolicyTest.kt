package com.devi.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeKeyPolicyTest {
    @Test fun consumesOnSilentLockScreen() = assertTrue(VolumeKeyPolicy.shouldConsume(true, false, true, "com.android.systemui"))
    @Test fun consumesOverOwnViewer() = assertTrue(VolumeKeyPolicy.shouldConsume(true, false, true, "com.devi.overlay"))
    @Test fun passesWhenUnlocked() = assertFalse(VolumeKeyPolicy.shouldConsume(false, false, true, "com.android.systemui"))
    @Test fun passesWhenMusicPlaying() = assertFalse(VolumeKeyPolicy.shouldConsume(true, true, true, "com.android.systemui"))
    @Test fun passesWhenRingingOrInCall() = assertFalse(VolumeKeyPolicy.shouldConsume(true, false, false, "com.android.systemui"))
    @Test fun passesWhenAlarmAppOnTop() = assertFalse(VolumeKeyPolicy.shouldConsume(true, false, true, "com.coloros.alarmclock"))
    @Test fun passesWhenPackageUnknown() = assertFalse(VolumeKeyPolicy.shouldConsume(true, false, true, null))
    @Test fun passesWhenAlarmAudioPlaying() =
        assertFalse(VolumeKeyPolicy.shouldConsume(true, false, true, "com.android.systemui", protectedAudioActive = true))
    @Test fun passesWhenChantPlaying() =
        assertFalse(VolumeKeyPolicy.shouldConsume(true, false, true, "com.android.systemui", chantPlaying = true))
}
