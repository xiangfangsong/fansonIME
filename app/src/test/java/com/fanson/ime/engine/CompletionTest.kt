package com.fanson.ime.engine

import org.junit.Assert.*
import org.junit.Test

class CompletionTest {
    private val converter = TraditionalConverter(mapOf(
        "发" to listOf("發", "髮"), "头" to listOf("頭"), "头发" to listOf("頭髮"),
        "发展" to listOf("發展"), "国" to listOf("國"), "干" to listOf("幹", "乾", "干")
    ))
    private fun wubi(text: String = "发") = WubiEngine(object : Lexicon {
        override fun lookup(code: String) = listOf(Candidate(text, code))
    }, converter)
    private fun english() = EnglishEngine(EnglishLexicon(listOf("application", "apple", "apply", "app", "banana")))
    private fun type(e: InputEngine, text: String) = text.map { e.letter(it) }

    @Test fun allTraditionalSingleCharacterAlternatives() { assertEquals(listOf("發", "髮"), converter.convert("发")); assertEquals(3, converter.convert("干").size) }
    @Test fun phrasesDisambiguate() { assertEquals(listOf("頭髮"), converter.convert("头发")); assertEquals(listOf("發展"), converter.convert("发展")) }
    @Test fun longestPhraseWithinCustomTextAndUnknownCharacters() { assertEquals(listOf("頭髮😀ABC國"), converter.convert("头发😀ABC国")) }
    @Test fun deduplicateWithoutLosingCodeOrExactStatus() {
        val result = converter.candidates(listOf(Candidate("发", "v"), Candidate("發", "v"), Candidate("发", "vnt", false)))
        assertEquals(4, result.size); assertFalse(result.last().exact)
    }
    @Test fun realtimeTogglePreservesCode() {
        val e = wubi(); e.letter('v'); assertEquals("发", e.candidates.first().text)
        e.traditionalMode = true; assertEquals("v", e.code); assertEquals(listOf("發", "髮"), e.candidates.map { it.text })
        e.traditionalMode = false; assertEquals("发", e.candidates.single().text)
    }
    @Test fun ambiguityPreventsUniqueAutoCommit() {
        val e = wubi(); e.traditionalMode = true
        assertTrue(type(e, "vntc").flatMap { it.commits }.isEmpty()); assertEquals("vntc", e.code)
        assertEquals(listOf("髮"), e.select(1).commits)
    }
    @Test fun unambiguousTraditionalStillAutoCommits() { val e = wubi("国"); e.traditionalMode = true; assertEquals(listOf("國"), type(e, "lgyi").flatMap { it.commits }) }
    @Test fun spacePunctuationAndFifthUseConvertedCandidates() {
        val e = wubi(); e.traditionalMode = true
        e.letter('v'); assertEquals(listOf("發"), e.space().commits)
        e.letter('v'); assertEquals(listOf("發", "，"), e.punctuation("，").commits)
        type(e, "vntc"); assertEquals(listOf("發"), e.letter('a').commits); assertEquals("a", e.code)
    }
    @Test fun traditionalEnterKeepsRawLetters() { val e = wubi(); e.traditionalMode = true; e.letter('v'); assertEquals(listOf("v"), e.enter().commits) }
    @Test fun traditionalEnterCanSelectFirstConvertedCandidate() {
        val e = wubi().apply { traditionalMode = true; enterAction = WubiEnterAction.FIRST_CANDIDATE }
        e.letter('v')
        assertEquals(listOf("發"), e.enter().commits)
        assertTrue(e.code.isEmpty() && e.candidates.isEmpty())
    }
    @Test fun englishPrefixAndExactPriority() {
        val e = english(); type(e, "app")
        assertEquals(listOf("app", "application", "apple", "apply"), e.candidates.map { it.text })
        assertTrue(e.candidates.all { it.exact && it.code.isEmpty() })
    }
    @Test fun typingComposesWithoutCommitting() { val e = english(); val r = type(e, "app"); assertTrue(r.all { it.commits.isEmpty() }); assertEquals("app", r.last().composing) }
    @Test fun candidateSelectionReplacesWord() { val e = english(); type(e, "app"); assertEquals(listOf("apple"), e.select(2).commits); assertEquals("", e.code); assertTrue(e.candidates.isEmpty()) }
    @Test fun unknownWordSpaceRemainsLiteral() { val e = english(); type(e, "qzzq"); assertTrue(e.candidates.isEmpty()); assertEquals(listOf("qzzq", " "), e.space().commits) }
    @Test fun spaceNeverSilentlyCorrectsPrefix() { val e = english(); type(e, "ap"); assertEquals(listOf("ap", " "), e.space().commits) }
    @Test fun uppercaseAndTitlecase() { val e = english(); type(e, "APP"); assertTrue(e.candidates.any { it.text == "APPLE" }); e.reset(); type(e, "App"); assertTrue(e.candidates.any { it.text == "Apple" }) }
    @Test fun deleteRefreshesThenFallsBackToEditor() { val e = english(); type(e, "ap"); assertTrue(e.backspace()); assertEquals("a", e.code); assertTrue(e.backspace()); assertTrue(e.candidates.isEmpty()); assertFalse(e.backspace()) }
    @Test fun disabledIsDirectInput() { val e = english(); e.enabled = false; assertEquals(listOf("A"), e.letter('A').commits); assertEquals("", e.code); assertTrue(e.candidates.isEmpty()) }
    @Test fun enterAndPanelResolveLiteral() { val e = english(); type(e, "app"); assertEquals(listOf("app"), e.enter().commits); type(e, "ap"); assertEquals(listOf("ap"), e.resolve().commits) }
    @Test fun punctuationFollowsCurrentWord() { val e = english(); type(e, "app"); assertEquals(listOf("app", "."), e.punctuation(".").commits) }
    @Test fun resetAndInvalidSelection() { val e = english(); type(e, "app"); assertTrue(e.select(-1).commits.isEmpty()); assertEquals("app", e.code); e.reset(); assertTrue(e.code.isEmpty() && e.candidates.isEmpty()) }
    @Test fun prefixRangeExcludesOtherWordsAndLimitsLargePools() {
        val lexicon = EnglishLexicon((0..100).map { "app" + ('a' + it / 26) + ('a' + it % 26) } + "banana")
        assertEquals(80, lexicon.lookup("app").size); assertTrue(lexicon.lookup("qzz").isEmpty()); assertTrue(lexicon.lookup("").isEmpty())
    }
}
