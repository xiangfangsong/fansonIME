package com.fanson.ime.keyboard

import android.content.res.ColorStateList
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.*

/** Keeps the category rail intact while only replacing the fixed-order grid. */
@android.annotation.SuppressLint("ViewConstructor")
class SymbolPanel(private val ui: Ui, private val keyHeight: Int, private val insert: (String) -> Unit) : LinearLayout(ui.context) {
    private val buttons = linkedMapOf<String, Button>()
    private val grid = GridLayout(context).apply { columnCount = 6; contentDescription = "符号网格" }
    private val gridScroll = scroll("符号列表").apply { addView(grid) }
    var selectedCategory: String = "常用"
        private set

    init {
        orientation = HORIZONTAL
        val rail = ui.column()
        SymbolCatalog.categories.forEach { category ->
            val key = ui.button(category.name) { selectCategory(category.name) }.apply {
                textSize = 14f
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_selected), ui.round(ui.palette.accent, 8))
                    addState(intArrayOf(android.R.attr.state_pressed), ui.round(ui.palette.background, 8))
                    addState(intArrayOf(), ui.round(ui.palette.key, 8, true))
                }
                setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()), intArrayOf(ui.palette.accentInk, ui.palette.ink)))
            }
            buttons[category.name] = key
            rail.addView(key, LayoutParams(-1, context.dp(40)).apply { setMargins(context.dp(2), context.dp(2), context.dp(4), context.dp(2)) })
        }
        addView(scroll("符号分类").apply { addView(rail) }, LayoutParams(context.dp(64), -1))
        addView(gridScroll, LayoutParams(0, -1, 1f))
        selectCategory("常用")
    }

    private fun scroll(description: String) = ScrollView(context).apply {
        contentDescription = description
        isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false
        isVerticalFadingEdgeEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
    }

    private fun selectCategory(name: String) {
        val category = SymbolCatalog.categories.first { it.name == name }
        selectedCategory = name
        buttons.forEach { (label, button) -> button.isSelected = label == name }
        grid.removeAllViews()
        category.symbols.forEach { symbol ->
            val button = ui.button(symbol) { insert(symbol) }.apply { textSize = if (symbol.length > 3) 14f else 20f }
            grid.addView(button, GridLayout.LayoutParams().apply {
                width = 0; height = context.dp(keyHeight)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(context.dp(2), context.dp(2), context.dp(2), context.dp(2))
            })
        }
        gridScroll.scrollTo(0, 0)
    }
}
