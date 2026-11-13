package com.devi.overlay

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import java.io.File

/**
 * Downloads a full YouTube video, in the best quality YouTube offers, straight into her chants.
 * It plays as a video, or as sound alone with the screen off.
 * Uses yt-dlp (bundled through youtubedl-android). If YouTube has changed and the download fails,
 * yt-dlp updates itself once and tries again.
 */
object YtDownloader {
    private const val TAG = "DeviYt"
    private const val MARK = "|DEVI|"

    sealed interface State {
        data object Idle : State
        data class Working(val url: String, val percent: Int, val note: String) : State
        data class Done(val title: String) : State
        data class Failed(val reason: String) : State
    }

    @Volatile var state: State = State.Idle
        private set

    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<(State) -> Unit>()
    private var ready = false

    fun listen(l: (State) -> Unit) { listeners += l }
    fun unlisten(l: (State) -> Unit) { listeners -= l }

    private fun post(s: State) { state = s; main.post { listeners.toList().forEach { it(s) } } }

    val busy get() = state is State.Working

    /** Starts the download in a foreground service, so a long video finishes even if Devi is closed. */
    fun start(ctx: Context, url: String) {
        if (busy) return
        post(State.Working(url, 0, "Getting ready"))
        ctx.startForegroundService(android.content.Intent(ctx, DownloadService::class.java).putExtra(DownloadService.EXTRA_URL, url))
    }

    /** Runs on the service's thread; returns when finished. */
    fun run(ctx: Context, url: String) {
        val app = ctx.applicationContext
        run {
            try {
                if (!ready) { YoutubeDL.getInstance().init(app); FFmpeg.getInstance().init(app); ready = true }
                val result = try { download(app, url) } catch (e: Exception) {
                    Log.w(TAG, "first try failed, updating yt-dlp", e)
                    post(State.Working(url, 0, "Updating the downloader"))
                    try { YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE) } catch (u: Exception) { Log.w(TAG, "update failed", u) }
                    download(app, url)
                }
                val (title, file) = result
                file.inputStream().use { ChantStore.forContext(app).add(it, file.extension.ifEmpty { "mp4" }, title) }
                file.delete()
                post(State.Done(title))
            } catch (e: Exception) {
                Log.w(TAG, "download failed", e)
                val msg = e.message.orEmpty()
                post(State.Failed(when {
                    "Unable to resolve host" in msg || "network" in msg.lowercase() -> "No internet connection."
                    "Private video" in msg || "unavailable" in msg.lowercase() -> "This video isn't available."
                    else -> "YouTube didn't allow the download. Try again in a little while."
                }))
            }
        }
    }

    private fun download(ctx: Context, url: String): Pair<String, File> {
        val dir = File(ctx.cacheDir, "yt").apply { deleteRecursively(); mkdirs() }
        val request = YoutubeDLRequest(url).apply {
            // Best video (not AV1, which this phone can't play smoothly) with the best sound, as one mp4.
            addOption("-f", "bv*[vcodec!^=av01]+ba/b[vcodec!^=av01]/bv*+ba/b")
            addOption("--merge-output-format", "mp4")
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("-o", "${dir.absolutePath}/%(id)s.%(ext)s")
            addOption("--print", "after_move:%(title)s$MARK%(filepath)s")
            addOption("--no-simulate")
        }
        val response = YoutubeDL.getInstance().execute(request, "devi-chant") { progress, _, _ ->
            post(State.Working(url, progress.toInt().coerceIn(0, 100), "Downloading"))
        }
        val line = response.out.lines().lastOrNull { MARK in it } ?: error("no output")
        val title = line.substringBefore(MARK).trim().ifEmpty { "Chant" }
        val file = File(line.substringAfter(MARK).trim()).takeIf { it.exists() }
            ?: dir.listFiles()?.maxByOrNull { it.length() } ?: error("no file")
        return title to file
    }

    fun reset() { if (!busy) state = State.Idle }
}
