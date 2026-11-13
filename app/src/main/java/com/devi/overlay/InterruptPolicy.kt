package com.devi.overlay

enum class Interrupt { CALL, NOTIFICATION }

/** A call always takes her screen away; a notification does when the phone is locked. */
object InterruptPolicy {
    fun shouldClose(i: Interrupt, locked: Boolean): Boolean = when (i) {
        Interrupt.CALL -> true
        Interrupt.NOTIFICATION -> locked
    }
}
