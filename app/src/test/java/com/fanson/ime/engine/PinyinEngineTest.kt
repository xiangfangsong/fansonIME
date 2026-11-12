package com.fanson.ime.engine

import org.junit.Assert.*
import org.junit.Test

class PinyinEngineTest {
    private fun engine() = PinyinEngine(object : Lexicon {
        override fun lookup(code: String) = when (PinyinSpelling.key(code)) {
            "ni" -> listOf(Candidate("你", "ni"), Candidate("呢", "ni"))
            "nihao" -> listOf(Candidate("你好", "nihao"))
            "guo" -> listOf(Candidate("国", "guo"))
            else -> emptyList()
        }
    }, TraditionalConverter(mapOf("国" to listOf("國"))))
    private fun type(e: InputEngine, code: String) = code.map(e::letter)

    @Test fun wordsNeverCommitAtFourOrFiveLettersAndUseInternalPreedit() {
        val e = engine()
        assertTrue(type(e, "NIHAO").all { it.commits.isEmpty() && it.composing == null && it.selections.isEmpty() })
        assertEquals("nihao", e.code)
        assertEquals(listOf("你好"), e.space().commits)
        assertTrue(e.code.isEmpty() && e.candidates.isEmpty())
    }
    @Test fun selectAlternativeAndInvalidSelection() {
        val e = engine(); type(e, "ni")
        assertTrue(e.select(99).commits.isEmpty()); assertEquals("ni", e.code)
        assertEquals(listOf("呢"), e.select(1).commits)
    }
    @Test fun noMatchBlocksResolveSpaceAndPunctuationUntilCorrection() {
        val e = engine(); type(e, "niz")
        for (result in listOf(e.space(), e.resolve(), e.punctuation("，"))) {
            assertTrue(result.commits.isEmpty()); assertNotNull(result.hint)
        }
        assertEquals("niz", e.code); assertTrue(e.backspace())
        assertEquals(listOf("你", "，"), e.punctuation("，").commits)
    }
    @Test fun enterCommitsRawCodeWithoutAnExtraNewlineOrWordSelection() {
        val e = engine(); type(e, "ni'hao")
        assertEquals(listOf("ni'hao"), e.enter().commits)
        assertTrue(e.code.isEmpty()); assertTrue(e.enter().commits.isEmpty())
        assertEquals(listOf(" "), e.space().commits)
    }
    @Test fun backspaceResetAndLiteralZ() {
        val e = engine(); type(e, "ni"); assertTrue(e.backspace()); assertEquals("n", e.code)
        e.reset(); assertFalse(e.backspace()); e.letter('z'); assertEquals("z", e.code)
        e.reset(); assertTrue(e.candidates.isEmpty())
    }
    @Test fun separatorValidationAndLengthBound() {
        val e = engine(); assertNotNull(e.letter('\'').hint); assertEquals("", e.code)
        type(e, "ni'"); assertNotNull(e.letter('\'').hint); assertEquals("ni'", e.code)
        e.reset(); type(e, "z".repeat(64)); assertNotNull(e.letter('z').hint); assertEquals(64, e.code.length)
    }
    @Test fun traditionalTogglePreservesCodeAndConvertsCandidate() {
        val e = engine(); type(e, "guo"); e.traditionalMode = true
        assertEquals("guo", e.code); assertEquals("國", e.candidates.first().text)
        assertEquals(listOf("國"), e.resolve().commits)
    }
    @Test fun canonicalUmlautsTonesAndAliasesAgree() {
        assertEquals("lv", PinyinSpelling.key("lǚ")); assertEquals("nv", PinyinSpelling.key("nǚ"))
        assertEquals("lve", PinyinSpelling.key("lüè")); assertEquals("lve", PinyinSpelling.key("lve"))
        assertEquals("jve", PinyinSpelling.key("jve")); assertEquals("xian", PinyinSpelling.key("xi'an"))
        assertEquals("nver", PinyinSpelling.key("nv'er")); assertEquals("nver", PinyinSpelling.key("nver"))
        assertEquals("nveren", PinyinSpelling.key("nveren"))
    }
    @Test fun explicitSyllableBoundariesDisambiguateWithoutInventingReadings() {
        assertTrue(PinyinSpelling.matchesBoundaries("xi'an", "xi an"))
        assertFalse(PinyinSpelling.matchesBoundaries("xi'an", "xian"))
        assertTrue(PinyinSpelling.matchesBoundaries("xian", "xi an"))
        assertTrue(PinyinSpelling.matchesBoundaries("zhi'fu'bao", "zhi fu bao"))
        assertFalse(PinyinSpelling.matchesBoundaries("zh'ifubao", "zhi fu bao"))
    }
}
