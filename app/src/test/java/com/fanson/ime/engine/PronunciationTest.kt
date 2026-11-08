package com.fanson.ime.engine

import org.junit.Assert.*
import org.junit.Test

class PronunciationTest {
    private val values = mapOf(
        "重" to listOf("zhòng", "chóng", "zhòng"), "庆" to listOf("qìng"),
        "重庆" to listOf("chóng qìng"), "𠮷" to listOf("jí"),
        "单于" to listOf("chán yú", "shàn yú")
    )
    private val dictionary = PronunciationDictionary { values[it].orEmpty() }
    private fun engine() = WubiEngine(object : Lexicon {
        override fun lookup(code: String): List<Candidate> {
            check(code.matches(Regex("[a-y]{1,4}")))
            return if (code in listOf("a", "aa", "aaa", "aaaa")) listOf(Candidate("重", "aaaa", code == "aaaa")) else emptyList()
        }
    }, pronunciation = dictionary)

    @Test fun singleCharacterIncludesAllDistinctTonedReadings() {
        assertEquals("zhòng / chóng", dictionary.reading("重"))
    }
    @Test fun exactPhraseAvoidsUnrelatedCharacterPronunciations() {
        assertEquals("chóng qìng", dictionary.reading("重庆"))
    }
    @Test fun phraseCanHaveMoreThanOneReading() {
        assertEquals("chán yú / shàn yú", dictionary.reading("单于"))
    }
    @Test fun unknownPhraseMarksCharacterFallbackWithoutCartesianProduct() {
        assertEquals("逐字：qìng zhòng/chóng", dictionary.reading("庆重"))
    }
    @Test fun supplementaryCharactersAreNotSplitIntoSurrogates() {
        assertEquals("jí", dictionary.reading("𠮷"))
        assertEquals("逐字：jí zhòng/chóng", dictionary.reading("𠮷重"))
    }
    @Test fun missingReadingIsExplicit() {
        assertEquals("暂无读音", dictionary.reading("𠀀"))
        assertEquals("逐字：暂无读音 zhòng/chóng", dictionary.reading("𠀀重"))
    }
    @Test fun idleZStaysInPreeditUntilRawReturn() {
        val e = engine()
        assertTrue(e.letter('z').commits.isEmpty())
        assertEquals("z", e.code); assertTrue(e.candidates.isEmpty())
        assertEquals(listOf("z"), e.enter().commits)
        assertEquals("", e.code)
    }
    @Test fun zHonoursClearAndFirstCandidateReturnSettings() {
        val e = engine()
        e.enterAction = WubiEnterAction.CLEAR_CODE
        e.letter('z'); assertTrue(e.enter().commits.isEmpty()); assertEquals("", e.code)
        e.enterAction = WubiEnterAction.FIRST_CANDIDATE
        e.letter('z'); assertTrue(e.enter().commits.isEmpty()); assertEquals("z", e.code)
        assertTrue(e.backspace()); assertEquals("", e.code)
    }
    @Test fun rawZNeverReachesTheWubiSqlQuery() {
        val e = engine()
        "zabc".forEach { e.letter(it) }
        assertEquals("zabc", e.code); assertTrue(e.candidates.isEmpty())
        assertEquals(listOf("zabc"), e.enter().commits)
    }
    @Test fun zTogglesAnnotationsWithoutChangingCandidatesOrCommitting() {
        val e = engine(); e.letter('a')
        val before = e.candidates
        assertTrue(e.letter('z').commits.isEmpty())
        assertTrue(e.showingReadings); assertEquals("a", e.code)
        assertEquals("zhòng / chóng", e.candidates.single().reading)
        e.letter('z')
        assertFalse(e.showingReadings); assertEquals(before, e.candidates)
    }
    @Test fun noCandidatesKeepsCodeAndDoesNotToggleReadings() {
        val e = engine(); e.letter('b')
        assertEquals("暂无候选可查读音", e.letter('z').hint)
        assertFalse(e.showingReadings); assertEquals("b", e.code)
    }
    @Test fun readingModeProtectsFourAndFifthCodeFromAutomaticCommit() {
        val e = engine(); e.letter('a'); e.letter('z')
        "aaa".forEach { assertTrue(e.letter(it).commits.isEmpty()) }
        assertEquals("aaaa", e.code); assertTrue(e.showingReadings)
        assertEquals("zhòng / chóng", e.candidates.single().reading)
        assertTrue(e.letter('b').commits.isEmpty()); assertEquals("aaaa", e.code)
        assertEquals(listOf("重"), e.space().commits)
        assertFalse(e.showingReadings)
        assertEquals(listOf("重"), "aaaa".flatMap { e.letter(it).commits })
    }
    @Test fun lookupStateSurvivesCorrectionButEndsWhenCompositionClears() {
        val e = engine(); e.letter('a'); e.letter('z'); e.letter('b')
        assertTrue(e.candidates.isEmpty()); assertTrue(e.showingReadings)
        e.backspace(); assertEquals("zhòng / chóng", e.candidates.single().reading)
        e.backspace(); assertFalse(e.showingReadings)
        e.letter('a'); assertEquals("", e.candidates.single().reading)
    }
    @Test fun manualResolutionNeverCommitsReadingAnnotation() {
        for (action in listOf<(WubiEngine) -> EngineResult>({ it.select(0) }, { it.resolve() }, { it.punctuation("。") })) {
            val e = engine(); e.letter('a'); e.letter('z')
            val result = action(e)
            assertEquals("重", result.commits.first()); assertFalse(e.showingReadings)
        }
    }
    @Test fun clearReturnAndSessionResetDiscardReadingState() {
        val e = engine(); e.letter('a'); e.letter('z')
        e.enter(WubiEnterAction.CLEAR_CODE); assertFalse(e.showingReadings)
        e.letter('a'); e.letter('z'); e.reset()
        assertFalse(e.showingReadings); assertTrue(e.candidates.isEmpty())
    }
    @Test fun traditionalCandidatesLookUpTheirOwnReadings() {
        val converter = TraditionalConverter(mapOf("重" to listOf("異", "重")))
        val e = WubiEngine(object : Lexicon {
            override fun lookup(code: String) = listOf(Candidate("重", code))
        }, converter, PronunciationLexicon { if (it == "異") "yì" else dictionary.reading(it) })
        e.letter('a'); e.letter('z'); e.traditionalMode = true
        assertEquals(listOf("yì", "zhòng / chóng"), e.candidates.map { it.reading })
    }
}
