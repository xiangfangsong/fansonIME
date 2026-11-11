package com.fanson.ime.ime

import android.view.inputmethod.InputConnection

/** Called only after the existing session-checked, successful quote commit.
 * Read at most one preceding character; never move on an unknown/rewritten tail. */
object PairedQuoteCursor {
    fun target(text: CharSequence, start: Int, end: Int, offset: Int): Int? {
        if (offset < 0 || start != end || start <= 0 || text.getOrNull(start - 1) != '”') return null
        return (offset.toLong() + start - 1).takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
    }
    fun moveInside(connection: InputConnection?): Boolean {
        val cursor = connection?.getSurroundingText(1, 0, 0) ?: return false
        val position = target(cursor.text, cursor.selectionStart, cursor.selectionEnd, cursor.offset) ?: return false
        return connection.setSelection(position, position)
    }
}
