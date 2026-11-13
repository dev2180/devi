package com.devi.overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import kotlin.math.abs

/**
 * Watches a video chant full screen. Tap to pause or play; swipe down or back to close the picture
 * while the sound keeps going; turn the screen off and it keeps playing in the background.
 */
class VideoActivity : Activity() {

    private lateinit var surfaceView: SurfaceView
    private lateinit var frame: FrameLayout
    private lateinit var controls: LinearLayout
    private lateinit var seek: SeekBar
    private val handler = Handler(Looper.getMainLooper())
    private var userSeeking = false

    private val tick = object : Runnable {
        override fun run() {
            fitVideo()
            val d = ChantService.durationMs()
            if (d > 0 && !userSeeking) { seek.max = d; seek.progress = ChantService.positionMs() }
            if (ChantService.currentChantId == null) { finish(); return }
            handler.postDelayed(this, 500)
        }
    }

    private val hideControls = Runnable { controls.animate().alpha(0f).setDuration(300).start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        frame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        surfaceView = SurfaceView(this)
        frame.addView(surfaceView, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) = ChantService.attachSurface(h.surface)
            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) = Unit
            override fun surfaceDestroyed(h: SurfaceHolder) = ChantService.attachSurface(null)
        })

        controls = Ui.vertical(this).apply {
            setBackgroundColor(0x99000000.toInt())
            setPadding(Ui.dp(this@VideoActivity, 20f), Ui.dp(this@VideoActivity, 14f), Ui.dp(this@VideoActivity, 20f), Ui.dp(this@VideoActivity, 28f))
        }
        val id = intent.getStringExtra(EXTRA_CHANT)
        controls.addView(Ui.heading(this, id?.let { ChantStore.forContext(this).title(it) } ?: "Chant", 18f))
        seek = SeekBar(this).apply {
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) = Unit
                override fun onStartTrackingTouch(s: SeekBar) { userSeeking = true }
                override fun onStopTrackingTouch(s: SeekBar) { ChantService.seekTo(s.progress); userSeeking = false; showControls() }
            })
        }
        controls.addView(seek, Ui.lp(top = Ui.dp(this, 8f)))
        controls.addView(Ui.bodyText(this, "Turn the screen off and she keeps playing. Swipe down to listen without the picture.", 12f, muted = true),
            Ui.lp(top = Ui.dp(this, 6f)))
        frame.addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(frame)
        hideBars()

        val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                ChantService.pauseOrResume(this@VideoActivity); showControls(); return true
            }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (vy > 0 && abs(vy) > abs(vx)) finish()
                return true
            }
        })
        frame.setOnTouchListener { _, ev -> gestures.onTouchEvent(ev) }

        if (id != null && ChantService.currentChantId != id) ChantService.play(this, id, startedByViewer = false)
    }

    private fun showControls() {
        controls.animate().cancel(); controls.alpha = 1f
        handler.removeCallbacks(hideControls); handler.postDelayed(hideControls, 3_000)
    }

    /** Fits the whole video on screen, untouched, black around it. */
    private fun fitVideo() {
        val (vw, vh) = ChantService.videoSize() ?: return
        val fw = frame.width; val fh = frame.height
        if (fw == 0 || fh == 0) return
        val scale = minOf(fw.toFloat() / vw, fh.toFloat() / vh)
        val w = (vw * scale).toInt(); val h = (vh * scale).toInt()
        val lp = surfaceView.layoutParams as FrameLayout.LayoutParams
        if (lp.width != w || lp.height != h) { lp.width = w; lp.height = h; surfaceView.layoutParams = lp }
    }

    private fun hideBars() {
        window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onResume() { super.onResume(); handler.post(tick); showControls() }
    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }

    companion object {
        private const val EXTRA_CHANT = "chant"
        fun open(ctx: Context, chantId: String) =
            ctx.startActivity(Intent(ctx, VideoActivity::class.java).putExtra(EXTRA_CHANT, chantId))
    }
}
