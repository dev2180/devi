package com.devi.overlay

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Opens [ViewerActivity] on the chosen volume combination, or on "power, then a volume button".
 * Reads no screen content: it only notes which app's window is in front (to know when the
 * plain lock screen is showing), looks at volume key presses, and hears screen on/off.
 *
 * With AccessGuard in control it lives only while the phone is locked, and drops itself the
 * instant it sees the phone unlocked or any payment/banking window (see [AccessGuard]).
 */
class DeviAccessibilityService : AccessibilityService() {

    private var preset: ComboPreset? = null
    private var detector: ComboDetector? = null
    private val wakeDetector = WakeThenVolumeDetector()
    private var screenReceiverRegistered = false
    private var dropped = false

    private val protectedUsages = setOf(
        AudioAttributes.USAGE_ALARM,
        AudioAttributes.USAGE_NOTIFICATION_RINGTONE,
        AudioAttributes.USAGE_VOICE_COMMUNICATION,
        AudioAttributes.USAGE_NOTIFICATION_EVENT,
    )

    @Volatile
    private var activePackage: String? = null

    private companion object {
        val CALL_UI = listOf("incallui", "dialer", "telecom")
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val now = SystemClock.uptimeMillis() // same clock as KeyEvent.eventTime
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> wakeDetector.onScreenOff(now)
                Intent.ACTION_SCREEN_ON -> { wakeDetector.onScreenOn(now); guardCheck(null) }
                Intent.ACTION_USER_PRESENT -> guardCheck(null)
            }
        }
    }

    private val locked get() = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Safety: connecting while the phone is unlocked (e.g. after Devi was killed) never lasts.
        if (guardCheck(null)) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        screenReceiverRegistered = true
        if (AccessGuard.capturing() && !locked) capture()
    }

    /** The eye break's one picture of the screen, then straight off again. Never over a payment app. */
    private fun capture() {
        val front = try { rootInActiveWindow?.packageName?.toString() } catch (_: Exception) { null }
        if (guardCheck(front)) return
        try {
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(r: ScreenshotResult) {
                    val bmp = try {
                        android.graphics.Bitmap.wrapHardwareBuffer(r.hardwareBuffer, r.colorSpace)
                            ?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                    } catch (_: Exception) { null } finally { r.hardwareBuffer.close() }
                    finishCapture(bmp)
                }
                override fun onFailure(code: Int) = finishCapture(null)
            })
        } catch (_: Exception) { finishCapture(null) }
    }

    private fun finishCapture(bmp: android.graphics.Bitmap?) {
        dropped = true
        AccessGuard.endCapture(this)
        try { disableSelf() } catch (_: Exception) { }
        EyeBreak.captured(this, bmp)
    }

    /** True if it dropped itself. Only when AccessGuard is in control; otherwise it behaves as before. */
    private fun guardCheck(windowPackage: String?): Boolean {
        if (dropped) return true
        if (!AccessGuard.canControl(this)) return false
        if (!AccessGuardPolicy.mustDropNow(windowPackage, locked, AccessGuard.screenOn(this), AccessGuard.capturing())) return false
        if (AccessGuard.capturing()) EyeBreak.captureRefused(this)
        dropped = true
        AccessGuard.hold("service saw ${windowPackage ?: "unlock"}")
        AccessGuard.off(this, "service saw ${windowPackage ?: "unlock"}")
        try { disableSelf() } catch (_: Exception) { }
        return true
    }

    override fun onDestroy() {
        if (screenReceiverRegistered) unregisterReceiver(screenReceiver)
        screenReceiverRegistered = false
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && guardCheck(pkg)) return
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                pkg?.let { activePackage = it }
                if (pkg != null && CALL_UI.any { pkg.contains(it) }) interrupt(Interrupt.CALL)
            }
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED ->
                if (pkg != null && pkg != packageName) interrupt(Interrupt.NOTIFICATION)
        }
    }

    /** A call, or a notification on the lock screen, takes her screen away (her chant fades with her). */
    private fun interrupt(kind: Interrupt) {
        if (!ViewerActivity.isShowing) return
        if (FocusPolicy.closeFor(kind, locked, DeviSettings.focusMode(this))) {
            sendBroadcast(Intent(ViewerActivity.ACTION_CLOSE).setPackage(packageName))
        }
    }

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (dropped) return false
        val key = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> VolumeKey.UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> VolumeKey.DOWN
            else -> return false
        }
        val chosen = DeviSettings.volumeCombo(this)
        val powerOn = DeviSettings.powerTrigger(this)
        if (chosen != preset) {
            preset = chosen
            detector = chosen?.let { ComboDetector(it.keys) }
        }
        if (chosen == null && !powerOn) return false

        val audio = getSystemService(AudioManager::class.java)
        val consume = VolumeKeyPolicy.shouldConsume(
            keyguardLocked = locked,
            musicActive = audio?.isMusicActive == true,
            audioModeNormal = audio?.mode == AudioManager.MODE_NORMAL,
            activePackage = activePackage,
            protectedAudioActive = isProtectedAudioPlaying(audio),
            chantPlaying = ChantService.isPlaying,
        )

        if (event.action == KeyEvent.ACTION_DOWN) {
            val repeat = event.repeatCount > 0
            val inCall = audio?.mode != AudioManager.MODE_NORMAL
            val byPower = powerOn && wakeDetector.onVolumeKeyDown(event.eventTime, repeat, inCall)
            val byCombo = detector?.onKeyDown(key, event.eventTime, repeat) == true
            if (byPower || byCombo) {
                detector?.reset()
                openViewer()
            }
        }
        return consume
    }

    /** An alarm, ringtone, call or notification sound is playing: volume keys must reach it. */
    private fun isProtectedAudioPlaying(audio: AudioManager?): Boolean = try {
        audio?.activePlaybackConfigurations?.any { it.audioAttributes.usage in protectedUsages } == true
    } catch (e: Exception) {
        true // unsure -> let the key through (the safe side)
    }

    private fun openViewer() {
        if (ViewerActivity.isShowing) return
        if (getSystemService(AudioManager::class.java)?.mode != AudioManager.MODE_NORMAL) return // not during a call
        if (ImageStore.forContext(this).main() == null) return
        startActivity(ViewerActivity.intent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
