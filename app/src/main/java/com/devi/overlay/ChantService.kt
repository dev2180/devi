package com.devi.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast

/**
 * Plays one chant at a time in the background: rises softly over 10 s, fades out over 1.5 s,
 * crossfades when the chant changes, yields to calls and alarms, and shows media controls.
 */
class ChantService : Service() {

    private class Voice(val player: MediaPlayer, val chantId: String, val instant: Boolean) {
        var gain = if (instant) 1f else 0f
        var fadingOut = false
        var fadeStart = 0L
        var fadeFrom = 0f
        var clock = FadeClock(SystemClock.uptimeMillis())
    }

    private val handler = Handler(Looper.getMainLooper())
    private var current: Voice? = null
    private val outgoing = mutableListOf<Voice>()
    private var paused = false
    private var pausedByFocus = false
    private lateinit var session: MediaSession
    private lateinit var audio: AudioManager
    private var focusRequest: AudioFocusRequest? = null

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            current?.let { v ->
                if (!paused) {
                    v.gain = if (v.fadingOut) FadeCurve.fadeOut(now - v.fadeStart, v.fadeFrom)
                    else if (v.instant) 1f else FadeCurve.fadeIn(v.clock.elapsed(now))
                    v.player.setVolume(v.gain, v.gain)
                    if (v.fadingOut && now - v.fadeStart >= FADE_OUT_MS) {
                        release(v); current = null
                    }
                }
            }
            outgoing.toList().forEach { v ->
                val g = FadeCurve.fadeOut(now - v.fadeStart, v.fadeFrom)
                v.player.setVolume(g, g)
                if (now - v.fadeStart >= FADE_OUT_MS) { release(v); outgoing.remove(v) }
            }
            publishState()
            if (current == null && outgoing.isEmpty()) { shutDown(); return }
            handler.postDelayed(this, TICK_MS)
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> stopAll(fade = false)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                outgoing.toList().forEach { release(it) }; outgoing.clear()
                if (!paused) { pausePlayer(); pausedByFocus = true }
            }
            AudioManager.AUDIOFOCUS_GAIN -> if (pausedByFocus) { pausedByFocus = false; resumePlayer() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        instance = this
        session = MediaSession(this, "Devi").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = resumePlayer()
                override fun onPause() = pausePlayer()
                override fun onStop() = stopAll(fade = true)
            })
            isActive = true
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Chants", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                // Promise kept first: Android requires startForeground() soon after startForegroundService().
                startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                intent.getStringExtra(EXTRA_CHANT)?.let { play(it, intent.getBooleanExtra(EXTRA_BY_VIEWER, false)) }
            }
            ACTION_TOGGLE -> if (paused) resumePlayer() else pausePlayer()
            ACTION_STOP -> stopAll(fade = intent.getBooleanExtra(EXTRA_FADE, true))
        }
        if (current == null && outgoing.isEmpty()) shutDown()
        return START_NOT_STICKY
    }

    private fun play(chantId: String, byViewer: Boolean) {
        if (!ChantSource.exists(this, chantId)) { failed(); return }
        if (current?.chantId == chantId && current?.fadingOut == false) {
            startedByViewer = startedByViewer && byViewer
            if (paused) resumePlayer()
            return
        }
        current?.let { beginFadeOut(it); outgoing += it }
        current = null
        val video = ChantStore.isVideo(chantId)
        val instant = ChantPlayback.instant(video, byViewer)
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(attrs)
            ChantSource.setOn(this, chantId, player)
            player.isLooping = ChantPlayback.loops(video)
            player.setVolume(if (instant) 1f else 0f, if (instant) 1f else 0f)
            if (video) surface?.let { player.setSurface(it) }
            // Keep the sound going with the screen off.
            player.setWakeMode(this, android.os.PowerManager.PARTIAL_WAKE_LOCK)
            player.prepare()
        } catch (e: Exception) {
            player.release(); failed(); return
        }
        player.setOnErrorListener { _, _, _ -> failed(); stopAll(fade = false); true }
        if (video) player.setOnCompletionListener { stopAll(fade = false) }
        if (!requestFocus()) { player.release(); return }
        val v = Voice(player, chantId, instant)
        current = v
        paused = false
        pausedByFocus = false
        currentChantId = chantId
        startedByViewer = byViewer
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        player.start()
        v.clock = FadeClock(SystemClock.uptimeMillis())
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun beginFadeOut(v: Voice) {
        if (v.fadingOut) return
        v.fadingOut = true
        v.fadeFrom = v.gain
        v.fadeStart = SystemClock.uptimeMillis()
    }

    private fun stopAll(fade: Boolean) {
        val v = current
        if (v == null) { if (!fade) outgoing.toList().forEach { release(it) }.also { outgoing.clear() }; return }
        if (fade && !paused) {
            beginFadeOut(v)
        } else {
            release(v); current = null
        }
        if (!fade) { outgoing.toList().forEach { release(it) }; outgoing.clear() }
        startedByViewer = false
        publishState()
    }

    private fun pausePlayer() {
        val v = current ?: return
        if (paused) return
        v.player.pause(); paused = true
        v.clock.pause(SystemClock.uptimeMillis())
        refreshNotification()
    }

    private fun resumePlayer() {
        val v = current ?: return
        if (!paused) return
        if (!requestFocus()) return
        v.player.start(); paused = false
        v.clock.resume(SystemClock.uptimeMillis())
        refreshNotification()
    }

    private fun requestFocus(): Boolean {
        val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(focusListener, handler)
            .build().also { focusRequest = it }
        return audio.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun release(v: Voice) {
        try { v.player.stop() } catch (_: Exception) { }
        v.player.release()
    }

    private fun failed() {
        handler.post { Toast.makeText(this, "This chant can't be played", Toast.LENGTH_SHORT).show() }
    }

    private fun publishState() {
        val v = current
        isPlaying = v != null && !paused && !v.fadingOut
        gain = v?.gain ?: 0f
        currentChantId = if (v != null && !v.fadingOut) v.chantId else null
        rising = v != null && !paused && !v.fadingOut && v.gain < 0.99f
    }

    private fun shutDown() {
        handler.removeCallbacks(tick)
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        isPlaying = false; currentChantId = null; startedByViewer = false; gain = 0f; rising = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        current?.let { release(it) }; current = null
        outgoing.forEach { release(it) }; outgoing.clear()
        session.release()
        if (instance === this) instance = null
        isPlaying = false; currentChantId = null; startedByViewer = false; gain = 0f; rising = false
        super.onDestroy()
    }

    // ---------- notification ----------

    private fun refreshNotification() {
        if (current == null) return
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private var artFor: String? = null
    private var art: android.graphics.Bitmap? = null

    /** Her picture for this chant (the one paired with it, else the main one): shown as the
     *  "album art" on the lock screen, AOD and realme's Fluid Cloud while the chant plays. */
    private fun artFor(chantId: String): android.graphics.Bitmap? {
        if (artFor == chantId) return art
        val images = ImageStore.forContext(this)
        val pairs = Pairings.forContext(this)
        val id = images.ids().firstOrNull { pairs.chantFor(it) == chantId } ?: images.main()
        art = id?.let { try { ImageLoading.decodeThumb(images.file(it), 720) } catch (_: Exception) { null } }
        artFor = chantId
        return art
    }

    private fun buildNotification(): Notification {
        val title = current?.let { ChantStore.forContext(this).title(it.chantId) } ?: "Chant"
        val picture = current?.let { artFor(it.chantId) }
        session.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "Devi")
            .apply { picture?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it); putBitmap(MediaMetadata.METADATA_KEY_ART, it) } }
            .build())
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP)
                .setState(if (paused) PlaybackState.STATE_PAUSED else PlaybackState.STATE_PLAYING, 0, 1f)
                .build(),
        )
        val toggle = action(
            if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
            if (paused) "Play" else "Pause", ACTION_TOGGLE, 1,
        )
        val stop = action(android.R.drawable.ic_menu_close_clear_cancel, "Stop", ACTION_STOP, 2)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, FeedActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile_devi)
            .setContentTitle(title)
            .setContentText("Devi")
            .apply { picture?.let { setLargeIcon(it) } }
            .setContentIntent(open)
            .setOngoing(!paused)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(toggle)
            .addAction(stop)
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1))
            .build()
    }

    private fun action(icon: Int, label: String, act: String, code: Int): Notification.Action {
        val pi = PendingIntent.getService(
            this, code, Intent(this, ChantService::class.java).setAction(act).putExtra(EXTRA_FADE, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), label, pi).build()
    }

    companion object {
        private const val CHANNEL = "chants"
        private const val NOTIFICATION_ID = 7
        private const val TICK_MS = 50L
        private const val FADE_OUT_MS = 1_500L
        private const val ACTION_PLAY = "com.devi.overlay.PLAY"
        private const val ACTION_TOGGLE = "com.devi.overlay.TOGGLE"
        private const val ACTION_STOP = "com.devi.overlay.STOP"
        private const val EXTRA_CHANT = "chant"
        private const val EXTRA_BY_VIEWER = "byViewer"
        private const val EXTRA_FADE = "fade"

        @Volatile var isPlaying = false
            private set

        @Volatile private var instance: ChantService? = null
        @Volatile private var surface: android.view.Surface? = null

        /** The video screen shows the playing video here; null keeps only the sound (screen off). */
        fun attachSurface(s: android.view.Surface?) {
            surface = s
            val svc = instance ?: return
            svc.handler.post {
                val v = svc.current ?: return@post
                if (ChantStore.isVideo(v.chantId)) try { v.player.setSurface(s) } catch (_: Exception) { }
            }
        }

        /** Main thread only. */
        fun videoSize(): Pair<Int, Int>? = instance?.current?.player?.let { p ->
            try { if (p.videoWidth > 0) p.videoWidth to p.videoHeight else null } catch (_: Exception) { null }
        }
        fun positionMs(): Int = try { instance?.current?.player?.currentPosition ?: 0 } catch (_: Exception) { 0 }
        fun durationMs(): Int = try { instance?.current?.player?.duration ?: 0 } catch (_: Exception) { 0 }
        fun seekTo(ms: Int) { try { instance?.current?.player?.seekTo(ms) } catch (_: Exception) { } }
        val paused: Boolean get() = instance?.paused ?: true
        @Volatile var currentChantId: String? = null
            private set
        @Volatile var startedByViewer = false
            private set
        @Volatile var gain = 0f
            private set
        @Volatile var rising = false
            private set

        fun play(ctx: Context, chantId: String, startedByViewer: Boolean) {
            ctx.startForegroundService(
                Intent(ctx, ChantService::class.java).setAction(ACTION_PLAY)
                    .putExtra(EXTRA_CHANT, chantId).putExtra(EXTRA_BY_VIEWER, startedByViewer),
            )
        }

        fun stop(ctx: Context, fade: Boolean = true, force: Boolean = false) {
            if (!force && currentChantId == null && !isPlaying) return
            try {
                ctx.startService(Intent(ctx, ChantService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_FADE, fade))
            } catch (_: Exception) { }
        }

        fun pauseOrResume(ctx: Context) {
            try { ctx.startService(Intent(ctx, ChantService::class.java).setAction(ACTION_TOGGLE)) } catch (_: Exception) { }
        }
    }
}
