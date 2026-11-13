package com.devi.overlay

import android.Manifest
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * AccessGuard: Devi's Accessibility exists only while the phone is locked.
 *
 * Payment and banking apps (GPay first) refuse to run while a sideloaded app holds Accessibility.
 * Devi only needs it to hear the buttons on the lock screen, where no payment app can be used.
 * So the guard turns it on when the screen goes off, and off the moment the phone is unlocked.
 *
 * Safety layers, any one of which turns it off:
 *  1. Unlock (USER_PRESENT) and screen-on-while-unlocked, from the guard service.
 *  2. The Accessibility service itself, on its very first event after unlock, or on any window of
 *     a protected (payment/banking) app, even while locked: it drops itself at once.
 *  3. A watchdog in the guard service while the screen is on and unlocked.
 *  4. Whenever Devi's process starts (after being killed) the state is re-checked, and the
 *     Accessibility service never stays on if it connects while the phone is unlocked.
 *  5. Shutdown turns it off, so a restart never begins with it on while unlocked.
 */
object AccessGuardPolicy {
    private val protectedPackages = setOf(
        "com.google.android.apps.nbu.paisa.user", // GPay
        "com.phonepe.app",
        "net.one97.paytm",
        "in.org.npci.upiapp", // BHIM
        "in.amazon.mShop.android.shopping",
        "com.csam.icici.bank.imobile",
        "com.sbi.lotusintouch",
        "com.sbi.upi",
        "com.snapwork.hdfc",
        "com.axis.mobile",
        "com.msf.kbank.mobile",
        "com.dreamplug.androidapp", // CRED
        "com.mobikwik_new",
    )
    private val protectedHints = listOf("bank", "upi", "pay", "wallet", "finance")

    fun isProtected(pkg: String): Boolean =
        pkg in protectedPackages || protectedHints.any { pkg.lowercase().contains(it) }

    /** Wanted only while nobody can be using an app: screen off, or locked. */
    fun wantOn(screenOn: Boolean, locked: Boolean): Boolean = !screenOn || locked

    /** Checked by the Accessibility service on every window it sees. */
    fun mustDropNow(windowPackage: String?, locked: Boolean, screenOn: Boolean = true, capturing: Boolean = false): Boolean =
        (windowPackage != null && isProtected(windowPackage)) || (!capturing && !wantOn(screenOn, locked))
}

/** Edits the colon-separated list of enabled Accessibility services without touching the others. */
object ServiceList {
    private fun parts(list: String?) = list.orEmpty().split(':').map { it.trim() }.filter { it.isNotEmpty() }

    /** "pkg/.Cls" and "pkg/pkg.Cls" name the same service. */
    private fun full(entry: String): String {
        val pkg = entry.substringBefore('/')
        val cls = entry.substringAfter('/', "")
        return "$pkg/" + if (cls.startsWith(".")) pkg + cls else cls
    }

    private fun same(entry: String, ours: String) = full(entry).equals(full(ours), true)

    fun contains(list: String?, ours: String) = parts(list).any { same(it, ours) }
    fun with(list: String?, ours: String) = if (contains(list, ours)) parts(list).joinToString(":") else (parts(list) + ours).joinToString(":")
    fun without(list: String?, ours: String) = parts(list).filterNot { same(it, ours) }.joinToString(":")
}

object AccessGuard {
    private const val TAG = "DeviGuard"

    /**
     * Layer 6: once a payment app (or the unlock) has made her drop it, nothing may turn it back on
     * until the screen next goes off, so a half-finished unlock can't flip it on over GPay.
     */
    @Volatile private var holdUntilScreenOff = false

    /**
     * The eye break's "dissolve" needs one screenshot while you're using the phone. For that one
     * moment (at most [CAPTURE_MS]) Accessibility is switched on, the service refuses if a payment
     * or banking app is in front, takes the picture, and switches itself off again.
     */
    @Volatile private var captureUntil = 0L
    const val CAPTURE_MS = 4_000L
    fun capturing() = android.os.SystemClock.elapsedRealtime() < captureUntil

    @Synchronized fun beginCapture(ctx: Context): Boolean {
        if (!canControl(ctx)) return false
        captureUntil = android.os.SystemClock.elapsedRealtime() + CAPTURE_MS
        on(ctx, "eye break picture")
        return true
    }

    @Synchronized fun endCapture(ctx: Context) {
        captureUntil = 0L
        off(ctx, "eye break picture taken")
        apply(ctx, "after eye break")
    }

    fun hold(reason: String) { holdUntilScreenOff = true; Log.i(TAG, "hold: $reason") }
    fun release() { holdUntilScreenOff = false }

    private fun ours(ctx: Context) = ComponentName(ctx, DeviAccessibilityService::class.java).flattenToString()

    /** The one-time permission granted from a computer: pm grant com.devi.overlay android.permission.WRITE_SECURE_SETTINGS */
    fun canControl(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun isOn(ctx: Context): Boolean =
        ServiceList.contains(Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), ours(ctx))

    fun locked(ctx: Context) = ctx.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
    fun screenOn(ctx: Context) = ctx.getSystemService(PowerManager::class.java)?.isInteractive != false

    /** Brings Accessibility to what the moment calls for. Off is always allowed; on only when wanted. */
    @Synchronized fun apply(ctx: Context, reason: String) {
        if (!canControl(ctx) || capturing()) return
        val screenOn = screenOn(ctx)
        if (!screenOn) release()
        val want = !holdUntilScreenOff && AccessGuardPolicy.wantOn(screenOn, locked(ctx)) && needed(ctx)
        if (want) on(ctx, reason) else off(ctx, reason)
    }

    /** Lock-screen shortcuts that need Accessibility. */
    fun needed(ctx: Context) = DeviSettings.volumeCombo(ctx) != null || DeviSettings.powerTrigger(ctx)

    @Synchronized fun off(ctx: Context, reason: String) {
        if (!canControl(ctx)) return
        val cr = ctx.contentResolver
        val list = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (!ServiceList.contains(list, ours(ctx))) return
        try {
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, ServiceList.without(list, ours(ctx)))
            Log.i(TAG, "off: $reason")
        } catch (e: Exception) { Log.w(TAG, "off failed: $reason", e) }
    }

    @Synchronized private fun on(ctx: Context, reason: String) {
        val cr = ctx.contentResolver
        val list = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (ServiceList.contains(list, ours(ctx))) return
        try {
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, ServiceList.with(list, ours(ctx)))
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            Log.i(TAG, "on: $reason")
        } catch (e: Exception) { Log.w(TAG, "on failed: $reason", e) }
    }
}
