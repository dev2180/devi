package com.devi.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

/**
 * The little how-to scene beside each shortcut: a drawn phone, a thumb pressing the buttons
 * (they sink and flash gold, a ripple spreads), chips lighting in step, the screen waking to her.
 * Driven entirely by [DemoTimeline]; drawn in the mockup's 160x210 design box.
 */
class HowToDemoView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var mode: DemoMode = DemoMode.VOLUME
        set(v) { field = v; invalidate() }
    var keys: List<VolumeKey> = ComboPreset.UP_DOWN_UP.keys
        set(v) { field = v; invalidate() }
    var active: Boolean = true
        set(v) { field = v; syncAnimator(); invalidate() }
    var her: Bitmap? = null
        set(v) { field = v; invalidate() }

    private var tMs = STILL_MS
    private var animator: ValueAnimator? = null

    private fun c(id: Int) = context.getColor(id)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 9f
        typeface = try { resources.getFont(R.font.geist_medium) } catch (_: Exception) { Typeface.DEFAULT }
    }
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val glassClip = Path().apply { addRoundRect(RectF(48.5f, 21.5f, 111.5f, 160.5f), 11.5f, 11.5f, Path.Direction.CW) }

    // Thumb, drawn pointing up with its tip at (0, 2), from the mockup SVG.
    private val skin = Path().apply { moveTo(-18f, 70f); cubicTo(-20f, 40f, -14f, 8f, 0f, 2f); cubicTo(12f, -2f, 18f, 10f, 16f, 30f); cubicTo(14f, 48f, 8f, 64f, 4f, 76f); close() }
    private val shade = Path().apply { moveTo(4f, 76f); cubicTo(8f, 64f, 14f, 48f, 16f, 30f); cubicTo(17f, 22f, 15f, 14f, 12f, 9f); cubicTo(14f, 22f, 10f, 46f, -2f, 72f); close() }
    private val nail = Path().apply { moveTo(2f, 6f); cubicTo(9f, 4f, 13f, 10f, 12f, 18f); cubicTo(8f, 20f, 3f, 19f, 0f, 16f); cubicTo(-1f, 11f, 0f, 8f, 2f, 6f); close() }

    private fun reducedMotion(): Boolean =
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    private fun syncAnimator() {
        val run = active && isAttachedToWindow && !reducedMotion()
        if (run && animator == null) {
            animator = ValueAnimator.ofFloat(0f, DemoTimeline.LOOP_MS.toFloat()).apply {
                duration = DemoTimeline.LOOP_MS
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { tMs = (it.animatedValue as Float).toLong(); invalidate() }
                start()
            }
        } else if (!run) {
            animator?.cancel(); animator = null
            tMs = STILL_MS
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); syncAnimator() }
    override fun onDetachedFromWindow() { animator?.cancel(); animator = null; super.onDetachedFromWindow() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = resolveSize((132 * resources.displayMetrics.density).toInt(), widthMeasureSpec)
        setMeasuredDimension(w, (w * 210f / 160f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val f = DemoTimeline.frame(mode, keys, tMs)
        val s = min(width / 160f, height / 210f)
        canvas.save()
        canvas.translate((width - 160f * s) / 2f, (height - 210f * s) / 2f)
        canvas.scale(s, s)
        if (!active) canvas.saveLayerAlpha(0f, 0f, 160f, 210f, 140)

        drawButtons(canvas, f.pressedButton)
        fill.color = c(R.color.metal); rect.set(43f, 16f, 117f, 166f); canvas.drawRoundRect(rect, 16f, 16f, fill)
        fill.color = c(R.color.void_black); rect.set(45.5f, 18.5f, 114.5f, 163.5f); canvas.drawRoundRect(rect, 14f, 14f, fill)
        fill.color = c(R.color.glass); rect.set(48.5f, 21.5f, 111.5f, 160.5f); canvas.drawRoundRect(rect, 11.5f, 11.5f, fill)

        canvas.save(); canvas.clipPath(glassClip)
        if (f.wake > 0f) { fill.color = c(R.color.wake); fill.alpha = (f.wake * 255).toInt(); canvas.drawRect(48.5f, 21.5f, 111.5f, 160.5f, fill); fill.alpha = 255 }
        her?.let { b ->
            if (f.herAlpha > 0f) {
                val scale = 1f + 0.06f * (1f - f.herAlpha)
                val fit = min(63f / b.width, 112f / b.height) * scale
                val bw = b.width * fit; val bh = b.height * fit
                rect.set(80f - bw / 2, 96f - bh / 2, 80f + bw / 2, 96f + bh / 2)
                bmpPaint.alpha = (f.herAlpha * 255).toInt(); canvas.drawBitmap(b, null, rect, bmpPaint)
            }
        }
        canvas.restore()
        fill.color = c(R.color.void_black); canvas.drawCircle(80f, 28f, 2.2f, fill)

        if (f.ripple > 0f) {
            val (cx, cy) = when (f.pressedButtonOrLast(mode, keys)) { 2 -> 120f to 76f; 1 -> 40f to 89f; else -> 40f to 63f }
            stroke.color = c(R.color.accent); stroke.strokeWidth = 1.5f; stroke.alpha = ((1f - f.ripple) * 230).toInt()
            canvas.drawCircle(cx, cy, 9f * (0.5f + 1.1f * f.ripple), stroke); stroke.alpha = 255
        }
        if (active && !Ui.reducedMotion(context)) drawThumb(canvas, f)
        drawChips(canvas, f.litChips)
        if (!active) canvas.restore()
        canvas.restore()
    }

    private fun drawButtons(canvas: Canvas, pressed: Int?) {
        fun btn(i: Int, l: Float, t: Float, r: Float, b: Float, inward: Float) {
            val on = pressed == i
            fill.color = if (on) c(R.color.accent) else c(R.color.metal)
            val dx = if (on) inward else 0f
            rect.set(l + dx, t, r + dx, b); canvas.drawRoundRect(rect, 2.5f, 2.5f, fill)
        }
        btn(0, 40f, 52f, 45f, 74f, 2f)
        btn(1, 40f, 78f, 45f, 100f, 2f)
        btn(2, 115f, 62f, 120f, 90f, -2f)
    }

    private fun drawThumb(canvas: Canvas, f: DemoFrame) {
        canvas.save()
        canvas.translate(f.thumbX, f.thumbY)
        canvas.rotate(f.thumbDeg)
        if (f.fromRight) canvas.scale(-1f, 1f)
        if (f.pressedButton != null) canvas.scale(0.97f, 1f)
        fill.color = c(R.color.void_black); fill.alpha = 90; rect.set(-15f, 29f, 15f, 39f); canvas.drawOval(rect, fill); fill.alpha = 255
        fill.color = c(R.color.skin); canvas.drawPath(skin, fill)
        fill.color = c(R.color.skin_2); canvas.drawPath(shade, fill)
        fill.color = c(R.color.nail); canvas.drawPath(nail, fill)
        canvas.restore()
    }

    private fun drawChips(canvas: Canvas, lit: Int) {
        val labels = if (mode == DemoMode.POWER_THEN_VOLUME) listOf("Power", "Volume")
        else keys.map { if (it == VolumeKey.UP) "Up" else "Down" }
        val w = 34f; val gap = 6f
        val total = labels.size * w + (labels.size - 1) * gap
        var x = 80f - total / 2
        labels.forEachIndexed { i, label ->
            val on = i < lit
            rect.set(x, 182f, x + w, 200f)
            fill.color = c(R.color.ground_3); canvas.drawRoundRect(rect, 9f, 9f, fill)
            stroke.color = if (on) c(R.color.accent) else c(R.color.rule); stroke.strokeWidth = 1f
            canvas.drawRoundRect(rect, 9f, 9f, stroke)
            text.color = if (on) c(R.color.accent) else c(R.color.ink_2)
            text.textSize = if (label.length > 4) 7.5f else 9f
            canvas.drawText(label, x + w / 2, 194.5f, text)
            x += w + gap
        }
    }

    private fun DemoFrame.pressedButtonOrLast(mode: DemoMode, keys: List<VolumeKey>): Int {
        pressedButton?.let { return it }
        // While the ring spreads after a press, find which button that press was.
        val t = (Math.floorMod(tMs, DemoTimeline.LOOP_MS)).toFloat() / DemoTimeline.LOOP_MS
        return if (mode == DemoMode.POWER_THEN_VOLUME) (if (t < 0.30f) 2 else 0)
        else {
            val idx = (((t - 0.16f) / 0.10f).toInt()).coerceIn(0, keys.size - 1)
            if (keys[idx] == VolumeKey.UP) 0 else 1
        }
    }

    companion object {
        private const val STILL_MS = 4000L
    }
}
