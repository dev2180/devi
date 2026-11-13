package com.devi.overlay

import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class FocusPolicyTest {
    @Test fun focusStaysUntilClosedByDefault() = assertNull(FocusPolicy.autoCloseMs(focus = true, stayMinutes = 0))
    @Test fun focusCustomMinutes() = assertEquals(15 * 60_000L, FocusPolicy.autoCloseMs(focus = true, stayMinutes = 15))
    @Test fun withoutFocusOldTenMinutes() = assertEquals(10 * 60_000L, FocusPolicy.autoCloseMs(focus = false, stayMinutes = 0))

    @Test fun dndLetsAlarmsThrough() =
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALARMS, FocusPolicy.dndFilter(letAlarms = true))
    @Test fun dndTotalSilence() =
        assertEquals(NotificationManager.INTERRUPTION_FILTER_NONE, FocusPolicy.dndFilter(letAlarms = false))

    @Test fun focusIgnoresCallsAndNotifications() {
        assertFalse(FocusPolicy.closeFor(Interrupt.CALL, locked = true, focus = true))
        assertFalse(FocusPolicy.closeFor(Interrupt.NOTIFICATION, locked = true, focus = true))
    }

    @Test fun withoutFocusOldRules() {
        assertTrue(FocusPolicy.closeFor(Interrupt.CALL, locked = false, focus = false))
        assertTrue(FocusPolicy.closeFor(Interrupt.NOTIFICATION, locked = true, focus = false))
        assertFalse(FocusPolicy.closeFor(Interrupt.NOTIFICATION, locked = false, focus = false))
    }

    @Test fun burnInShiftStaysTinyAndMoves() {
        val offsets = (0 until 12).map { FocusPolicy.burnInOffset(it) }
        offsets.forEach { (x, y) -> assertTrue(abs(x) <= 2 && abs(y) <= 2) }
        assertTrue(offsets.distinct().size >= 4)
        assertEquals(FocusPolicy.burnInOffset(0), FocusPolicy.burnInOffset(FocusPolicy.SHIFT_CYCLE))
    }

    @Test fun brightnessFollowsPhoneOrClamps() {
        assertEquals(-1f, FocusPolicy.windowBrightness(-1f), 0f)
        assertEquals(0.02f, FocusPolicy.windowBrightness(0f), 0f)
        assertEquals(1f, FocusPolicy.windowBrightness(3f), 0f)
        assertEquals(0.5f, FocusPolicy.windowBrightness(0.5f), 0f)
    }
}

class DndRestoreTest {
    private val all = NotificationManager.INTERRUPTION_FILTER_ALL
    private val alarms = NotificationManager.INTERRUPTION_FILTER_ALARMS
    private val priority = NotificationManager.INTERRUPTION_FILTER_PRIORITY

    @Test fun restoresWhatOwnerHad() = assertEquals(all, DndRestore.restoreTo(saved = all, ourFilter = alarms, current = alarms))
    @Test fun keepsOwnersPriorityMode() = assertEquals(priority, DndRestore.restoreTo(saved = priority, ourFilter = alarms, current = alarms))
    @Test fun leavesItIfOwnerChangedItMeanwhile() = assertNull(DndRestore.restoreTo(saved = all, ourFilter = alarms, current = priority))
    @Test fun nothingSaved() = assertNull(DndRestore.restoreTo(saved = null, ourFilter = alarms, current = alarms))
}
