package com.zenkai.novatuner

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

object C {
    val BG = Color.parseColor("#0B0D12")
    val CARD = Color.parseColor("#141821")
    val CARD2 = Color.parseColor("#1E2430")
    val ACCENT = Color.parseColor("#2BD9FE")
    val GOOD = Color.parseColor("#2BE38A")
    val BAD = Color.parseColor("#FF5C6C")
    val TEXT = Color.parseColor("#EAF0F7")
    val DIM = Color.parseColor("#8A94A6")
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun rounded(fill: Int, radius: Float): GradientDrawable =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius
    }

fun Context.tv(text: String, size: Float = 14f, color: Int = C.TEXT, bold: Boolean = false): TextView =
    TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

fun Context.vbox(pad: Int = 0): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
    }

fun Context.card(): LinearLayout =
    vbox(14).apply { background = rounded(C.CARD, dp(14).toFloat()) }

fun LinearLayout.put(v: View, top: Int = 0): LinearLayout {
    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    lp.topMargin = (top * resources.displayMetrics.density).toInt()
    addView(v, lp)
    return this
}

fun Context.btn(text: String, color: Int = C.ACCENT, filled: Boolean = true, onClick: () -> Unit): TextView =
    TextView(this).apply {
        this.text = text
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(if (filled) C.BG else color)
        background = rounded(if (filled) color else C.CARD2, dp(12).toFloat())
        setPadding(dp(16), dp(13), dp(16), dp(13))
        setOnClickListener { onClick() }
    }

fun Context.chips(options: List<String>, selected: Int, onSelect: (Int) -> Unit): View {
    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    val views = mutableListOf<TextView>()
    fun refresh(sel: Int) {
        views.forEachIndexed { i, v ->
            val on = i == sel
            v.setTextColor(if (on) C.BG else C.TEXT)
            v.background = rounded(if (on) C.ACCENT else C.CARD2, dp(10).toFloat())
        }
    }
    options.forEachIndexed { i, o ->
        val v = TextView(this)
        v.text = o
        v.textSize = 13f
        v.gravity = Gravity.CENTER
        v.setPadding(dp(14), dp(9), dp(14), dp(9))
        v.setOnClickListener {
            refresh(i)
            onSelect(i)
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
    val c = card()
    c.put(tv(title, 14f, C.TEXT, true))
    c.put(tv(hint, 12f, C.DIM), 2)
    c.put(chips(options, selected, onSelect), 10)
    return c
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
    val sw = Switch(this)
    sw.isChecked = checked
    sw.setOnCheckedChangeListener { _, v -> onChange(v) }
    row.addView(sw)
    return row
}
