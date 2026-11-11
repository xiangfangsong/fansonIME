package com.fanson.ime.keyboard

/** PC number-row shortcuts, with explicit Chinese punctuation overrides. */
object KeyboardShortcuts {
    const val QUOTE_PAIR = "“”"
    private const val DIGITS = "1234567890"
    private const val SYMBOLS = "!@#$%^&*()"
    fun number(digit: Char, chinese: Boolean = false): String? = when {
        chinese && digit == '1' -> "！"
        chinese && digit == '9' -> "（"
        chinese && digit == '0' -> "）"
        else -> DIGITS.indexOf(digit).takeIf { it >= 0 }?.let { SYMBOLS[it].toString() }
    }
    fun comma(chinese: Boolean, numberRow: Boolean): String =
        if (numberRow) { if (chinese) "：" else ":" } else { if (chinese) "！" else "!" }
    fun period(chinese: Boolean, numberRow: Boolean): String =
        if (chinese) "？" else if (numberRow) "\"" else "?"
}
