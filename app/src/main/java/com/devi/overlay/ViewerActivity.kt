package com.devi.overlay

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Full-screen, nothing-but-the-picture viewer. Shows over the lock screen without unlocking.
 * Swipe sideways for the next/previous picture; back swipe (from the edge) or swipe down to close.
 */
class ViewerActivity : Activity() {

    private lateinit var imageView: ImageView
    private lateinit var store: ImageStore
    private lateinit var gestures: GestureDetector
    private var ids: List<String> = emptyList()
    private var index = 0
    private var loadToken = 0
    private var receiverRegistered = false

    private val decoder: ExecutorService = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val autoClose = Runnable { finish() }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF || intent.action == ACTION_CLOSE) finish()
        }
    }

    private var focus = false
    private var shiftStep = 0
    private lateinit var root: FrameLayout

    /** Burn-in guard: a 1-2 pixel walk once a minute, too small to notice. */
    private val burnInShift = object : Runnable {
        override fun run() {
            val (x, y) = FocusPolicy.burnInOffset(++shiftStep)
            root.translationX = x.toFloat(); root.translationY = y.toFloat()
            handler.postDelayed(this, 60_000)
        }
    }

    private lateinit var pairings: Pairings
    private var requestedChant = false
    private var chantStopped = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        store = ImageStore.forContext(this)
        ids = store.ids()
        if (ids.isEmpty()) {
            finish()
            return
        }
        index = store.mainIndex()

        imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
        }
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(
                imageView,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
            )
        }
        // Keep the whole picture clear of the punch-hole camera (a tall picture would otherwise lose
        // a sliver under it). Normal photos already have black bands there, so they don't change.
        root.setOnApplyWindowInsetsListener { _, insets ->
            val cut = insets.getInsets(WindowInsets.Type.displayCutout())
            imageView.setPadding(cut.left, cut.top, cut.right, cut.bottom)
            insets
        }
        setContentView(root)
        hideSystemBars()

        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            // A tap deliberately does nothing. Close with the system back swipe or a swipe down.

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (abs(vx) > abs(vy)) {
                    if (vx < 0) show(index + 1, fromRight = true) else show(index - 1, fromRight = false)
                } else if (vy > 0) {
                    finish()
                }
                return true
            }
        })

        alive = true
        focus = DeviSettings.focusMode(this)
        FocusPolicy.autoCloseMs(focus, DeviSettings.stayMinutes(this))?.let { handler.postDelayed(autoClose, it) }
        window.attributes = window.attributes.apply { screenBrightness = FocusPolicy.windowBrightness(DeviSettings.brightness(this@ViewerActivity)) }
        if (focus) {
            DndKeeper.silence(this, DeviSettings.letAlarms(this))
            handler.postDelayed(burnInShift, 60_000)
        }
        pairings = Pairings.forContext(this)
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(ACTION_CLOSE) }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenOffReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOffReceiver, filter)
        }
        receiverRegistered = true

        load(index, animateFromRight = null)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::gestures.isInitialized) gestures.onTouchEvent(ev)
        return true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        isShowing = true
        DeviListenerService.clearShowNotification(this)
    }

    override fun onPause() {
        if (isFinishing) { stopViewerChant(); DndKeeper.restore(this) }
        isShowing = false
        super.onPause()
    }

    override fun onDestroy() {
        stopViewerChant()
        handler.removeCallbacks(autoClose)
        handler.removeCallbacks(burnInShift)
        isShowing = false
        alive = false
        DndKeeper.restore(this)
        if (receiverRegistered) unregisterReceiver(screenOffReceiver)
        decoder.shutdownNow()
        isShowing = false
        super.onDestroy()
    }

    /** Moves to [newIndex] if it exists (no wrap-around). */
    private fun show(newIndex: Int, fromRight: Boolean) {
        if (newIndex !in ids.indices) return
        index = newIndex
        load(index, animateFromRight = fromRight)
    }

    /** Fades out the chant this viewer started, even if the service hasn't reported it yet. */
    private fun stopViewerChant() {
        if (chantStopped) return
        chantStopped = true
        if (requestedChant || ChantService.startedByViewer) ChantService.stop(this, fade = true, force = requestedChant)
    }

    /** Her paired chant rises with her; a picture without one lets the viewer's chant fade away. */
    private fun syncChant(imageId: String) {
        val chant = pairings.chantFor(imageId)
        if (chant != null) {
            if (ChantService.currentChantId != chant) { ChantService.play(this, chant, startedByViewer = true); requestedChant = true }
        } else if (ChantService.startedByViewer) {
            ChantService.stop(this, fade = true)
        }
    }

    private fun load(i: Int, animateFromRight: Boolean?) {
        syncChant(ids[i])
        val token = ++loadToken
        val file = store.file(ids[i])
        val metrics = resources.displayMetrics
        val w = maxOf(metrics.widthPixels, 1)
        val h = maxOf(metrics.heightPixels, 1)
        decoder.execute {
            val bmp: Bitmap? = ImageLoading.decodeForDisplay(file, w, h)
            runOnUiThread {
                if (token != loadToken || isFinishing || isDestroyed) return@runOnUiThread
                imageView.setImageBitmap(bmp) // null -> plain black
                if (animateFromRight != null) {
                    val width = imageView.width.toFloat()
                    imageView.translationX = if (animateFromRight) width else -width
                    imageView.animate().translationX(0f).setDuration(220).start()
                }
            }
        }
    }

    private fun hideSystemBars() {
        window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    companion object {
        const val ACTION_CLOSE = "com.devi.overlay.CLOSE_VIEWER"

        @Volatile
        var isShowing = false

        /** Between create and destroy (even while paused): her Do Not Disturb is still wanted. */
        @Volatile
        var alive = false

        fun intent(ctx: Context): Intent = Intent(ctx, ViewerActivity::class.java)
    }
}
