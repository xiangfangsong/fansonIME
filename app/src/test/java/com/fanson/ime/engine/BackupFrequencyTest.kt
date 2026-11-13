package com.fanson.ime.engine

import org.junit.Assert.*
import org.junit.Test

class BackupFrequencyTest {
    private val now = 1_800_000_000_000L
    private val original = Candidate("合成旧词", "a")
    private val restored = FrequencyRecord(FrequencyKey("a", "a", "合成新词", "合成新词"), 5.0, now, false)

    @Test fun snapshotIncludesUnsavedLearningWithoutAcknowledgingIt() {
        val model = WubiFrequency()
        model.record("a", original, model.epoch, now)
        assertEquals(1, model.snapshot(now).size)
        assertEquals(1, model.pendingSize)
        assertTrue(model.snapshot(now + WubiFrequency.HALF_LIFE_MS * 6).isEmpty())
        assertEquals(1, model.size)
    }

    @Test fun committedRestoreRejectsOldAndInFlightFeedbackAndDoesNotResaveOldRows() {
        val model = WubiFrequency()
        model.record("a", original, model.epoch, now)
        val epoch = model.epoch
        model.beginRestore()
        assertFalse(model.record("a", original, model.epoch, now))
        model.finishRestore(listOf(restored), now)
        assertFalse(model.record("a", original, epoch, now))
        assertEquals(listOf(restored), model.snapshot(now))
        assertTrue(model.prepareSave(now).changes.isEmpty())
        assertTrue(model.record("a", original, model.epoch, now))
    }

    @Test fun failedRestorePreservesDirtyLearningAndResumesWithNewEpoch() {
        val model = WubiFrequency()
        model.record("a", original, model.epoch, now)
        val snapshot = model.snapshot(now)
        val epoch = model.epoch
        model.beginRestore(); model.finishRestore(null, now)
        assertEquals(snapshot, model.snapshot(now))
        assertEquals(1, model.pendingSize)
        assertFalse(model.record("a", original, epoch, now))
        assertTrue(model.record("a", original, model.epoch, now))
        assertEquals(2.0, model.snapshot(now).single().score, 0.0)
    }
}
