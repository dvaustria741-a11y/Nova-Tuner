package com.zenkai.novatuner

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

object C {
    val BG = Color.parseColor("#07080C")
    val PANEL = Color.parseColor("#0E1117")
    val PANEL2 = Color.parseColor("#171C26")
    val LINE = Color.parseColor("#252C39")
    val ACCENT = Color.parseColor("#19F5D0")
    val ACCENT2 = Color.parseColor("#8B5CFF")
    val GOOD = Color.parseColor("#2BE38A")
    val WARN = Color.parseColor("#FFB020")
    val BAD = Color.parseColor("#FF5C7A")
    val TEXT = Color.parseColor("#EEF2F8")
    val DIM = Color.parseColor("#7C8799")
}

object Fonts {
    var bold: Typeface = Typeface.DEFAULT_BOLD
    var semi: Typeface = Typeface.DEFAULT_BOLD
    var medium: Typeface = Typeface.DEFAULT
    private var ready = false

    fun init(c: Context) {
        if (ready) return
        ready = true
        bold = load(c, "ChakraPetch-Bold.ttf", Typeface.DEFAULT_BOLD)
        semi = load(c, "ChakraPetch-SemiBold.ttf", Typeface.DEFAULT_BOLD)
        medium = load(c, "ChakraPetch-Medium.ttf", Typeface.DEFAULT)
    }

    private fun load(c: Context, name: String, fb: Typeface): Typeface =
        try {
            Typeface.createFromAsset(c.assets, "fonts/$name")
        } catch (e: Exception) {
            fb
        }
}

fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.sp(v: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

fun Context.assetDrawable(name: String): Drawable? {
    val id = resources.getIdentifier(name, "drawable", packageName)
    return if (id != 0) resources.getDrawable(id, theme) else null
}

fun drawCenterCrop(c: Canvas, art: Drawable, w: Float, h: Float) {
    val iw = art.intrinsicWidth.toFloat()
    val ih = art.intrinsicHeight.toFloat()
    if (iw <= 0f || ih <= 0f) return
    val s = max(w / iw, h / ih)
    c.save()
    c.clipRect(0f, 0f, w, h)
    c.translate((w - iw * s) / 2f, (h - ih * s) / 2f)
    c.scale(s, s)
    art.setBounds(0, 0, iw.toInt(), ih.toInt())
    art.draw(c)
    c.restore()
}

class CutDrawable(
    private val fill: Int,
    private val cut: Float,
    private val strokeW: Float = 0f,
    private val fillEnd: Int = 0
) : Drawable() {
    private val fp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        val i = strokeW / 2f
        path.reset()
        path.moveTo(i, i)
        path.lineTo(w - cut, i)
        path.lineTo(w - i, cut)
        path.lineTo(w - i, h - i)
        path.lineTo(cut, h - i)
        path.lineTo(i, h - cut)
        path.close()
        if (fillEnd != 0) {
            fp.shader = LinearGradient(0f, 0f, w, 0f, fill, fillEnd, Shader.TileMode.CLAMP)
        } else {
            fp.shader = null
            fp.color = fill
        }
        c.save()
        c.translate(b.left.toFloat(), b.top.toFloat())
        c.drawPath(path, fp)
        if (strokeW > 0f) {
            sp.strokeWidth = strokeW
            sp.shader = LinearGradient(0f, 0f, w, h, C.ACCENT, C.ACCENT2, Shader.TileMode.CLAMP)
            sp.alpha = 110
            c.drawPath(path, sp)
        }
        c.restore()
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

fun Context.cut(fill: Int, cutDp: Int = 10, stroke: Boolean = true, end: Int = 0): CutDrawable =
    CutDrawable(fill, dp(cutDp).toFloat(), if (stroke) max(1f, resources.displayMetrics.density) else 0f, end)

fun Context.tv(text: String, size: Float = 14f, color: Int = C.TEXT, bold: Boolean = false, spacing: Float = 0f): TextView =
    TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        typeface = if (bold) Fonts.bold else Fonts.medium
        letterSpacing = spacing
    }

fun Context.vbox(pad: Int = 0): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
    }

fun Context.panel(): LinearLayout = vbox(14).apply { background = cut(C.PANEL, 12) }

fun LinearLayout.put(v: View, top: Int = 0): LinearLayout {
    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    lp.topMargin = (top * resources.displayMetrics.density).toInt()
    addView(v, lp)
    return this
}

fun View.pressable(): View {
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                v.animate().scaleX(0.96f).scaleY(0.96f).setStartDelay(0).setDuration(90)
                    .setInterpolator(DecelerateInterpolator()).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(240)
                    .setInterpolator(OvershootInterpolator(3f)).start()
        }
        false
    }
    return this
}

fun ViewGroup.staggerIn(step: Long = 45) {
    val off = 36f * resources.displayMetrics.density
    for (i in 0 until childCount) {
        val v = getChildAt(i)
        v.alpha = 0f
        v.translationY = off
        v.animate().alpha(1f).translationY(0f)
            .setStartDelay(min(i, 12) * step).setDuration(380)
            .setInterpolator(DecelerateInterpolator(1.6f)).start()
    }
}

const val BTN_PRIMARY = 0
const val BTN_GHOST = 1
const val BTN_DANGER = 2
const val BTN_GOOD = 3

fun Context.btn(text: String, kind: Int = BTN_PRIMARY, onClick: () -> Unit): TextView {
    val t = TextView(this)
    t.text = text.uppercase()
    t.textSize = 13f
    t.typeface = Fonts.bold
    t.letterSpacing = 0.08f
    t.gravity = Gravity.CENTER
    when (kind) {
        BTN_PRIMARY -> {
            t.setTextColor(C.BG)
            t.background = cut(C.ACCENT, 10, true, Color.parseColor("#7FB4FF"))
        }
        BTN_GOOD -> {
            t.setTextColor(C.BG)
            t.background = cut(Color.parseColor("#2BE38A"), 10, true, C.ACCENT)
        }
        BTN_DANGER -> {
            t.setTextColor(C.BAD)
            t.background = cut(C.PANEL2, 10, false)
        }
        else -> {
            t.setTextColor(C.ACCENT)
            t.background = cut(C.PANEL2, 10, false)
        }
    }
    t.setPadding(dp(16), dp(14), dp(16), dp(14))
    t.setOnClickListener { onClick() }
    t.pressable()
    return t
}

fun Context.chips(options: List<String>, selected: Int, onSelect: (Int) -> Unit): View {
    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    val views = mutableListOf<TextView>()
    fun refresh(sel: Int) {
        views.forEachIndexed { i, v ->
            val on = i == sel
            v.setTextColor(if (on) C.BG else C.TEXT)
            v.background = if (on) cut(C.ACCENT, 6, false, Color.parseColor("#7FB4FF")) else cut(C.PANEL2, 6, false)
        }
    }
    options.forEachIndexed { i, o ->
        val v = TextView(this)
        v.text = o
        v.textSize = 13f
        v.typeface = Fonts.semi
        v.gravity = Gravity.CENTER
        v.setPadding(dp(14), dp(9), dp(14), dp(9))
        v.setOnClickListener {
            refresh(i)
            onSelect(i)
            v.animate().scaleX(1.1f).scaleY(1.1f).setStartDelay(0).setDuration(90).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(180).start()
            }.start()
        }
        views.add(v)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.rightMargin = dp(8)
        row.addView(v, lp)
    }
    refresh(selected)
    return HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(row)
    }
}

fun Context.chipCard(title: String, hint: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit): LinearLayout {
    val c = panel()
    c.put(tv(title.uppercase(), 13f, C.TEXT, true, 0.1f))
    c.put(tv(hint, 12f, C.DIM), 2)
    c.put(chips(options, selected, onSelect), 10)
    return c
}

fun Context.sectionLabel(text: String): TextView = tv(text.uppercase(), 11f, C.ACCENT, true, 0.2f)

fun Context.dot(color: Int): View {
    val v = View(this)
    v.background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }
    val a = ObjectAnimator.ofFloat(v, "alpha", 1f, 0.25f).apply {
        duration = 900
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
    }
    v.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(x: View) {
            a.start()
        }

        override fun onViewDetachedFromWindow(x: View) {
            a.cancel()
        }
    })
    return v
}

class ToggleView(c: Context, checked: Boolean, private val onChange: (Boolean) -> Unit) : View(c) {
    private var progress = if (checked) 1f else 0f
    private var on = checked
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ev = ArgbEvaluator()
    private var anim: ValueAnimator? = null

    init {
        isClickable = true
        setOnClickListener {
            setOn(!on)
            onChange(on)
        }
    }

    private fun setOn(v: Boolean) {
        on = v
        anim?.cancel()
        val a = ValueAnimator.ofFloat(progress, if (v) 1f else 0f)
        a.duration = 220
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
        anim = a
        a.start()
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(context.dp(50), context.dp(28))
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        p.style = Paint.Style.FILL
        p.color = ev.evaluate(progress, C.PANEL2, C.ACCENT) as Int
        c.drawRoundRect(0f, 0f, w, h, r, r, p)
        val tx = r + progress * (w - 2f * r)
        p.color = ev.evaluate(progress, C.DIM, C.BG) as Int
        c.drawCircle(tx, r, r - context.dp(5), p)
    }
}

fun Context.switchRow(label: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(8), 0, dp(8))
    }
    val col = vbox()
    col.addView(tv(label, 15f, C.TEXT, true))
    col.addView(tv(sub, 12f, C.DIM))
    row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    row.addView(ToggleView(this, checked, onChange))
    return row
}

class HeroView(c: Context) : View(c) {
    var title = "NO LINK"
    var sub = "Shizuku is not running"
    var color = C.BAD
    var art: Drawable? = null
    var frame: Drawable? = null

    private var t = 0f
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private val anim = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 7000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            t = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim.start()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), context.dp(240))
    }

    private fun d(v: Float) = v * resources.displayMetrics.density

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h * 0.46f
        val r = h * 0.28f

        art?.let {
            it.alpha = 190
            drawCenterCrop(c, it, w, h)
        }

        glow.shader = RadialGradient(cx, cy, r * 2f, withAlpha(color, 80), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 2f, glow)

        stroke.shader = null
        stroke.color = C.LINE
        stroke.strokeWidth = d(2f)
        c.drawCircle(cx, cy, r, stroke)

        stroke.color = withAlpha(C.TEXT, 60)
        stroke.strokeWidth = d(1.5f)
        for (i in 0 until 60) {
            c.save()
            c.rotate(i * 6f + t * 0.15f, cx, cy)
            val len = if (i % 5 == 0) d(9f) else d(4f)
            c.drawLine(cx, cy - r - d(10f), cx, cy - r - d(10f) - len, stroke)
            c.restore()
        }

        stroke.color = color
        stroke.strokeWidth = d(4f)
        rect.set(cx - r, cy - r, cx + r, cy + r)
        c.save()
        c.rotate(t, cx, cy)
        c.drawArc(rect, 0f, 100f, false, stroke)
        c.restore()

        stroke.color = C.ACCENT2
        stroke.strokeWidth = d(3f)
        val r2 = r * 0.8f
        rect.set(cx - r2, cy - r2, cx + r2, cy + r2)
        c.save()
        c.rotate(-t * 1.6f, cx, cy)
        c.drawArc(rect, 0f, 60f, false, stroke)
        c.drawArc(rect, 180f, 60f, false, stroke)
        c.restore()

        tp.typeface = Fonts.bold
        tp.color = C.TEXT
        tp.textSize = context.sp(22f)
        tp.letterSpacing = 0.08f
        c.drawText(title, cx, cy + d(8f), tp)

        tp.typeface = Fonts.medium
        tp.color = C.DIM
        tp.textSize = context.sp(12f)
        tp.letterSpacing = 0.05f
        c.drawText(sub, cx, cy + r + d(40f), tp)

        frame?.let {
            it.setBounds(0, 0, width, height)
            it.draw(c)
        }
    }
}

class BackdropView(c: Context) : View(c) {
    var art: Drawable? = null
    var layer: Drawable? = null
    private var t = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 9000
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = LinearInterpolator()
        addUpdateListener {
            t = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim.start()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawColor(C.BG)
        art?.let { drawCenterCrop(c, it, w, h) }

        p.shader = RadialGradient(w * (0.85f - 0.12f * t), h * 0.10f, w, withAlpha(C.ACCENT2, 70), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = RadialGradient(w * (0.10f + 0.12f * t), h * 0.92f, w * 0.9f, withAlpha(C.ACCENT, 45), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)

        layer?.let {
            c.save()
            c.translate((t - 0.5f) * 24f * resources.displayMetrics.density, 0f)
            c.scale(1.08f, 1.08f, w / 2f, h / 2f)
            drawCenterCrop(c, it, w, h)
            c.restore()
        }

        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f
        p.color = withAlpha(C.TEXT, 10)
        val gap = 30f * resources.displayMetrics.density
        var x = -h * 0.577f
        while (x < w) {
            c.drawLine(x, h, x + h * 0.577f, 0f, p)
            x += gap
        }
    }
}

class ScanBar(c: Context) : View(c) {
    private var t = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1100
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            t = it.animatedValue as Float
            invalidate()
        }
    }

    var active: Boolean = false
        set(v) {
            field = v
            if (v) {
                if (isAttachedToWindow && !anim.isStarted) anim.start()
            } else {
                anim.cancel()
            }
            invalidate()
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (active) anim.start()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), context.dp(3))
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.shader = null
        p.color = C.PANEL2
        c.drawRect(0f, 0f, w, h, p)
        if (!active) return
        val bw = w * 0.35f
        val x = (w + bw) * t - bw
        p.shader = LinearGradient(x, 0f, x + bw, 0f, intArrayOf(Color.TRANSPARENT, C.ACCENT, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        c.drawRect(x, 0f, x + bw, h, p)
    }
}

class OrbView(c: Context) : View(c) {
    private var t = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bolt = Path()
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1800
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            t = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim.start()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(context.dp(56), context.dp(56))
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val cx = w / 2f
        val r = w / 2f * 0.74f
        val density = resources.displayMetrics.density

        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * density
        p.color = withAlpha(C.ACCENT, ((1f - t) * 140).toInt())
        c.drawCircle(cx, cx, r + (w / 2f - r) * t, p)

        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cx, r, C.PANEL2, C.BG, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cx, r, p)

        p.shader = LinearGradient(0f, 0f, w, w, C.ACCENT, C.ACCENT2, Shader.TileMode.CLAMP)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * density
        c.drawCircle(cx, cx, r, p)

        bolt.reset()
        bolt.moveTo(w * 0.57f, w * 0.20f)
        bolt.lineTo(w * 0.36f, w * 0.58f)
        bolt.lineTo(w * 0.50f, w * 0.58f)
        bolt.lineTo(w * 0.44f, w * 0.82f)
        bolt.lineTo(w * 0.68f, w * 0.44f)
        bolt.lineTo(w * 0.54f, w * 0.44f)
        bolt.close()
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = C.ACCENT
        c.drawPath(bolt, p)
    }
}
