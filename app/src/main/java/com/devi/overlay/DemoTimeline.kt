package com.devi.overlay

enum class DemoMode { VOLUME, POWER_THEN_VOLUME }

/**
 * One moment of the how-to animation, in the 160x210 design box of the mockup SVG.
 * pressedButton: 0 = volume up, 1 = volume down, 2 = power, null = none.
 */
data class DemoFrame(
    val thumbX: Float, val thumbY: Float, val thumbDeg: Float, val pressedButton: Int?,
    val ripple: Float, val litChips: Int, val wake: Float, val herAlpha: Float, val fromRight: Boolean,
)

/** The 6-second loop shown beside each shortcut, as a pure function of time. */
object DemoTimeline {
    const val LOOP_MS = 6000L

    private const val FIRST_PRESS = 0.16f
    private const val PRESS_STEP = 0.10f
    private const val LIT_UNTIL = 0.88f
    private const val PRESS_HOLD = 0.02f
    private const val Y_UP = 43f
    private const val Y_DOWN = 69f

    private data class Key(val t: Float, val x: Float, val y: Float, val deg: Float)

    fun frame(mode: DemoMode, keys: List<VolumeKey>, tMs: Long): DemoFrame {
        val t = (Math.floorMod(tMs, LOOP_MS)).toFloat() / LOOP_MS
        return if (mode == DemoMode.VOLUME) volume(keys.ifEmpty { ComboPreset.UP_DOWN_UP.keys }, t) else powerThenVolume(t)
    }

    private fun volume(keys: List<VolumeKey>, t: Float): DemoFrame {
        val presses = keys.indices.map { FIRST_PRESS + PRESS_STEP * it }
        val path = mutableListOf(Key(0f, -8f, 150f, -14f))
        keys.forEachIndexed { i, k ->
            val y = if (k == VolumeKey.UP) Y_UP else Y_DOWN
            path += Key(presses[i] - 0.04f, 32f, y, -6f)
            path += Key(presses[i], 37f, y, -6f)
            path += Key(presses[i] + PRESS_HOLD, 37f, y, -6f)
        }
        val last = presses.last()
        path += Key(last + 0.12f, -14f, 160f, -18f)
        path += Key(1f, -8f, 150f, -14f)
        val (x, y, deg) = along(path, t)

        val pressedIdx = presses.indexOfFirst { t >= it && t <= it + PRESS_HOLD }
        val pressed = if (pressedIdx >= 0) (if (keys[pressedIdx] == VolumeKey.UP) 0 else 1) else null
        val lit = if (t >= LIT_UNTIL) 0 else presses.count { t >= it }
        return DemoFrame(
            x, y, deg, pressed, ripple(presses, t), lit,
            wake = tri(t, last + 0.02f, last + 0.08f, last + 0.18f) * 0.5f,
            herAlpha = her(t, last + 0.06f), fromRight = false,
        )
    }

    private fun powerThenVolume(t: Float): DemoFrame {
        val pPower = FIRST_PRESS
        val pVol = 0.34f
        val fromRight = t < 0.22f
        val (x, y, deg) = if (fromRight) {
            along(listOf(Key(0f, 176f, 150f, 14f), Key(0.12f, 140f, 56f, 6f), Key(pPower, 135f, 56f, 6f),
                Key(pPower + PRESS_HOLD, 135f, 56f, 6f), Key(0.22f, 180f, 160f, 18f)), t)
        } else {
            along(listOf(Key(0.22f, -14f, 160f, -18f), Key(0.30f, 32f, Y_UP, -6f), Key(pVol, 37f, Y_UP, -6f),
                Key(pVol + PRESS_HOLD, 37f, Y_UP, -6f), Key(0.46f, -14f, 160f, -18f), Key(1f, -14f, 160f, -18f)), t)
        }
        val pressed = when {
            t >= pPower && t <= pPower + PRESS_HOLD -> 2
            t >= pVol && t <= pVol + PRESS_HOLD -> 0
            else -> null
        }
        val lit = if (t >= LIT_UNTIL) 0 else listOf(pPower, pVol).count { t >= it }
        val wake = when {
            t < pPower -> 0f
            t < 0.20f -> (t - pPower) / 0.04f * 0.35f
            t < pVol -> 0.35f
            t < 0.50f -> 0.35f * (1f - (t - pVol) / 0.16f)
            else -> 0f
        }
        return DemoFrame(x, y, deg, pressed, ripple(listOf(pPower, pVol), t), lit, wake.coerceIn(0f, 1f), her(t, 0.36f), fromRight)
    }

    /** 0 = no ripple; otherwise 0..1 progress of the ring from the most recent press. */
    private fun ripple(presses: List<Float>, t: Float): Float {
        val p = presses.lastOrNull { t >= it } ?: return 0f
        val r = (t - p) / 0.07f
        return if (r in 0f..1f) r.coerceAtLeast(0.001f) else 0f
    }

    private fun her(t: Float, start: Float): Float = when {
        t < start -> 0f
        t < start + 0.14f -> smooth((t - start) / 0.14f)
        t < LIT_UNTIL -> 1f
        t < 0.96f -> 1f - smooth((t - LIT_UNTIL) / 0.08f)
        else -> 0f
    }

    private fun tri(t: Float, a: Float, peak: Float, b: Float): Float = when {
        t <= a || t >= b -> 0f
        t <= peak -> (t - a) / (peak - a)
        else -> (b - t) / (b - peak)
    }

    private fun smooth(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3 - 2 * c) }

    private fun along(path: List<Key>, t: Float): Triple<Float, Float, Float> {
        if (t <= path.first().t) return path.first().let { Triple(it.x, it.y, it.deg) }
        for (i in 1 until path.size) {
            val a = path[i - 1]
            val b = path[i]
            if (t <= b.t) {
                val s = if (b.t == a.t) 1f else smooth((t - a.t) / (b.t - a.t))
                return Triple(a.x + (b.x - a.x) * s, a.y + (b.y - a.y) * s, a.deg + (b.deg - a.deg) * s)
            }
        }
        return path.last().let { Triple(it.x, it.y, it.deg) }
    }
}
