package com.zenkai.novatuner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max

class OverlayService : Service() {

    companion object {
        @Volatile
        var running = false
        const val CHANNEL = "nova_overlay"
    }

    private lateinit var wm: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var root: LinearLayout? = null
    private var orb: View? = null
    private var panel: LinearLayout? = null
    private var frontView: TextView? = null
    private var stateView: TextView? = null
    private var statusView: TextView? = null
    private var front: String? = null
    private var expanded = false
    private var savedY = -1
    private var savedX = -1
    private var attached = false
    private var hud: TextView? = null
    private var hudOn = false
    private var inFlight = false
    private val handler = Handler(Looper.getMainLooper())
    private val tick = Runnable { hudTick() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onCreate() {
        super.onCreate()
        Fonts.init(applicationContext)
        startFg()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        hudOn = getSharedPreferences("nova", 0).getBoolean("hud", false)
        buildOverlay()
        running = true
        if (hudOn) {
            Shell.bind(this)
            handler.postDelayed(tick, 800)
        }
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(tick)
        if (attached) {
            try {
                root?.let { wm.removeView(it) }
            } catch (e: Exception) {
            }
            attached = false
        }
        super.onDestroy()
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Floating panel", NotificationManager.IMPORTANCE_MIN))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Nova Tuner")
            .setContentText("Floating panel is active")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun buildOverlay() {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.VERTICAL
        root = r

        val art = assetDrawable("overlay_orb")
        val o: View = if (art != null) {
            val iv = android.widget.ImageView(this)
            iv.setImageDrawable(art)
            iv.layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
            iv
        } else {
            OrbView(this)
        }
        orb = o
        r.addView(o)

        val h = tv("", 11f, C.ACCENT, true)
        h.setSingleLine()
        h.setPadding(dp(8), dp(4), dp(8), dp(4))
        h.background = cut(C.PANEL, 6)
        h.visibility = if (hudOn) View.VISIBLE else View.GONE
        hud = h
        r.addView(h, hudLp())

        params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = dp(8)
        params.y = dp(180)

        var sx = 0f
        var sy = 0f
        var ox = 0
        var oy = 0
        var moved = false
        o.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX
                    sy = e.rawY
                    ox = params.x
                    oy = params.y
                    moved = false
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx
                    val dy = e.rawY - sy
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved) {
                        params.x = max(0, (ox + dx).toInt())
                        params.y = max(0, (oy + dy).toInt())
                        wm.updateViewLayout(r, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                    if (!moved && e.actionMasked == MotionEvent.ACTION_UP) toggle()
                    true
                }
                else -> false
            }
        }

        wm.addView(r, params)
        attached = true
    }

    private fun toggle() {
        if (expanded) collapse() else expand()
    }

    private fun labelOf(pkg: String): String =
        try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            KnownGames.MAP[pkg]?.label ?: pkg
        }

    private fun expand() {
        val r = root ?: return
        val o = orb ?: return
        Shell.bind(this)
        expanded = true
        val p = buildPanel()
        panel = p
        val screenH = resources.displayMetrics.heightPixels
        val screenW = resources.displayMetrics.widthPixels
        val panelW = minOf(dp(440), screenW - dp(16))
        val below = params.y < screenH * 0.55f
        if (params.x + panelW > screenW) {
            savedX = params.x
            params.x = max(0, screenW - panelW - dp(8))
        }
        p.measure(
            View.MeasureSpec.makeMeasureSpec(panelW, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST)
        )
        val ph = p.measuredHeight
        r.removeAllViews()
        val lp = LinearLayout.LayoutParams(panelW, ViewGroup.LayoutParams.WRAP_CONTENT)
        val h = hud
        if (below) {
            r.addView(o)
            if (h != null) r.addView(h, hudLp())
            lp.topMargin = dp(8)
            r.addView(p, lp)
            p.pivotY = 0f
        } else {
            savedY = params.y
            params.y = max(0, params.y - ph - dp(8))
            lp.bottomMargin = dp(8)
            r.addView(p, lp)
            r.addView(o)
            if (h != null) r.addView(h, hudLp())
            p.pivotY = ph.toFloat()
        }
        p.alpha = 0f
        p.scaleY = 0.85f
        p.animate().alpha(1f).scaleY(1f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        wm.updateViewLayout(r, params)
        detectFront()
    }

    private fun collapse() {
        val r = root ?: return
        val p = panel ?: return
        expanded = false
        p.animate().alpha(0f).scaleY(0.85f).setDuration(150).withEndAction {
            r.removeView(p)
            if (savedY >= 0) {
                params.y = savedY
                savedY = -1
            }
            if (savedX >= 0) {
                params.x = savedX
                savedX = -1
            }
            wm.updateViewLayout(r, params)
        }.start()
    }

    private fun buildPanel(): LinearLayout {
        val p = vbox(18)
        p.background = slice("fx_panel", 55, 14) ?: cut(C.PANEL, 12)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val hl = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        hl.leftMargin = dp(18)
        head.addView(tv("NOVA TUNER", 13f, C.TEXT, true, 0.12f), hl)
        p.put(head)

        val body = LinearLayout(this)
        body.orientation = LinearLayout.HORIZONTAL

        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        left.addView(tv("FRONT APP", 10f, C.ACCENT, true, 0.2f))
        val fv = tv("Detecting", 15f, C.TEXT, true)
        fv.setSingleLine()
        fv.ellipsize = TextUtils.TruncateAt.END
        frontView = fv
        left.put(fv, 2)
        val sv = tv("", 12f, C.DIM)
        sv.setSingleLine()
        stateView = sv
        left.put(sv, 2)
        left.put(switchRow("Live HUD", "FPS, temp, free RAM", hudOn) { setHud(it) }, 8)
        val st = tv("Restart the game after applying", 11f, C.DIM)
        st.setSingleLine()
        st.ellipsize = TextUtils.TruncateAt.END
        statusView = st
        left.put(st, 6)
        body.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val right = LinearLayout(this)
        right.orientation = LinearLayout.VERTICAL
        val grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        for (row in 0 until 2) {
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            for (col in 0 until 2) {
                val pr = Presets.QUICK[row * 2 + col]
                val b = btn(pr.name, BTN_GHOST) { applyPreset(pr) }
                b.textSize = 11f
                b.setPadding(dp(6), dp(10), dp(6), dp(10))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                lp.setMargins(dp(3), dp(3), dp(3), dp(3))
                line.addView(b, lp)
            }
            grid.addView(line)
        }
        right.addView(grid)
        right.put(btn("Open Nova Tuner", BTN_PRIMARY) {
            val i = Intent(this, MainActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        }, 6)
        val rl = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        rl.leftMargin = dp(18)
        body.addView(right, rl)

        p.put(body, 10)
        return p
    }

    private fun updateFront() {
        val f = front
        frontView?.text = if (f == null) "None detected" else labelOf(f)
        stateView?.text = when {
            Shell.service == null -> "Shizuku not connected"
            f == null -> "Open a game first"
            Store.load(this).any { it.pkg == f } -> "Profile saved"
            else -> "No profile yet"
        }
    }

    private fun detectFront() {
        if (Shell.service == null) {
            front = null
            updateFront()
            return
        }
        Shell.exec("dumpsys activity activities | grep -E 'ResumedActivity' | head -n 2") { out ->
            val m = Regex("u\\d+ ([A-Za-z0-9_.]+)/").find(out)
            val pkg = m?.groupValues?.get(1)
            front = if (pkg == null || pkg == packageName) null else pkg
            updateFront()
        }
    }

    private fun hudLp(): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(4)
        return lp
    }

    private fun setHud(on: Boolean) {
        hudOn = on
        getSharedPreferences("nova", 0).edit().putBoolean("hud", on).apply()
        hud?.visibility = if (on) View.VISIBLE else View.GONE
        root?.let { if (attached) wm.updateViewLayout(it, params) }
        handler.removeCallbacks(tick)
        if (on) {
            Shell.bind(this)
            handler.post(tick)
        }
    }

    private fun hudTick() {
        if (!hudOn || !attached) return
        if (Shell.service == null) {
            Shell.bind(this)
            hud?.text = "Shizuku not connected"
            handler.postDelayed(tick, 3000)
            return
        }
        if (inFlight) return
        inFlight = true
        Shell.exec(Stats.script(front)) { out ->
            inFlight = false
            if (hudOn && attached) {
                val r = Stats.parse(out)
                val f = r.front
                front = if (f == null || f == packageName) null else f
                hud?.text = Stats.format(r)
                handler.postDelayed(tick, 2000)
            }
        }
    }

    private fun applyPreset(pr: Preset) {
        val pkg = front
        if (pkg == null) {
            Toast.makeText(this, "No game detected", Toast.LENGTH_SHORT).show()
            return
        }
        if (Shell.service == null) {
            Toast.makeText(this, "Shizuku is not connected", Toast.LENGTH_SHORT).show()
            return
        }
        val list = Store.load(this)
        var prof = list.firstOrNull { it.pkg == pkg }
        if (prof == null) {
            prof = Profile(pkg, labelOf(pkg))
            list.add(prof)
        }
        pr.applyTo(prof)
        Store.save(this, list)
        statusView?.text = "Applying ${pr.name}"
        Shell.exec(Tuner.applyScript(prof, list, false)) { out ->
            val bad = out.contains("Exception") || out.contains("Error")
            statusView?.text = if (bad) "Some commands failed, see app log" else "${pr.name} applied, restart game"
            updateFront()
        }
    }
}
