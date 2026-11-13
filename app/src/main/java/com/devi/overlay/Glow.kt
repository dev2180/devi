package com.devi.overlay

import android.graphics.Bitmap

/**
 * The soft glow behind each page, made once on a worker thread: a tiny copy of the picture,
 * box-blurred three times (close to a Gaussian). Stretched to full screen it is smooth, and
 * costs the GPU nothing per frame, unlike a live blur.
 */
object Glow {
    private const val W = 36

    fun of(src: Bitmap): Bitmap {
        val h = (W * src.height / src.width.coerceAtLeast(1)).coerceIn(8, 160)
        val small = Bitmap.createScaledBitmap(if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src, W, h, true)
        val px = IntArray(W * h)
        small.getPixels(px, 0, W, 0, 0, W, h)
        repeat(3) { BoxBlur.pass(px, W, h, 3) }
        return Bitmap.createBitmap(px, W, h, Bitmap.Config.ARGB_8888)
    }
}

/** Pure box blur on packed ARGB pixels, horizontal then vertical, edges clamped. */
object BoxBlur {
    fun pass(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        for (y in 0 until h) for (x in 0 until w) tmp[y * w + x] = avg(px, r) { i -> y * w + (x + i).coerceIn(0, w - 1) }
        for (y in 0 until h) for (x in 0 until w) px[y * w + x] = avg(tmp, r) { i -> (y + i).coerceIn(0, h - 1) * w + x }
    }

    private inline fun avg(src: IntArray, r: Int, at: (Int) -> Int): Int {
        var a = 0; var rr = 0; var g = 0; var b = 0
        for (i in -r..r) {
            val c = src[at(i)]
            a += c ushr 24; rr += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
        }
        val n = 2 * r + 1
        return ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (b / n)
    }
}
