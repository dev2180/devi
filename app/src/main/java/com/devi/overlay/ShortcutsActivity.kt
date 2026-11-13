package com.devi.overlay

import android.app.Activity
import android.Manifest
import android.app.AlertDialog
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** The phone settings Devi needs, checked live. */
object Setup {
    /** Lets her open straight over whatever is on screen (not only the lock screen). */
    fun overlayOk(ctx: Context) = Settings.canDrawOverlays(ctx)

    fun notificationsOk(ctx: Context) =
        ctx.getSystemService(android.app.NotificationManager::class.java)?.areNotificationsEnabled() == true

    fun batteryOk(ctx: Context) =
        ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true

    fun ready(ctx: Context) = AccessGuard.canControl(ctx) && overlayOk(ctx) && notificationsOk(ctx) && batteryOk(ctx)
}

/** Settings: how she comes and how she stays, in quiet grouped cards. */
class ShortcutsActivity : Activity() {

    private lateinit var col: LinearLayout
    private val demos = mutableListOf<HowToDemoView>()
    private var her: Bitmap? = null
    private val worker = Executors.newSingleThreadExecutor()

    private fun dp(v: Float) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(getColor(R.color.ground)) }
        col = Ui.vertical(this, 12f).apply { setPadding(dp(20f), dp(16f), dp(20f), dp(28f)) }
        scroll.addView(col)
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            col.setPadding(dp(20f) + bars.left, dp(16f) + bars.top, dp(20f) + bars.right, dp(28f) + bars.bottom)
            insets
        }
        setContentView(scroll)
        ImageStore.forContext(this).main()?.let { id ->
            val file = ImageStore.forContext(this).file(id)
            worker.execute {
                val bmp = ImageLoading.decodeThumb(file, dp(140f))
                runOnUiThread { her = bmp; demos.forEach { it.her = bmp } }
            }
        }
    }

    override fun onResume() { super.onResume(); build() }
    override fun onPause() { DeviListenerService.sync(this); super.onPause() }
    override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }

    private fun build() {
        col.removeAllViews(); demos.clear()
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_back); contentDescription = "Back"
            background = Ui.pill(this@ShortcutsActivity, R.color.ground_2); setColorFilter(getColor(R.color.ink))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44f), dp(44f)))
        col.addView(top)
        col.addView(Ui.heading(this, "Settings", 34f), Ui.lp(top = dp(20f)))
        col.addView(Ui.bodyText(this, "How she comes, and how she stays", 14f, muted = true), Ui.lp(top = dp(2f)))

        if (!Setup.ready(this)) section("First, a few permissions", setupCard())
        section("Bring her", bringCard())
        section("While she's here", stayCard())
        section("Rest your eyes", eyeCard())
        section("App icon", iconCard())
    }

    // ---- building blocks: a quiet label, one card per section, hairline rows ----

    private fun section(label: String, card: View) {
        col.addView(TextView(this).apply {
            text = label.uppercase(); typeface = Ui.bodyMedium(this@ShortcutsActivity)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11.5f); letterSpacing = 0.14f
            setTextColor(getColor(R.color.accent))
        }, Ui.lp(top = dp(32f)).apply { marginStart = dp(4f) })
        col.addView(card, Ui.lp(top = dp(10f)))
    }

    private fun card() = Ui.vertical(this).apply {
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(22f).toFloat(); setColor(getColor(R.color.ground_2))
        }
        Ui.roundClip(this, dp(22f).toFloat())
    }

    private fun LinearLayout.rule() {
        if (childCount == 0) return
        addView(View(this@ShortcutsActivity).apply { setBackgroundColor(getColor(R.color.rule)) },
            LinearLayout.LayoutParams(-1, 1).apply { marginStart = dp(18f); marginEnd = dp(18f) })
    }

    private fun ripple() = android.graphics.drawable.RippleDrawable(
        android.content.res.ColorStateList.valueOf(getColor(R.color.rule)), null,
        android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE),
    )

    private fun rowTexts(title: String, note: String?, small: Boolean = false) = Ui.vertical(this).apply {
        addView(TextView(this@ShortcutsActivity).apply {
            text = title; typeface = Ui.bodyMedium(this@ShortcutsActivity)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (small) 15f else 16.5f)
            setTextColor(getColor(R.color.ink))
        })
        if (note != null) addView(Ui.bodyText(this@ShortcutsActivity, note, 13f, muted = true), Ui.lp(top = dp(3f)))
    }

    private fun rowShell() = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(60f)
        setPadding(dp(18f), dp(14f), dp(14f), dp(14f))
    }

    /** A row with a switch on the right; the whole row toggles it. */
    private fun LinearLayout.switchRow(title: String, note: String?, on: Boolean, small: Boolean = false, change: (Boolean) -> Unit) {
        rule()
        val row = rowShell()
        row.addView(rowTexts(title, note, small), LinearLayout.LayoutParams(0, -2, 1f))
        val sw = Ui.goldSwitch(this@ShortcutsActivity).apply {
            isChecked = on; contentDescription = title
            setOnCheckedChangeListener { _, c -> change(c) }
        }
        row.addView(sw, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12f) })
        row.background = ripple(); row.setOnClickListener { sw.toggle() }
        addView(row)
    }

    /** A row that shows its current value and opens a chooser. */
    private fun LinearLayout.valueRow(title: String, value: String, note: String? = null, accent: Boolean = false, click: () -> Unit) {
        rule()
        val row = rowShell()
        row.addView(rowTexts(title, note, small = true), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(this@ShortcutsActivity).apply {
            text = "$value  ›"; typeface = Ui.body(this@ShortcutsActivity)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(getColor(if (accent) R.color.accent else R.color.muted))
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12f) })
        row.background = ripple(); row.setOnClickListener { click() }
        addView(row)
    }

    private fun LinearLayout.noteRow(text: String) {
        addView(Ui.bodyText(this@ShortcutsActivity, text, 12.5f, muted = true).apply {
            setPadding(dp(18f), 0, dp(18f), dp(14f))
        })
    }

    private fun LinearLayout.demoRow(mode: DemoMode, keys: List<VolumeKey>, caption: String) {
        val row = LinearLayout(this@ShortcutsActivity).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18f), 0, dp(18f), dp(16f))
        }
        row.addView(HowToDemoView(this@ShortcutsActivity).also {
            it.mode = mode; it.keys = keys; it.active = true; it.her = her; demos += it
        }, LinearLayout.LayoutParams(dp(84f), -2))
        row.addView(Ui.bodyText(this@ShortcutsActivity, caption, 13f, muted = true),
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(16f) })
        addView(row)
    }

    // ---- sections ----

    private fun bringCard() = card().apply {
        if (Build.VERSION.SDK_INT >= 33) valueRow("Quick Settings tile", "Add", "Swipe down, tap Devi. Works locked too.", accent = true) {
            getSystemService(StatusBarManager::class.java)?.requestAddTileService(
                ComponentName(this@ShortcutsActivity, DeviTileService::class.java), "Devi",
                Icon.createWithResource(this@ShortcutsActivity, R.drawable.ic_tile_devi),
                mainExecutor,
            ) { }
        }

        val preset = DeviSettings.volumeCombo(this@ShortcutsActivity)
        val shown = preset ?: lastPreset
        switchRow("Volume buttons", "Works on the lock screen", preset != null) {
            DeviSettings.setVolumeCombo(this@ShortcutsActivity, if (it) shown else null); build()
        }
        if (preset != null) {
            valueRow("Combination", shown.label) { choosePreset() }
            demoRow(DemoMode.VOLUME, shown.keys, "Press ${shown.label.lowercase()}, quickly. She opens over anything.")
        }

        val power = DeviSettings.powerTrigger(this@ShortcutsActivity)
        switchRow("Power, then volume", "One press of power, then a volume button", power) {
            DeviSettings.setPowerTrigger(this@ShortcutsActivity, it); build()
        }
        if (power) demoRow(DemoMode.POWER_THEN_VOLUME, emptyList(), "Never press power three times: that calls SOS.")
    }

    private val lastPreset: ComboPreset get() = DeviSettings.volumeCombo(this) ?: ComboPreset.UP_DOWN_UP

    private fun choosePreset() {
        val all = ComboPreset.entries
        AlertDialog.Builder(this).setTitle("Volume combination")
            .setSingleChoiceItems(all.map { it.label }.toTypedArray(), all.indexOf(lastPreset)) { d, which ->
                DeviSettings.setVolumeCombo(this, all[which]); d.dismiss(); build()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun stayLabel(m: Int) = if (m <= 0) "Until I close her" else "$m minutes"
    private fun brightLabel(b: Float) = if (b < 0f) "Follow phone" else "${(b * 100).toInt()}%"

    /** Focus while she's here: screen stays on, Do Not Disturb, nothing takes her away. */
    private fun stayCard() = card().apply {
        val on = DeviSettings.focusMode(this@ShortcutsActivity)
        switchRow("Focus", "Screen stays on. No calls, messages or notifications.", on) {
            DeviSettings.setFocusMode(this@ShortcutsActivity, it); build()
        }
        if (on) {
            if (!DndKeeper.hasAccess(this@ShortcutsActivity)) valueRow("Do Not Disturb access", "Allow", "Needed for Focus", accent = true) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
            valueRow("She stays", stayLabel(DeviSettings.stayMinutes(this@ShortcutsActivity))) { chooseStay() }
            switchRow("Let alarms ring through", null, DeviSettings.letAlarms(this@ShortcutsActivity), small = true) {
                DeviSettings.setLetAlarms(this@ShortcutsActivity, it)
            }
        }
        valueRow("Brightness", brightLabel(DeviSettings.brightness(this@ShortcutsActivity))) { chooseBrightness() }
    }

    /** Eye break: the night sky and Devi, every so often, to rest your eyes. */
    private fun eyeCard() = card().apply {
        val on = DeviSettings.eyeBreak(this@ShortcutsActivity)
        switchRow("Eye break", "Stars and Devi, for a few breaths", on) {
            DeviSettings.setEyeBreak(this@ShortcutsActivity, it && !needOverlay())
            DeviListenerService.sync(this@ShortcutsActivity); build()
        }
        if (on) {
            valueRow("Every", "${DeviSettings.eyeEvery(this@ShortcutsActivity)} min") { chooseEvery() }
            valueRow("Lasts", "${DeviSettings.eyeSeconds(this@ShortcutsActivity)} s") { chooseSeconds() }
        }
        val style = DeviSettings.eyeStyle(this@ShortcutsActivity)
        valueRow("Style", if (style == EyeStyle.COVER) "Cover" else "Dissolve", style.label) { chooseStyle() }
        valueRow("Preview", "Now", accent = true) { preview(style) }
        if (style == EyeStyle.DISSOLVE && !AccessGuard.canControl(this@ShortcutsActivity))
            noteRow("Dissolve needs the one-time computer step. Until then the sky covers the screen.")
    }

    private fun chooseStyle() {
        val all = EyeStyle.entries
        AlertDialog.Builder(this).setTitle("Eye break style")
            .setSingleChoiceItems(all.map { it.label }.toTypedArray(), all.indexOf(DeviSettings.eyeStyle(this))) { d, w ->
                DeviSettings.setEyeStyle(this, all[w]); d.dismiss(); build()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun preview(s: EyeStyle) {
        if (needOverlay()) return
        // Leave Devi first, so the dissolve breaks your own screen, not this page.
        if (s == EyeStyle.DISSOLVE) {
            val app = applicationContext
            moveTaskToBack(true)
            android.os.Handler(mainLooper).postDelayed({ EyeBreak.start(app, s) }, 900)
        } else EyeBreak.start(this, s)
    }

    private fun iconCard() = card().apply {
        val style = DeviSettings.iconStyle(this@ShortcutsActivity)
        val grid = Ui.vertical(this@ShortcutsActivity, 6f).apply { setPadding(dp(10f), dp(14f), dp(10f), dp(14f)) }
        DeviSettings.IconStyle.entries.chunked(4).forEach { rowStyles ->
            val row = LinearLayout(this@ShortcutsActivity)
            rowStyles.forEach { s ->
                val chosen = s == style
                val cell = Ui.vertical(this@ShortcutsActivity).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(dp(4f), dp(8f), dp(4f), dp(8f))
                    contentDescription = s.label
                    background = ripple()
                    setOnClickListener { if (!chosen) setIcon(s) }
                }
                val ring = android.widget.FrameLayout(this@ShortcutsActivity).apply {
                    setPadding(dp(3f), dp(3f), dp(3f), dp(3f))
                    if (chosen) background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = dp(18f).toFloat(); setStroke(dp(2f), getColor(R.color.accent))
                    }
                    addView(ImageView(this@ShortcutsActivity).apply { setImageDrawable(getDrawable(s.icon)) }, android.widget.FrameLayout.LayoutParams(dp(50f), dp(50f)))
                }
                cell.addView(ring, LinearLayout.LayoutParams(-2, -2))
                cell.addView(Ui.bodyText(this@ShortcutsActivity, s.label, 11f, muted = !chosen).apply { gravity = Gravity.CENTER }, Ui.lp(top = dp(6f)))
                row.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
            }
            repeat(4 - rowStyles.size) { row.addView(View(this@ShortcutsActivity), LinearLayout.LayoutParams(0, 0, 1f)) }
            grid.addView(row)
        }
        addView(grid)
    }

    private fun setupCard() = card().apply {
        if (!AccessGuard.canControl(this@ShortcutsActivity)) {
            addView(Ui.vertical(this@ShortcutsActivity).apply {
                setPadding(dp(18f), dp(16f), dp(18f), dp(16f))
                addView(rowTexts("Lock-screen buttons", "One time, from a computer with USB or wireless debugging:", small = true))
                addView(Ui.bodyText(this@ShortcutsActivity, "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS", 12f).apply {
                    setTextIsSelectable(true); typeface = android.graphics.Typeface.MONOSPACE
                    setTextColor(getColor(R.color.ink_2))
                    background = Ui.pill(this@ShortcutsActivity, R.color.ground).apply { cornerRadius = dp(10f).toFloat() }
                    setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
                }, Ui.lp(top = dp(10f)))
                addView(Ui.bodyText(this@ShortcutsActivity, "Devi then keeps Accessibility on only while the phone is locked, so payment apps never see it. Don't turn it on yourself.", 12.5f, muted = true), Ui.lp(top = dp(10f)))
            })
        }
        if (!Setup.overlayOk(this@ShortcutsActivity)) valueRow("Display over other apps", "Allow", accent = true) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        if (!Setup.notificationsOk(this@ShortcutsActivity)) valueRow("Notifications", "Allow", accent = true) {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            } else {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
        }
        if (!Setup.batteryOk(this@ShortcutsActivity)) valueRow("Run without battery limits", "Allow", accent = true) {
            try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) } catch (_: Exception) { }
        }
    }

    private fun chooseStay() {
        val options = listOf(0, 5, 10, 15, 30, 60)
        val current = options.indexOf(DeviSettings.stayMinutes(this)).coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("She stays")
            .setSingleChoiceItems(options.map { stayLabel(it) }.toTypedArray(), current) { d, which ->
                DeviSettings.setStayMinutes(this, options[which]); d.dismiss(); build()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun chooseBrightness() {
        val saved = DeviSettings.brightness(this)
        val bar = android.widget.SeekBar(this).apply { max = 100; progress = if (saved < 0f) 50 else (saved * 100).toInt() }
        val wrap = Ui.vertical(this).apply { setPadding(dp(24f), dp(16f), dp(24f), 0); addView(bar) }
        AlertDialog.Builder(this).setTitle("Her brightness").setView(wrap)
            .setPositiveButton("Use this") { _, _ -> DeviSettings.setBrightness(this, (bar.progress / 100f).coerceAtLeast(0.02f)); build() }
            .setNeutralButton("Follow phone") { _, _ -> DeviSettings.setBrightness(this, -1f); build() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun needOverlay(): Boolean {
        if (Settings.canDrawOverlays(this)) return false
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        return true
    }

    private fun chooseEvery() {
        val options = listOf(10, 20, 30, 45, 60)
        AlertDialog.Builder(this).setTitle("A break every")
            .setSingleChoiceItems(options.map { "$it minutes" }.toTypedArray(), options.indexOf(DeviSettings.eyeEvery(this))) { d, w ->
                DeviSettings.setEyeEvery(this, options[w]); d.dismiss(); build()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun chooseSeconds() {
        val options = listOf(20, 40, 60)
        AlertDialog.Builder(this).setTitle("Each break lasts")
            .setSingleChoiceItems(options.map { "$it seconds" }.toTypedArray(), options.indexOf(DeviSettings.eyeSeconds(this))) { d, w ->
                DeviSettings.setEyeSeconds(this, options[w]); d.dismiss(); build()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun setIcon(s: DeviSettings.IconStyle) {
        DeviSettings.setIconStyle(this, s)
        val pm = packageManager
        fun set(alias: String, on: Boolean) = pm.setComponentEnabledSetting(
            ComponentName(this, "$packageName.$alias"),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        set(s.alias, true) // the new one first, so the launcher is never left without an icon
        DeviSettings.IconStyle.entries.filter { it != s }.forEach { set(it.alias, false) }
        build()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        build()
    }
}
