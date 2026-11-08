package com.fanson.ime.engine

import org.junit.Assert.*
import org.junit.Test

class WubiEngineTest {
    private fun engine(vararg entries: Pair<String, String>) = WubiEngine(object : Lexicon {
        override fun lookup(code: String) = entries.filter { it.first == code }.map { Candidate(it.second, it.first) }
    })
    private fun type(engine: WubiEngine, code: String): List<String> = code.flatMap { engine.letter(it).commits }

    @Test fun abbreviatedCodeWaitsForSpace() {
        val e = engine("a" to "工")
        assertTrue(e.letter('a').commits.isEmpty())
        assertEquals("a", e.code)
        assertEquals(listOf("工"), e.space().commits)
        assertEquals("", e.code)
    }
    @Test fun uniqueFourCodeCommitsImmediately() {
        val e = engine("wqiy" to "你好")
        assertEquals(listOf("你好"), type(e, "wqiy"))
        assertTrue(e.candidates.isEmpty())
        assertEquals("", e.code)
    }
    @Test fun uniqueFourCodeCanWaitWhenDisabled() {
        val e = engine("wqiy" to "你好").apply { autoCommitUnique = false }
        assertTrue(type(e, "wqiy").isEmpty())
        assertEquals(listOf("你好"), e.space().commits)
    }
    @Test fun ambiguousFourCodeDoesNotCommit() {
        val e = engine("aaaa" to "甲", "aaaa" to "乙")
        assertTrue(type(e, "aaaa").isEmpty())
        assertEquals(2, e.candidates.size)
        assertEquals(listOf("乙"), e.select(1).commits)
    }
    @Test fun fifthLetterCommitsFirstAndStartsNextCode() {
        val e = engine("aaaa" to "甲", "aaaa" to "乙", "b" to "了")
        type(e, "aaaa")
        assertEquals(listOf("甲"), e.letter('b').commits)
        assertEquals("b", e.code)
        assertEquals(listOf("了"), e.space().commits)
    }
    @Test fun fifthLetterCanBeDisabled() {
        val e = engine("aaaa" to "甲", "aaaa" to "乙").apply { commitOnFifth = false }
        type(e, "aaaa")
        assertTrue(e.letter('b').commits.isEmpty())
        assertEquals("aaaa", e.code)
    }
    @Test fun missingCodeIsPreservedThroughSpaceAndPunctuation() {
        val e = engine()
        type(e, "abcd")
        assertTrue(e.space().commits.isEmpty())
        assertTrue(e.punctuation("。").commits.isEmpty())
        assertTrue(e.letter('e').commits.isEmpty())
        assertEquals("abcd", e.code)
        assertEquals(listOf("abcd"), e.enter().commits)
    }
    @Test fun backspaceEditsCodeBeforeHostText() {
        val e = engine("a" to "工")
        type(e, "ab")
        assertTrue(e.backspace())
        assertEquals("a", e.code)
        assertEquals("工", e.candidates.first().text)
        assertTrue(e.backspace())
        assertFalse(e.backspace())
    }
    @Test fun punctuationResolvesCandidateBeforeSymbol() {
        val e = engine("a" to "工")
        e.letter('a')
        assertEquals(listOf("工", "。"), e.punctuation("。").commits)
    }
    @Test fun enteringAPanelDoesNotInsertASpace() {
        val e = engine()
        assertTrue(e.resolve().commits.isEmpty())
        assertEquals(listOf(" "), e.space().commits)
    }
    @Test fun returnSubmitsRawCodeEvenWithCandidates() {
        val e = engine("a" to "工")
        e.letter('a')
        assertEquals(listOf("a"), e.enter().commits)
        assertTrue(e.enter().commits.isEmpty())
    }
    @Test fun returnAndSpaceHaveDifferentMeaningsWithTheSameCode() {
        val e = engine("a" to "工")
        e.letter('a'); assertEquals(listOf("a"), e.enter().commits)
        e.letter('a'); assertEquals(listOf("工"), e.space().commits)
    }
    @Test fun returnSubmitsFourCodeAmbiguityAsLetters() {
        val e = engine("aaaa" to "甲", "aaaa" to "乙")
        type(e, "aaaa")
        assertEquals(listOf("aaaa"), e.enter().commits)
        assertEquals("", e.code)
        assertTrue(e.candidates.isEmpty())
    }
    @Test fun resetCannotCarryTextIntoAnotherSession() {
        val e = engine("a" to "工")
        e.letter('a'); e.reset()
        assertEquals("", e.code)
        assertTrue(e.candidates.isEmpty())
        assertTrue(e.enter().commits.isEmpty())
    }
    @Test fun invalidSelectionDoesNotDiscardPreedit() {
        val e = engine("a" to "工")
        e.letter('a')
        assertTrue(e.select(20).commits.isEmpty())
        assertEquals("a", e.code)
    }
    @Test fun reservedZCannotCorruptCode() {
        val e = engine("a" to "工")
        e.letter('a'); assertNotNull(e.letter('z').hint)
        assertEquals("a", e.code)
    }
    @Test fun prefixCompletionIsExplicitAndCannotTriggerFourCodeAutoCommit() {
        val e = WubiEngine(object : Lexicon {
            override fun lookup(code: String) = listOf(Candidate("补全", "aaaab", false))
        })
        assertTrue(type(e, "aaaa").isEmpty())
        assertEquals("aaaa", e.code)
        assertFalse(e.candidates.single().exact)
    }
    @Test fun phraseEncodingUsesFullCodes() {
        assertEquals("wqiy", PhraseEncoder.encode(listOf("wqiy", "iybg")))
        assertEquals("wiyb", PhraseEncoder.encode(listOf("wqiy", "iybg", "ybcd")))
        assertEquals("wiyd", PhraseEncoder.encode(listOf("wqiy", "iybg", "ybcd", "dabc")))
        assertEquals("wiyf", PhraseEncoder.encode(listOf("wqiy", "iybg", "ybcd", "dabc", "fghi")))
        assertNull(PhraseEncoder.encode(listOf("w", "iybg")))
        assertNull(PhraseEncoder.encode(listOf("wqiy")))
        assertNull(PhraseEncoder.encode(listOf("abcd", "zzzz")))
    }
}
