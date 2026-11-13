package com.devi.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.WindowManager

/** Shows the eye break over everything, in either style. Main thread. */
object EyeBreak {
    private const val TAG = "DeviEyes"
    private val main = Handler(Looper.getMainLooper())
    private var view: CosmosView? = null
    private var waitingForPicture = false

    val showing get() = view != null || waitingForPicture

    /** Whether a break may start now on its own (a "Try" press skips these checks). */
    fun allowedNow(ctx: Context): Boolean {
        val audio = ctx.getSystemService(AudioManager::class.java)
        val call = audio?.mode != AudioManager.MODE_NORMAL
        val deviVideo = ChantService.currentChantId?.let { ChantStore.isVideo(it) } == true && ChantService.isPlaying
        val otherSound = audio?.isMusicActive == true && !ChantService.isPlaying
        return EyeBreakPolicy.shouldRun(call, otherSound, deviVideo, ViewerActivity.isShowing)
    }

    fun start(ctx: Context, style: EyeStyle) {
        val app = ctx.applicationContext
        if (showing) return
        if (!Settings.canDrawOverlays(app)) { Log.i(TAG, "no overlay permission"); return }
        if (style == EyeStyle.DISSOLVE && AccessGuard.beginCapture(app)) {
            waitingForPicture = true
            // If the picture never comes (or was refused), the sky simply covers the screen.
            main.postDelayed({ if (waitingForPicture) { AccessGuard.endCapture(app); waitingForPicture = false; show(app, null) } }, 3_000)
        } else show(app, null)
    }

    /** From the Accessibility service: the screen's picture, or null if it couldn't be taken. */
    fun captured(ctx: Context, bmp: Bitmap?) = main.post {
        if (!waitingForPicture) return@post
        waitingForPicture = false
        show(ctx.applicationContext, bmp)
    }

    /** A payment app was in front: no picture, and no break over it. */
    fun captureRefused(ctx: Context) = main.post {
        AccessGuard.endCapture(ctx.applicationContext)
        waitingForPicture = false
        Log.i(TAG, "skipped: payment app in front")
    }

    private fun show(ctx: Context, screen: Bitmap?) {
        if (view != null) return
        val images = ImageStore.forContext(ctx)
        val her = images.main()?.let { ImageLoading.decodeThumb(images.file(it), 900) }
        val wm = ctx.getSystemService(WindowManager::class.java)
        val seconds = DeviSettings.eyeSeconds(ctx)
        lateinit var v: CosmosView
        v = CosmosView(ctx, screen, her, seconds * 1000L) {
            main.post { try { wm.removeView(v) } catch (_: Exception) { }; if (view === v) view = null }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            setFitInsetsTypes(0)
            title = "Devi eye break"
        }
        try { wm.addView(v, lp); view = v } catch (e: Exception) { Log.w(TAG, "could not show", e) }
    }

    fun stop() { view?.close() }
}
