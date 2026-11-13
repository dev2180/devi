package com.devi.overlay

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Home: an endless full-screen feed. Swipe up through her pictures and the chant videos; a picture
 * brings its chant, rising softly; a video plays and flows on to the next page when it ends.
 * Sound keeps going with the screen off. Tap to pause or play.
 */
class FeedActivity : Activity() {

    private lateinit var images: ImageStore
    private lateinit var chants: ChantStore
    private lateinit var pairings: Pairings
    private val worker = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(80 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private lateinit var pager: ViewPager2
    private lateinit var chrome: View
    private lateinit var playButton: ImageButton
    private lateinit var progress: View
    private lateinit var glyph: ImageView
    private lateinit var hint: TextView
    private lateinit var empty: LinearLayout

    private var items: List<FeedItem> = emptyList()
    private var signature = ""
    private var selectedPos = -1
    private var startedVideo: String? = null
    private var videoSeenPlaying = false
    /** Silent until you tap play; then each page plays as you swipe, until you pause. */
    private var listening = false
    /** Battery saver or colour off: no drifting, bobbing or glow, fewer redraws. */
    private var lean = false
    private var shownPlaying: Boolean? = null
    private var bob: ObjectAnimator? = null

    private fun dp(v: Float) = Ui.dp(this, v)

    private val tick = object : Runnable {
        override fun run() { onTick(); main.postDelayed(this, if (lean) 1_000 else 250) }
    }
    private val fadeChrome = Runnable { if (ChantService.isPlaying) chrome.animate().alpha(0f).setDuration(600).start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        images = ImageStore.forContext(this)
        chants = ChantStore.forContext(this)
        pairings = Pairings.forContext(this)
        setContentView(buildUi())
        immersive()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        reload(force = true)
    }

    override fun onResume() {
        super.onResume()
        val wasLean = lean
        lean = Lean.on(this)
        if (lean != wasLean) pager.adapter?.notifyDataSetChanged()
        if (lean) { bob?.cancel(); hint.translationY = 0f }
        immersive()
        reload(force = false)
        selectedHolder()?.let { h -> if (h.item is FeedItem.Video) h.surface?.let { ChantService.attachSurface(it) } }
        selectedHolder()?.startDrift()
        DeviListenerService.sync(this)
        DndKeeper.restoreIfLeftOver(this)
        showChrome()
        main.post(tick)
    }

    override fun onPause() {
        main.removeCallbacks(tick)
        selectedHolder()?.stopDrift()
        ChantService.attachSurface(null) // the sound carries on; only the picture goes
        super.onPause()
    }

    override fun onDestroy() { main.removeCallbacksAndMessages(null); worker.shutdownNow(); super.onDestroy() }

    // ---------- content ----------

    private fun reload(force: Boolean) {
        val videos = chants.ids().filter { ChantStore.isVideo(it) }
        val sig = images.ids().joinToString() + "|" + images.main() + "|" + videos.joinToString()
        if (!force && sig == signature) return
        signature = sig
        val keep = items.getOrNull(FeedOrder.indexOf(selectedPos, items.size))
        items = FeedOrder.build(images.ids(), images.main(), videos)
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        pager.adapter?.notifyDataSetChanged()
        val idx = keep?.let { items.indexOf(it) }?.takeIf { it >= 0 } ?: 0
        val pos = FeedOrder.startPosition(items.size) + idx
        selectedPos = -1
        if (items.isNotEmpty()) {
            pager.setCurrentItem(pos, false)
            pager.post { if (selectedPos != pager.currentItem) onSelected(pager.currentItem) }
        }
    }

    private fun itemAt(position: Int) = items[FeedOrder.indexOf(position, items.size)]

    private fun onSelected(position: Int) {
        if (items.isEmpty()) return
        val wasSelected = selectedPos
        selectedPos = position
        holderAt(wasSelected)?.stopDrift()
        if (wasSelected >= 0 && wasSelected != position) {
            pager.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            if (hint.visibility == View.VISIBLE) hint.animate().alpha(0f).setDuration(400).withEndAction { hint.visibility = View.GONE; bob?.cancel(); bob = null }.start()
        }
        val holder = holderAt(position)
        holder?.startDrift()
        when (val item = itemAt(position)) {
            is FeedItem.Picture -> {
                ChantService.attachSurface(null)
                startedVideo = null
                val chant = FeedOrder.chantForPicture(pairings.chantFor(item.id), chants.ids())
                if (listening && chant != null && ChantService.currentChantId != chant) ChantService.play(this, chant, startedByViewer = false)
            }
            is FeedItem.Video -> {
                holder?.surface?.let { ChantService.attachSurface(it) } ?: ChantService.attachSurface(null)
                holder?.showPoster()
                startedVideo = item.chantId
                videoSeenPlaying = false
                if (listening && ChantService.currentChantId != item.chantId) ChantService.play(this, item.chantId, startedByViewer = false)
            }
        }
        showChrome()
    }

    private fun onTick() {
        val playing = ChantService.isPlaying
        if (playing != shownPlaying) { shownPlaying = playing; playButton.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play) }
        val d = ChantService.durationMs()
        val p = if (ChantService.currentChantId != null && d > 0) (ChantService.positionMs().toFloat() / d).coerceIn(0f, 1f) else 0f
        if (abs(p - progress.scaleX) > 0.0005f) progress.scaleX = p
        val v = startedVideo ?: return
        if (ChantService.currentChantId == v) {
            if (!videoSeenPlaying && ChantService.positionMs() > 120) { videoSeenPlaying = true; selectedHolder()?.hidePoster() }
        } else if (videoSeenPlaying && ChantService.currentChantId == null) {
            // The video ended on its own: flow on to the next page.
            startedVideo = null
            pager.setCurrentItem(pager.currentItem + 1, true)
        }
    }

    private fun togglePlay() {
        val item = items.getOrNull(FeedOrder.indexOf(pager.currentItem, items.size)) ?: return
        val wanted = when (item) {
            is FeedItem.Picture -> FeedOrder.chantForPicture(pairings.chantFor(item.id), chants.ids())
            is FeedItem.Video -> item.chantId
        }
        val resume: Boolean
        if (ChantService.isPlaying && ChantService.currentChantId == wanted) {
            resume = false; listening = false
            ChantService.pauseOrResume(this)
        } else {
            resume = true; listening = true
            if (ChantService.currentChantId == wanted) ChantService.pauseOrResume(this)
            else { selectedPos = -1; onSelected(pager.currentItem) }
        }
        glyph.setImageResource(if (resume) R.drawable.ic_play else R.drawable.ic_pause)
        glyph.animate().cancel()
        glyph.alpha = 0f; glyph.scaleX = 0.8f; glyph.scaleY = 0.8f
        glyph.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).withEndAction {
            glyph.animate().alpha(0f).scaleX(1.15f).scaleY(1.15f).setStartDelay(350).setDuration(400).start()
        }.start()
        showChrome()
    }

    private fun showChrome() {
        chrome.animate().cancel(); chrome.animate().alpha(1f).setDuration(200).start()
        main.removeCallbacks(fadeChrome); main.postDelayed(fadeChrome, 3_500)
    }

    // ---------- layout ----------

    private fun buildUi(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(getColor(R.color.void_black)) }
        pager = ViewPager2(this).apply {
            orientation = ViewPager2.ORIENTATION_VERTICAL
            offscreenPageLimit = 1
            adapter = FeedAdapter()
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) = onSelected(position)
            })
            // Gentle depth: the picture lags its page a little and the words fade as it leaves.
            setPageTransformer { page, pos ->
                val h = page.getTag(R.id.feed_holder) as? PageHolder ?: return@setPageTransformer
                h.media.translationY = -pos * page.height * 0.18f
                h.words.alpha = (1f - abs(pos) * 1.6f).coerceIn(0f, 1f)
            }
        }
        (pager.getChildAt(0) as? RecyclerView)?.overScrollMode = View.OVER_SCROLL_NEVER
        root.addView(pager, FrameLayout.LayoutParams(-1, -1))

        empty = Ui.vertical(this).apply {
            gravity = Gravity.CENTER; visibility = View.GONE; setPadding(dp(32f), 0, dp(32f), 0)
            addView(Ui.heading(this@FeedActivity, "Devi", 40f).apply { gravity = Gravity.CENTER })
            addView(Ui.bodyText(this@FeedActivity, "Add her pictures and chants to begin.", 16f, muted = true).apply { gravity = Gravity.CENTER }, Ui.lp(top = dp(10f)))
            addView(Ui.primaryButton(this@FeedActivity, "Open library").apply { setOnClickListener { openLibrary() } },
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(24f); gravity = Gravity.CENTER_HORIZONTAL })
        }
        root.addView(empty, FrameLayout.LayoutParams(-1, -1))

        glyph = ImageView(this).apply {
            alpha = 0f; setColorFilter(getColor(R.color.ink)); setPadding(dp(22f), dp(22f), dp(22f), dp(22f))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x66000000) }
        }
        root.addView(glyph, FrameLayout.LayoutParams(dp(84f), dp(84f), Gravity.CENTER))

        // Chrome: wordmark and library at the top; play and a hairline of progress at the foot.
        val chromeBox = FrameLayout(this)
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(Ui.heading(this, "Devi", 26f).apply { setShadowLayer(12f, 0f, 0f, 0x99000000.toInt()) }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(roundIcon(R.drawable.ic_library, "Library") { openLibrary() }, LinearLayout.LayoutParams(dp(44f), dp(44f)))
        top.addView(roundIcon(R.drawable.ic_tune, "Settings") { startActivity(Intent(this, ShortcutsActivity::class.java)) },
            LinearLayout.LayoutParams(dp(44f), dp(44f)).apply { marginStart = dp(10f) })
        chromeBox.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        playButton = roundIcon(R.drawable.ic_pause, "Play or pause") { togglePlay() }.apply {
            background = Ui.pill(this@FeedActivity, R.color.accent); setColorFilter(getColor(R.color.accent_ink))
        }
        chromeBox.addView(playButton, FrameLayout.LayoutParams(dp(56f), dp(56f), Gravity.BOTTOM or Gravity.END))
        root.addView(chromeBox, FrameLayout.LayoutParams(-1, -1))
        chrome = chromeBox

        val track = FrameLayout(this).apply { setBackgroundColor(0x33F4ECDD) }
        progress = View(this).apply { setBackgroundColor(getColor(R.color.accent)); pivotX = 0f; scaleX = 0f }
        track.addView(progress, FrameLayout.LayoutParams(-1, -1))
        root.addView(track, FrameLayout.LayoutParams(-1, dp(2f), Gravity.BOTTOM))

        hint = Ui.bodyText(this, "Swipe up  ⌃", 13f, muted = true).apply {
            letterSpacing = 0.12f; gravity = Gravity.CENTER
        }
        root.addView(hint, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        if (!Lean.on(this)) bob = ObjectAnimator.ofFloat(hint, View.TRANSLATION_Y, 0f, -dp(6f).toFloat()).apply {
            duration = 1100; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE; start()
        }

        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            top.setPadding(dp(20f) + bars.left, dp(14f) + bars.top, dp(16f) + bars.right, 0)
            (playButton.layoutParams as FrameLayout.LayoutParams).apply { setMargins(0, 0, dp(20f) + bars.right, dp(28f) + bars.bottom) }
            playButton.requestLayout()
            (hint.layoutParams as FrameLayout.LayoutParams).bottomMargin = dp(14f) + bars.bottom
            hint.requestLayout()
            bottomInset = bars.bottom
            insets
        }
        return root
    }

    private var bottomInset = 0

    private fun roundIcon(icon: Int, label: String, onClick: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon); contentDescription = label
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x590B0606); setStroke(dp(1f), getColor(R.color.rule)) }
        setColorFilter(getColor(R.color.ink))
        setOnClickListener { onClick() }
    }

    private fun openLibrary() = startActivity(Intent(this, ManageActivity::class.java))

    private fun immersive() {
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // ---------- pages ----------

    private fun recycler() = pager.getChildAt(0) as RecyclerView
    private fun holderAt(position: Int) = if (position < 0) null else recycler().findViewHolderForAdapterPosition(position) as? PageHolder
    private fun selectedHolder() = holderAt(pager.currentItem)

    private inner class FeedAdapter : RecyclerView.Adapter<PageHolder>() {
        override fun getItemCount() = FeedOrder.pageCount(items.size)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = PageHolder(FrameLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        })
        override fun onBindViewHolder(holder: PageHolder, position: Int) = holder.bind(itemAt(position), position)
        override fun onViewRecycled(holder: PageHolder) = holder.unbind()
    }

    private inner class PageHolder(val root: FrameLayout) : RecyclerView.ViewHolder(root) {
        val backdrop = ImageView(this@FeedActivity).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0.55f }
        val media = FrameLayout(this@FeedActivity)
        val picture = ImageView(this@FeedActivity)
        var texture: TextureView? = null
        val words = Ui.vertical(this@FeedActivity)
        val eyebrow = Ui.bodyText(this@FeedActivity, "", 11f).apply { letterSpacing = 0.22f; setTextColor(getColor(R.color.accent)) }
        val title = Ui.heading(this@FeedActivity, "", 30f).apply { setShadowLayer(16f, 0f, 2f, 0xCC000000.toInt()); maxLines = 3 }
        val sub = Ui.bodyText(this@FeedActivity, "", 14f, muted = true)
        var item: FeedItem? = null
        var surface: Surface? = null
        private var drift: ObjectAnimator? = null
        private var position = -1

        init {
            root.setTag(R.id.feed_holder, this)
            root.setBackgroundColor(getColor(R.color.void_black))
            root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
            media.addView(picture, FrameLayout.LayoutParams(-1, -1))
            root.addView(media, FrameLayout.LayoutParams(-1, -1))
            root.addView(View(this@FeedActivity).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0, 0x330B0606, 0xE60B0606.toInt()))
            }, FrameLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.5f).toInt(), Gravity.BOTTOM))
            root.addView(View(this@FeedActivity).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x990B0606.toInt(), 0))
            }, FrameLayout.LayoutParams(-1, dp(140f), Gravity.TOP))
            words.addView(eyebrow); words.addView(title, Ui.lp(top = dp(6f))); words.addView(sub, Ui.lp(top = dp(6f)))
            root.addView(words, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                setMargins(dp(24f), 0, dp(96f), dp(44f))
            })
            val taps = GestureDetector(this@FeedActivity, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent) = true
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean { togglePlay(); return true }
            })
            root.setOnTouchListener { _, ev -> taps.onTouchEvent(ev); true }
        }

        fun bind(it: FeedItem, pos: Int) {
            item = it; position = pos
            picture.setImageDrawable(null); backdrop.setImageDrawable(null)
            picture.scaleX = 1f; picture.scaleY = 1f; picture.alpha = 1f
            (words.layoutParams as FrameLayout.LayoutParams).bottomMargin = dp(44f) + bottomInset
            when (it) {
                is FeedItem.Picture -> bindPicture(it)
                is FeedItem.Video -> bindVideo(it)
            }
        }

        private fun bindPicture(p: FeedItem.Picture) {
            removeTexture()
            val chant = FeedOrder.chantForPicture(pairings.chantFor(p.id), chants.ids())
            eyebrow.text = if (p.id == images.main()) "HER MAIN PICTURE" else "DEVI"
            title.text = chant?.let { chants.title(it) } ?: "Devi"
            sub.text = if (chant != null) "Tap to hear her chant" else "Pair a chant in the library"
            load("pic:${p.id}") {
                val dm = resources.displayMetrics
                ImageLoading.decodeForDisplay(images.file(p.id), dm.widthPixels, dm.heightPixels)?.let { soft ->
                    cache.put("blur:${p.id}", Glow.of(soft))
                    soft.copy(Bitmap.Config.HARDWARE, false) ?: soft // uploaded once, here, not mid-swipe
                }
            }.let { key -> deliver(key, p) { bmp ->
                val screen = resources.displayMetrics.run { heightPixels.toFloat() / widthPixels }
                val ratio = bmp.height.toFloat() / bmp.width
                picture.scaleType = if (abs(ratio - screen) / screen < 0.22f) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
                picture.setImageBitmap(bmp); backdrop.setImageBitmap(if (lean) null else cache.get("blur:${p.id}"))
                if (lean) picture.alpha = 1f else fadeIn(picture)
            } }
        }

        private fun bindVideo(v: FeedItem.Video) {
            eyebrow.text = "VIDEO"
            title.text = chants.title(v.chantId)
            sub.text = "Plays on with the screen off"
            val tv = texture ?: TextureView(this@FeedActivity).also { t ->
                texture = t
                media.addView(t, 0, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
                t.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        surface = Surface(st)
                        if (position == pager.currentItem && item is FeedItem.Video) ChantService.attachSurface(surface)
                    }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        if (position == pager.currentItem) ChantService.attachSurface(null)
                        surface?.release(); surface = null; return true
                    }
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
            showPoster()
            val key = load("frame:${v.chantId}") {
                MediaMetadataRetriever().use { r ->
                    ChantSource.setOn(this@FeedActivity, v.chantId, r)
                    val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    main.post { if (item == v) sub.text = "Video · %d:%02d · plays on with the screen off".format(ms / 60000, (ms / 1000) % 60) }
                    val dm = resources.displayMetrics
                    r.getScaledFrameAtTime(minOf(4_000_000L, ms * 500L), MediaMetadataRetriever.OPTION_CLOSEST_SYNC, dm.widthPixels, dm.widthPixels)
                }?.let { soft -> cache.put("blur:${v.chantId}", Glow.of(soft)); soft }
            }
            deliver(key, v) { bmp ->
                picture.scaleType = ImageView.ScaleType.FIT_CENTER
                picture.setImageBitmap(bmp); backdrop.setImageBitmap(if (lean) null else cache.get("blur:${v.chantId}"))
                fitTexture(tv, bmp.width, bmp.height)
            }
        }

        private fun fitTexture(tv: TextureView, vw: Int, vh: Int) {
            val dm = resources.displayMetrics
            val scale = minOf(dm.widthPixels.toFloat() / vw, dm.heightPixels.toFloat() / vh)
            tv.layoutParams = (tv.layoutParams as FrameLayout.LayoutParams).apply {
                width = (vw * scale).toInt(); height = (vh * scale).toInt(); gravity = Gravity.CENTER
            }
        }

        fun showPoster() { picture.animate().cancel(); picture.alpha = 1f; picture.visibility = View.VISIBLE }
        fun hidePoster() { picture.animate().alpha(0f).setDuration(350).start() }

        private fun removeTexture() {
            texture?.let { media.removeView(it) }; texture = null
            surface?.release(); surface = null
        }

        fun unbind() { stopDrift(); item = null; position = -1 }

        /** A slow breath of movement on her picture while you stay on it. */
        fun startDrift() {
            if (item !is FeedItem.Picture || lean) return
            drift?.cancel()
            drift = ObjectAnimator.ofPropertyValuesHolder(picture,
                PropertyValuesHolder.ofFloat(View.SCALE_X, picture.scaleX, 1.07f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, picture.scaleY, 1.07f)).apply {
                duration = 16_000; interpolator = LinearInterpolator()
                repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE; start()
            }
        }

        fun stopDrift() { drift?.cancel(); drift = null }

        private fun fadeIn(v: View) { v.alpha = 0f; v.animate().alpha(1f).setDuration(280).setInterpolator(DecelerateInterpolator()).start() }
    }

    // ---------- bitmaps ----------

    private fun load(key: String, make: () -> Bitmap?): String {
        if (cache.get(key) == null && !worker.isShutdown) {
            if (pending.add(key)) worker.execute {
                val bmp = try { make() } catch (_: Exception) { null }
                main.post {
                    pending.remove(key)
                    if (bmp != null) cache.put(key, bmp)
                    waiting.remove(key)?.forEach { it(bmp) }
                }
            }
        }
        return key
    }

    private val pending = HashSet<String>()
    private val waiting = HashMap<String, MutableList<(Bitmap?) -> Unit>>()

    private fun PageHolder.deliver(key: String, forItem: FeedItem, use: (Bitmap) -> Unit) {
        val ready = cache.get(key)
        if (ready != null) { use(ready); return }
        waiting.getOrPut(key) { mutableListOf() } += { bmp -> if (bmp != null && item == forItem) use(bmp) }
    }
}
