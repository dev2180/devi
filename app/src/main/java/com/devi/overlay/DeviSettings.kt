package com.devi.overlay

import android.content.Context

/** The app's user settings. */
object DeviSettings {
    private const val FILE = "settings"
    private const val KEY_OLD_SHORTCUT = "shortcut_enabled" // v1 on/off switch, migrated
    private const val KEY_VOLUME_COMBO = "volume_combo"
    private const val KEY_POWER = "power_trigger"
    private const val KEY_EYES = "eye_break"
    private const val KEY_EYE_STYLE = "eye_style"
    private const val KEY_EYE_EVERY = "eye_every"
    private const val KEY_EYE_SECONDS = "eye_seconds"
    private const val KEY_ICON = "icon_style"
    private const val OFF = "OFF"
    private const val KEY_FOCUS = "focus_mode"
    private const val KEY_STAY = "stay_minutes"
    private const val KEY_BRIGHT = "brightness"
    private const val KEY_ALARMS = "let_alarms"

    /** Launcher icons: her eyes, geometry untouched, only the colours change. */
    enum class IconStyle(val alias: String, val icon: Int, val label: String) {
        THEME("LauncherTheme", R.mipmap.ic_launcher, "Phone theme"),
        IVORY("LauncherIvory", R.mipmap.ic_launcher_ivory, "Ivory"),
        WHITE("LauncherWhite", R.mipmap.ic_launcher_white, "White"),
        INK("LauncherInk", R.mipmap.ic_launcher_ink, "Ink on white"),
        CRIMSON("LauncherCrimson", R.mipmap.ic_launcher_crimson, "Crimson"),
        SNOW("LauncherSnow", R.mipmap.ic_launcher_snow, "White on crimson"),
        MAROON("LauncherMaroon", R.mipmap.ic_launcher_maroon, "Maroon"),
        BLACK("LauncherBlack", R.mipmap.ic_launcher_black, "Black"),
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The chosen volume combination, or null when the volume shortcut is off. */
    fun volumeCombo(ctx: Context): ComboPreset? {
        val p = prefs(ctx)
        val id = p.getString(KEY_VOLUME_COMBO, null)
            ?: if (p.getBoolean(KEY_OLD_SHORTCUT, true)) ComboPreset.UP_DOWN_UP.name else OFF
        return if (id == OFF) null else ComboPreset.fromId(id)
    }

    fun setVolumeCombo(ctx: Context, preset: ComboPreset?) {
        prefs(ctx).edit().putString(KEY_VOLUME_COMBO, preset?.name ?: OFF).apply()
    }

    fun powerTrigger(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_POWER, false)
    fun setPowerTrigger(ctx: Context, enabled: Boolean) { prefs(ctx).edit().putBoolean(KEY_POWER, enabled).apply() }

    /** The eye break: off until chosen. */
    fun eyeBreak(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_EYES, false)
    fun setEyeBreak(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean(KEY_EYES, on).apply() }
    fun eyeStyle(ctx: Context): EyeStyle = EyeStyle.entries.firstOrNull { it.name == prefs(ctx).getString(KEY_EYE_STYLE, null) } ?: EyeStyle.COVER
    fun setEyeStyle(ctx: Context, s: EyeStyle) { prefs(ctx).edit().putString(KEY_EYE_STYLE, s.name).apply() }
    fun eyeEvery(ctx: Context): Int = prefs(ctx).getInt(KEY_EYE_EVERY, 20)
    fun setEyeEvery(ctx: Context, m: Int) { prefs(ctx).edit().putInt(KEY_EYE_EVERY, m).apply() }
    fun eyeSeconds(ctx: Context): Int = prefs(ctx).getInt(KEY_EYE_SECONDS, 20)
    fun setEyeSeconds(ctx: Context, s: Int) { prefs(ctx).edit().putInt(KEY_EYE_SECONDS, s).apply() }

    fun iconStyle(ctx: Context): IconStyle =
        IconStyle.entries.firstOrNull { it.name == prefs(ctx).getString(KEY_ICON, null) } ?: IconStyle.THEME
    fun setIconStyle(ctx: Context, style: IconStyle) { prefs(ctx).edit().putString(KEY_ICON, style.name).apply() }

    /** Focus while she's here (screen on, Do Not Disturb, nothing takes her away). */
    fun focusMode(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_FOCUS, true)
    fun setFocusMode(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean(KEY_FOCUS, on).apply() }

    /** 0 = until I close her. */
    fun stayMinutes(ctx: Context): Int = prefs(ctx).getInt(KEY_STAY, 0)
    fun setStayMinutes(ctx: Context, m: Int) { prefs(ctx).edit().putInt(KEY_STAY, m).apply() }

    /** -1 = follow the phone, else 0..1. */
    fun brightness(ctx: Context): Float = prefs(ctx).getFloat(KEY_BRIGHT, -1f)
    fun setBrightness(ctx: Context, b: Float) { prefs(ctx).edit().putFloat(KEY_BRIGHT, b).apply() }

    fun letAlarms(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ALARMS, true)
    fun setLetAlarms(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean(KEY_ALARMS, on).apply() }
}
