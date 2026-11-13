package com.devi.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoliciesV3Test {
    @Test fun callAlwaysCloses() {
        assertTrue(InterruptPolicy.shouldClose(Interrupt.CALL, locked = true))
        assertTrue(InterruptPolicy.shouldClose(Interrupt.CALL, locked = false))
    }

    @Test fun notificationClosesOnlyWhenLocked() {
        assertTrue(InterruptPolicy.shouldClose(Interrupt.NOTIFICATION, locked = true))
        assertFalse(InterruptPolicy.shouldClose(Interrupt.NOTIFICATION, locked = false))
    }
}
