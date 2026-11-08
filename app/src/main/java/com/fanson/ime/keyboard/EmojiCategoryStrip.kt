package com.fanson.ime.keyboard

import android.content.Context
import android.view.View
import android.widget.HorizontalScrollView

/** Restores position after native layout/focus adjustment, without a visible bar. */
@android.annotation.SuppressLint("ViewConstructor") // Code-only view with explicit retained-position state.
class EmojiCategoryStrip(context: Context, private val initialX: Int, private val positionChanged: (EmojiCategoryStrip, Int) -> Unit) : HorizontalScrollView(context) {
    private var restoring = true
    val retainedScrollX: Int get() = if (restoring) initialX else scrollX

    init {
        contentDescription = "表情分类"
        isHorizontalScrollBarEnabled = false; isVerticalScrollBarEnabled = false
        isHorizontalFadingEdgeEnabled = false; isVerticalFadingEdgeEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (restoring) {
            restoring = false
            scrollTo(initialX, 0)
            positionChanged(this, scrollX)
        }
    }
    override fun onScrollChanged(left: Int, top: Int, oldLeft: Int, oldTop: Int) {
        super.onScrollChanged(left, top, oldLeft, oldTop)
        if (!restoring) positionChanged(this, left)
    }
}
