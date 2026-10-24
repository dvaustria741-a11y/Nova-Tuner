package com.zenkai.novatuner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private lateinit var profiles: MutableList<Profile>
    private lateinit var holder: ScrollView
    private var back: (() -> Unit)? = null
    private var onHome = false

    private val binderReceived = Shizuku.OnBinderReceivedListener { runOnUiThread { refresh() } }
    private val binderDead = Shizuku.OnBinderDeadListener {
        Shell.service = null
        runOnUiThread { refresh() }
    }
    private val permResult = Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread { refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Fonts.init(applicationContext)
        profiles = Store.load(this)

        val root = FrameLayout(this)
        val backdrop = BackdropView(this)
        backdrop.art = assetDrawable("bg_main")
        backdrop.layer = assetDrawable("bg_layer")
        root.addView(backdrop, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        holder = ScrollView(this)
        holder.isFillViewport = true
        holder.overScrollMode = View.OVER_SCROLL_NEVER
        root.addView(holder, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        Shell.onChange = { runOnUiThread { refresh() } }
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permResult)
        showHome(true)
    }

    override fun onResume() {
        super.onResume()
        profiles = Store.load(this)
        refresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permResult)
        Shell.onChange = null
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        val b = back
        if (b != null) b() else super.onBackPressed()
    }

    private fun refresh() {
        Shell.bind(this)
        if (onHome) showHome(false)
    }

    private fun setScreen(v: View, animate: Boolean = true, keepScroll: Boolean = false) {
        val y = holder.scrollY
        holder.removeAllViews()
        holder.addView(v, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (keepScroll) holder.post { holder.scrollTo(0, y) } else holder.scrollTo(0, 0)
        if (animate && v is ViewGroup) v.staggerIn()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun iconOf(pkg: String): Drawable? = try {
        packageManager.getApplicationIcon(pkg)
    } catch (e: Exception) {
        null
    }

    private fun launch(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) startActivity(i) else toast("Cannot launch $pkg")
    }

    private fun openShizuku() {
        val i = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (i != null) startActivity(i) else toast("Shizuku is not installed")
    }

    private fun toggleOverlay(on: Boolean) {
        if (!on) {
            stopService(Intent(this, OverlayService::class.java))
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("Allow display over other apps, then enable again")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            holder.post { showHome(false) }
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        }
        startForegroundService(Intent(this, OverlayService::class.java))
    }

    private fun appRow(icon: Drawable?, title: String, sub: String, onClick: () -> Unit): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(12), dp(12), dp(12), dp(12))
        row.background = cut(C.PANEL, 10)
        row.setOnClickListener { onClick() }
        row.pressable()
        val iv = ImageView(this)
        if (icon != null) iv.setImageDrawable(icon)
        row.addView(iv, LinearLayout.LayoutParams(dp(42), dp(42)))
        val col = vbox()
        col.addView(tv(title, 15f, C.TEXT, true))
        col.addView(tv(sub, 12f, C.DIM))
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp.leftMargin = dp(12)
        row.addView(col, lp)
        row.addView(tv("›", 22f, C.ACCENT, true))
        return row
    }

    private fun showHome(animate: Boolean) {
        onHome = true
        back = null
        val col = vbox(18)
        col.put(tv("NOVA TUNER", 28f, C.TEXT, true, 0.14f))
        col.put(tv("PER-APP PERFORMANCE CONTROL", 11f, C.ACCENT, true, 0.22f), 2)

        val hero = HeroView(this)
        hero.art = assetDrawable("hero_art")
        hero.frame = assetDrawable("hero_overlay")
        when {
            !Shell.running() -> {
                hero.title = "NO LINK"
                hero.sub = "Shizuku is not running"
                hero.color = C.BAD
            }
            !Shell.granted() -> {
                hero.title = "LOCKED"
                hero.sub = "Grant Shizuku permission"
                hero.color = C.WARN
            }
            Shell.service == null -> {
                hero.title = "LINKING"
                hero.sub = "Connecting to Shizuku"
                hero.color = C.ACCENT2
            }
            else -> {
                hero.title = "READY"
                hero.sub = "${profiles.size} tuned app" + (if (profiles.size == 1) "" else "s")
                hero.color = C.GOOD
            }
        }
        col.put(hero, 10)

        if (!Shell.running()) {
            val st = panel()
            st.put(tv("Start Shizuku with wireless debugging or adb, then come back.", 12f, C.DIM))
            st.put(btn("Open Shizuku", BTN_PRIMARY) { openShizuku() }, 10)
            col.put(st, 8)
        } else if (!Shell.granted()) {
            val st = panel()
            st.put(tv("Nova Tuner needs Shizuku permission to run per-app commands.", 12f, C.DIM))
            st.put(btn("Grant permission", BTN_PRIMARY) { Shell.requestPermission() }, 10)
            col.put(st, 8)
        }

        val ov = panel()
        ov.put(switchRow("Floating panel", "Quick presets over any game", OverlayService.running) { toggleOverlay(it) })
        col.put(ov, 12)

        col.put(sectionLabel("Tuned apps"), 22)
        if (profiles.isEmpty()) col.put(tv("Nothing yet. Add a game such as CODM to begin.", 13f, C.DIM), 8)
        for (p in profiles) {
            col.put(appRow(iconOf(p.pkg), p.label, Tuner.summary(p)) { showEditor(p, true, false) }, 8)
        }
        col.put(btn("Add app or game", BTN_PRIMARY) { showPicker() }, 16)
        col.put(btn("Shell", BTN_GHOST) { showShell() }, 8)
        setScreen(col, animate)
    }

    private fun newProfile(pkg: String, label: String): Profile {
        val p = Profile(pkg, label)
        val g = KnownGames.MAP[pkg]
        if (g != null) Presets.byName(g.preset)?.applyTo(p)
        return p
    }

    private fun showPicker() {
        onHome = false
        back = { showHome(true) }
        val col = vbox(18)
        col.put(tv("CHOOSE AN APP", 22f, C.TEXT, true, 0.12f))
        val search = EditText(this)
        search.hint = "Search apps or package name"
        search.setTextColor(C.TEXT)
        search.setHintTextColor(C.DIM)
        search.typeface = Fonts.medium
        search.inputType = InputType.TYPE_CLASS_TEXT
        search.background = cut(C.PANEL, 10)
        search.setPadding(dp(14), dp(12), dp(14), dp(12))
        col.put(search, 12)
        val list = vbox()
        col.put(list, 12)
        col.put(btn("Back", BTN_GHOST) { showHome(true) }, 12)
        setScreen(col)

        val apps = mutableListOf<Pair<String, ApplicationInfo>>()
        fun open(info: ApplicationInfo, label: String) {
            val existing = profiles.firstOrNull { it.pkg == info.packageName }
            showEditor(existing ?: newProfile(info.packageName, label), true, false)
        }
        fun render(q: String) {
            list.removeAllViews()
            val query = q.trim().lowercase()
            val match = apps.filter { (label, info) ->
                query.isEmpty() || label.lowercase().contains(query) || info.packageName.lowercase().contains(query)
            }
            val detected = match.filter { KnownGames.MAP.containsKey(it.second.packageName) }
            if (detected.isNotEmpty()) {
                list.put(sectionLabel("Detected games"), 4)
                for ((label, info) in detected) {
                    val g = KnownGames.MAP[info.packageName]
                    list.put(appRow(iconOf(info.packageName), label, "Preset ready: " + (g?.preset ?: "")) { open(info, label) }, 8)
                }
            }
            list.put(sectionLabel("All apps"), 16)
            var shown = 0
            for ((label, info) in match) {
                if (shown >= 100) break
                shown++
                list.put(appRow(iconOf(info.packageName), label, info.packageName) { open(info, label) }, 8)
            }
            if (shown == 0) list.put(tv("No matches.", 13f, C.DIM), 8)
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                render(s?.toString() ?: "")
            }
        })
        Thread {
            val pm = packageManager
            val found = pm.getInstalledApplications(0)
                .filter { it.packageName != packageName && pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { Pair(pm.getApplicationLabel(it).toString(), it) }
                .sortedBy { it.first.lowercase() }
            runOnUiThread {
                apps.addAll(found)
                render(search.text.toString())
            }
        }.start()
    }

    private fun showEditor(p: Profile, animate: Boolean, keepScroll: Boolean) {
        onHome = false
        back = { showHome(true) }
        val col = vbox(18)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val iv = ImageView(this)
        val ic = iconOf(p.pkg)
        if (ic != null) iv.setImageDrawable(ic)
        head.addView(iv, LinearLayout.LayoutParams(dp(52), dp(52)))
        val titles = vbox()
        titles.addView(tv(p.label, 20f, C.TEXT, true))
        titles.addView(tv(p.pkg, 12f, C.DIM))
        val tlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tlp.leftMargin = dp(12)
        head.addView(titles, tlp)
        col.put(head)
        col.put(tv("These tweaks apply to this app only.", 12f, C.DIM), 6)

        val rec = KnownGames.MAP[p.pkg]
        val presetHint = if (rec != null) "Recommended for this game: ${rec.preset}" else "Load a set of values, then tune it"
        col.put(chipCard("Presets", presetHint, Presets.ALL.map { it.name }, -1) {
            Presets.ALL[it].applyTo(p)
            showEditor(p, false, true)
        }, 14)
        col.put(chipCard("Game mode", "Android Game Mode for this app", Tuner.MODE_LABELS, p.gameMode) { p.gameMode = it }, 10)
        col.put(chipCard("Resolution scale", "Render lower than native for less GPU load", Tuner.DOWNSCALE_LABELS, p.downscale) { p.downscale = it }, 10)
        col.put(chipCard("FPS cap", "Frame rate limit requested for this app", Tuner.FPS_LABELS, p.fps) { p.fps = it }, 10)
        col.put(chipCard("Ahead-of-time compile", "Faster loading, slow one time", Tuner.COMPILE_LABELS, p.compile) { p.compile = it }, 10)

        val toggles = panel()
        toggles.put(switchRow("ANGLE renderer", "Needs ANGLE on the device, ignored if missing", p.angle) { p.angle = it })
        toggles.put(switchRow("Keep app active", "Prevent standby throttling", p.standby) { p.standby = it })
        toggles.put(switchRow("Battery whitelist", "Exempt from Doze for this app", p.whitelist) { p.whitelist = it })
        toggles.put(switchRow("Kill background apps", "Frees RAM before launch", p.killBg) { p.killBg = it })
        col.put(toggles, 10)

        val scan = ScanBar(this)
        val log = tv("", 12f, C.GOOD)
        log.typeface = android.graphics.Typeface.MONOSPACE
        log.setTextIsSelectable(true)

        fun persist() {
            if (!profiles.contains(p)) profiles.add(p)
            Store.save(this, profiles)
        }

        fun run(script: String, after: (() -> Unit)? = null) {
            if (Shell.service == null) {
                toast("Shizuku is not connected")
                return
            }
            log.text = ""
            scan.active = true
            Shell.exec(script) { out ->
                scan.active = false
                log.text = out
                after?.invoke()
            }
        }

        col.put(btn("Apply and launch", BTN_GOOD) {
            persist()
            run(Tuner.applyScript(p, profiles, true)) { launch(p.pkg) }
        }, 16)
        col.put(btn("Apply", BTN_PRIMARY) {
            persist()
            run(Tuner.applyScript(p, profiles, false))
        }, 8)
        col.put(btn("Save only", BTN_GHOST) {
            persist()
            toast("Saved")
        }, 8)
        col.put(btn("Restore defaults", BTN_GHOST) {
            run(Tuner.restoreScript(p, profiles.filter { it !== p }))
        }, 8)
        col.put(btn("Delete profile", BTN_DANGER) {
            run(Tuner.restoreScript(p, profiles.filter { it !== p })) {
                profiles.remove(p)
                Store.save(this, profiles)
                showHome(true)
            }
        }, 8)
        col.put(scan, 16)
        col.put(log, 8)
        setScreen(col, animate, keepScroll)
    }

    private fun showShell() {
        onHome = false
        back = { showHome(true) }
        val col = vbox(18)
        col.put(tv("SHELL", 22f, C.TEXT, true, 0.14f))
        col.put(tv("Runs as the Shizuku shell user, not root.", 12f, C.DIM), 2)
        val input = EditText(this)
        input.hint = "Command"
        input.setTextColor(C.TEXT)
        input.setHintTextColor(C.DIM)
        input.typeface = android.graphics.Typeface.MONOSPACE
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        input.background = cut(C.PANEL, 10)
        input.setPadding(dp(14), dp(12), dp(14), dp(12))
        col.put(input, 12)
        val scan = ScanBar(this)
        val out = tv("", 12f, C.GOOD)
        out.typeface = android.graphics.Typeface.MONOSPACE
        out.setTextIsSelectable(true)
        col.put(btn("Execute", BTN_PRIMARY) {
            val c = input.text.toString().trim()
            if (c.isEmpty()) return@btn
            if (Shell.service == null) {
                toast("Shizuku is not connected")
                return@btn
            }
            out.text = ""
            scan.active = true
            Shell.exec(c) {
                scan.active = false
                out.text = it
            }
        }, 10)
        col.put(scan, 14)
        col.put(out, 8)
        col.put(btn("Back", BTN_GHOST) { showHome(true) }, 14)
        setScreen(col)
    }
}
