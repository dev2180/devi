package com.devi.overlay

import android.app.NotificationManager
import android.content.Context
import android.util.Log

/**
 * Focus while she's here: the screen stays on, she stays until closed (or a chosen number of
 * minutes), Do Not Disturb silences calls and messages, and nothing takes her away.
 */
object FocusPolicy {
    private const val DEFAULT_CLOSE_MS = 10 * 60_000L
    const val SHIFT_CYCLE = 8

    /** When she leaves by herself; null = only when closed. */
    fun autoCloseMs(focus: Boolean, stayMinutes: Int): Long? = when {
        !focus -> DEFAULT_CLOSE_MS
        stayMinutes <= 0 -> null
        else -> stayMinutes * 60_000L
    }

    fun dndFilter(letAlarms: Boolean): Int =
        if (letAlarms) NotificationManager.INTERRUPTION_FILTER_ALARMS else NotificationManager.INTERRUPTION_FILTER_NONE

    fun closeFor(kind: Interrupt, locked: Boolean, focus: Boolean): Boolean =
        !focus && InterruptPolicy.shouldClose(kind, locked)

    /** A slow walk around a tiny square, one step a minute, so no pixel stays lit in one place. */
    fun burnInOffset(step: Int): Pair<Int, Int> {
        val path = listOf(0 to 0, 1 to 0, 2 to 1, 1 to 2, 0 to 2, -1 to 1, -2 to 0, -1 to -1)
        return path[((step % SHIFT_CYCLE) + SHIFT_CYCLE) % SHIFT_CYCLE]
    }

    /** -1 follows the phone; otherwise a window brightness, never fully black. */
    fun windowBrightness(setting: Float): Float = if (setting < 0f) -1f else setting.coerceIn(0.02f, 1f)
}

object DndRestore {
    /** What to put back when she leaves; null = leave it (nothing saved, or the owner changed it since). */
    fun restoreTo(saved: Int?, ourFilter: Int, current: Int): Int? =
        if (saved == null || current != ourFilter) null else saved
}

/**
 * Sets Do Not Disturb while she's showing and puts the owner's own setting back afterwards.
 * What it changed is written down first, so even if Devi is killed it is undone the next time
 * Devi runs ([restoreIfLeftOver]).
 */
object DndKeeper {
    private const val TAG = "DeviFocus"
    private const val FILE = "dnd"
    private const val KEY_SAVED = "saved"
    private const val KEY_OURS = "ours"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

    fun hasAccess(ctx: Context) = nm(ctx)?.isNotificationPolicyAccessGranted == true

    fun silence(ctx: Context, letAlarms: Boolean) {
        val nm = nm(ctx) ?: return
        if (!nm.isNotificationPolicyAccessGranted) return
        val ours = FocusPolicy.dndFilter(letAlarms)
        val current = nm.currentInterruptionFilter
        val p = prefs(ctx)
        if (!p.contains(KEY_SAVED)) {
            // The owner's own quieter setting stays as it is.
            if (current == NotificationManager.INTERRUPTION_FILTER_NONE) return
            p.edit().putInt(KEY_SAVED, current).putInt(KEY_OURS, ours).commit()
        }
        try { nm.setInterruptionFilter(ours); Log.i(TAG, "silenced") } catch (e: Exception) { Log.w(TAG, "silence failed", e) }
    }

    fun restore(ctx: Context) {
        val p = prefs(ctx)
        if (!p.contains(KEY_SAVED)) return
        val nm = nm(ctx)
        if (nm != null && nm.isNotificationPolicyAccessGranted) {
            val back = DndRestore.restoreTo(p.getInt(KEY_SAVED, 0), p.getInt(KEY_OURS, 0), nm.currentInterruptionFilter)
            if (back != null) try { nm.setInterruptionFilter(back); Log.i(TAG, "restored") } catch (e: Exception) { Log.w(TAG, "restore failed", e) }
        }
        p.edit().clear().commit()
    }

    /** After a crash or kill: if she's not on screen, the owner's setting comes back. */
    fun restoreIfLeftOver(ctx: Context) {
        if (!ViewerActivity.alive) restore(ctx)
    }
}
