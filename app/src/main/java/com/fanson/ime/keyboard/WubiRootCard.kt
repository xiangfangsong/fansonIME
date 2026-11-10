package com.fanson.ime.keyboard

import android.graphics.Typeface
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import org.json.JSONObject

/** Bundled component shapes include roots that cannot be displayed with ordinary Unicode fonts. */
class WubiRootCard(private val ui: Ui) {
    private val context = ui.context
    private val typeface by lazy { Typeface.createFromAsset(context.assets, "fonts/fanson_wubi_roots.ttf") }
    private val keys by lazy {
        context.assets.open("wubi_roots.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("keys") }
    }
    private var popup: PopupWindow? = null

    fun dismiss() { popup?.dismiss(); popup = null }

    fun show(anchor: View, key: Char) {
        if (!anchor.isAttachedToWindow || !keys.has(key.toString())) return
        dismiss()
        val visible = Rect().also { anchor.getWindowVisibleDisplayFrame(it) }
        val width = minOf(context.dp(304), visible.width() - context.dp(16)).coerceAtLeast(1)
        val card = ui.column().apply {
            setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
            background = ui.round(ui.palette.surface, 14, true)
            contentDescription = "${key.uppercaseChar()} 键完整字根"
        }
        val heading = ui.row()
        heading.addView(ui.title("${key.uppercaseChar()} 键 · 五笔86字根", 17f), LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(ui.button("×") { dismiss() }.apply { contentDescription = "关闭字根卡片" }, LinearLayout.LayoutParams(context.dp(36), context.dp(36)))
        card.addView(heading)
        val columns = ((width - context.dp(24)) / context.dp(42)).coerceIn(1, 6)
        val grid = GridLayout(context).apply { columnCount = columns; contentDescription = "完整字根网格" }
        val roots = keys.getJSONArray(key.toString())
        for (index in 0 until roots.length()) {
            grid.addView(ui.text(roots.getString(index), 30f).apply {
                typeface = this@WubiRootCard.typeface
                gravity = Gravity.CENTER
                contentDescription = if (roots.getString(index) == "\uE000")
                    "${key.uppercaseChar()} 键字根：礻去末点，衤去末两笔"
                else "${key.uppercaseChar()} 键字根第${index + 1}项"
                setPadding(0, 0, 0, 0)
            }, GridLayout.LayoutParams().apply { this.width = (width - context.dp(24)) / columns; height = context.dp(42) })
        }
        val scroll = ScrollView(context).apply { addView(grid) }
        val gridHeight = ((roots.length() + columns - 1) / columns) * context.dp(42)
        val maxGridHeight = (visible.height() - context.dp(110)).coerceAtLeast(context.dp(42))
        card.addView(scroll, LinearLayout.LayoutParams(-1, minOf(gridHeight, maxGridHeight)))
        card.addView(ui.text("点击外部或继续输入可关闭", 12f, ui.palette.muted))
        card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(visible.height(), View.MeasureSpec.AT_MOST))
        val location = IntArray(2).also { anchor.getLocationOnScreen(it) }
        val left = (location[0] + anchor.width / 2 - width / 2).coerceIn(visible.left + context.dp(8), maxOf(visible.left + context.dp(8), visible.right - width - context.dp(8)))
        val top = (location[1] - card.measuredHeight - context.dp(8)).coerceAtLeast(visible.top)
        val start = if (anchor.rootView.layoutDirection == View.LAYOUT_DIRECTION_RTL) context.resources.displayMetrics.widthPixels - left - width else left
        popup = PopupWindow(card, width, ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
            setBackgroundDrawable(ui.round(ui.palette.surface, 14, true))
            isOutsideTouchable = true
            setIsLaidOutInScreen(true)
            setIsClippedToScreen(true)
            elevation = context.dp(8).toFloat()
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            showAtLocation(anchor, Gravity.TOP or Gravity.START, start, top)
        }
    }
}
