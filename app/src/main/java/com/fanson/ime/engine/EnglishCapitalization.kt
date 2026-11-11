package com.fanson.ime.engine

/** Session-only English case policy. The host supplies the sentence boundary. */
class EnglishCapitalization {
    enum class Shift { LOWER, AUTO, CAPS }
    private var caps = false
    private var automatic = false
    private var suppressed = false
    val shift: Shift get() = when { caps -> Shift.CAPS; automatic -> Shift.AUTO; else -> Shift.LOWER }
    val uppercase: Boolean get() = shift != Shift.LOWER

    fun refresh(sentenceStart: Boolean) { automatic = sentenceStart && !suppressed }
    fun toggle() {
        if (shift == Shift.AUTO) { automatic = false; suppressed = true }
        else { caps = !caps; automatic = false; suppressed = !caps }
    }
    fun letter(char: Char): Char {
        val result = if (uppercase) char.uppercaseChar() else char
        automatic = false
        return result
    }
    /** A delimiter/finished word ends a temporary opt-out; manual caps survives. */
    fun boundary() { automatic = false; suppressed = false }
    fun positionChanged() = boundary()
    fun reset(locked: Boolean = false) { caps = locked; boundary() }
}
