package com.zenkai.novatuner

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
        profiles = Store.load(this)
        holder = ScrollView(this).apply {
            setBackgroundColor(C.BG)
            isFillViewport = true
        }
        setContentView(holder)
        Shell.onChange = { runOnUiThread { refresh() } }
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permResult)
        showHome()
    }

    override fun onResume() {
        super.onResume()
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
        if (onHome) showHome()
    }

    private fun setScreen(v: View) {
        holder.removeAllViews()
        holder.addView(v, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        holder.scrollTo(0, 0)
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

    private fun appRow(icon: Drawable?, title: String, sub: String, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(C.CARD, dp(12).toFloat())
            setOnClickListener { onClick() }
        }
        val iv = ImageView(this)
        if (icon != null) iv.setImageDrawable(icon)
        row.addView(iv, LinearLayout.LayoutParams(dp(42), dp(42)))
        val col = vbox()
        col.addView(tv(title, 15f, C.TEXT, true))
        col.addView(tv(sub, 12f, C.DIM))
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp.leftMargin = dp(12)
        row.addView(col, lp)
        return row
    }

    private fun showHome() {
        onHome = true
        back = null
        val col = vbox(18)
        col.put(tv("NOVA TUNER", 26f, C.ACCENT, true))
        col.put(tv("Per-app tuning through Shizuku. Only the apps you add here are touched.", 13f, C.DIM), 4)

        val st = card()
        val state: Pair<String, Int> = when {
            !Shell.running() -> Pair("Shizuku is not running", C.BAD)
            !Shell.granted() -> Pair("Permission not granted", C.BAD)
            Shell.service == null -> Pair("Connecting to Shizuku…", C.DIM)
            else -> Pair("Shizuku connected", C.GOOD)
        }
        st.put(tv("● " + state.first, 15f, state.second, true))
        if (!Shell.running()) {
            st.put(tv("Start Shizuku (wireless debugging or adb), then come back.", 12f, C.DIM), 4)
            st.put(btn("Open Shizuku") { openShizuku() }, 10)
        } else if (!Shell.granted()) {
            st.put(btn("Grant permission") { Shell.requestPermission() }, 10)
        }
        col.put(st, 16)

        col.put(tv("TUNED APPS", 12f, C.DIM, true), 22)
        if (profiles.isEmpty()) col.put(tv("Nothing yet. Add a game such as CODM to begin.", 13f, C.DIM), 8)
        for (p in profiles) {
            col.put(appRow(iconOf(p.pkg), p.label, Tuner.summary(p)) { showEditor(p) }, 8)
        }
        col.put(btn("+ Add app / game") { showPicker() }, 16)
        col.put(btn("Shell", C.ACCENT, false) { showShell() }, 8)
        setScreen(col)
    }

    private fun showPicker() {
        onHome = false
        back = { showHome() }
        val col = vbox(18)
        col.put(tv("Choose an app", 22f, C.ACCENT, true))
        val search = EditText(this)
        search.hint = "Search apps or package name"
        search.setTextColor(C.TEXT)
        search.setHintTextColor(C.DIM)
        search.inputType = InputType.TYPE_CLASS_TEXT
        search.background = rounded(C.CARD, dp(12).toFloat())
        search.setPadding(dp(14), dp(12), dp(14), dp(12))
        col.put(search, 12)
        val list = vbox()
        col.put(list, 12)
        col.put(btn("Back", C.ACCENT, false) { showHome() }, 12)
        setScreen(col)

        val apps = mutableListOf<Pair<String, ApplicationInfo>>()
        fun render(q: String) {
            list.removeAllViews()
            val query = q.trim().lowercase()
            var shown = 0
            for ((label, info) in apps) {
                if (query.isNotEmpty() && !label.lowercase().contains(query) && !info.packageName.lowercase().contains(query)) continue
                if (shown >= 100) break
                shown++
                list.put(
                    appRow(iconOf(info.packageName), label, info.packageName) {
                        val existing = profiles.firstOrNull { it.pkg == info.packageName }
                        showEditor(existing ?: Profile(info.packageName, label))
                    }, 8
                )
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

    private fun showEditor(p: Profile) {
        onHome = false
        back = { showHome() }
        val col = vbox(18)

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val iv = ImageView(this)
        val ic = iconOf(p.pkg)
        if (ic != null) iv.setImageDrawable(ic)
        head.addView(iv, LinearLayout.LayoutParams(dp(48), dp(48)))
        val titles = vbox()
        titles.addView(tv(p.label, 20f, C.TEXT, true))
        titles.addView(tv(p.pkg, 12f, C.DIM))
        val tlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tlp.leftMargin = dp(12)
        head.addView(titles, tlp)
        col.put(head)
        col.put(tv("These tweaks apply to this app only.", 12f, C.DIM), 6)

        col.put(chipCard("Game mode", "Android Game Mode for this app", Tuner.MODE_LABELS, p.gameMode) { p.gameMode = it }, 14)
        col.put(chipCard("Resolution scale", "Render lower than native for less GPU load", Tuner.DOWNSCALE_LABELS, p.downscale) { p.downscale = it }, 10)
        col.put(chipCard("FPS cap", "Frame rate limit requested for this app", Tuner.FPS_LABELS, p.fps) { p.fps = it }, 10)
        col.put(chipCard("Ahead-of-time compile", "Compiles the app for faster loading (slow, one time)", Tuner.COMPILE_LABELS, p.compile) { p.compile = it }, 10)

        val toggles = card()
        toggles.put(switchRow("ANGLE renderer", "Needs ANGLE on your device; ignored if missing", p.angle) { p.angle = it })
        toggles.put(switchRow("Keep app active", "Prevent standby throttling", p.standby) { p.standby = it })
        toggles.put(switchRow("Battery whitelist", "Exempt from Doze for this app", p.whitelist) { p.whitelist = it })
        toggles.put(switchRow("Kill background apps", "Frees RAM before launch", p.killBg) { p.killBg = it })
        col.put(toggles, 10)

        val log = tv("", 12f, C.GOOD)
        log.typeface = Typeface.MONOSPACE
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
            log.text = "Running…"
            Shell.exec(script) { out ->
                log.text = out
                after?.invoke()
            }
        }

        col.put(btn("Apply & Launch", C.GOOD) {
            persist()
            run(Tuner.applyScript(p, profiles, true)) { launch(p.pkg) }
        }, 16)
        col.put(btn("Apply") {
            persist()
            run(Tuner.applyScript(p, profiles, false))
        }, 8)
        col.put(btn("Save only", C.ACCENT, false) {
            persist()
            toast("Saved")
        }, 8)
        col.put(btn("Restore defaults", C.ACCENT, false) {
            run(Tuner.restoreScript(p, profiles.filter { it !== p }))
        }, 8)
        col.put(btn("Delete profile", C.BAD, false) {
            run(Tuner.restoreScript(p, profiles.filter { it !== p })) {
                profiles.remove(p)
                Store.save(this, profiles)
                showHome()
            }
        }, 8)
        col.put(log, 14)
        setScreen(col)
    }

    private fun showShell() {
        onHome = false
        back = { showHome() }
        val col = vbox(18)
        col.put(tv("Shell", 22f, C.ACCENT, true))
        col.put(tv("Runs as the Shizuku shell user (not root).", 12f, C.DIM), 2)
        val input = EditText(this)
        input.hint = "Command"
        input.setTextColor(C.TEXT)
        input.setHintTextColor(C.DIM)
        input.typeface = Typeface.MONOSPACE
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        input.background = rounded(C.CARD, dp(12).toFloat())
        input.setPadding(dp(14), dp(12), dp(14), dp(12))
        col.put(input, 12)
        val out = tv("", 12f, C.GOOD)
        out.typeface = Typeface.MONOSPACE
        out.setTextIsSelectable(true)
        col.put(btn("Execute") {
            val c = input.text.toString().trim()
            if (c.isEmpty()) return@btn
            if (Shell.service == null) {
                toast("Shizuku is not connected")
                return@btn
            }
            out.text = "Running…"
            Shell.exec(c) { out.text = it }
        }, 10)
        col.put(out, 14)
        col.put(btn("Back", C.ACCENT, false) { showHome() }, 14)
        setScreen(col)
    }
}
