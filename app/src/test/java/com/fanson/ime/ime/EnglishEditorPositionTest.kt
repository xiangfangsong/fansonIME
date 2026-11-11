package com.fanson.ime.ime

import org.junit.Assert.*
import org.junit.Test

class EnglishEditorPositionTest {
    @Test fun composingReplacementAndCandidateLengthTrackTheirActualAnchor() {
        val position = EnglishEditorPosition()
        position.reset(5, 5); position.compose(1); position.compose(3)
        assertTrue(position.matches(8, 8)); position.commit(5)
        assertTrue(position.matches(10, 10)); position.commit(1); position.compose(2)
        assertTrue(position.matches(13, 13)); assertFalse(position.matches(12, 12))
    }
    @Test fun selectionReplacementFinishAndDeleteDoNotReuseAnOldComposingAnchor() {
        val position = EnglishEditorPosition()
        position.reset(8, 2); position.compose(3); position.finishComposing(); position.commit(1)
        assertTrue(position.matches(6, 6)); position.deleteBefore(2); assertTrue(position.matches(4, 4))
        position.reset(1, 4); position.commit(0); assertTrue(position.matches(1, 1))
        position.reset(1, 1); position.compose(1); position.compose(0); position.commit(2)
        assertTrue(position.matches(3, 3))
    }
    @Test fun unknownEditorOffsetsRemainUnknownUntilReconciled() {
        val position = EnglishEditorPosition()
        position.reset(-1, -1); position.compose(5); position.commit(4); position.deleteBefore(1)
        assertFalse(position.matches(8, 8)); position.reset(8, 8); assertTrue(position.matches(8, 8))
    }
}
