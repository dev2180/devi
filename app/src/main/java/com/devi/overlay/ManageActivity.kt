package com.devi.overlay

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ext.SdkExtensions
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** Library: her main picture and chant, then her pictures and the chants. The feed is home. */
class ManageActivity : Activity() {

    private lateinit var store: ImageStore
    private lateinit var chants: ChantStore
    private lateinit var pairings: Pairings
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val thumbs = HashMap<String, Bitmap?>()
    private val durations = HashMap<String, String>()

    private lateinit var frameImage: ImageView
    private lateinit var frameEmpty: TextView
    private lateinit var nowBar: LinearLayout
    private lateinit var nowTitle: TextView
    private lateinit var nowSub: TextView
    private lateinit var nowButton: ImageButton
    private lateinit var riseLine: View
    private lateinit var setupNudge: View
    private lateinit var picturesBox: LinearLayout
    private lateinit var chantsBox: LinearLayout
    private var shownMainId: String? = null

    private val tick = object : Runnable {
        override fun run() { refreshNowPlaying(); main.postDelayed(this, 120) }
    }

    private fun dp(v: Float) = Ui.dp(this, v)

    private lateinit var ytStatus: TextView
    private val ytListener: (YtDownloader.State) -> Unit = { showYt(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ImageStore.forContext(this)
        chants = ChantStore.forContext(this)
        pairings = Pairings.forContext(this)
        setContentView(buildUi())
        YtDownloader.listen(ytListener)
        showYt(YtDownloader.state)
        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** "Share" from the YouTube app straight to Devi. */
    private fun handleShare(i: Intent?) {
        if (i?.action != Intent.ACTION_SEND) return
        val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
        i.action = null
        val url = YouTubeLink.find(text)
        if (url == null) {
            AlertDialog.Builder(this).setMessage("That isn't a YouTube video link.").setPositiveButton("OK", null).show()
        } else confirmYouTube(url)
    }

    private fun askYouTube() {
        if (YtDownloader.busy) return
        val clip = (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).primaryClip
            ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        val field = android.widget.EditText(this).apply {
            hint = "Paste a YouTube link"; setSingleLine()
            setText(YouTubeLink.find(clip) ?: "")
        }
        val wrap = Ui.vertical(this).apply { setPadding(dp(24f), dp(8f), dp(24f), 0); addView(field) }
        AlertDialog.Builder(this).setTitle("Add a chant from YouTube")
            .setMessage("The full video is saved in the best quality YouTube has. Big videos take a while; it keeps going if you leave Devi.")
            .setView(wrap)
            .setPositiveButton("Download") { _, _ ->
                val url = YouTubeLink.find(field.text.toString())
                if (url == null) AlertDialog.Builder(this).setMessage("That isn't a YouTube video link.").setPositiveButton("OK", null).show()
                else YtDownloader.start(this, url)
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun confirmYouTube(url: String) {
        if (YtDownloader.busy) return
        AlertDialog.Builder(this).setTitle("Add this as a chant?").setMessage(url)
            .setPositiveButton("Download") { _, _ -> YtDownloader.start(this, url) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showYt(s: YtDownloader.State) {
        if (!::ytStatus.isInitialized) return
        ytStatus.visibility = if (s is YtDownloader.State.Idle) View.GONE else View.VISIBLE
        ytStatus.text = when (s) {
            is YtDownloader.State.Working -> if (s.percent > 0) "${s.note}… ${s.percent}%" else "${s.note}…"
            is YtDownloader.State.Done -> "Added “${s.title}”."
            is YtDownloader.State.Failed -> s.reason
            YtDownloader.State.Idle -> ""
        }
        if (s is YtDownloader.State.Done) { refreshAll() }
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
        main.post(tick)
    }

    private val rebuildChants = Runnable { if (!isDestroyed) buildChants() }

    override fun onPause() { main.removeCallbacks(tick); main.removeCallbacks(rebuildChants); super.onPause() }
    override fun onDestroy() { YtDownloader.unlisten(ytListener); worker.shutdownNow(); super.onDestroy() }

    // ---------- layout ----------

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(getColor(R.color.ground)); isFillViewport = true }
        val col = Ui.vertical(this).apply { setPadding(dp(20f), dp(16f), dp(20f), dp(28f)) }
        scroll.addView(col)
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            col.setPadding(dp(20f) + bars.left, dp(16f) + bars.top, dp(20f) + bars.right, dp(28f) + bars.bottom)
            insets
        }

        // Top bar: the wordmark and one action.
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_back); contentDescription = "Back to her feed"
            background = null; setColorFilter(getColor(R.color.ink))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44f), dp(44f)).apply { marginStart = -dp(10f) })
        top.addView(Ui.heading(this, "Library", 30f), LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_tune); contentDescription = "How she appears"
            background = Ui.pill(this@ManageActivity, R.color.ground, R.color.rule)
            setColorFilter(getColor(R.color.ink))
            setOnClickListener { startActivity(Intent(this@ManageActivity, ShortcutsActivity::class.java)) }
        }, LinearLayout.LayoutParams(dp(44f), dp(44f)))
        col.addView(top)

        // Gentle nudge until the shortcut can work.
        setupNudge = Ui.linkButton(this, "Set up how she appears").apply {
            setTextColor(getColor(R.color.accent))
            setOnClickListener { startActivity(Intent(this@ManageActivity, ShortcutsActivity::class.java)) }
        }
        col.addView(setupNudge, Ui.lp(top = dp(4f)))

        // The shrine frame with the now-playing bar.
        val frame = FrameLayout(this).apply { setBackgroundColor(getColor(R.color.void_black)) }
        Ui.roundClip(frame, dp(28f).toFloat())
        frameImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = "Her main picture"
            setOnClickListener { if (store.ids().isNotEmpty()) startActivity(ViewerActivity.intent(this@ManageActivity)) }
        }
        frameEmpty = Ui.bodyText(this, "Add a picture of Devi to begin.", 16f, muted = true).apply { gravity = Gravity.CENTER }
        frame.addView(frameImage, FrameLayout.LayoutParams(-1, -1))
        frame.addView(frameEmpty, FrameLayout.LayoutParams(-1, -1))
        nowBar = buildNowBar()
        frame.addView(nowBar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        val frameH = (resources.displayMetrics.heightPixels * 0.46f).toInt()
        col.addView(frame, Ui.lp(h = frameH, top = dp(12f)))

        col.addView(Ui.heading(this, "Her pictures", 22f), Ui.lp(top = dp(28f)))
        picturesBox = Ui.vertical(this, 8f)
        col.addView(picturesBox, Ui.lp(top = dp(12f)))

        col.addView(Ui.heading(this, "Chants", 22f), Ui.lp(top = dp(28f)))
        chantsBox = Ui.vertical(this)
        col.addView(chantsBox, Ui.lp(top = dp(8f)))
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        actions.addView(Ui.ghostButton(this, "Add chants").apply { setOnClickListener { pickChants() } })
        actions.addView(Ui.ghostButton(this, "From YouTube").apply { setOnClickListener { askYouTube() } },
            LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8f) })
        col.addView(actions, Ui.lp(top = dp(12f)))
        // Official chants live on Sadhguru's YouTube; share one back to Devi and it downloads.
        col.addView(Ui.linkButton(this, "Find official chants on YouTube  →").apply {
            setOnClickListener {
                val q = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode("Linga Bhairavi Sounds of Isha"))
                try { startActivity(Intent(Intent.ACTION_VIEW, q)) } catch (_: Exception) { }
            }
        }, Ui.lp(top = dp(4f)))
        ytStatus = Ui.bodyText(this, "", 13f, muted = true).apply { visibility = View.GONE }
        col.addView(ytStatus, Ui.lp(top = dp(8f)))
        return scroll
    }

    private fun buildNowBar(): LinearLayout {
        val bar = Ui.vertical(this).apply {
            setBackgroundColor(getColor(R.color.scrim)); setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val texts = Ui.vertical(this)
        nowTitle = Ui.heading(this, "", 20f).apply { maxLines = 1 }
        nowSub = Ui.bodyText(this, "", 13f, muted = true)
        texts.addView(nowTitle); texts.addView(nowSub)
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        nowButton = ImageButton(this).apply {
            background = Ui.pill(this@ManageActivity, R.color.accent); setColorFilter(getColor(R.color.accent_ink))
            contentDescription = "Play or pause"
            setOnClickListener { onNowButton() }
        }
        row.addView(nowButton, LinearLayout.LayoutParams(dp(48f), dp(48f)))
        bar.addView(row)
        riseLine = View(this).apply { setBackgroundColor(getColor(R.color.accent)); pivotX = 0f }
        val track = FrameLayout(this).apply { setBackgroundColor(getColor(R.color.rule)) }
        track.addView(riseLine, FrameLayout.LayoutParams(-1, -1))
        bar.addView(track, Ui.lp(h = dp(2f), top = dp(10f)))
        return bar
    }

    // ---------- state ----------

    private fun refreshAll() {
        val mainId = store.main()
        shownMainId = mainId
        frameEmpty.visibility = if (mainId == null) View.VISIBLE else View.GONE
        frameImage.setImageDrawable(null)
        if (mainId != null) {
            val file = store.file(mainId)
            val w = resources.displayMetrics.widthPixels
            worker.execute {
                val bmp = ImageLoading.decodeForDisplay(file, w, w * 2)
                main.post { if (!isDestroyed && shownMainId == mainId) frameImage.setImageBitmap(bmp) }
            }
        }
        setupNudge.visibility = if (Setup.ready(this)) View.GONE else View.VISIBLE
        DeviListenerService.sync(this)
        DndKeeper.restoreIfLeftOver(this)
        buildPictures()
        buildChants()
        refreshNowPlaying()
    }

    /** The chant the bar is about: the one playing, else the main picture's own. */
    private fun barChant(): String? = ChantService.currentChantId ?: shownMainId?.let { pairings.chantFor(it) }

    private fun refreshNowPlaying() {
        val id = barChant()?.takeIf { it in chants.ids() }
        nowBar.visibility = if (id == null) View.GONE else View.VISIBLE
        if (id == null) return
        val playing = ChantService.isPlaying && ChantService.currentChantId == id
        nowTitle.text = chants.title(id)
        nowSub.text = when {
            playing && ChantService.rising -> "Rising softly"
            playing -> "Playing"
            else -> "Her chant"
        }
        nowButton.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        riseLine.scaleX = if (ChantService.currentChantId == id) ChantService.gain.coerceIn(0f, 1f) else 0f
    }

    private fun onNowButton() {
        val id = barChant() ?: return
        if (ChantService.currentChantId == id) ChantService.pauseOrResume(this) else playChant(id)
    }

    private fun playChant(id: String) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFY)
        }
        ChantService.play(this, id, startedByViewer = false)
    }

    // ---------- pictures ----------

    private fun buildPictures() {
        picturesBox.removeAllViews()
        val ids = store.ids()
        val mainId = store.main()
        val cells = ids.map { it as String? } + listOf(null) // null = the add tile
        val side = (resources.displayMetrics.widthPixels - dp(40f) - dp(16f)) / 3
        cells.chunked(3).forEach { rowIds ->
            val row = LinearLayout(this)
            rowIds.forEachIndexed { i, id ->
                val tile = if (id == null) addTile() else pictureTile(id, id == mainId)
                row.addView(tile, LinearLayout.LayoutParams(side, side).apply { if (i > 0) marginStart = dp(8f) })
            }
            picturesBox.addView(row)
        }
    }

    private fun addTile(): View = FrameLayout(this).apply {
        background = Ui.well(this@ManageActivity); contentDescription = "Add pictures"
        addView(ImageView(context).apply { setImageResource(R.drawable.ic_plus); setColorFilter(getColor(R.color.ink)) },
            FrameLayout.LayoutParams(dp(24f), dp(24f), Gravity.CENTER))
        setOnClickListener { pickImages() }
    }

    private fun pictureTile(id: String, isMain: Boolean): View {
        val tile = FrameLayout(this).apply { setBackgroundColor(getColor(R.color.ground_2)) }
        Ui.roundClip(tile, dp(16f).toFloat())
        val img = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; tag = id }
        tile.addView(img, FrameLayout.LayoutParams(-1, -1))
        if (isMain) {
            tile.foreground = GradientDrawable().apply { cornerRadius = dp(16f).toFloat(); setStroke(dp(1.5f), getColor(R.color.accent)) }
            tile.addView(TextView(this).apply {
                text = "Main"; typeface = Ui.bodyMedium(context); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(getColor(R.color.accent_ink)); background = Ui.pill(context, R.color.accent)
                setPadding(dp(8f), dp(2f), dp(8f), dp(2f))
            }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(6f), 0, 0, dp(6f)) })
        }
        if (thumbs.containsKey(id)) img.setImageBitmap(thumbs[id]) else {
            val size = dp(120f)
            worker.execute {
                val bmp = ImageLoading.decodeThumb(store.file(id), size)
                main.post { thumbs[id] = bmp; if (img.tag == id) img.setImageBitmap(bmp) }
            }
        }
        tile.contentDescription = if (isMain) "Main picture" else "Picture"
        tile.setOnClickListener { store.setMain(id); refreshAll() }
        tile.setOnLongClickListener { pictureMenu(id); true }
        return tile
    }

    private fun pictureMenu(id: String) {
        val paired = pairings.chantFor(id)?.let { chants.title(it) }
        val items = arrayOf("Make main", if (paired != null) "Chant: $paired" else "Pair a chant", "Remove")
        AlertDialog.Builder(this).setItems(items) { _, which ->
            when (which) {
                0 -> { store.setMain(id); refreshAll() }
                1 -> pairChant(id)
                2 -> AlertDialog.Builder(this).setMessage("Remove this picture?")
                    .setPositiveButton("Remove") { _, _ -> store.remove(id); pairings.forgetImage(id); thumbs.remove(id); refreshAll() }
                    .setNegativeButton("Cancel", null).show()
            }
        }.show()
    }

    private fun pairChant(imageId: String) {
        val ids = chants.ids()
        if (ids.isEmpty()) { AlertDialog.Builder(this).setMessage("Add a chant first.").setPositiveButton("OK", null).show(); return }
        val labels = (ids.map { chants.title(it) } + "None").toTypedArray()
        val current = pairings.chantFor(imageId)?.let { ids.indexOf(it) }?.takeIf { it >= 0 } ?: ids.size
        AlertDialog.Builder(this).setTitle("Chant for this picture")
            .setSingleChoiceItems(labels, current) { d, which ->
                pairings.pair(imageId, ids.getOrNull(which)); d.dismiss(); refreshAll()
            }.setNegativeButton("Cancel", null).show()
    }

    // ---------- chants ----------

    private fun buildChants() {
        chantsBox.removeAllViews()
        val ids = chants.ids()
        if (ids.isEmpty()) {
            chantsBox.addView(Ui.bodyText(this, "Add a chant, and it will rise softly when she appears.", 15f, muted = true), Ui.lp(top = dp(4f)))
            return
        }
        val mainChant = shownMainId?.let { pairings.chantFor(it) }
        ids.forEach { id ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(52f)
                background = GradientDrawable().apply { setColor(0) }
                setPadding(0, dp(10f), 0, dp(10f))
            }
            val playing = ChantService.currentChantId == id
            row.addView(Ui.bodyText(this, chants.title(id), 16f).apply {
                maxLines = 2; if (playing) setTextColor(getColor(R.color.accent))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            val isVideo = ChantStore.isVideo(id)
            val meta = Ui.bodyText(this, when {
                id == mainChant -> "Paired with main"
                isVideo -> listOfNotNull("Video", durations[id]?.takeIf { it.isNotEmpty() }).joinToString(" · ")
                else -> durations[id] ?: ""
            }, 13f, muted = true)
            row.addView(meta, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12f) })
            if (id != mainChant && durations[id] == null) loadDuration(id, meta)
            if (isVideo) row.setOnClickListener { VideoActivity.open(this, id) } else row.setOnClickListener {
                if (ChantService.currentChantId == id) ChantService.pauseOrResume(this) else playChant(id)
                main.removeCallbacks(rebuildChants); main.postDelayed(rebuildChants, 250)
            }
            row.setOnLongClickListener {
                AlertDialog.Builder(this).setMessage("Remove this chant?")
                    .setPositiveButton("Remove") { _, _ ->
                        if (ChantService.currentChantId == id) ChantService.stop(this, fade = false)
                        chants.remove(id); pairings.forgetChant(id); refreshAll()
                    }.setNegativeButton("Cancel", null).show()
                true
            }
            chantsBox.addView(View(this).apply { setBackgroundColor(getColor(R.color.rule)) }, Ui.lp(h = dp(1f)))
            chantsBox.addView(row)
        }
    }

    private fun loadDuration(id: String, into: TextView) {
        if (worker.isShutdown) return
        worker.execute {
            val text = try {
                MediaMetadataRetriever().use { r ->
                    ChantSource.setOn(this, id, r)
                    val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
                }
            } catch (_: Exception) { "" }
            main.post {
                durations[id] = text
                into.text = if (ChantStore.isVideo(id)) listOfNotNull("Video", text.takeIf { it.isNotEmpty() }).joinToString(" · ") else text
            }
        }
    }

    private fun pickChants() {
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "audio/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }, REQUEST_CHANTS)
    }

    private fun addChants(uris: List<Uri>) {
        worker.execute {
            var failed = 0
            for (uri in uris) {
                val name = displayName(uri) ?: "Chant"
                val ext = name.substringAfterLast('.', "").lowercase().takeIf { it.length in 2..4 } ?: "m4a"
                val title = name.substringBeforeLast('.').trim()
                val id = try { contentResolver.openInputStream(uri)?.use { chants.add(it, ext, title) } } catch (_: Exception) { null }
                if (id == null) failed++
            }
            main.post {
                if (isDestroyed) return@post
                refreshAll()
                if (failed > 0) AlertDialog.Builder(this).setMessage(
                    if (failed == 1) "This chant couldn't be added." else "$failed chants couldn't be added.",
                ).setPositiveButton("OK", null).show()
            }
        }
    }

    private fun displayName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) { null }

    // ---------- adding pictures ----------

    private fun pickImages() {
        val photoPicker = Build.VERSION.SDK_INT >= 33 || SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 2
        val intent = if (photoPicker) {
            Intent(MediaStore.ACTION_PICK_IMAGES).apply { type = "image/*"; putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit()) }
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) }
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_PICK)
    }

    @Deprecated("Platform Activity API; fine for this app")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        val uris = buildList {
            data.clipData?.let { clip -> for (i in 0 until clip.itemCount) add(clip.getItemAt(i).uri) }
            if (isEmpty()) data.data?.let { add(it) }
        }
        if (uris.isEmpty()) return
        when (requestCode) {
            REQUEST_PICK -> addPictures(uris)
            REQUEST_CHANTS -> addChants(uris)
        }
    }

    private fun addPictures(uris: List<Uri>) {
        val bounds = windowManager.maximumWindowMetrics.bounds
        val screenW = minOf(bounds.width(), bounds.height())
        val screenH = maxOf(bounds.width(), bounds.height())
        worker.execute {
            val notes = linkedSetOf<FitNote>()
            var failed = 0
            for (uri in uris) {
                val id = try { contentResolver.openInputStream(uri)?.use { store.add(it, imageExtension(uri)) } } catch (_: Exception) { null }
                val size = id?.let { ImageLoading.readSize(store.file(it)) }
                if (id == null || size == null) { id?.let { store.remove(it) }; failed++; continue }
                notes += FitCheck.evaluate(size.first, size.second, screenW, screenH)
            }
            main.post {
                if (isDestroyed) return@post
                refreshAll()
                val lines = mutableListOf<String>()
                if (failed > 0) lines += if (failed == 1) "This picture can't be opened, so it wasn't added." else "$failed pictures can't be opened, so they weren't added."
                notes.forEach { lines += FitCheck.message(it) }
                if (lines.isNotEmpty()) AlertDialog.Builder(this).setMessage(lines.joinToString("\n\n")).setPositiveButton("OK", null).show()
            }
        }
    }

    private fun imageExtension(uri: Uri): String = when (contentResolver.getType(uri)?.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic", "image/heif" -> "heic"
        "image/gif" -> "gif"
        else -> "img"
    }

    companion object {
        private const val REQUEST_PICK = 1
        private const val REQUEST_CHANTS = 2
        private const val REQUEST_NOTIFY = 3
    }
}
