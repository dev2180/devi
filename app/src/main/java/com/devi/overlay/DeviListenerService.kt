package com.devi.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings

/**
 * The guard service: keeps AccessGuard in step with the screen (Accessibility only while locked),
 * brings the eye break, and closes her picture when a call starts.
 */
class DeviListenerService : Service() {

    private var receiverRegistered = false
    private var modeListenerAdded = false
    private val handler = Handler(Looper.getMainLooper())

    private val audio get() = getSystemService(AudioManager::class.java)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> { AccessGuard.apply(context, "screen off"); eyes.onScreenOff(SystemClock.elapsedRealtime()); EyeBreak.stop() }
                Intent.ACTION_SCREEN_ON -> {
                    AccessGuard.apply(context, "screen on"); startWatchdog()
                    if (!AccessGuard.locked(context)) eyes.onUnlockedScreen(SystemClock.elapsedRealtime())
                }
                Intent.ACTION_USER_PRESENT -> {
                    AccessGuard.hold("unlocked"); AccessGuard.off(context, "unlocked"); startWatchdog()
                    eyes.onUnlockedScreen(SystemClock.elapsedRealtime())
                }
                Intent.ACTION_SHUTDOWN -> AccessGuard.off(context, "shutdown")
            }
        }
    }

    /** Layer 3: while the screen is on and unlocked, keep checking that Accessibility stays off. */
    private val watchdog = object : Runnable {
        override fun run() {
            if (!AccessGuard.screenOn(this@DeviListenerService)) return
            AccessGuard.apply(this@DeviListenerService, "watchdog")
            handler.postDelayed(this, if (AccessGuard.locked(this@DeviListenerService)) 1_000 else 15_000)
        }
    }

    /** Eye break: every so many minutes of unbroken use, the sky and Devi, for a few breaths. */
    private val eyes = EyeClock()
    private val eyeCheck = object : Runnable {
        override fun run() {
            val ctx = this@DeviListenerService
            val now = SystemClock.elapsedRealtime()
            if (DeviSettings.eyeBreak(ctx) && AccessGuard.screenOn(ctx) && !AccessGuard.locked(ctx) && eyes.due(now, DeviSettings.eyeEvery(ctx))) {
                if (EyeBreak.allowedNow(ctx)) { eyes.restartAt(now); EyeBreak.start(ctx, DeviSettings.eyeStyle(ctx)) }
                else eyes.restartAt(now - DeviSettings.eyeEvery(ctx) * 60_000L + EyeBreakPolicy.RETRY_MS)
            }
            handler.postDelayed(this, 30_000)
        }
    }

    private fun startWatchdog() {
        handler.removeCallbacks(watchdog)
        handler.post(watchdog)
    }

    private val modeListener = AudioManager.OnModeChangedListener { mode ->
        if (mode != AudioManager.MODE_NORMAL && ViewerActivity.isShowing &&
            FocusPolicy.closeFor(Interrupt.CALL, AccessGuard.locked(this), DeviSettings.focusMode(this))
        ) {
            sendBroadcast(Intent(ViewerActivity.ACTION_CLOSE).setPackage(packageName))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Keeping payment apps safe", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false); description = "Turns Devi's Accessibility on only while locked. Can be hidden."
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(SHOW_CHANNEL, "Bring her on the lock screen", NotificationManager.IMPORTANCE_HIGH).apply {
                setShowBadge(false); setSound(null, null); enableVibration(false)
            },
        )
        startForeground(NOTIFICATION_ID, listeningNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_SHUTDOWN)
        }
        registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        audio?.addOnModeChangedListener(mainExecutor, modeListener)
        modeListenerAdded = true
        // Layer 4: whatever happened while Devi wasn't running, set it right now.
        AccessGuard.apply(this, "service start")
        DndKeeper.restoreIfLeftOver(this)
        startWatchdog()
        if (AccessGuard.screenOn(this) && !AccessGuard.locked(this)) eyes.onUnlockedScreen(SystemClock.elapsedRealtime())
        handler.post(eyeCheck)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!needed(this)) { AccessGuard.off(this, "not needed"); stopSelf(); return START_NOT_STICKY }
        AccessGuard.apply(this, "start command")
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(watchdog)
        handler.removeCallbacks(eyeCheck)
        // Without the guard running, nothing would turn it off at unlock: leave it off.
        AccessGuard.off(this, "guard stopped")
        if (receiverRegistered) unregisterReceiver(receiver)
        receiverRegistered = false
        if (modeListenerAdded) {
            try { audio?.removeOnModeChangedListener(modeListener) } catch (_: Exception) { }
            modeListenerAdded = false
        }
        super.onDestroy()
    }

    private fun listeningNotification(): Notification =
        Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile_devi)
            .setContentTitle("Devi is guarding your payment apps")
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, ManageActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL = "listening"
        private const val SHOW_CHANNEL = "show_her"
        private const val NOTIFICATION_ID = 7
        private const val SHOW_ID = 8

        /** Only runs while something needs it. */
        fun needed(ctx: Context) =
            (AccessGuard.canControl(ctx) && AccessGuard.needed(ctx)) || DeviSettings.eyeBreak(ctx)

        /** Starts or stops the listener to match the settings. */
        fun sync(ctx: Context) {
            val i = Intent(ctx, DeviListenerService::class.java)
            if (needed(ctx)) {
                try { ctx.startForegroundService(i) } catch (_: Exception) { }
            } else {
                ctx.stopService(i)
            }
        }

        /**
         * Brings her. With "Display over other apps" allowed Android lets Devi open her screen
         * directly; without it, a full-screen notification does it on the lock screen.
         */
        fun openViewer(ctx: Context) {
            if (ViewerActivity.isShowing) return
            if (ctx.getSystemService(AudioManager::class.java)?.mode != AudioManager.MODE_NORMAL) return
            if (ImageStore.forContext(ctx).main() == null) return
            val intent = ViewerActivity.intent(ctx).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Settings.canDrawOverlays(ctx)) {
                try { ctx.startActivity(intent); return } catch (_: Exception) { }
            }
            val pi = PendingIntent.getActivity(ctx, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = Notification.Builder(ctx, SHOW_CHANNEL)
                .setSmallIcon(R.drawable.ic_tile_devi)
                .setContentTitle("Devi")
                .setCategory(Notification.CATEGORY_ALARM)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setTimeoutAfter(5_000)
                .build()
            ctx.getSystemService(NotificationManager::class.java).notify(SHOW_ID, n)
        }

        /** Clears the "bring her" notification once she's on screen. */
        fun clearShowNotification(ctx: Context) {
            ctx.getSystemService(NotificationManager::class.java).cancel(SHOW_ID)
        }
    }
}
