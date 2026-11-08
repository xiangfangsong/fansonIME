package com.fanson.ime.keyboard

import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.fanson.ime.R
import com.fanson.ime.engine.Candidate

/** Normal candidates use measured slots. Reading mode scrolls without truncating polyphones. */
@android.annotation.SuppressLint("ViewConstructor") // Created with callbacks in code, never inflated from XML.
class CandidateStrip(
    private val ui: Ui,
    private val select: (Candidate) -> Unit,
    private val expand: () -> Unit,
    private val collapse: () -> Unit,
    private val overflowChanged: (Boolean) -> Unit
) : ViewGroup(ui.context) {
    private val buttons = mutableListOf<Button>()
    private var arrow: Button? = null
    private var hint: View? = null
    private var readingScroll: HorizontalScrollView? = null
    val composingLabel: TextView = ui.text("", 12f, ui.palette.muted).apply {
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
    }
    private var composingWidth = 0
    private var contentLeft = 0
    private var plan = CandidateRowPlan(emptyList(), false)
    private var expanded = false
    private var revision = 0
    private var reportedOverflow: Boolean? = null
    val hasOverflow: Boolean get() = plan.overflow
    val visibleCandidateCount: Int get() = plan.widths.size
    private val gap get() = context.dp(4)
    private val arrowWidth get() = context.dp(44)

    init { contentDescription = "候选栏"; clipChildren = true; clipToPadding = true }

    fun submit(candidates: List<Candidate>, expanded: Boolean, emptyHint: String, composition: String = "", readings: Boolean = false) {
        revision++
        reportedOverflow = null
        plan = CandidateRowPlan(emptyList(), false)
        removeAllViews(); buttons.clear(); arrow = null; hint = null; readingScroll = null
        this.expanded = expanded
        composingLabel.text = composition
        composingLabel.contentDescription = composition
        if (composition.isNotEmpty()) addView(composingLabel)
        if (candidates.isEmpty()) {
            hint = ui.text(emptyHint, 13f, ui.palette.muted).apply {
                isSingleLine = true; ellipsize = TextUtils.TruncateAt.END
            }.also { addView(it) }
        } else {
            val readingContent = if (readings) ui.row() else null
            if (readingContent != null) {
                readingScroll = HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    contentDescription = "读音候选，左右滑动查看"
                    addView(readingContent)
                }.also { addView(it) }
            }
            candidates.forEachIndexed { index, candidate ->
                val button = candidateButton(ui, candidate, readings) {
                    select(candidate)
                }.apply {
                    visibility = if (readings) VISIBLE else GONE
                }
                buttons += button
                if (readingContent != null) readingContent.addView(button, LinearLayout.LayoutParams(-2, context.dp(44)).apply { marginEnd = if (index < candidates.lastIndex) gap else 0 })
                else addView(button)
            }
            arrow = ui.iconButton(
                if (expanded) R.drawable.ic_collapse_candidates else R.drawable.ic_expand_candidates,
                if (expanded) "收起候选" else "展开候选"
            ) { if (this.expanded) collapse() else expand() }.also { it.visibility = GONE; addView(it) }
        }
        requestLayout()
    }

    fun showHint(message: String): Boolean {
        val text = hint as? TextView ?: return false
        text.text = message
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = resolveSize(context.dp(44), heightMeasureSpec)
        val natural = buttons.map {
            it.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
            it.measuredWidth
        }
        val hasComposition = composingLabel.parent === this
        if (hasComposition) composingLabel.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        val wantedComposition = if (hasComposition) composingLabel.measuredWidth.coerceAtMost(context.dp(120)) else 0
        val desired = natural.sum() + gap * (natural.size - 1).coerceAtLeast(0) + wantedComposition + if (hasComposition) gap else 0
        val width = resolveSize(desired, widthMeasureSpec)
        composingWidth = wantedComposition.coerceAtMost((width * .3f).toInt())
        contentLeft = if (hasComposition) (composingWidth + gap).coerceAtMost(width) else 0
        val candidateWidth = (width - contentLeft).coerceAtLeast(0)
        if (hasComposition) composingLabel.measure(MeasureSpec.makeMeasureSpec(composingWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        val scroll = readingScroll
        if (scroll != null) {
            plan = CandidateRowLayout.readingPlan(natural, candidateWidth, gap)
            val overflow = plan.overflow
            val arrowSize = if (overflow) minOf(arrowWidth, candidateWidth) else 0
            scroll.measure(MeasureSpec.makeMeasureSpec((candidateWidth - arrowSize).coerceAtLeast(0), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
            arrow?.let {
                it.visibility = if (overflow) VISIBLE else GONE
                it.measure(MeasureSpec.makeMeasureSpec(arrowSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
            }
            setMeasuredDimension(width, height)
            reportOverflow()
            return
        }
        plan = CandidateRowLayout.plan(natural, candidateWidth, arrowWidth, gap)
        buttons.forEachIndexed { index, button ->
            button.visibility = if (index < plan.widths.size) VISIBLE else GONE
            if (index < plan.widths.size) button.measure(
                MeasureSpec.makeMeasureSpec(plan.widths[index], MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            )
        }
        arrow?.let {
            it.visibility = if (plan.overflow) VISIBLE else GONE
            it.measure(MeasureSpec.makeMeasureSpec(minOf(arrowWidth, candidateWidth), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        }
        hint?.measure(MeasureSpec.makeMeasureSpec(candidateWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        setMeasuredDimension(width, height)
        reportOverflow()
    }

    private fun reportOverflow() {
        if (reportedOverflow != plan.overflow) {
            reportedOverflow = plan.overflow
            val value = plan.overflow
            val submitted = revision
            post { if (submitted == revision && hasOverflow == value && isAttachedToWindow) overflowChanged(value) }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (composingLabel.parent === this) composingLabel.layout(0, 0, composingWidth, measuredHeight)
        var x = contentLeft
        if (readingScroll != null) {
            readingScroll?.let { it.layout(x, 0, x + it.measuredWidth, measuredHeight) }
        } else {
            buttons.take(plan.widths.size).forEach { button ->
                button.layout(x, 0, x + button.measuredWidth, measuredHeight)
                x += button.measuredWidth + gap
            }
        }
        arrow?.takeIf { it.visibility == VISIBLE }?.let { it.layout(width - it.measuredWidth, 0, width, measuredHeight) }
        hint?.layout(contentLeft, 0, width, measuredHeight)
    }
}
