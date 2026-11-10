package com.fanson.ime.engine

import org.junit.Assert.*
import org.junit.Test

class WubiFrequencyTest {
    private val now = 2_000_000_000_000L
    private val half = WubiFrequency.HALF_LIFE_MS
    private val a = Candidate("甲", "a")
    private val b = Candidate("乙", "a")
    private fun use(model: WubiFrequency, candidate: Candidate, at: Long = now, code: String = "a") {
        check(model.record(code, candidate, model.epoch, at))
    }
    private fun score(model: WubiFrequency, candidate: Candidate, at: Long = now) =
        model.prepareSave(at).changes[WubiFrequency.key("a", candidate)]!!.record!!.score

    @Test fun halfLifeCapAndExpiryRestoreOriginalOrder() {
        val model = WubiFrequency()
        repeat(100) { use(model, b) }
        assertEquals(16.0, score(model, b), 0.0)
        val row = FrequencyRecord(WubiFrequency.key("a", b), 16.0, now, false)
        assertEquals(8.0, WubiFrequency.effective(row, now + half), 1e-9)
        assertEquals(listOf(b, a), model.rank("a", listOf(a, b), now + half * 5))
        assertEquals(listOf(a, b), model.rank("a", listOf(a, b), now + half * 6))
        model.prepareSave(now + half * 6)
        assertEquals(0, model.size)
    }
    @Test fun newUseDecaysOldWeightInsteadOfRevivingIt() {
        val model = WubiFrequency()
        repeat(16) { use(model, b) }
        use(model, b, now + half * 4)
        assertEquals(2.0, score(model, b, now + half * 4), 1e-9)
    }
    @Test fun recentPreferenceBeatsOldBurst() {
        val model = WubiFrequency()
        repeat(100) { use(model, a) }
        repeat(3) { use(model, b, now + half * 3) }
        assertEquals(listOf(b, a), model.rank("a", listOf(a, b), now + half * 3))
    }
    @Test fun stableTiesAndTierBoundaries() {
        val model = WubiFrequency()
        val phrase = Candidate("短语", "a", custom = true)
        val completion = Candidate("补全", "abcd", false)
        repeat(16) { use(model, completion) }
        use(model, b)
        val input = listOf(phrase, a, b, completion)
        assertEquals(listOf(phrase, b, a, completion), model.rank("a", input, now))
        use(model, a)
        assertEquals(input, model.rank("a", input, now))
    }
    @Test fun typedCodesAndTraditionalFormsLearnIndependently() {
        val model = WubiFrequency()
        val simplified = Candidate("发", "abcd")
        val variants = TraditionalConverter(mapOf("发" to listOf("發", "髮"))).candidates(listOf(simplified))
        val hair = variants.last()
        assertEquals("发", hair.sourceText)
        use(model, hair)
        assertEquals(listOf(hair, variants.first()), model.rank("a", variants, now))
        assertEquals(variants, model.rank("ab", variants, now))
        assertEquals(listOf(simplified), model.rank("a", listOf(simplified), now))
        assertEquals(listOf(simplified.copy(exact = false)), model.recall("a", now))
    }
    @Test fun recallBoundAndStaleRowsCannotManufactureCandidates() {
        val model = WubiFrequency()
        repeat(200) { use(model, Candidate("词$it", "abcd", false)) }
        assertEquals(64, model.recall("a", now).size)
        assertTrue(model.recall("b", now).isEmpty())
        assertTrue(model.recall("a", now + half * 3).isEmpty())
    }
    @Test fun capacityAndUnsavedTombstonesStayBounded() {
        val model = WubiFrequency(20, 18)
        repeat(2000) {
            use(model, Candidate("词$it", "a"), now + it)
            assertTrue(model.size <= 20)
            assertTrue(model.pendingSize <= 20)
        }
        val batch = model.prepareSave(now + 2000)
        assertEquals(18, model.size)
        model.acknowledge(batch)
        assertEquals(0, model.pendingSize)
    }
    @Test fun fullHighScoreTableStillAdmitsNewHabits() {
        val model = WubiFrequency(2, 2)
        repeat(16) { use(model, a); use(model, b) }
        val c = Candidate("新习惯", "a")
        use(model, c)
        use(model, c)
        assertEquals(2.0, score(model, c), 0.0)
        assertEquals(2, model.size)
    }
    @Test fun evictingInFlightInsertsProducesDeletesAndNoLostUpdate() {
        val model = WubiFrequency(2, 2)
        use(model, a); use(model, b); use(model, b)
        val batch = model.prepareSave(now)
        val c = Candidate("丙", "a")
        use(model, c, now + 100)
        use(model, b, now + 100)
        model.acknowledge(batch)
        val next = model.prepareSave(now + 100)
        assertNull(next.changes[WubiFrequency.key("a", a)]!!.record)
        assertTrue(next.changes[WubiFrequency.key("a", b)]!!.record!!.score > 1)
        assertNotNull(next.changes[WubiFrequency.key("a", c)]!!.record)
    }
    @Test fun snapshotAcknowledgementDoesNotClearNewLearning() {
        val model = WubiFrequency()
        use(model, a)
        val old = model.prepareSave(now)
        use(model, a)
        model.acknowledge(old)
        assertEquals(2.0, score(model, a), 0.0)
        val current = model.prepareSave(now)
        model.acknowledge(current)
        assertTrue(model.prepareSave(now).changes.isEmpty())
    }
    @Test fun repeatedFailedSavesCannotAccumulatePhantomDiskTombstones() {
        val model = WubiFrequency(20, 18)
        repeat(20) { use(model, Candidate("已保存$it", "a")) }
        model.acknowledge(model.prepareSave(now))
        repeat(200) { session ->
            repeat(20) { use(model, Candidate("未存${session}_$it", "a"), now + session + 1) }
            val failed = model.prepareSave(now + session + 1)
            repeat(20) { use(model, Candidate("在写${session}_$it", "a"), now + session + 2) }
            model.failed(failed)
            assertTrue(model.pendingSize <= 40)
            assertTrue(model.size <= 20)
        }
    }
    @Test fun backgroundExpirationCanInterleaveWithFreshFeedback() {
        val model = WubiFrequency(2000, 1800)
        repeat(2000) { use(model, Candidate("旧$it", "a")) }
        val persisted = model.prepareSave(now)
        model.acknowledge(persisted)
        val later = now + half * 6
        val error = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val start = java.util.concurrent.CountDownLatch(1)
        val maintenance = Thread {
            try {
                start.countDown()
                val batch = model.prepareSave(later)
                model.acknowledge(batch)
            } catch (failure: Throwable) { error.set(failure) }
        }.apply { isDaemon = true; start() }
        assertTrue(start.await(5, java.util.concurrent.TimeUnit.SECONDS))
        repeat(1000) { use(model, Candidate("新$it", "a"), later) }
        maintenance.join(5000)
        assertFalse(maintenance.isAlive)
        assertNull(error.get())
        assertEquals(1000, model.size)
        assertEquals(64, model.recall("a", later).size)
    }
    @Test fun clearRejectsOldFeedbackAndOldAcknowledgement() {
        val model = WubiFrequency()
        val before = model.epoch
        use(model, a)
        val old = model.prepareSave(now)
        model.clear()
        assertFalse(model.record("a", b, before, now))
        use(model, b)
        model.acknowledge(old)
        assertEquals(1, model.size)
        assertEquals(1, model.pendingSize)
    }
    @Test fun loadingClampsFutureTimeAndInvalidScores() {
        val key = WubiFrequency.key("a", a)
        val model = WubiFrequency()
        model.load(listOf(FrequencyRecord(key, 100.0, now + half, false)), now)
        assertEquals(16.0, score(model, a), 0.0)
        val invalid = WubiFrequency()
        invalid.load(listOf(FrequencyRecord(key, Double.NaN, now, false)), now)
        assertEquals(0, invalid.size)
        assertNull(invalid.prepareSave(now).changes[key]!!.record)
    }
    @Test fun phraseDeletionPreservesIdenticalBuiltinButRemovesCustomOnly() {
        val model = WubiFrequency()
        val customA = a.copy(custom = true)
        val customB = b.copy(custom = true)
        use(model, customA); use(model, customB)
        model.removePhrase("a", a.text, true)
        model.removePhrase("a", b.text, false)
        assertEquals(1, model.size)
        assertEquals(listOf(a), model.recall("a", now))
    }
    @Test fun feedbackRequiresAcceptedEligibleCommitAndMatchingEpoch() {
        val model = WubiFrequency()
        val result = EngineResult(listOf(a.text, "。"), selections = listOf(WubiSelection(0, "a", a)))
        val eligible = FrequencyPermit(true, model.epoch)
        assertFalse(model.accept(result, 0, false, eligible, now))
        assertFalse(model.accept(result, 0, true, eligible.copy(allowed = false), now))
        assertFalse(model.accept(result, 1, true, eligible, now))
        assertFalse(model.accept(result.copy(commits = listOf("别的文字")), 0, true, eligible, now))
        assertEquals(0, model.size)
        assertTrue(model.accept(result, 0, true, eligible, now))
        assertEquals(1.0, score(model, a), 0.0)
        model.clear()
        assertFalse(model.accept(result, 0, true, eligible, now))
    }
    @Test fun engineMetadataCoversSelectionsAutoFifthResolveEnterAndPunctuation() {
        val dictionary = object : Lexicon {
            override fun lookup(code: String) = when (code) {
                "a" -> listOf(a, b)
                "aaaa" -> listOf(a.copy(code = code), b.copy(code = code))
                "bbbb" -> listOf(Candidate("唯一", code))
                else -> emptyList()
            }
        }
        fun engine() = WubiEngine(dictionary)
        fun verify(result: EngineResult, code: String, text: String) {
            val selected = result.selections.single()
            assertEquals(0, selected.commitIndex)
            assertEquals(code, selected.inputCode)
            assertEquals(text, selected.candidate.text)
            assertEquals(text, result.commits[selected.commitIndex])
        }
        for (operation in listOf<(WubiEngine) -> EngineResult>(
            { it.select(1) }, { it.space() }, { it.resolve() },
            { it.enter(WubiEnterAction.FIRST_CANDIDATE) }, { it.punctuation("。") }
        )) {
            val e = engine(); e.letter('a'); val result = operation(e)
            verify(result, "a", result.commits.first())
        }
        val fifth = engine(); "aaaa".forEach { fifth.letter(it) }
        verify(fifth.letter('b'), "aaaa", "甲")
        val unique = engine(); "bbb".forEach { unique.letter(it) }
        verify(unique.letter('b'), "bbbb", "唯一")
        val raw = engine(); raw.letter('a')
        assertTrue(raw.enter().selections.isEmpty())
        assertTrue(raw.punctuation("。").selections.isEmpty())
    }
    @Test fun engineSwitchRestoresFixedOrderAndKeepsScores() {
        val model = WubiFrequency()
        use(model, b)
        val engine = WubiEngine(object : Lexicon { override fun lookup(code: String) = listOf(a, b) }, frequency = model, clock = { now })
        engine.letter('a')
        assertEquals(listOf(b, a), engine.candidates)
        engine.frequencyEnabled = false
        assertEquals(listOf(a, b), engine.candidates)
        engine.frequencyEnabled = true
        assertEquals(listOf(b, a), engine.candidates)
    }
}
