package com.fanson.ime.ime

/** Predict only edits accepted by InputConnection, to distinguish our cursor
 * callbacks from navigation. Unknown/host-adjusted positions are reconciled by
 * the service against the live, zero-character surrounding-position query. */
internal class EnglishEditorPosition {
    private var start = -1
    private var end = -1
    private var composingStart = -1
    fun reset(selectionStart: Int, selectionEnd: Int) {
        start = selectionStart; end = selectionEnd; composingStart = -1
    }
    fun matches(selectionStart: Int, selectionEnd: Int) = start >= 0 && start == selectionStart && end == selectionEnd
    private fun replace(length: Int): Int {
        if (start < 0 || end < 0) return -1
        val base = if (composingStart >= 0) composingStart else minOf(start, end)
        start = base + length; end = start
        return base
    }
    fun commit(length: Int) { replace(length); composingStart = -1 }
    fun compose(length: Int) {
        val base = replace(length)
        composingStart = if (length > 0) base else -1
    }
    fun finishComposing() { composingStart = -1 }
    fun deleteBefore(length: Int) {
        if (start < 0 || end < 0) return
        start = if (start != end) minOf(start, end) else (start - length).coerceAtLeast(0)
        end = start; composingStart = -1
    }
}
