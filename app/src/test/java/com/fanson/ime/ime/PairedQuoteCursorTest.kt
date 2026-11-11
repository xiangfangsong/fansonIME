package com.fanson.ime.ime

import org.junit.Assert.*
import org.junit.Test

class PairedQuoteCursorTest {
    @Test fun cursorUsesAbsoluteUtf16OffsetForEmptyAndNonemptyEditor() {
        assertEquals(1, PairedQuoteCursor.target("”", 1, 1, 1))
        assertEquals(12, PairedQuoteCursor.target("”", 1, 1, 12))
        assertEquals(4, PairedQuoteCursor.target("😀“”", 4, 4, 1))
    }
    @Test fun doNotMoveOnUnavailableSelectedOrRewrittenText() {
        assertNull(PairedQuoteCursor.target("”", 1, 1, -1))
        assertNull(PairedQuoteCursor.target("”", 0, 1, 8))
        assertNull(PairedQuoteCursor.target("", 0, 0, 0))
        assertNull(PairedQuoteCursor.target("x", 1, 1, 0))
        assertNull(PairedQuoteCursor.target("”", 2, 2, 0))
        assertNull(PairedQuoteCursor.target("””", 2, 2, Int.MAX_VALUE))
    }
}
