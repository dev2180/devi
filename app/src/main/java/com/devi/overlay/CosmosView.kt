package com.devi.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The eye break: deep space, drifting stars and a slowly turning galaxy, with Devi rising at its
 * heart. With a picture of the screen ([screen]) the screen itself first breaks into tiles that
 * spin away and become stars.
 */
class CosmosView(
    context: Context,
    private val screen: Bitmap?,
    private val her: Bitmap?,
    private val durationMs: Long,
    private val onDone: () -> Unit,
) : View(context) {

    private val rnd = Random(7)
    private var start = 0L
    private var closingAt = -1L
    private var done = false

    // ---------- paints ----------
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.ink); textAlign = Paint.Align.CENTER
        typeface = Ui.display(context); textSize = sp(30f); letterSpacing = -0.01f
    }
    private val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.muted); textAlign = Paint.Align.CENTER
        typeface = Ui.body(context); textSize = sp(14f); letterSpacing = 0.04f
    }

    private fun sp(v: Float) = v * context.resources.displayMetrics.scaledDensity
    private fun dpf(v: Float) = v * context.resources.displayMetrics.density

    // ---------- stars ----------
    private class Star(var angle: Float, var dist: Float, val speed: Float, val size: Float, val phase: Float, val tint: Int)
    private val stars = ArrayList<Star>()
    private val starTints = intArrayOf(Color.WHITE, 0xFFFFE9C4.toInt(), 0xFFC9D4FF.toInt(), 0xFFF0C46A.toInt())

    // ---------- galaxy ----------
    private class Ring(val points: Array<FloatArray>, val spin: Float)
    private val rings = ArrayList<Ring>()
    private val galaxyTints = intArrayOf(0xFFF0C46A.toInt(), 0xFFE8735A.toInt(), 0xFFB98CFF.toInt(), 0xFFFFF4E0.toInt())

    // ---------- tiles (dissolve) ----------
    private class Tile(val src: Rect, val cx: Float, val cy: Float, val vx: Float, val vy: Float, val spin: Float, val delay: Float, val color: Int)
    private val tiles = ArrayList<Tile>()
    private val tileDst = RectF()

    private var herDisc: Bitmap? = null
    private var built = false

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isClickable = true
        contentDescription = "Eye break. Rest your eyes."
    }

    private fun build(w: Int, h: Int) {
        built = true
        val diag = hypot(w.toFloat(), h.toFloat()) / 2
        repeat(420) {
            stars += Star(rnd.nextFloat() * 2 * PI.toFloat(), rnd.nextFloat() * diag, 0.015f + rnd.nextFloat() * 0.05f,
                dpf(0.6f + rnd.nextFloat() * 1.6f), rnd.nextFloat() * 6.28f, starTints[rnd.nextInt(starTints.size)])
        }
        // A barred spiral: two arms, a little scatter, inner rings turning faster than outer ones.
        val maxR = w * 0.62f
        val ringCount = 7
        for (r in 0 until ringCount) {
            val groups = Array(galaxyTints.size) { ArrayList<Float>() }
            val r0 = maxR * r / ringCount; val r1 = maxR * (r + 1) / ringCount
            repeat(260) {
                val rad = r0 + rnd.nextFloat() * (r1 - r0)
                val arm = rnd.nextInt(2) * PI.toFloat()
                val theta = arm + rad / maxR * 4.2f + (rnd.nextFloat() - 0.5f) * (0.55f + 0.6f * (1 - rad / maxR))
                val g = when { rad < maxR * 0.18f -> 3; rnd.nextFloat() < 0.5f -> 0; rnd.nextFloat() < 0.6f -> 1; else -> 2 }
                groups[g] += rad * cos(theta); groups[g] += rad * sin(theta) * 0.42f // tilted disc
            }
            rings += Ring(Array(groups.size) { groups[it].toFloatArray() }, 0.10f * (1.4f - r.toFloat() / ringCount))
        }
        screen?.let { buildTiles(it, w, h) }
        her?.let { herDisc = disc(it, (w * 0.56f).toInt()) }
    }

    private fun buildTiles(bmp: Bitmap, w: Int, h: Int) {
        val size = (w / 18f).toInt().coerceAtLeast(24)
        val sx = bmp.width.toFloat() / w; val sy = bmp.height.toFloat() / h
        val origin = w / 2f to h * 0.42f
        val far = hypot(w.toFloat(), h.toFloat())
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val src = Rect((x * sx).toInt(), (y * sy).toInt(), ((x + size) * sx).toInt().coerceAtMost(bmp.width), ((y + size) * sy).toInt().coerceAtMost(bmp.height))
                val cx = x + size / 2f; val cy = y + size / 2f
                val dx = cx - origin.first; val dy = cy - origin.second
                val d = hypot(dx, dy).coerceAtLeast(1f)
                val push = dpf(60f) + rnd.nextFloat() * dpf(140f)
                val color = bmp.getPixel(((src.left + src.right) / 2).coerceIn(0, bmp.width - 1), ((src.top + src.bottom) / 2).coerceIn(0, bmp.height - 1))
                tiles += Tile(src, cx, cy, dx / d * push, dy / d * push - dpf(40f), (rnd.nextFloat() - 0.5f) * 220f,
                    d / far * 1.3f + rnd.nextFloat() * 0.25f, color)
                x += size
            }
            y += size
        }
    }

    /** Her picture in a soft-edged circle. */
    private fun disc(picture: Bitmap, d: Int): Bitmap {
        // Pictures load as hardware bitmaps; this one is painted in software, so copy it first.
        val src = if (picture.config == Bitmap.Config.HARDWARE) picture.copy(Bitmap.Config.ARGB_8888, false) else picture
        val out = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val scale = max(d.toFloat() / src.width, d.toFloat() / src.height)
        val m = Matrix().apply { setScale(scale, scale); postTranslate((d - src.width * scale) / 2, (d - src.height * scale) / 2) }
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) } }
        c.drawCircle(d / 2f, d / 2f, d / 2f, p)
        val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(d / 2f, d / 2f, d / 2f, intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT), floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        c.drawRect(0f, 0f, d.toFloat(), d.toFloat(), mask)
        return out
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && elapsed() >= EyeBreakPolicy.SKIP_AFTER_MS) close()
        return true
    }

    fun close() { if (closingAt < 0) closingAt = SystemClock.uptimeMillis() }

    private fun elapsed() = if (start == 0L) 0L else SystemClock.uptimeMillis() - start

    private fun ramp(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)
    private fun ease(x: Float) = 1 - (1 - x) * (1 - x) * (1 - x)

    override fun onDraw(canvas: Canvas) {
        if (done) return
        val w = width; val h = height
        if (w == 0 || h == 0) return
        if (!built) build(w, h)
        if (start == 0L) start = SystemClock.uptimeMillis()
        val ms = elapsed()
        if (closingAt < 0 && ms >= durationMs) close()
        val t = ms / 1000f
        val out = if (closingAt < 0) 1f else 1f - ((SystemClock.uptimeMillis() - closingAt) / 1200f).coerceIn(0f, 1f)
        val dissolve = screen != null
        val skyIn = if (dissolve) ease(ramp(t, 0.3f, 2.2f)) else ease(ramp(t, 0f, 1.4f))
        val master = out

        // Deep space.
        val cx = w / 2f; val cy = h * 0.42f
        fill.shader = RadialGradient(cx, cy, max(w, h) * 0.8f, intArrayOf(0xFF1A0C24.toInt(), 0xFF0B0612.toInt(), 0xFF040208.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        fill.alpha = (255 * skyIn * master).toInt()
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
        fill.shader = null

        // The screen breaking into stars.
        if (dissolve && t < 3.4f) drawTiles(canvas, t, master)

        // Nebulae: slow, soft clouds of crimson, violet and gold.
        val neb = skyIn * master
        nebula(canvas, cx + sin(t * 0.07f) * w * 0.18f, cy - h * 0.12f, w * 0.75f, 0x5A7A0E1C, neb)
        nebula(canvas, cx - w * 0.25f, cy + h * 0.2f + cos(t * 0.05f) * h * 0.05f, w * 0.7f, 0x46351A6A, neb)
        nebula(canvas, cx + w * 0.3f, cy + h * 0.05f, w * 0.5f, 0x2E8A6418, neb)

        // Stars drift gently outward, twinkling.
        val diag = hypot(w.toFloat(), h.toFloat()) / 2
        for (s in stars) {
            s.dist *= 1f + s.speed / 60f
            if (s.dist > diag) { s.dist = rnd.nextFloat() * diag * 0.15f; s.angle = rnd.nextFloat() * 6.28f }
            val tw = 0.55f + 0.45f * sin(t * 1.7f + s.phase)
            dot.color = s.tint
            dot.alpha = (255 * tw * skyIn * master * min(1f, s.dist / (diag * 0.1f))).toInt()
            dot.strokeWidth = s.size * (0.6f + s.dist / diag)
            canvas.drawPoint(cx + cos(s.angle) * s.dist, cy + sin(s.angle) * s.dist, dot)
        }

        // The galaxy, turning.
        val gIn = ease(ramp(t, if (dissolve) 1.6f else 0.9f, 4.5f)) * master
        if (gIn > 0f) {
            fill.shader = RadialGradient(cx, cy, w * 0.34f, intArrayOf(0xCCFFE2A8.toInt(), 0x55E8735A, 0x00000000), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
            fill.alpha = (255 * gIn).toInt()
            canvas.drawCircle(cx, cy, w * 0.34f, fill)
            fill.shader = null
            canvas.save(); canvas.translate(cx, cy); canvas.rotate(-18f)
            for (ring in rings) {
                canvas.save(); canvas.rotate(t * ring.spin * 57.3f)
                ring.points.forEachIndexed { g, pts ->
                    dot.color = galaxyTints[g]; dot.strokeWidth = dpf(if (g == 3) 1.6f else 1.2f)
                    dot.alpha = (255 * gIn * 0.85f).toInt()
                    canvas.drawPoints(pts, dot)
                }
                canvas.restore()
            }
            canvas.restore()
        }

        // Devi rises at the heart of it, breathing.
        herDisc?.let { d ->
            val a = ease(ramp(t, if (dissolve) 3.2f else 2.4f, 6.5f)) * master
            if (a > 0f) {
                val breathe = 1f + 0.018f * sin(t * 2 * PI.toFloat() / 6f)
                val r = d.width / 2f * breathe
                fill.shader = RadialGradient(cx, cy, r * 1.5f, intArrayOf(0x66F0C46A, 0x22F0C46A, 0x00000000), floatArrayOf(0.55f, 0.8f, 1f), Shader.TileMode.CLAMP)
                fill.alpha = (255 * a).toInt()
                canvas.drawCircle(cx, cy, r * 1.5f, fill)
                fill.shader = null
                tilePaint.alpha = (255 * a).toInt()
                tileDst.set(cx - r, cy - r, cx + r, cy + r)
                canvas.drawBitmap(d, null, tileDst, tilePaint)
            }
        }

        // Words.
        val wa = ease(ramp(t, 4.5f, 6.5f)) * master
        if (wa > 0f) {
            val baseY = h * 0.78f
            title.alpha = (255 * wa).toInt()
            canvas.drawText("Rest your eyes", cx, baseY, title)
            small.alpha = (220 * wa).toInt()
            val left = ((durationMs - ms) / 1000).coerceAtLeast(0)
            canvas.drawText("Look at something far away  ·  $left", cx, baseY + sp(30f), small)
            if (ms >= EyeBreakPolicy.SKIP_AFTER_MS) {
                small.alpha = (140 * wa).toInt()
                canvas.drawText("Tap to return", cx, h - dpf(48f), small)
            }
        }

        if (closingAt >= 0 && out <= 0f) { done = true; onDone(); return }
        postInvalidateOnAnimation()
    }

    private fun nebula(c: Canvas, x: Float, y: Float, r: Float, color: Int, a: Float) {
        if (a <= 0f) return
        fill.shader = RadialGradient(x, y, r, intArrayOf(color, color and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        fill.alpha = (255 * a).toInt()
        c.drawCircle(x, y, r, fill)
        fill.shader = null
    }

    private fun drawTiles(canvas: Canvas, t: Float, master: Float) {
        val bmp = screen ?: return
        val life = 1.5f
        for (tile in tiles) {
            val local = ((t - 0.15f - tile.delay) / life)
            if (local >= 1f) {
                // Now a star where it came to rest.
                val px = tile.cx + tile.vx * 1.15f; val py = tile.cy + tile.vy * 1.15f
                dot.color = Color.WHITE; dot.alpha = (200 * master * (0.6f + 0.4f * sin(t * 2.3f + tile.delay * 9))).toInt()
                dot.strokeWidth = dpf(1.3f)
                canvas.drawPoint(px, py, dot)
                continue
            }
            val p = ease(local.coerceAtLeast(0f))
            val scale = 1f - 0.94f * p
            val x = tile.cx + tile.vx * p * 1.15f
            val y = tile.cy + tile.vy * p * 1.15f
            val half = (tile.src.width().toFloat() / bmp.width * width) / 2 * scale
            val halfH = (tile.src.height().toFloat() / bmp.height * height) / 2 * scale
            canvas.save()
            canvas.rotate(tile.spin * p, x, y)
            tileDst.set(x - half, y - halfH, x + half, y + halfH)
            tilePaint.alpha = (255 * master * (1f - 0.3f * p)).toInt()
            canvas.drawBitmap(bmp, tile.src, tileDst, tilePaint)
            if (p > 0.55f) { // warming into starlight
                fill.color = tile.color; fill.alpha = (255 * master * ramp(p, 0.55f, 1f)).toInt()
                canvas.drawRect(tileDst, fill)
                fill.color = Color.WHITE; fill.alpha = (200 * master * ramp(p, 0.7f, 1f)).toInt()
                canvas.drawRect(tileDst, fill)
            }
            canvas.restore()
        }
    }
}
