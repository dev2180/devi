package com.devi.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

/** The Kiln-reduction visual system, as small helpers for programmatic views. */
object Ui {
    fun dp(ctx: Context, v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics).toInt()
    fun color(ctx: Context, id: Int) = ctx.getColor(id)

    private fun font(ctx: Context, id: Int): Typeface = try { ctx.resources.getFont(id) } catch (_: Exception) { Typeface.DEFAULT }
    fun display(ctx: Context) = font(ctx, R.font.boska_medium)
    fun body(ctx: Context) = font(ctx, R.font.geist_regular)
    fun bodyMedium(ctx: Context) = font(ctx, R.font.geist_medium)

    fun heading(ctx: Context, text: String, sizeSp: Float = 22f) = TextView(ctx).apply {
        this.text = text
        typeface = display(ctx)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        letterSpacing = -0.02f
        setTextColor(color(ctx, R.color.ink))
    }

    fun bodyText(ctx: Context, text: String, sizeSp: Float = 16f, muted: Boolean = false) = TextView(ctx).apply {
        this.text = text
        typeface = body(ctx)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setLineSpacing(0f, 1.3f)
        setTextColor(color(ctx, if (muted) R.color.muted else R.color.ink))
    }

    /** A smoked well: one step above the ground, 16dp radius, a hairline edge. */
    fun well(ctx: Context, accentEdge: Boolean = false) = GradientDrawable().apply {
        cornerRadius = dp(ctx, 16f).toFloat()
        setColor(color(ctx, R.color.ground_2))
        setStroke(dp(ctx, if (accentEdge) 1.5f else 1f), color(ctx, if (accentEdge) R.color.accent else R.color.rule))
    }

    fun pill(ctx: Context, fillId: Int, strokeId: Int? = null) = GradientDrawable().apply {
        cornerRadius = 999f
        setColor(color(ctx, fillId))
        strokeId?.let { setStroke(dp(ctx, 1f), color(ctx, it)) }
    }

    /** The gold primary pill. */
    fun primaryButton(ctx: Context, label: String) = Button(ctx).apply {
        text = label; isAllCaps = false; typeface = bodyMedium(ctx)
        setTextColor(color(ctx, R.color.accent_ink)); background = pill(ctx, R.color.accent)
        minHeight = dp(ctx, 48f); setPadding(dp(ctx, 20f), 0, dp(ctx, 20f), 0); stateListAnimator = null
    }

    /** The ghost pill: hairline ring, cream label. */
    fun ghostButton(ctx: Context, label: String) = Button(ctx).apply {
        text = label; isAllCaps = false; typeface = bodyMedium(ctx)
        setTextColor(color(ctx, R.color.ink)); background = pill(ctx, R.color.ground, R.color.rule)
        minHeight = dp(ctx, 44f); setPadding(dp(ctx, 18f), 0, dp(ctx, 18f), 0); stateListAnimator = null
    }

    fun linkButton(ctx: Context, label: String) = Button(ctx, null, android.R.attr.borderlessButtonStyle).apply {
        text = label; isAllCaps = false; typeface = body(ctx)
        setTextColor(color(ctx, R.color.muted)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); gravity = Gravity.START or Gravity.CENTER_VERTICAL
    }

    fun goldSwitch(ctx: Context) = Switch(ctx).apply {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        // On: a bright gold knob. Off: a dim knob. Readable by brightness alone, so "Colour off" can't hide it.
        thumbTintList = ColorStateList(states, intArrayOf(color(ctx, R.color.accent), color(ctx, R.color.metal)))
        trackTintList = ColorStateList(states, intArrayOf(color(ctx, R.color.accent), color(ctx, R.color.track_off)))
        minHeight = dp(ctx, 44f)
    }

    fun roundClip(view: View, radiusPx: Float) {
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) = o.setRoundRect(0, 0, v.width, v.height, radiusPx)
        }
        view.clipToOutline = true
    }

    fun vertical(ctx: Context, gapDp: Float = 0f) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        if (gapDp > 0f) {
            showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
            dividerDrawable = GradientDrawable().apply { setSize(1, dp(ctx, gapDp)); setColor(0) }
        }
    }

    fun lp(w: Int = -1, h: Int = -2, top: Int = 0) = LinearLayout.LayoutParams(w, h).apply { topMargin = top }

    fun reducedMotion(ctx: Context) =
        android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
