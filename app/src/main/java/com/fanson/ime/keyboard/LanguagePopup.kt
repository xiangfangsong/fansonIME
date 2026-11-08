package com.fanson.ime.keyboard

import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import com.fanson.ime.R
import com.fanson.ime.engine.InputLanguage

class LanguagePopup(private val ui: Ui) {
    private var popup: PopupWindow? = null
    val isShowing: Boolean get() = popup?.isShowing == true
    fun dismiss() { popup?.dismiss(); popup = null }

    fun show(anchor: View, current: InputLanguage, select: (InputLanguage) -> Unit) {
        if (!anchor.isAttachedToWindow) return
        dismiss()
        val context = ui.context
        val frame = Rect().also { anchor.getWindowVisibleDisplayFrame(it) }
        val width = minOf(context.dp(208), frame.width() - context.dp(16)).coerceAtLeast(1)
        val list = ui.column().apply {
            contentDescription = "输入语言列表"
            setPadding(context.dp(4), context.dp(4), context.dp(4), context.dp(4))
            background = ui.round(ui.palette.surface, 12, true)
        }
        InputLanguage.entries.forEach { language ->
            list.addView(ui.button(language.label, language == current) { dismiss(); select(language) }.apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(context.dp(14), 0, context.dp(14), 0)
                isSelected = language == current
                if (isSelected) {
                    val check = context.getDrawable(R.drawable.ic_check)!!.mutate().apply { setTint(ui.palette.accentInk) }
                    setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, check, null)
                }
                stateDescription = if (isSelected) "当前输入语言" else "切换到${language.label}"
            }, LinearLayout.LayoutParams(-1, context.dp(48)))
        }
        list.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(frame.height(), View.MeasureSpec.AT_MOST))
        val location = IntArray(2).also { anchor.getLocationOnScreen(it) }
        val left = location[0].coerceIn(frame.left + context.dp(8), maxOf(frame.left + context.dp(8), frame.right - width - context.dp(8)))
        val top = (location[1] - list.measuredHeight - context.dp(6)).coerceAtLeast(frame.top)
        val start = if (anchor.rootView.layoutDirection == View.LAYOUT_DIRECTION_RTL) context.resources.displayMetrics.widthPixels - left - width else left
        popup = PopupWindow(list, width, ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
            setBackgroundDrawable(ui.round(ui.palette.surface, 12, true))
            isOutsideTouchable = true
            setIsLaidOutInScreen(true); setIsClippedToScreen(true)
            elevation = context.dp(8).toFloat()
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            showAtLocation(anchor, Gravity.TOP or Gravity.START, start, top)
        }
    }
}
