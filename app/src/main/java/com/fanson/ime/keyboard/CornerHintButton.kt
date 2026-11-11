package com.fanson.ime.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.widget.Button

/** Draw the alternate separately; the primary label keeps a normal Button's
 * single-line layout, padding, gravity and baseline, including enlarged fonts. */
class CornerHintButton @JvmOverloads constructor(context: Context, val cornerHint: String = "", color: Int = Color.GRAY) : Button(context) {
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textAlign = Paint.Align.RIGHT
        textSize = 10f * resources.displayMetrics.scaledDensity
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawText(cornerHint, width - paddingRight - context.dp(3).toFloat(),
            context.dp(3) - hintPaint.fontMetrics.top, hintPaint)
    }
}
