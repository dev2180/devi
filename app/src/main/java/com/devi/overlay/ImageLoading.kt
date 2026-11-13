package com.devi.overlay

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import java.io.File
import java.io.InputStream

/** Display-only decoding. Never writes or alters the stored files. */
object ImageLoading {

    /** Decodes [file] for full-screen display, keeping EXIF orientation; downsamples only very large images. */
    fun decodeForDisplay(file: File, maxW: Int, maxH: Int): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val plan = DisplaySampling.plan(info.size.width, info.size.height, maxW, maxH)
            if (plan.targetW != null && plan.targetH != null) {
                decoder.setTargetSize(plan.targetW, plan.targetH)
            } else if (plan.sampleSize > 1) {
                decoder.setTargetSampleSize(plan.sampleSize)
            }
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    fun decodeThumb(file: File, sizePx: Int): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = sizePx.toDouble() / minOf(w, h)
            if (scale < 1.0) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    /** Width and height as displayed (EXIF-rotated), or null if the bytes are not a decodable image. */
    fun readSize(file: File): Pair<Int, Int>? = try {
        var size: Pair<Int, Int>? = null
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
            size = info.size.width to info.size.height
            decoder.setTargetSampleSize(64) // cheap: we only want the header
        }
        size
    } catch (e: Exception) {
        null
    }

    /** Header-only size read from a stream (BitmapFactory); null if not an image it understands. */
    fun readSize(input: InputStream): Pair<Int, Int>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(input, null, opts)
        return if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
    }
}
