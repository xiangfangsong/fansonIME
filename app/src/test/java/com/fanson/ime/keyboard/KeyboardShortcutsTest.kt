package com.fanson.ime.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyboardShortcutsTest {
    @Test fun numberRowUsesExactUsPcShiftMappingAndRejectsOtherKeys() {
        assertEquals(listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")"), "1234567890".map { KeyboardShortcuts.number(it) })
        assertNull(KeyboardShortcuts.number('a'))
    }
    @Test fun chineseNumberRowUsesFullwidthExclamationAndParenthesesOnly() {
        assertEquals(listOf("！", "@", "#", "$", "%", "^", "&", "*", "（", "）"), "1234567890".map { KeyboardShortcuts.number(it, true) })
        assertNull(KeyboardShortcuts.number('a', true))
    }
    @Test fun enabledNumberRowKeepsChineseQuestionMarkAndLatinQuote() {
        assertEquals("：", KeyboardShortcuts.comma(true, true))
        assertEquals("？", KeyboardShortcuts.period(true, true))
        assertEquals(":", KeyboardShortcuts.comma(false, true))
        assertEquals("\"", KeyboardShortcuts.period(false, true))
    }
    @Test fun disabledNumberRowPreservesOldExclamationAndQuestionShortcuts() {
        assertEquals("！", KeyboardShortcuts.comma(true, false)); assertEquals("？", KeyboardShortcuts.period(true, false))
        assertEquals("!", KeyboardShortcuts.comma(false, false)); assertEquals("?", KeyboardShortcuts.period(false, false))
    }
}
