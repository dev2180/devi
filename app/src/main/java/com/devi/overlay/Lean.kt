package com.devi.overlay

import android.content.Context
import android.os.PowerManager
import android.provider.Settings

/** Battery saver (incl. realme's super power saving) or colour off: Devi keeps still and dark. */
object LeanPolicy {
    fun lean(powerSave: Boolean, greyscale: Boolean) = powerSave || greyscale

    /** realme's "Colour off" sets gray_scale_on; Android's own greyscale is the daltonizer in monochromacy (0). */
    fun greyscale(realmeGray: Int, daltonizerOn: Int, daltonizer: Int) =
        realmeGray == 1 || (daltonizerOn == 1 && daltonizer == 0)
}

object Lean {
    private fun sys(ctx: Context, key: String) = try { Settings.System.getInt(ctx.contentResolver, key, 0) } catch (_: Exception) { 0 }
    private fun sec(ctx: Context, key: String, def: Int) = try { Settings.Secure.getInt(ctx.contentResolver, key, def) } catch (_: Exception) { def }

    fun powerSave(ctx: Context) = ctx.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true

    fun greyscale(ctx: Context) = LeanPolicy.greyscale(
        sys(ctx, "gray_scale_on"),
        sec(ctx, "accessibility_display_daltonizer_enabled", 0),
        sec(ctx, "accessibility_display_daltonizer", -1),
    )

    /** True when animations and glows should rest: saver on, colour off, or animations turned off. */
    fun on(ctx: Context) = LeanPolicy.lean(powerSave(ctx), greyscale(ctx)) || Ui.reducedMotion(ctx)
}
