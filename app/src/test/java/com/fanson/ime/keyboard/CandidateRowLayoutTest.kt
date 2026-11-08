package com.fanson.ime.keyboard

import org.junit.Assert.*
import org.junit.Test

class CandidateRowLayoutTest {
    @Test fun emptyHasNoExpansion() {
        assertEquals(CandidateRowPlan(emptyList(), false), CandidateRowLayout.plan(emptyList(), 300, 44, 4))
    }
    @Test fun oneCandidateFitsWithoutArrow() {
        assertEquals(CandidateRowPlan(listOf(90), false), CandidateRowLayout.plan(listOf(90), 100, 44, 4))
    }
    @Test fun completeRowMayUseSpaceThatWouldOtherwiseBeReservedForArrow() {
        assertEquals(CandidateRowPlan(listOf(80, 80), false), CandidateRowLayout.plan(listOf(80, 80), 164, 44, 4))
    }
    @Test fun onePixelOfOverflowMakesRoomForArrow() {
        assertEquals(CandidateRowPlan(listOf(80), true), CandidateRowLayout.plan(listOf(80, 80), 163, 44, 4))
    }
    @Test fun onlyWholeLeadingCandidatesFitBeforeArrow() {
        assertEquals(CandidateRowPlan(listOf(80, 80), true), CandidateRowLayout.plan(listOf(80, 80, 80), 212, 44, 4))
    }
    @Test fun aSingleVeryLongCandidateStillNeedsExpansion() {
        assertEquals(CandidateRowPlan(listOf(152), true), CandidateRowLayout.plan(listOf(500), 200, 44, 4))
    }
    @Test fun narrowRowNeverAllocatesNegativePreviewWidth() {
        assertEquals(CandidateRowPlan(emptyList(), true), CandidateRowLayout.plan(listOf(80), 20, 44, 4))
    }
    @Test fun resizingRecomputesOverflow() {
        val widths = listOf(80, 80, 80)
        assertTrue(CandidateRowLayout.plan(widths, 200, 44, 4).overflow)
        assertFalse(CandidateRowLayout.plan(widths, 248, 44, 4).overflow)
        assertTrue(CandidateRowLayout.plan(widths, 200, 44, 4).overflow)
    }
}
