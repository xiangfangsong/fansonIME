package com.fanson.ime.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

data class Palette(val background: Int, val surface: Int, val key: Int, val ink: Int, val muted: Int, val accent: Int, val accentInk: Int, val stroke: Int) {
    companion object {
        fun of(context: Context, theme: String): Palette {
            val dark = theme == "dark" || (theme == "system" && context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
            fun c(hex: String) = Color.parseColor(hex)
            return if (dark) Palette(c("#17211F"), c("#22312D"), c("#30423C"), c("#F0F6F2"), c("#A5BCB0"), c("#91DAC2"), c("#10372B"), c("#43574E"))
            else Palette(c("#E8EEEB"), c("#F4F7F5"), Color.WHITE, c("#182F26"), c("#61786D"), c("#24776B"), Color.WHITE, c("#D5E0D9"))
        }
    }
}

fun Context.dp(value: Int) = (resources.displayMetrics.density * value).toInt()

class Ui(val context: Context, val palette: Palette) {
    fun round(color: Int, radius: Int = 10, border: Boolean = false): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = context.dp(radius).toFloat()
        if (border) setStroke(context.dp(1), palette.stroke)
    }
    fun column(): LinearLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    fun row(): LinearLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    fun text(value: String, size: Float = 16f, color: Int = palette.ink): TextView = TextView(context).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(8), context.dp(4), context.dp(8), context.dp(4))
    }
    fun button(label: String, accent: Boolean = false, action: () -> Unit): Button = styleButton(Button(context), label, accent, action)
    private fun styleButton(button: Button, label: String, accent: Boolean, action: () -> Unit): Button = button.apply {
        text = label; textSize = 16f; isAllCaps = false; minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(context.dp(3), 0, context.dp(3), 0)
        gravity = Gravity.CENTER
        setTextColor(if (accent) palette.accentInk else palette.ink)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), round(if (accent) palette.stroke else palette.background, 9))
            addState(intArrayOf(), round(if (accent) palette.accent else palette.key, 9, !accent))
        }
        setOnClickListener { action() }
        contentDescription = label
    }
    fun cornerHintButton(primary: String, alternate: String, insert: (String) -> Unit): Button =
        styleButton(CornerHintButton(context, alternate, palette.muted), primary, false) { insert(primary) }.apply {
            maxLines = 1
            stateDescription = "长按输入$alternate"
            setOnLongClickListener { insert(alternate); true }
        }
    fun punctuationButton(primary: String, alternate: String, insert: (String) -> Unit): Button =
        button(primary) { insert(primary) }.apply {
            text = SpannableString("$alternate\n$primary").apply {
                setSpan(AbsoluteSizeSpan(10, true), 0, alternate.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(palette.muted), 0, alternate.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            maxLines = 2
            includeFontPadding = false
            stateDescription = "长按输入$alternate"
            setOnLongClickListener { insert(alternate); true }
        }
    fun iconButton(icon: Int, description: String, accent: Boolean = false, action: () -> Unit): Button =
        button("", accent, action).apply {
            foreground = context.getDrawable(icon)!!.mutate().apply { setTint(if (accent) palette.accentInk else palette.ink) }
            foregroundGravity = Gravity.CENTER
            contentDescription = description
            tooltipText = description
        }
    fun title(value: String, size: Float = 24f): TextView = text(value, size).apply { setTypeface(typeface, Typeface.BOLD) }
    fun addWeighted(row: LinearLayout, view: View, weight: Float = 1f, height: Int = 48) {
        row.addView(view, LinearLayout.LayoutParams(0, context.dp(height), weight).apply { setMargins(context.dp(2), context.dp(2), context.dp(2), context.dp(2)) })
    }
}
