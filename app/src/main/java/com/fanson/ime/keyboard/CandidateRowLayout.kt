package com.fanson.ime.keyboard

data class CandidateRowPlan(val widths: List<Int>, val overflow: Boolean)

/** Packs whole candidates into one row and reserves the arrow only on overflow. */
object CandidateRowLayout {
    fun readingPlan(widths: List<Int>, available: Int, gap: Int): CandidateRowPlan {
        val total = widths.sumOf { it.toLong() } + gap.toLong() * (widths.size - 1).coerceAtLeast(0)
        return CandidateRowPlan(widths, total > available.coerceAtLeast(0))
    }

    fun plan(widths: List<Int>, available: Int, arrowWidth: Int, gap: Int): CandidateRowPlan {
        val width = available.coerceAtLeast(0)
        val total = widths.sumOf { it.toLong() } + gap.toLong() * (widths.size - 1).coerceAtLeast(0)
        if (total <= width) return CandidateRowPlan(widths, false)
        var remaining = (width - arrowWidth.coerceAtMost(width) - gap).coerceAtLeast(0)
        val visible = mutableListOf<Int>()
        for (itemWidth in widths) {
            val spacing = if (visible.isEmpty()) 0 else gap
            if (itemWidth + spacing > remaining) {
                // A single long first candidate is previewed with an ellipsis.
                if (visible.isEmpty() && remaining > 0) visible += remaining
                break
            }
            visible += itemWidth
            remaining -= itemWidth + spacing
        }
        return CandidateRowPlan(visible, true)
    }
}
