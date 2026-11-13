package com.devi.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessGuardPolicyTest {
    @Test fun onWhileScreenOff() = assertTrue(AccessGuardPolicy.wantOn(screenOn = false, locked = false))
    @Test fun onWhileLocked() = assertTrue(AccessGuardPolicy.wantOn(screenOn = true, locked = true))
    @Test fun offWhileUnlocked() = assertFalse(AccessGuardPolicy.wantOn(screenOn = true, locked = false))

    @Test fun protectedAppAlwaysForcesOff() {
        assertTrue(AccessGuardPolicy.mustDropNow("com.google.android.apps.nbu.paisa.user", locked = true))
        assertTrue(AccessGuardPolicy.mustDropNow("com.csam.icici.bank.imobile", locked = true))
        assertTrue(AccessGuardPolicy.mustDropNow("net.one97.paytm", locked = true))
    }

    @Test fun anyAppAfterUnlockForcesOff() =
        assertTrue(AccessGuardPolicy.mustDropNow("com.android.launcher", locked = false))

    @Test fun screenOffBeforeLockDelayKeepsIt() =
        assertFalse(AccessGuardPolicy.mustDropNow("com.android.systemui", locked = false, screenOn = false))

    @Test fun protectedAppDropsEvenWithScreenOff() =
        assertTrue(AccessGuardPolicy.mustDropNow("com.phonepe.app", locked = false, screenOn = false))

    @Test fun lockScreenWindowsKeepItOn() {
        assertFalse(AccessGuardPolicy.mustDropNow("com.android.systemui", locked = true))
        assertFalse(AccessGuardPolicy.mustDropNow("com.devi.overlay", locked = true))
        assertFalse(AccessGuardPolicy.mustDropNow(null, locked = true))
    }

    @Test fun bankingLikeNamesAreProtected() {
        assertTrue(AccessGuardPolicy.isProtected("com.sbi.lotusintouch"))
        assertTrue(AccessGuardPolicy.isProtected("com.phonepe.app"))
        assertTrue(AccessGuardPolicy.isProtected("com.example.mybank"))
        assertFalse(AccessGuardPolicy.isProtected("com.android.chrome"))
    }
}

class ServiceListTest {
    private val ours = "com.devi.overlay/com.devi.overlay.DeviAccessibilityService"
    private val menu = "com.android.systemui.accessibility.accessibilitymenu/com.android.systemui.accessibility.accessibilitymenu.AccessibilityMenuService"

    @Test fun addKeepsOthers() = assertEquals("$menu:$ours", ServiceList.with("$menu", ours))
    @Test fun addToEmpty() = assertEquals(ours, ServiceList.with(null, ours))
    @Test fun addIsIdempotent() = assertEquals("$menu:$ours", ServiceList.with("$menu:$ours", ours))
    @Test fun removeKeepsOthers() = assertEquals(menu, ServiceList.without("$ours:$menu", ours))
    @Test fun removeShortForm() = assertEquals(menu, ServiceList.without("$menu:com.devi.overlay/.DeviAccessibilityService", ours))
    @Test fun removeLast() = assertEquals("", ServiceList.without(ours, ours))
    @Test fun contains() {
        assertTrue(ServiceList.contains("$menu:com.devi.overlay/.DeviAccessibilityService", ours))
        assertFalse(ServiceList.contains(menu, ours))
        assertFalse(ServiceList.contains(null, ours))
    }
}
