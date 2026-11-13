package com.devi.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

/** Keeps a YouTube download alive with a progress notification until it is safely in her chants. */
class DownloadService : Service() {

    private val listener: (YtDownloader.State) -> Unit = { s ->
        if (s is YtDownloader.State.Working) notifyProgress(s.percent, s.note)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        startForeground(ID, build(0, "Getting ready"), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        val url = intent?.getStringExtra(EXTRA_URL)
        if (url == null) { stopSelf(); return START_NOT_STICKY }
        YtDownloader.listen(listener)
        Thread {
            YtDownloader.run(this, url)
            YtDownloader.unlisten(listener)
            val done = YtDownloader.state
            stopForeground(STOP_FOREGROUND_REMOVE)
            nm.notify(ID + 1, Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_tile_devi)
                .setContentTitle(if (done is YtDownloader.State.Done) "Added to her chants" else "Download didn't finish")
                .setContentText(when (done) { is YtDownloader.State.Done -> done.title; is YtDownloader.State.Failed -> done.reason; else -> "" })
                .setContentIntent(open()).setAutoCancel(true).build())
            stopSelf()
        }.start()
        return START_NOT_STICKY
    }

    private fun open() = PendingIntent.getActivity(this, 5, Intent(this, ManageActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    private fun build(percent: Int, note: String): Notification =
        Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_tile_devi)
            .setContentTitle("Downloading a chant").setContentText(note)
            .setProgress(100, percent, percent == 0).setOngoing(true).setContentIntent(open()).build()

    private var lastShown = -1
    private fun notifyProgress(percent: Int, note: String) {
        if (percent == lastShown) return
        lastShown = percent
        getSystemService(NotificationManager::class.java).notify(ID, build(percent, note))
    }

    companion object {
        const val EXTRA_URL = "url"
        private const val CHANNEL = "downloads"
        private const val ID = 21
    }
}
